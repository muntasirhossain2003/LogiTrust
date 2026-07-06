package com.logitrust.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logitrust.domain.CustodyRecord;
import com.logitrust.domain.FraudFlag;
import com.logitrust.domain.ProductCategory;
import com.logitrust.domain.RouteBaseline;
import com.logitrust.domain.ScoringConfig;
import com.logitrust.domain.Shipment;
import com.logitrust.domain.ShipmentItem;
import com.logitrust.domain.ShipmentStatus;
import com.logitrust.dto.ConditionData;
import com.logitrust.dto.FraudScoreResult;
import com.logitrust.dto.ScoringFactorResult;
import com.logitrust.repository.FraudFlagRepository;
import com.logitrust.repository.RouteBaselineRepository;
import com.logitrust.repository.ScoringConfigRepository;
import com.logitrust.repository.ShipmentItemRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rule-based-plus-statistical fraud scoring engine (SRS 4.4 / 6.2): five
 * independently-explainable factors, combined via weights read from the
 * database (never hardcoded) so thresholds can be tuned without a
 * redeploy. Deliberately not a trained ML model — see SRS 6.2's own
 * reasoning: full explainability, defensible without a black box.
 */
@Service
public class FraudScoringService {

    private record Range(double min, double max) {
        boolean contains(double v) {
            return v >= min && v <= max;
        }
    }

    private static final Map<ProductCategory, Range> TEMP_RANGES_C = Map.of(
            ProductCategory.PHARMA, new Range(2, 8),
            ProductCategory.ELECTRONICS, new Range(-10, 45),
            ProductCategory.GENERAL, new Range(-20, 50));

    private static final Map<ProductCategory, Range> HUMIDITY_RANGES_PCT = Map.of(
            ProductCategory.PHARMA, new Range(0, 60),
            ProductCategory.ELECTRONICS, new Range(0, 70),
            ProductCategory.GENERAL, new Range(0, 90));

    /** Minimum completed shipments on a route before its timing baseline is trusted. */
    private static final int MIN_BASELINE_SAMPLES = 3;

    private final ScoringConfigRepository scoringConfigRepository;
    private final RouteBaselineRepository routeBaselineRepository;
    private final ShipmentItemRepository shipmentItemRepository;
    private final FraudFlagRepository fraudFlagRepository;
    private final ObjectMapper objectMapper;

