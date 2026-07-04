package com.logitrust.exception;

/** Deliberately generic — never reveals whether the email or password was wrong. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid email or password.");
    }
}
