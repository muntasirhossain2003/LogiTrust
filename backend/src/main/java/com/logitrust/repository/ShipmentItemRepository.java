package com.logitrust.repository;

import com.logitrust.domain.ShipmentItem;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentItemRepository extends JpaRepository<ShipmentItem, UUID> {

    Optional<ShipmentItem> findBySerialNumber(String serialNumber);

    boolean existsBySerialNumber(String serialNumber);
}
