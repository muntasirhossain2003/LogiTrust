package com.logitrust.service;

import com.logitrust.dto.AuthTokensResponse;
import com.logitrust.dto.OtpRequiredResponse;

/** Login either completes immediately or hands back a ticket pending 2FA. */
public sealed interface LoginOutcome {
    record Authenticated(AuthTokensResponse tokens) implements LoginOutcome {
    }

    record OtpRequired(OtpRequiredResponse challenge) implements LoginOutcome {
    }
}
