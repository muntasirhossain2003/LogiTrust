package com.logitrust.repository;

import com.logitrust.domain.RouteBaseline;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteBaselineRepository extends JpaRepository<RouteBaseline, UUID> {
    Optional<RouteBaseline> findByOriginLabelAndDestinationLabel(String originLabel, String destinationLabel);
}
