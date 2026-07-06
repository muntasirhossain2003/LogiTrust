package com.logitrust.repository;

import com.logitrust.domain.ScoringConfig;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoringConfigRepository extends JpaRepository<ScoringConfig, UUID> {
}
