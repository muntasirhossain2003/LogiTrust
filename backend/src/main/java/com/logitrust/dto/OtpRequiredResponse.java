package com.logitrust.dto;

public record OtpRequiredResponse(String otpToken, long expiresInSeconds) {
}
