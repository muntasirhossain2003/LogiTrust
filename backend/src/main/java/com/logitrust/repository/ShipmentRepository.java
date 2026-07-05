package com.logitrust.repository;

import com.logitrust.domain.Shipment;
import com.logitrust.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    // Controllers map entities to DTOs after the transaction has closed
    // (open-in-view is off), so every read used for a response must fetch
    // the party references and items up front.

    @Override
    @EntityGraph(attributePaths = {"manufacturer", "currentCourier", "destinationParty", "items"})
    Optional<Shipment> findById(UUID id);

    @EntityGraph(attributePaths = {"manufacturer", "currentCourier", "destinationParty", "items"})
    Optional<Shipment> findByTrackingCode(String trackingCode);

    @EntityGraph(attributePaths = {"manufacturer", "currentCourier", "destinationParty", "items"})
    List<Shipment> findAllByManufacturerOrderByCreatedAtDesc(User manufacturer);

    @EntityGraph(attributePaths = {"manufacturer", "currentCourier", "destinationParty", "items"})
    List<Shipment> findAllByCurrentCourierOrderByCreatedAtDesc(User courier);

    @EntityGraph(attributePaths = {"manufacturer", "currentCourier", "destinationParty", "items"})
    List<Shipment> findAllByDestinationPartyOrderByCreatedAtDesc(User destinationParty);

    @Override
    @EntityGraph(attributePaths = {"manufacturer", "currentCourier", "destinationParty", "items"})
    List<Shipment> findAll();

    boolean existsByTrackingCode(String trackingCode);
}
