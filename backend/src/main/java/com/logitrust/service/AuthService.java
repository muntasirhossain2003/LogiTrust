package com.logitrust.service;

import com.logitrust.config.JwtProperties;
import com.logitrust.domain.RefreshToken;
import com.logitrust.domain.Role;
import com.logitrust.domain.User;
import com.logitrust.dto.AuthTokensResponse;
import com.logitrust.dto.LoginRequest;
import com.logitrust.dto.OtpRequiredResponse;
import com.logitrust.dto.RegisterRequest;
import com.logitrust.exception.AccountLockedException;
import com.logitrust.exception.EmailAlreadyRegisteredException;
import com.logitrust.exception.InvalidCredentialsException;
import com.logitrust.exception.InvalidOtpException;
import com.logitrust.exception.InvalidTokenException;
import com.logitrust.repository.RefreshTokenRepository;
import com.logitrust.repository.UserRepository;
import com.logitrust.security.JwtService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final long LOCKOUT_MINUTES = 15;
    private static final long OTP_VALIDITY_MINUTES = 5;
    private static final int MAX_OTP_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            EmailService emailService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.emailService = emailService;
    }

    @Transactional
    public User register(RegisterRequest request) {
        if (request.role() == Role.ADMIN) {
            // Platform admins are provisioned out of band, never self-registered.
            throw new IllegalArgumentException("Cannot self-register as ADMIN.");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyRegisteredException();
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                // 2FA is mandatory for Manufacturer accounts per SRS 7.1; everyone
                // else can opt in later via a settings endpoint (not in Phase 1 scope).
                .twoFactorEnabled(request.role() == Role.MANUFACTURER)
                .build();

        return userRepository.save(user);
    }

    // noRollbackFor: a wrong password increments the failed-attempt counter
    // and then throws; the counter write must survive the exception or the
    // lockout can never trigger.
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public LoginOutcome login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (user.isLocked()) {
            throw new AccountLockedException(user.getLockedUntil());
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailedAttempt(user);
            throw new InvalidCredentialsException();
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);

        if (user.isTwoFactorEnabled()) {
            String code = generateOtpCode();
            user.setOtpCodeHash(passwordEncoder.encode(code));
            user.setOtpExpiresAt(Instant.now().plusSeconds(OTP_VALIDITY_MINUTES * 60));
            user.setOtpAttempts(0);
            userRepository.save(user);

            emailService.sendOtpEmail(user.getEmail(), code);

            String otpToken = jwtService.generateOtpPendingToken(user);
            return new LoginOutcome.OtpRequired(
                    new OtpRequiredResponse(otpToken, jwtProperties.otpPendingTtlSeconds()));
        }

        userRepository.save(user);
        return new LoginOutcome.Authenticated(issueTokens(user));
    }

    // noRollbackFor: wrong-code attempts increment otpAttempts before
    // throwing — same persistence-vs-rollback concern as login().
    @Transactional(noRollbackFor = InvalidOtpException.class)
    public AuthTokensResponse verifyOtp(String otpToken, String code) {
        UUID userId = jwtService.parseOtpPendingToken(otpToken)
                .orElseThrow(() -> new InvalidTokenException("OTP challenge is invalid or has expired."));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("OTP challenge is invalid or has expired."));

        if (user.getOtpCodeHash() == null || user.getOtpExpiresAt() == null
                || user.getOtpExpiresAt().isBefore(Instant.now())) {
            throw new InvalidOtpException("OTP has expired. Please log in again.");
        }

        if (user.getOtpAttempts() >= MAX_OTP_ATTEMPTS) {
            clearOtp(user);
            userRepository.save(user);
            throw new InvalidOtpException("Too many incorrect attempts. Please log in again.");
        }

        if (!passwordEncoder.matches(code, user.getOtpCodeHash())) {
            user.setOtpAttempts(user.getOtpAttempts() + 1);
            userRepository.save(user);
            throw new InvalidOtpException("Incorrect verification code.");
        }

        clearOtp(user);
        userRepository.save(user);

        return issueTokens(user);
    }

    // noRollbackFor: when we detect refresh-token reuse we revoke every live
    // session for the user and THEN throw. A plain @Transactional would roll
    // the revocation back together with the exception, silently undoing it.
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public AuthTokensResponse refresh(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidTokenException("Refresh token is invalid."));

        if (existing.isRevoked()) {
            // Reuse of a token we already rotated away from is a strong signal the
            // token was stolen — kill every live session for this user immediately.
            refreshTokenRepository.revokeAllForUser(existing.getUser());
            throw new InvalidTokenException("Refresh token has already been used. All sessions revoked.");
        }

        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("Refresh token has expired.");
        }

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        return issueTokens(existing.getUser());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(sha256(rawRefreshToken))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    private AuthTokensResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);

        String rawRefreshToken = generateOpaqueToken();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256(rawRefreshToken))
                .expiresAt(Instant.now().plusSeconds(jwtProperties.refreshTokenTtlSeconds()))
                .build();
        refreshTokenRepository.save(refreshToken);

        return new AuthTokensResponse(accessToken, rawRefreshToken, jwtProperties.accessTokenTtlSeconds());
    }

    private void registerFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plusSeconds(LOCKOUT_MINUTES * 60));
            user.setFailedLoginAttempts(0);
        }
        userRepository.save(user);
    }

    private void clearOtp(User user) {
        user.setOtpCodeHash(null);
        user.setOtpExpiresAt(null);
        user.setOtpAttempts(0);
    }

    private String generateOtpCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
