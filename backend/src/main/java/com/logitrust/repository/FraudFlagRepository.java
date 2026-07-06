package com.logitrust.repository;

import com.logitrust.domain.FraudFlag;
import com.logitrust.domain.FraudFlagStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudFlagRepository extends JpaRepository<FraudFlag, UUID> {

    @EntityGraph(attributePaths = {"shipment", "custodyRecord", "resolvedByAdmin"})
    List<FraudFlag> findAllByStatusOrderByScoreDesc(FraudFlagStatus status);

    @EntityGraph(attributePaths = {"shipment", "custodyRecord", "resolvedByAdmin"})
    List<FraudFlag> findAllByShipmentIdOrderByCreatedAtDesc(UUID shipmentId);
}
