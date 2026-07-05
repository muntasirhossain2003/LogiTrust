package com.logitrust.exception;

public class CustodyRecordNotFoundException extends RuntimeException {
    public CustodyRecordNotFoundException() {
        super("Custody record not found.");
    }
}
