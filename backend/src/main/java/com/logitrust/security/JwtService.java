package com.logitrust.security;

import com.logitrust.config.JwtProperties;
import com.logitrust.domain.Role;
import com.logitrust.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and validates two distinct JWT types, distinguished by a "type"
 * claim so one can never be replayed as the other:
 *
 * <ul>
 *   <li>{@code access} — normal bearer token accepted by the request filter</li>
 *   <li>{@code otp_pending} — short-lived ticket identifying a login that is
 *       waiting on a 2FA code; carries no authorities and is only accepted
 *       by the verify-otp endpoint</li>
 * </ul>
 *
 * Refresh tokens are deliberately NOT JWTs — see {@link com.logitrust.service.AuthService}.
 */
@Service
public class JwtService {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_ROLE = "role";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_OTP_PENDING = "otp_pending";

    private final SecretKey signingKey;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim("email", user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.accessTokenTtlSeconds())))
                .signWith(signingKey)
                .compact();
    }

    public String generateOtpPendingToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_TYPE, TYPE_OTP_PENDING)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.otpPendingTtlSeconds())))
                .signWith(signingKey)
                .compact();
    }

    public Optional<AccessTokenClaims> parseAccessToken(String token) {
        return parseClaims(token)
                .filter(claims -> TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class)))
                .map(claims -> new AccessTokenClaims(
                        UUID.fromString(claims.getSubject()),
                        claims.get("email", String.class),
                        Role.valueOf(claims.get(CLAIM_ROLE, String.class))));
    }

    public Optional<UUID> parseOtpPendingToken(String token) {
        return parseClaims(token)
                .filter(claims -> TYPE_OTP_PENDING.equals(claims.get(CLAIM_TYPE, String.class)))
                .map(claims -> UUID.fromString(claims.getSubject()));
    }

    private Optional<Claims> parseClaims(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record AccessTokenClaims(UUID userId, String email, Role role) {
    }
}
