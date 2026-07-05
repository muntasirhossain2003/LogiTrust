package com.logitrust.exception;

public class ShipmentNotFoundException extends RuntimeException {
    public ShipmentNotFoundException() {
        super("Shipment not found.");
    }
}
