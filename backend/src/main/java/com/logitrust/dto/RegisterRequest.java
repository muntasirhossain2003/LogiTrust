package com.logitrust.dto;

import com.logitrust.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotNull @Email String email,
        @NotNull @Size(min = 8, max = 100, message = "Password must be at least 8 characters") String password,
        @NotNull Role role) {
}
