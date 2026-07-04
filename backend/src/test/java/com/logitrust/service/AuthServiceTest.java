package com.logitrust.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.logitrust.config.JwtProperties;
import com.logitrust.domain.RefreshToken;
import com.logitrust.domain.Role;
import com.logitrust.domain.User;
import com.logitrust.dto.AuthTokensResponse;
import com.logitrust.dto.LoginRequest;
import com.logitrust.dto.RegisterRequest;
import com.logitrust.exception.AccountLockedException;
import com.logitrust.exception.EmailAlreadyRegisteredException;
import com.logitrust.exception.InvalidCredentialsException;
import com.logitrust.exception.InvalidOtpException;
import com.logitrust.exception.InvalidTokenException;
import com.logitrust.repository.RefreshTokenRepository;
import com.logitrust.repository.UserRepository;
import com.logitrust.security.JwtService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private JwtService jwtService;
    private EmailService emailService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        emailService = mock(EmailService.class);
        JwtProperties jwtProperties = new JwtProperties(
                "test-secret-key-that-is-long-enough-for-hmac-sha-256-signing", 900L, 300L, 604800L);
        jwtService = new JwtService(jwtProperties);

        authService = new AuthService(
                userRepository, refreshTokenRepository, passwordEncoder, jwtService, jwtProperties, emailService);

        // Persist-and-return-same-instance, matching real JPA save() semantics closely
        // enough for these tests.
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private User buildUser(String password, Role role, boolean twoFactor) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("user@logitrust.dev")
                .passwordHash(passwordEncoder.encode(password))
                .role(role)
                .twoFactorEnabled(twoFactor)
                .failedLoginAttempts(0)
                .otpAttempts(0)
                .createdAt(Instant.now())
                .build();
    }

    // ---------- register ----------

    @Test
    void register_hashesPassword_andPersistsUser() {
        when(userRepository.existsByEmail("new@logitrust.dev")).thenReturn(false);
        RegisterRequest request = new RegisterRequest("new@logitrust.dev", "password123", Role.RETAILER);

        User saved = authService.register(request);

        assertThat(saved.getEmail()).isEqualTo("new@logitrust.dev");
        assertThat(saved.getPasswordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", saved.getPasswordHash())).isTrue();
        assertThat(saved.isTwoFactorEnabled()).isFalse();
    }

    @Test
    void register_forcesTwoFactorForManufacturer() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        RegisterRequest request = new RegisterRequest("mfg@logitrust.dev", "password123", Role.MANUFACTURER);

        User saved = authService.register(request);

        assertThat(saved.isTwoFactorEnabled()).isTrue();
    }

    @Test
    void register_rejectsDuplicateEmail() {
        when(userRepository.existsByEmail("dupe@logitrust.dev")).thenReturn(true);
        RegisterRequest request = new RegisterRequest("dupe@logitrust.dev", "password123", Role.CUSTOMER);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void register_rejectsSelfRegisteredAdmin() {
        RegisterRequest request = new RegisterRequest("wannabe-admin@logitrust.dev", "password123", Role.ADMIN);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- login: password + lockout (FR-1.5) ----------

    @Test
    void login_correctPasswordNoTwoFactor_returnsTokensDirectly() {
        User user = buildUser("correct-password", Role.CUSTOMER, false);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        LoginOutcome outcome = authService.login(new LoginRequest(user.getEmail(), "correct-password"));

        assertThat(outcome).isInstanceOf(LoginOutcome.Authenticated.class);
        AuthTokensResponse tokens = ((LoginOutcome.Authenticated) outcome).tokens();
        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    void login_wrongPassword_incrementsFailedAttempts() {
        User user = buildUser("correct-password", Role.CUSTOMER, false);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.isLocked()).isFalse();
    }

    @Test
    void login_fifthConsecutiveFailure_locksAccountFor15Minutes() {
        User user = buildUser("correct-password", Role.CUSTOMER, false);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), "wrong")))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        assertThat(user.isLocked()).isFalse();

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(user.isLocked()).isTrue();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isAfter(Instant.now().plusSeconds(14 * 60));
        assertThat(user.getLockedUntil()).isBefore(Instant.now().plusSeconds(16 * 60));
    }

    @Test
    void login_lockedAccount_rejectsEvenCorrectPassword() {
        User user = buildUser("correct-password", Role.CUSTOMER, false);
        user.setLockedUntil(Instant.now().plusSeconds(600));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest(user.getEmail(), "correct-password")))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void login_unknownEmail_throwsGenericInvalidCredentials() {
        when(userRepository.findByEmail("ghost@logitrust.dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@logitrust.dev", "anything")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // ---------- login: 2FA (FR-1.4) ----------

    @Test
    void login_withTwoFactorEnabled_returnsOtpRequired_andEmailsCode() {
        User user = buildUser("correct-password", Role.MANUFACTURER, true);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        LoginOutcome outcome = authService.login(new LoginRequest(user.getEmail(), "correct-password"));

        assertThat(outcome).isInstanceOf(LoginOutcome.OtpRequired.class);
        assertThat(user.getOtpCodeHash()).isNotNull();
        assertThat(user.getOtpExpiresAt()).isAfter(Instant.now());
        verify(emailService, times(1)).sendOtpEmail(org.mockito.ArgumentMatchers.eq(user.getEmail()), any());
    }

    @Test
    void verifyOtp_correctCode_returnsTokens_andClearsOtpState() {
        User user = buildUser("correct-password", Role.MANUFACTURER, true);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        authService.login(new LoginRequest(user.getEmail(), "correct-password"));

        String otpToken = jwtService.generateOtpPendingToken(user);
        String capturedCode = captureOtpSentTo(user.getEmail());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        AuthTokensResponse tokens = authService.verifyOtp(otpToken, capturedCode);

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(user.getOtpCodeHash()).isNull();
        assertThat(user.getOtpAttempts()).isZero();
    }

    @Test
    void verifyOtp_wrongCode_incrementsAttempts_andThrows() {
        User user = buildUser("correct-password", Role.MANUFACTURER, true);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        authService.login(new LoginRequest(user.getEmail(), "correct-password"));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        String otpToken = jwtService.generateOtpPendingToken(user);

        assertThatThrownBy(() -> authService.verifyOtp(otpToken, "000000"))
                .isInstanceOf(InvalidOtpException.class);
        assertThat(user.getOtpAttempts()).isEqualTo(1);
    }

    @Test
    void verifyOtp_expiredCode_throws() {
        User user = buildUser("correct-password", Role.MANUFACTURER, true);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        authService.login(new LoginRequest(user.getEmail(), "correct-password"));
        user.setOtpExpiresAt(Instant.now().minusSeconds(1));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        String otpToken = jwtService.generateOtpPendingToken(user);

        assertThatThrownBy(() -> authService.verifyOtp(otpToken, "123456"))
                .isInstanceOf(InvalidOtpException.class);
    }

    // ---------- refresh rotation + reuse detection (SRS 9.1) ----------

    @Test
    void refresh_rotatesToken_revokingTheOldOne() {
        User user = buildUser("correct-password", Role.CUSTOMER, false);
        RefreshToken stored = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash("irrelevant-hash-value")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .createdAt(Instant.now())
                .build();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));

        AuthTokensResponse tokens = authService.refresh("raw-refresh-token-value");

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(stored.isRevoked()).isTrue();
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void refresh_reusedRevokedToken_revokesAllSessionsForThatUser() {
        User user = buildUser("correct-password", Role.CUSTOMER, false);
        RefreshToken alreadyUsed = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash("irrelevant-hash-value")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(true)
                .createdAt(Instant.now())
                .build();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(alreadyUsed));

        assertThatThrownBy(() -> authService.refresh("stolen-token"))
                .isInstanceOf(InvalidTokenException.class);

        verify(refreshTokenRepository, times(1)).revokeAllForUser(user);
    }

    @Test
    void refresh_expiredToken_throws() {
        User user = buildUser("correct-password", Role.CUSTOMER, false);
        RefreshToken expired = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash("irrelevant-hash-value")
                .expiresAt(Instant.now().minusSeconds(1))
                .revoked(false)
                .createdAt(Instant.now())
                .build();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh("expired-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    // ---------- helpers ----------

    private String captureOtpSentTo(String email) {
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendOtpEmail(org.mockito.ArgumentMatchers.eq(email), codeCaptor.capture());
        return codeCaptor.getValue();
    }
}
