package com.logitrust.dto;

import com.logitrust.domain.Shipment;
import com.logitrust.domain.ShipmentStatus;
import java.time.Instant;

/**
 * Public view by tracking code (FR: GET /api/shipments/{trackingCode}/public).
 * Deliberately reveals no party identities, serials, or risk data.
 */
public record PublicTrackingResponse(
        String trackingCode,
        ShipmentStatus status,
        String originLabel,
        String destinationLabel,
        int itemCount,
        Instant lastUpdatedAt) {

    public static PublicTrackingResponse from(Shipment s) {
        return new PublicTrackingResponse(
                s.getTrackingCode(),
                s.getStatus(),
                s.getOriginLabel(),
                s.getDestinationLabel(),
                s.getItems().size(),
                s.getUpdatedAt());
    }
}
