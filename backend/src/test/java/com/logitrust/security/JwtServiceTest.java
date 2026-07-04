package com.logitrust.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.logitrust.config.JwtProperties;
import com.logitrust.domain.Role;
import com.logitrust.domain.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                "test-secret-key-that-is-long-enough-for-hmac-sha-256-signing",
                900L,
                300L,
                604800L);
        jwtService = new JwtService(properties);
        user = User.builder()
                .id(UUID.randomUUID())
                .email("courier@logitrust.dev")
                .passwordHash("irrelevant")
                .role(Role.COURIER)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void accessToken_roundTripsClaims() {
        String token = jwtService.generateAccessToken(user);

        Optional<JwtService.AccessTokenClaims> claims = jwtService.parseAccessToken(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().userId()).isEqualTo(user.getId());
        assertThat(claims.get().email()).isEqualTo(user.getEmail());
        assertThat(claims.get().role()).isEqualTo(Role.COURIER);
    }

    @Test
    void otpPendingToken_roundTripsUserId() {
        String token = jwtService.generateOtpPendingToken(user);

        Optional<UUID> userId = jwtService.parseOtpPendingToken(token);

        assertThat(userId).contains(user.getId());
    }

    @Test
    void otpPendingToken_isRejectedByAccessTokenParser() {
        String token = jwtService.generateOtpPendingToken(user);

        assertThat(jwtService.parseAccessToken(token)).isEmpty();
    }

    @Test
    void accessToken_isRejectedByOtpPendingParser() {
        String token = jwtService.generateAccessToken(user);

        assertThat(jwtService.parseOtpPendingToken(token)).isEmpty();
    }

    @Test
    void tamperedToken_isRejected() {
        String token = jwtService.generateAccessToken(user) + "tampered";

        assertThat(jwtService.parseAccessToken(token)).isEmpty();
    }

    @Test
    void tokenSignedWithDifferentSecret_isRejected() {
        JwtService otherService = new JwtService(
                new JwtProperties("a-completely-different-secret-key-of-sufficient-length", 900L, 300L, 604800L));
        String token = otherService.generateAccessToken(user);

        assertThat(jwtService.parseAccessToken(token)).isEmpty();
    }
}
