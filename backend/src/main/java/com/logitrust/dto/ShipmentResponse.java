package com.logitrust.dto;

import com.logitrust.domain.ProductCategory;
import com.logitrust.domain.Shipment;
import com.logitrust.domain.ShipmentStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ShipmentResponse(
        UUID id,
        String trackingCode,
        ShipmentStatus status,
        String originLabel,
        String destinationLabel,
        String manufacturerEmail,
        String courierEmail,
        String destinationPartyEmail,
        int riskScore,
        boolean frozen,
        List<Item> items,
        Instant createdAt,
        Instant updatedAt) {

    public record Item(UUID id, String serialNumber, String qrCode, String productName, ProductCategory category) {
    }

    public static ShipmentResponse from(Shipment s) {
        return new ShipmentResponse(
                s.getId(),
                s.getTrackingCode(),
                s.getStatus(),
                s.getOriginLabel(),
                s.getDestinationLabel(),
                s.getManufacturer().getEmail(),
                s.getCurrentCourier() != null ? s.getCurrentCourier().getEmail() : null,
                s.getDestinationParty() != null ? s.getDestinationParty().getEmail() : null,
                s.getRiskScore(),
                s.isFrozen(),
                s.getItems().stream()
                        .map(i -> new Item(i.getId(), i.getSerialNumber(), i.getQrCode(),
                                i.getProductName(), i.getProductCategory()))
                        .toList(),
                s.getCreatedAt(),
                s.getUpdatedAt());
    }
}
