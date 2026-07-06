package com.logitrust.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Rolling mean/stddev of total transit time for a route, keyed by
 * (originLabel, destinationLabel) since real GPS routing is out of scope
 * for this project (SRS 2.2) — the label pair is the practical stand-in for
 * "shipping route" (FR-4.3). Updated incrementally via Welford's online
 * algorithm each time a shipment on this route is delivered, so no full
 * history table needs to be re-scanned to keep the baseline current.
 */
@Entity
@Table(name = "route_baselines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteBaseline {

    @Id
    private UUID id;

    @Column(name = "origin_label", nullable = false)
    private String originLabel;

    @Column(name = "destination_label", nullable = false)
    private String destinationLabel;

    @Column(name = "sample_count", nullable = false)
    @Builder.Default
    private int sampleCount = 0;

    @Column(name = "mean_transit_seconds", nullable = false)
    @Builder.Default
    private double meanTransitSeconds = 0.0;

    /** Welford's M2 (sum of squared deviations from the running mean). */
    @Column(name = "m2", nullable = false)
    @Builder.Default
    private double m2 = 0.0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    public double stdDevSeconds() {
        return sampleCount > 1 ? Math.sqrt(m2 / sampleCount) : 0.0;
    }

    /** Welford's online update — numerically stable, no stored history needed. */
    public void recordSample(double transitSeconds) {
        sampleCount++;
        double delta = transitSeconds - meanTransitSeconds;
        meanTransitSeconds += delta / sampleCount;
        double delta2 = transitSeconds - meanTransitSeconds;
        m2 += delta * delta2;
        updatedAt = Instant.now();
    }
}
