package com.logitrust.web;

import com.logitrust.dto.AssignCourierRequest;
import com.logitrust.dto.CreateShipmentRequest;
import com.logitrust.dto.PublicTrackingResponse;
import com.logitrust.dto.ShipmentResponse;
import com.logitrust.dto.TransitUpdateRequest;
import com.logitrust.security.JwtService;
import com.logitrust.service.ShipmentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('MANUFACTURER')")
    public ResponseEntity<ShipmentResponse> create(
            @AuthenticationPrincipal JwtService.AccessTokenClaims claims,
            @Valid @RequestBody CreateShipmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ShipmentResponse.from(shipmentService.createShipment(claims.userId(), request)));
    }

    @GetMapping
    public ResponseEntity<List<ShipmentResponse>> list(
            @AuthenticationPrincipal JwtService.AccessTokenClaims claims) {
        return ResponseEntity.ok(shipmentService.listForUser(claims.userId()).stream()
                .map(ShipmentResponse::from)
                .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShipmentResponse> get(
            @AuthenticationPrincipal JwtService.AccessTokenClaims claims,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ShipmentResponse.from(shipmentService.getForUser(claims.userId(), id)));
    }

    @PostMapping("/{id}/assign-courier")
    @PreAuthorize("hasAnyRole('MANUFACTURER','DISTRIBUTOR')")
    public ResponseEntity<ShipmentResponse> assignCourier(
            @AuthenticationPrincipal JwtService.AccessTokenClaims claims,
            @PathVariable UUID id,
            @Valid @RequestBody AssignCourierRequest request) {
        return ResponseEntity.ok(ShipmentResponse.from(
                shipmentService.assignCourier(claims.userId(), id, request.courierEmail())));
    }

    @PostMapping("/{id}/transit")
    @PreAuthorize("hasRole('COURIER')")
    public ResponseEntity<ShipmentResponse> updateTransit(
            @AuthenticationPrincipal JwtService.AccessTokenClaims claims,
            @PathVariable UUID id,
            @Valid @RequestBody TransitUpdateRequest request) {
        return ResponseEntity.ok(ShipmentResponse.from(
                shipmentService.updateTransitStatus(claims.userId(), id, request)));
    }

    @PostMapping("/{id}/confirm-handoff")
    @PreAuthorize("hasAnyRole('DISTRIBUTOR','RETAILER')")
    public ResponseEntity<ShipmentResponse> confirmHandoff(
            @AuthenticationPrincipal JwtService.AccessTokenClaims claims,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ShipmentResponse.from(
                shipmentService.confirmHandoff(claims.userId(), id)));
    }

    @PostMapping("/{id}/dispute")
    public ResponseEntity<ShipmentResponse> dispute(
            @AuthenticationPrincipal JwtService.AccessTokenClaims claims,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ShipmentResponse.from(
                shipmentService.dispute(claims.userId(), id)));
    }

    /** Public tracking by code — permitted without auth in SecurityConfig. */
    @GetMapping("/{trackingCode}/public")
    public ResponseEntity<PublicTrackingResponse> track(@PathVariable String trackingCode) {
        return ResponseEntity.ok(PublicTrackingResponse.from(
                shipmentService.getByTrackingCode(trackingCode)));
    }
}
