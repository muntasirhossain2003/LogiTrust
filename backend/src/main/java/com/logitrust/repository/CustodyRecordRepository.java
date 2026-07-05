package com.logitrust.repository;

import com.logitrust.domain.CustodyRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustodyRecordRepository extends JpaRepository<CustodyRecord, UUID> {

    @EntityGraph(attributePaths = {"fromParty", "toParty"})
    List<CustodyRecord> findAllByShipmentIdOrderBySequenceNumberAsc(UUID shipmentId);

    int countByShipmentId(UUID shipmentId);
}
