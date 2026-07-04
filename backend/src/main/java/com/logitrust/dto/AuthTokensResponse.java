package com.logitrust.dto;

public record AuthTokensResponse(String accessToken, String refreshToken, long expiresInSeconds) {
}
