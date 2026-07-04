package com.logitrust.exception;

import java.time.Instant;

public class AccountLockedException extends RuntimeException {
    public AccountLockedException(Instant lockedUntil) {
        super("Account is locked until " + lockedUntil + " due to repeated failed login attempts.");
    }
}
