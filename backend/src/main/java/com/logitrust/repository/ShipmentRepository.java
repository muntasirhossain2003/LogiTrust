package com.logitrust.repository;

import com.logitrust.domain.Shipment;
import com.logitrust.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    Optional<Shipment> findByTrackingCode(String trackingCode);

    List<Shipment> findAllByManufacturerOrderByCreatedAtDesc(User manufacturer);

    List<Shipment> findAllByCurrentCourierOrderByCreatedAtDesc(User courier);

    List<Shipment> findAllByDestinationPartyOrderByCreatedAtDesc(User destinationParty);

    boolean existsByTrackingCode(String trackingCode);
}
