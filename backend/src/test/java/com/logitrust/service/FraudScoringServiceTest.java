package com.logitrust.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logitrust.domain.ProductCategory;
import com.logitrust.domain.Role;
import com.logitrust.domain.RouteBaseline;
import com.logitrust.domain.ScoringConfig;
import com.logitrust.domain.Shipment;
import com.logitrust.domain.ShipmentItem;
import com.logitrust.domain.ShipmentStatus;
import com.logitrust.domain.User;
import com.logitrust.dto.ConditionData;
import com.logitrust.dto.FraudScoreResult;
import com.logitrust.repository.RouteBaselineRepository;
import com.logitrust.repository.ScoringConfigRepository;
import com.logitrust.repository.ShipmentItemRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FraudScoringServiceTest {

    private ScoringConfigRepository scoringConfigRepository;
    private RouteBaselineRepository routeBaselineRepository;
    private ShipmentItemRepository shipmentItemRepository;
    private com.logitrust.repository.FraudFlagRepository fraudFlagRepository;
    private HuggingFaceRiskService huggingFaceRiskService;
    private FraudScoringService service;

    private Shipment shipment;
    private User manufacturer;

    @BeforeEach
    void setUp() {
        scoringConfigRepository = mock(ScoringConfigRepository.class);
        routeBaselineRepository = mock(RouteBaselineRepository.class);
        shipmentItemRepository = mock(ShipmentItemRepository.class);
        fraudFlagRepository = mock(com.logitrust.repository.FraudFlagRepository.class);
        huggingFaceRiskService = mock(HuggingFaceRiskService.class);
        // No incident notes are exercised in these tests - keep the 6th factor
        // neutral so it never influences the assertions below.
        when(huggingFaceRiskService.scoreIncidentNote(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyDouble()))
                .thenAnswer(inv -> new com.logitrust.dto.ScoringFactorResult(
                        "incidentTextRisk", 0, inv.getArgument(1), "No incident note supplied."));

        ScoringConfig config = new ScoringConfig();
        config.setId(ScoringConfig.SINGLETON_ID);
        config.setRouteDeviationWeight(0.18);
        config.setTimingAnomalyWeight(0.18);
        config.setConditionBreachWeight(0.25);
        config.setQuantityMismatchWeight(0.13);
        config.setIdentityReuseWeight(0.13);
        config.setIncidentTextRiskWeight(0.13);
        config.setFlagThreshold(50);
        config.setFreezeThreshold(80);
        config.setUpdatedAt(Instant.now());
        when(scoringConfigRepository.findById(ScoringConfig.SINGLETON_ID)).thenReturn(Optional.of(config));

        service = new FraudScoringService(
                scoringConfigRepository, routeBaselineRepository, shipmentItemRepository,
                fraudFlagRepository, huggingFaceRiskService, new ObjectMapper());

        manufacturer = User.builder()
                .id(UUID.randomUUID()).email("mfg@t.dev").passwordHash("x")
                .role(Role.MANUFACTURER).createdAt(Instant.now()).build();

        shipment = Shipment.builder()
                .id(UUID.randomUUID())
                .manufacturer(manufacturer)
                .trackingCode("LT-TEST0001")
                .status(ShipmentStatus.IN_TRANSIT)
                .originLabel("Factory A")
                .destinationLabel("Store B")
                .items(new java.util.ArrayList<>())
                .build();
        shipment.getItems().add(pharmaItem());
    }

    private ShipmentItem pharmaItem() {
        return ShipmentItem.builder()
                .id(UUID.randomUUID())
                .shipment(shipment)
                .serialNumber("SN-001")
                .qrCode("QR-001")
                .productName("Insulin")
                .productCategory(ProductCategory.PHARMA)
                .build();
    }

    // ---------- route deviation ----------

    @Test
    void routeDeviation_noExpectedRouteDeclared_scoresZero() {
        shipment.setExpectedRouteJson(null);

        FraudScoreResult result = service.scoreCheckpoint(shipment, "Anywhere", null, null, null, null, Instant.now());

        assertThat(factor(result, "routeDeviation").score()).isZero();
    }

    @Test
    void routeDeviation_checkpointOnDeclaredRoute_scoresZero() {
        shipment.setExpectedRouteJson(service.serializeExpectedRoute(List.of("Highway 1", "Store B")));

        FraudScoreResult result = service.scoreCheckpoint(shipment, "Highway 1", null, null, null, null, Instant.now());

        assertThat(factor(result, "routeDeviation").score()).isZero();
    }

    @Test
    void routeDeviation_checkpointOffDeclaredRoute_scoresHigh() {
        shipment.setExpectedRouteJson(service.serializeExpectedRoute(List.of("Highway 1", "Store B")));

        FraudScoreResult result = service.scoreCheckpoint(shipment, "Random Back Alley", null, null, null, null, Instant.now());

        assertThat(factor(result, "routeDeviation").score()).isEqualTo(80);
    }

    // ---------- timing anomaly ----------

    @Test
    void timingAnomaly_noPriorEvent_scoresZero() {
        FraudScoreResult result = service.scoreCheckpoint(shipment, "Highway 1", null, null, null, null, Instant.now());

        assertThat(factor(result, "timingAnomaly").score()).isZero();
    }

    @Test
    void timingAnomaly_insufficientBaselineSamples_scoresZero() {
        RouteBaseline thin = RouteBaseline.builder().originLabel("Factory A").destinationLabel("Store B").build();
        thin.recordSample(3600); // only 1 sample, need 3+
        when(routeBaselineRepository.findByOriginLabelAndDestinationLabel("Factory A", "Store B"))
                .thenReturn(Optional.of(thin));

        Instant previous = Instant.now().minusSeconds(100000);
        FraudScoreResult result = service.scoreCheckpoint(shipment, "Highway 1", null, null, null, previous, Instant.now());

        assertThat(factor(result, "timingAnomaly").score()).isZero();
    }

    @Test
    void timingAnomaly_dwellFarAboveBaseline_scoresHigh() {
        RouteBaseline baseline = RouteBaseline.builder().originLabel("Factory A").destinationLabel("Store B").build();
        baseline.recordSample(3600);
        baseline.recordSample(3700);
        baseline.recordSample(3500);
        when(routeBaselineRepository.findByOriginLabelAndDestinationLabel("Factory A", "Store B"))
                .thenReturn(Optional.of(baseline));

        Instant previous = Instant.EPOCH;
        Instant now = previous.plusSeconds(50000); // wildly longer than ~3600s baseline
        FraudScoreResult result = service.scoreCheckpoint(shipment, "Highway 1", null, null, null, previous, now);

        assertThat(factor(result, "timingAnomaly").score()).isGreaterThan(50);
    }

    @Test
    void timingAnomaly_dwellShorterThanBaseline_scoresZero() {
        RouteBaseline baseline = RouteBaseline.builder().originLabel("Factory A").destinationLabel("Store B").build();
        baseline.recordSample(3600);
        baseline.recordSample(3700);
        baseline.recordSample(3500);
        when(routeBaselineRepository.findByOriginLabelAndDestinationLabel("Factory A", "Store B"))
                .thenReturn(Optional.of(baseline));

        Instant previous = Instant.EPOCH;
        Instant now = previous.plusSeconds(10); // arriving early is not fraud
        FraudScoreResult result = service.scoreCheckpoint(shipment, "Highway 1", null, null, null, previous, now);

        assertThat(factor(result, "timingAnomaly").score()).isZero();
    }

    // ---------- condition breach ----------

    @Test
    void conditionBreach_noReading_scoresZero() {
        FraudScoreResult result = service.scoreCheckpoint(shipment, "Highway 1", null, null, null, null, Instant.now());

        assertThat(factor(result, "conditionBreach").score()).isZero();
    }

    @Test
    void conditionBreach_pharmaWithinRange_scoresZero() {
        ConditionData reading = new ConditionData(5.0, 50.0);

        FraudScoreResult result = service.scoreCheckpoint(shipment, "Highway 1", reading, null, null, null, Instant.now());

        assertThat(factor(result, "conditionBreach").score()).isZero();
    }

    @Test
    void conditionBreach_pharmaTooWarm_scoresHigh() {
        ConditionData reading = new ConditionData(25.0, 50.0); // way above the 2-8C cold chain range

        FraudScoreResult result = service.scoreCheckpoint(shipment, "Highway 1", reading, null, null, null, Instant.now());

        assertThat(factor(result, "conditionBreach").score()).isEqualTo(100);
        assertThat(factor(result, "conditionBreach").explanation()).contains("PHARMA");
    }

    // ---------- identity reuse ----------

    @Test
    void identityReuse_noScannedSerials_scoresZero() {
        FraudScoreResult result = service.scoreCheckpoint(shipment, "Highway 1", null, null, null, null, Instant.now());

        assertThat(factor(result, "identityReuse").score()).isZero();
    }

    @Test
    void identityReuse_unknownSerial_scoresHigh() {
        when(shipmentItemRepository.findBySerialNumber("GHOST-1")).thenReturn(Optional.empty());

        FraudScoreResult result = service.scoreCheckpoint(shipment, "Highway 1", null, List.of("GHOST-1"), null, null, Instant.now());

        assertThat(factor(result, "identityReuse").score()).isEqualTo(100);
    }

    @Test
    void identityReuse_serialAlreadyDeliveredOnAnotherShipment_scoresHigh() {
        Shipment otherShipment = Shipment.builder()
                .id(UUID.randomUUID())
                .manufacturer(manufacturer)
                .trackingCode("LT-OTHER001")
                .status(ShipmentStatus.DELIVERED)
                .originLabel("X").destinationLabel("Y")
                .build();
        ShipmentItem elsewhereItem = ShipmentItem.builder()
                .id(UUID.randomUUID()).shipment(otherShipment)
                .serialNumber("SN-DUPE").qrCode("QR-DUPE")
                .productName("Widget").productCategory(ProductCategory.GENERAL)
                .build();
        when(shipmentItemRepository.findBySerialNumber("SN-DUPE")).thenReturn(Optional.of(elsewhereItem));

        FraudScoreResult result = service.scoreCheckpoint(shipment, "Highway 1", null, List.of("SN-DUPE"), null, null, Instant.now());

        assertThat(factor(result, "identityReuse").score()).isEqualTo(100);
    }

    @Test
    void identityReuse_ownSerialOnSameShipment_scoresZero() {
        when(shipmentItemRepository.findBySerialNumber("SN-001"))
                .thenReturn(Optional.of(shipment.getItems().get(0)));

        FraudScoreResult result = service.scoreCheckpoint(shipment, "Highway 1", null, List.of("SN-001"), null, null, Instant.now());

        assertThat(factor(result, "identityReuse").score()).isZero();
    }

    // ---------- quantity mismatch ----------

    @Test
    void quantityMismatch_notReported_scoresZero() {
        FraudScoreResult result = service.scoreQuantityMismatch(shipment, null);

        assertThat(result.factors().get(0).score()).isZero();
    }

    @Test
    void quantityMismatch_matchesDeclared_scoresZero() {
        FraudScoreResult result = service.scoreQuantityMismatch(shipment, 1);

        assertThat(result.factors().get(0).score()).isZero();
    }

    @Test
    void quantityMismatch_doesNotMatch_scoresHigh_andRenormalizesToFullSeverity() {
        FraudScoreResult result = service.scoreQuantityMismatch(shipment, 5);

        assertThat(result.factors().get(0).score()).isEqualTo(100);
        // Single-factor pass renormalizes against its own weight, so the
        // overall score reflects full severity, not 100 * quantityMismatchWeight.
        assertThat(result.totalScore()).isEqualTo(100);
    }

    // ---------- combination ----------

    @Test
    void combine_allFactorsClean_totalScoreIsZero() {
        FraudScoreResult result = service.scoreCheckpoint(shipment, "Highway 1", null, null, null, null, Instant.now());

        assertThat(result.totalScore()).isZero();
    }

    @Test
    void combine_oneSevereFactor_pullsTotalScoreUp() {
        ConditionData badReading = new ConditionData(30.0, 50.0);

        FraudScoreResult result = service.scoreCheckpoint(shipment, "Highway 1", badReading, null, null, null, Instant.now());

        // conditionBreach=100 at weight 0.25 out of ~0.74 total active weight
        // (route+timing+condition+identity, since expectedRoute/baseline/serials
        // are all absent here) still meaningfully raises the blended score.
        assertThat(result.totalScore()).isGreaterThan(0);
    }

    private static com.logitrust.dto.ScoringFactorResult factor(FraudScoreResult result, String name) {
        return result.factors().stream()
                .filter(f -> f.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Factor not found: " + name));
    }
}
