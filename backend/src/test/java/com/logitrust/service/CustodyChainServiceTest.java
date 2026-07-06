package com.logitrust.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logitrust.config.EncryptionProperties;
import com.logitrust.domain.CustodyRecord;
import com.logitrust.domain.Role;
import com.logitrust.domain.Shipment;
import com.logitrust.domain.ShipmentStatus;
import com.logitrust.domain.User;
import com.logitrust.dto.ChainVerificationResponse;
import com.logitrust.dto.ConditionData;
import com.logitrust.repository.CustodyRecordRepository;
import com.logitrust.security.EncryptionService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A fake, in-memory CustodyRecordRepository backs these tests (rather than
 * pure Mockito stubbing) because the interesting behavior here is genuinely
 * stateful across calls: appending several records in sequence, then
 * mutating one and re-verifying the whole chain.
 */
class CustodyChainServiceTest {

    private CustodyRecordRepository custodyRecordRepository;
    private CustodyChainService service;
    private final List<CustodyRecord> store = new ArrayList<>();

    private Shipment shipment;
    private User manufacturer;
    private User courier;
    private User retailer;

    @BeforeEach
    void setUp() {
        store.clear();
        custodyRecordRepository = mock(CustodyRecordRepository.class);

        when(custodyRecordRepository.countByShipmentId(any())).thenAnswer(inv -> (int) store.stream()
                .filter(r -> r.getShipment().getId().equals(inv.getArgument(0)))
                .count());

        when(custodyRecordRepository.findTopByShipmentIdOrderBySequenceNumberDesc(any())).thenAnswer(inv -> store
                .stream()
                .filter(r -> r.getShipment().getId().equals(inv.getArgument(0)))
                .max(Comparator.comparingInt(CustodyRecord::getSequenceNumber)));

        when(custodyRecordRepository.findAllByShipmentIdOrderBySequenceNumberAsc(any())).thenAnswer(inv -> store
                .stream()
                .filter(r -> r.getShipment().getId().equals(inv.getArgument(0)))
                .sorted(Comparator.comparingInt(CustodyRecord::getSequenceNumber))
                .toList());

        when(custodyRecordRepository.save(any(CustodyRecord.class))).thenAnswer(inv -> {
            CustodyRecord r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID()); // mimics @PrePersist
            }
            store.removeIf(existing -> existing.getId().equals(r.getId()));
            store.add(r);
            return r;
        });

        when(custodyRecordRepository.findById(any())).thenAnswer(
                inv -> store.stream().filter(r -> r.getId().equals(inv.getArgument(0))).findFirst());

        EncryptionService encryptionService = new EncryptionService(
                new EncryptionProperties(Base64.getEncoder().encodeToString(new byte[32])));
        service = new CustodyChainService(custodyRecordRepository, encryptionService, new ObjectMapper());

        manufacturer = user(Role.MANUFACTURER, "mfg@t.dev");
        courier = user(Role.COURIER, "courier@t.dev");
        retailer = user(Role.RETAILER, "retail@t.dev");

        shipment = Shipment.builder()
                .id(UUID.randomUUID())
                .manufacturer(manufacturer)
                .trackingCode("LT-TEST0001")
                .status(ShipmentStatus.CREATED)
                .originLabel("Factory A")
                .destinationLabel("Store B")
                .build();
    }

    private User user(Role role, String email) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash("x")
                .role(role)
                .createdAt(Instant.now())
                .build();
    }

    // ---------- appendRecord ----------

    @Test
    void appendRecord_firstRecord_chainsToAGenesisHash() {
        CustodyRecord record = service.appendRecord(shipment, null, manufacturer, "Factory A", "CREATED", null, null);

        assertThat(record.getSequenceNumber()).isZero();
        assertThat(record.getPreviousRecordHash()).isNotBlank();
        assertThat(record.getRecordHash()).isNotBlank().isNotEqualTo(record.getPreviousRecordHash());
    }

    @Test
    void appendRecord_secondRecord_chainsToFirstRecordsHash() {
        CustodyRecord first = service.appendRecord(shipment, null, manufacturer, "Factory A", "CREATED", null, null);
        CustodyRecord second = service.appendRecord(shipment, manufacturer, courier, "Factory A", "ASSIGNED", null, null);

        assertThat(second.getSequenceNumber()).isEqualTo(1);
        assertThat(second.getPreviousRecordHash()).isEqualTo(first.getRecordHash());
    }

    @Test
    void appendRecord_timestampHasNoSubMillisecondComponent() {
        // Regression guard: Postgres TIMESTAMPTZ round-trips at microsecond
        // precision while Instant.now() carries nanoseconds. If the hash is
        // computed from the untruncated value, verifyChain recomputes a
        // different hash after ANY real save+reload and reports false
        // tampering on every record, always. Caught live against real
        // Postgres - the H2 test DB alone does not reproduce it.
        CustodyRecord record = service.appendRecord(shipment, null, manufacturer, "Factory A", "CREATED", null, null);

        assertThat(record.getTimestamp())
                .isEqualTo(record.getTimestamp().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
    }

    @Test
    void appendRecord_encryptsConditionData_rawValueNeverStoredInTheClear() {
        ConditionData reading = new ConditionData(4.5, 60.0);

        CustodyRecord record = service.appendRecord(
                shipment, courier, courier, "Checkpoint 1", "IN_TRANSIT", reading, null);

        assertThat(record.getConditionDataCiphertext()).isNotNull().doesNotContain("4.5");
        ConditionData decrypted = service.decryptConditionData(record.getConditionDataCiphertext());
        assertThat(decrypted.temperatureC()).isEqualTo(4.5);
        assertThat(decrypted.humidityPct()).isEqualTo(60.0);
    }

    // ---------- verifyChain ----------

    @Test
    void verifyChain_untouchedChain_isIntact() {
        service.appendRecord(shipment, null, manufacturer, "Factory A", "CREATED", null, null);
        service.appendRecord(shipment, manufacturer, courier, "Factory A", "ASSIGNED", null, null);
        service.appendRecord(shipment, courier, courier, "Checkpoint 1", "IN_TRANSIT", null, null);
        service.appendRecord(shipment, courier, retailer, "Store B", "DELIVERED", null, null);

        ChainVerificationResponse result = service.verifyChain(shipment.getId());

        assertThat(result.intact()).isTrue();
        assertThat(result.totalRecords()).isEqualTo(4);
        assertThat(result.brokenAtSequence()).isNull();
        assertThat(result.records()).allMatch(ChainVerificationResponse.Entry::valid);
    }

    @Test
    void verifyChain_emptyChain_isTriviallyIntact() {
        ChainVerificationResponse result = service.verifyChain(shipment.getId());

        assertThat(result.intact()).isTrue();
        assertThat(result.totalRecords()).isZero();
    }

    @Test
    void verifyChain_directContentEdit_breaksTheTamperedRecordAndEveryOneAfterIt() {
        service.appendRecord(shipment, null, manufacturer, "Factory A", "CREATED", null, null);
        CustodyRecord second = service.appendRecord(shipment, manufacturer, courier, "Factory A", "ASSIGNED", null, null);
        service.appendRecord(shipment, courier, courier, "Checkpoint 1", "IN_TRANSIT", null, null);
        service.appendRecord(shipment, courier, retailer, "Store B", "DELIVERED", null, null);

        // Simulate a raw DB edit: change content, leave recordHash untouched.
        second.setLocation("Tampered Location");
        custodyRecordRepository.save(second);

        ChainVerificationResponse result = service.verifyChain(shipment.getId());

        assertThat(result.intact()).isFalse();
        assertThat(result.brokenAtSequence()).isEqualTo(1);
        assertThat(result.records().get(0).valid()).isTrue(); // untouched, before the break
        assertThat(result.records().get(1).valid()).isFalse(); // the tampered record itself
        // Everything after must also read broken - record #3's own content was
        // never touched and its self-hash still checks out, but it rests on a
        // corrupted history, so it must not read as trustworthy either.
        assertThat(result.records().get(2).valid()).isFalse();
        assertThat(result.records().get(3).valid()).isFalse();
    }

    @Test
    void verifyChain_editWithForgedSelfHash_stillCaughtByTheNextLink() {
        service.appendRecord(shipment, null, manufacturer, "Factory A", "CREATED", null, null);
        CustodyRecord second = service.appendRecord(shipment, manufacturer, courier, "Factory A", "ASSIGNED", null, null);
        service.appendRecord(shipment, courier, courier, "Checkpoint 1", "IN_TRANSIT", null, null);

        // A more sophisticated attacker edits the content AND forges this row's
        // own recordHash so its self-check passes in isolation...
        second.setLocation("Tampered Location");
        second.setRecordHash("0".repeat(64));
        custodyRecordRepository.save(second);

        // ...but didn't cascade the fix into the THIRD record's previousRecordHash,
        // which still points at the true original hash — so the break still
        // surfaces, pinpointing the tampered record.
        ChainVerificationResponse result = service.verifyChain(shipment.getId());

        assertThat(result.intact()).isFalse();
        assertThat(result.brokenAtSequence()).isEqualTo(1);
    }

    @Test
    void tamperRecord_mutatesContentOnly_thenVerifyChainCatchesIt() {
        CustodyRecord original = service.appendRecord(shipment, null, manufacturer, "Factory A", "CREATED", null, null);
        service.appendRecord(shipment, manufacturer, courier, "Factory A", "ASSIGNED", null, null);
        String originalHash = original.getRecordHash();

        CustodyRecord tampered = service.tamperRecord(original.getId());

        assertThat(tampered.getLocation()).contains("TAMPERED");
        assertThat(tampered.getRecordHash()).isEqualTo(originalHash); // hash field untouched

        ChainVerificationResponse result = service.verifyChain(shipment.getId());
        assertThat(result.intact()).isFalse();
        assertThat(result.brokenAtSequence()).isZero();
    }
}
