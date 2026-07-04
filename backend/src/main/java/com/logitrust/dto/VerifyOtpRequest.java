package com.logitrust.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyOtpRequest(@NotBlank String otpToken, @NotBlank String code) {
}
