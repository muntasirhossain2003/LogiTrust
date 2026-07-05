package com.logitrust.dto;

import java.util.UUID;

/** Demo-only response for the admin tamper endpoint (SRS 12). */
public record TamperDebugResponse(UUID recordId, UUID shipmentId, String newLocation) {
}