    public FraudScoringService(
            ScoringConfigRepository scoringConfigRepository,
            RouteBaselineRepository routeBaselineRepository,
            ShipmentItemRepository shipmentItemRepository,
            FraudFlagRepository fraudFlagRepository,
            ObjectMapper objectMapper) {
        this.scoringConfigRepository = scoringConfigRepository;
        this.routeBaselineRepository = routeBaselineRepository;
        this.shipmentItemRepository = shipmentItemRepository;
        this.fraudFlagRepository = fraudFlagRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ScoringConfig loadConfig() {
        return scoringConfigRepository.findById(ScoringConfig.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Scoring config row was not seeded."));
    }

    /**
     * Scores a checkpoint event against four of the five rule-based factors
     * (everything except quantity mismatch, which only makes sense at
     * confirm-handoff). {@code previousEventTimestamp} is null for the very
     * first checkpoint on a shipment, when there is nothing to measure dwell
     * time against yet.
     */
    @Transactional(readOnly = true)
    public FraudScoreResult scoreCheckpoint(
            Shipment shipment,
            String location,
            ConditionData conditionData,
            List<String> scannedSerials,
            Instant previousEventTimestamp,
            Instant eventTimestamp) {
        ScoringConfig config = loadConfig();
        List<ScoringFactorResult> factors = new ArrayList<>();
        factors.add(routeDeviation(shipment, location, config));
        factors.add(timingAnomaly(shipment, previousEventTimestamp, eventTimestamp, config));
        factors.add(conditionBreach(shipment, conditionData, config));
        factors.add(identityReuse(shipment, scannedSerials, config));
        return combine(factors);
    }

    @Transactional(readOnly = true)
    public FraudScoreResult scoreQuantityMismatch(Shipment shipment, Integer confirmedItemCount) {
        return combine(List.of(quantityMismatch(shipment, confirmedItemCount, loadConfig())));
    }

    /**
     * Scores a checkpoint and immediately applies the result: updates
     * {@code shipment.riskScore}, opens a {@link FraudFlag} above the flag
     * threshold, and freezes the shipment above the freeze threshold
     * (FR-4.4). Mutates the given shipment in place; the caller is
     * responsible for persisting it.
     */
    @Transactional
    public FraudScoreResult scoreCheckpointAndApply(
            Shipment shipment,
            CustodyRecord triggeringRecord,
            String location,
            ConditionData conditionData,
            List<String> scannedSerials,
            Instant previousEventTimestamp,
            Instant eventTimestamp) {
        FraudScoreResult result = scoreCheckpoint(
                shipment, location, conditionData, scannedSerials, previousEventTimestamp, eventTimestamp);
        applyScore(shipment, triggeringRecord, result);
        return result;
    }

    @Transactional
    public FraudScoreResult scoreQuantityMismatchAndApply(
            Shipment shipment, CustodyRecord triggeringRecord, Integer confirmedItemCount) {
        FraudScoreResult result = scoreQuantityMismatch(shipment, confirmedItemCount);
        applyScore(shipment, triggeringRecord, result);
        return result;
    }

    private void applyScore(Shipment shipment, CustodyRecord triggeringRecord, FraudScoreResult result) {
        ScoringConfig config = loadConfig();
        shipment.setRiskScore(result.totalScore());

        if (result.totalScore() >= config.getFlagThreshold()) {
            FraudFlag flag = FraudFlag.builder()
                    .shipment(shipment)
                    .custodyRecord(triggeringRecord)
                    .score(result.totalScore())
                    .factors(serializeFactors(result.factors()))
                    .build();
            fraudFlagRepository.save(flag);
        }

        if (result.totalScore() >= config.getFreezeThreshold()) {
            shipment.setFrozen(true);
        }
    }

    private String serializeFactors(List<ScoringFactorResult> factors) {
        try {
            return objectMapper.writeValueAsString(factors);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize fraud score factors", e);
        }
    }

    /** Called once a shipment is DELIVERED, folding its total transit time into the route's baseline. */
    @Transactional
    public void recordCompletedTransit(Shipment shipment, double transitSeconds) {
        RouteBaseline baseline = routeBaselineRepository
                .findByOriginLabelAndDestinationLabel(shipment.getOriginLabel(), shipment.getDestinationLabel())
                .orElseGet(() -> RouteBaseline.builder()
                        .originLabel(shipment.getOriginLabel())
                        .destinationLabel(shipment.getDestinationLabel())
                        .build());
        baseline.recordSample(transitSeconds);
        routeBaselineRepository.save(baseline);
    }

    public List<String> parseExpectedRoute(String expectedRouteJson) {
        if (expectedRouteJson == null || expectedRouteJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(expectedRouteJson, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (Exception e) {
            return null;
        }
    }

    public String serializeExpectedRoute(List<String> expectedRoute) {
        if (expectedRoute == null || expectedRoute.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(expectedRoute);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize expected route", e);
        }
    }

    // ---------- factors ----------

    private ScoringFactorResult routeDeviation(Shipment shipment, String location, ScoringConfig config) {
        List<String> expectedRoute = parseExpectedRoute(shipment.getExpectedRouteJson());
        double weight = config.getRouteDeviationWeight();
        if (expectedRoute == null || expectedRoute.isEmpty()) {
            return new ScoringFactorResult("routeDeviation", 0, weight,
                    "No expected route was declared for this shipment - not evaluated.");
        }
        boolean onRoute = expectedRoute.stream().anyMatch(loc -> loc.trim().equalsIgnoreCase(location.trim()));
        if (onRoute) {
            return new ScoringFactorResult("routeDeviation", 0, weight,
                    "Checkpoint location matches the declared route.");
        }
        return new ScoringFactorResult("routeDeviation", 80, weight,
                "Checkpoint location \"" + location + "\" is not on the declared route " + expectedRoute + ".");
    }

    private ScoringFactorResult timingAnomaly(
            Shipment shipment, Instant previousEventTimestamp, Instant eventTimestamp, ScoringConfig config) {
        double weight = config.getTimingAnomalyWeight();
        if (previousEventTimestamp == null) {
            return new ScoringFactorResult("timingAnomaly", 0, weight,
                    "First checkpoint on this shipment - no prior event to measure dwell time against.");
        }
        double dwellSeconds = java.time.Duration.between(previousEventTimestamp, eventTimestamp).getSeconds();

        Optional<RouteBaseline> baselineOpt = routeBaselineRepository
                .findByOriginLabelAndDestinationLabel(shipment.getOriginLabel(), shipment.getDestinationLabel());
        if (baselineOpt.isEmpty() || baselineOpt.get().getSampleCount() < MIN_BASELINE_SAMPLES) {
            return new ScoringFactorResult("timingAnomaly", 0, weight,
                    "Not enough completed shipments on this route yet to judge normal timing (need "
                            + MIN_BASELINE_SAMPLES + "+).");
        }

        RouteBaseline baseline = baselineOpt.get();
        double stdDev = baseline.stdDevSeconds();
        if (stdDev <= 0) {
            return new ScoringFactorResult("timingAnomaly", 0, weight,
                    "Route baseline has no variance yet; cannot compute a meaningful z-score.");
        }

        double z = (dwellSeconds - baseline.getMeanTransitSeconds()) / stdDev;
        // Only flag LONGER-than-normal dwell (FR-4.2); arriving early isn't fraud.
        int score = z <= 0 ? 0 : (int) Math.round(Math.min(100, z * 20));
        String explanation = String.format(
                "Dwell time %.0fs vs route average %.0fs (std dev %.0fs, z=%.2f).",
                dwellSeconds, baseline.getMeanTransitSeconds(), stdDev, z);
        return new ScoringFactorResult("timingAnomaly", score, weight, explanation);
    }

    private ScoringFactorResult conditionBreach(Shipment shipment, ConditionData conditionData, ScoringConfig config) {
        double weight = config.getConditionBreachWeight();
        if (conditionData == null) {
            return new ScoringFactorResult("conditionBreach", 0, weight, "No condition reading supplied.");
        }

        for (ShipmentItem item : shipment.getItems()) {
            ProductCategory category = item.getProductCategory();
            Range tempRange = TEMP_RANGES_C.get(category);
            Range humidityRange = HUMIDITY_RANGES_PCT.get(category);

            if (conditionData.temperatureC() != null && !tempRange.contains(conditionData.temperatureC())) {
                return new ScoringFactorResult("conditionBreach", 100, weight, String.format(
                        "Temperature %.1f°C is outside the safe range [%.0f, %.0f]°C for %s goods.",
                        conditionData.temperatureC(), tempRange.min(), tempRange.max(), category));
            }
            if (conditionData.humidityPct() != null && !humidityRange.contains(conditionData.humidityPct())) {
                return new ScoringFactorResult("conditionBreach", 100, weight, String.format(
                        "Humidity %.0f%% is outside the safe range [%.0f, %.0f]%% for %s goods.",
                        conditionData.humidityPct(), humidityRange.min(), humidityRange.max(), category));
            }
        }
        return new ScoringFactorResult("conditionBreach", 0, weight,
                "Condition reading is within the safe range for every item in this shipment.");
    }

    private ScoringFactorResult identityReuse(Shipment shipment, List<String> scannedSerials, ScoringConfig config) {
        double weight = config.getIdentityReuseWeight();
        if (scannedSerials == null || scannedSerials.isEmpty()) {
            return new ScoringFactorResult("identityReuse", 0, weight, "No serials scanned at this checkpoint.");
        }

        for (String serial : scannedSerials) {
            Optional<ShipmentItem> itemOpt = shipmentItemRepository.findBySerialNumber(serial);
            if (itemOpt.isEmpty()) {
                return new ScoringFactorResult("identityReuse", 100, weight,
                        "Scanned serial \"" + serial + "\" was not found in the system.");
            }
            ShipmentItem item = itemOpt.get();
            UUID owningShipmentId = item.getShipment().getId();
            if (!owningShipmentId.equals(shipment.getId())
                    && item.getShipment().getStatus() == ShipmentStatus.DELIVERED) {
                return new ScoringFactorResult("identityReuse", 100, weight,
                        "Serial \"" + serial + "\" is already marked DELIVERED on shipment "
                                + item.getShipment().getTrackingCode() + ".");
            }
        }
        return new ScoringFactorResult("identityReuse", 0, weight, "All scanned serials check out.");
    }

    private ScoringFactorResult quantityMismatch(Shipment shipment, Integer confirmedItemCount, ScoringConfig config) {
        double weight = config.getQuantityMismatchWeight();
        if (confirmedItemCount == null) {
            return new ScoringFactorResult("quantityMismatch", 0, weight,
                    "Receiving party did not report a confirmed item count.");
        }
        int declared = shipment.getItems().size();
        if (confirmedItemCount != declared) {
            return new ScoringFactorResult("quantityMismatch", 100, weight,
                    "Declared " + declared + " item(s) but the receiver confirmed " + confirmedItemCount + ".");
        }
        return new ScoringFactorResult("quantityMismatch", 0, weight,
                "Confirmed count matches the declared count (" + declared + ").");
    }

    // ---------- combination ----------

    /**
     * Weighted average, renormalized over just the factors actually supplied
     * — so a confirm-handoff pass scoring quantityMismatch alone still
     * produces a proper 0-100 severity instead of being capped at that one
     * factor's raw DB weight.
     */
    private FraudScoreResult combine(List<ScoringFactorResult> factors) {
        double weightSum = factors.stream().mapToDouble(ScoringFactorResult::weight).sum();
        int total = weightSum <= 0
                ? 0
                : (int) Math.round(factors.stream().mapToDouble(ScoringFactorResult::contribution).sum() / weightSum);
        total = Math.max(0, Math.min(100, total));
        return new FraudScoreResult(total, factors);
    }
}
