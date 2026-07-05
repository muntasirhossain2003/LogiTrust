package com.logitrust.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AssignCourierRequest(@NotBlank @Email String courierEmail) {
}
