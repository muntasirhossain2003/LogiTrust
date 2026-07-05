package com.logitrust.exception;

/** The caller is authenticated but is not a party allowed to do this; HTTP 403. */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
