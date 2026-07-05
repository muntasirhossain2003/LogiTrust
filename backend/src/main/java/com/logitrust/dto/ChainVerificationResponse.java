package com.logitrust.dto;

import java.time.Instant;
import java.util.List;

/** Result of recomputing a shipment's custody chain from stored data alone (FR-3.2). */
public record ChainVerificationResponse(
        boolean intact,
        int totalRecords,
        Integer brokenAtSequence,
        List<Entry> records) {

    public record Entry(
            int sequenceNumber,
            String eventType,
            String fromPartyEmail,
            String toPartyEmail,
            String location,
            Instant timestamp,
            boolean valid) {
    }
}
