package com.logitrust.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logitrust.domain.CustodyRecord;
import com.logitrust.domain.Shipment;
import com.logitrust.domain.User;
import com.logitrust.dto.ChainVerificationResponse;
import com.logitrust.dto.ConditionData;
import com.logitrust.exception.CustodyRecordNotFoundException;
import com.logitrust.repository.CustodyRecordRepository;
import com.logitrust.security.EncryptionService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends and verifies a shipment's tamper-evident custody chain (SRS 4.3 /
 * 7.4). Every record's hash is a fingerprint of its own stored content;
 * every record also pins the hash of the one before it. Verification
 * recomputes both from what is currently in the database — never from a
 * cached or stored "is valid" flag — so it is deterministic and reproducible
 * from stored data alone (NFR: Reliability).
 */
@Service
public class CustodyChainService {

    private static final String GENESIS_PREFIX = "LOGITRUST-GENESIS:";

    private final CustodyRecordRepository custodyRecordRepository;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    public CustodyChainService(
            CustodyRecordRepository custodyRecordRepository,
            EncryptionService encryptionService,
            ObjectMapper objectMapper) {
        this.custodyRecordRepository = custodyRecordRepository;
        this.encryptionService = encryptionService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CustodyRecord appendRecord(
            Shipment shipment,
            User fromParty,
            User toParty,
            String location,
            String eventType,
            ConditionData conditionData,
            String incidentNote) {

        int sequenceNumber = custodyRecordRepository.countByShipmentId(shipment.getId());
        String previousHash = custodyRecordRepository
                .findTopByShipmentIdOrderBySequenceNumberDesc(shipment.getId())
                .map(CustodyRecord::getRecordHash)
                .orElseGet(() -> genesisHash(shipment.getId().toString()));

        String conditionCiphertext = conditionData == null ? null : encrypt(conditionData);
        // Truncate to millis: Instant.now() carries nanosecond precision, but
        // TIMESTAMPTZ round-trips at microsecond precision (and other DBs may be
        // coarser still). Hash a value that survives the store/reload exactly,
        // or every verification looks like tampering that never happened.
        Instant timestamp = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

        String recordHash = computeHash(
                shipment.getId(), sequenceNumber,
                fromParty != null ? fromParty.getId() : null,
                toParty.getId(), location, eventType, conditionCiphertext, incidentNote, timestamp, previousHash);

        CustodyRecord record = CustodyRecord.builder()
                .shipment(shipment)
                .sequenceNumber(sequenceNumber)
                .fromParty(fromParty)
                .toParty(toParty)
                .location(location)
                .eventType(eventType)
                .conditionDataCiphertext(conditionCiphertext)
                .incidentNote(incidentNote)
                .previousRecordHash(previousHash)
                .recordHash(recordHash)
                .timestamp(timestamp)
                .build();

        return custodyRecordRepository.save(record);
    }

    @Transactional(readOnly = true)
    public ChainVerificationResponse verifyChain(java.util.UUID shipmentId) {
        List<CustodyRecord> chain = custodyRecordRepository.findAllByShipmentIdOrderBySequenceNumberAsc(shipmentId);

        String expectedPrevious = genesisHash(shipmentId.toString());
        Integer brokenAt = null;
        List<ChainVerificationResponse.Entry> entries = new java.util.ArrayList<>();

        for (CustodyRecord record : chain) {
            String recomputedHash = computeHash(
                    shipmentId, record.getSequenceNumber(),
                    record.getFromParty() != null ? record.getFromParty().getId() : null,
                    record.getToParty().getId(), record.getLocation(), record.getEventType(),
                    record.getConditionDataCiphertext(), record.getIncidentNote(),
                    record.getTimestamp(), record.getPreviousRecordHash());

            // Self-check: does the row's own stored hash match its stored content?
            boolean selfValid = recomputedHash.equals(record.getRecordHash());
            // Link-check: does this row point at the TRUE (recomputed) hash of its
            // predecessor, not just whatever that predecessor's hash field claims?
            // This is what catches a tamper even if the attacker also patched the
            // tampered row's own recordHash to stay self-consistent.
            boolean linkValid = record.getPreviousRecordHash().equals(expectedPrevious);

            if ((!selfValid || !linkValid) && brokenAt == null) {
                brokenAt = record.getSequenceNumber();
            }
            // Once broken, every record from that point on is unverifiable — not
            // just the one that failed its own check. A later record's own hash
            // can still self-check "valid" while resting on a corrupted history,
            // so it must not read as trustworthy (SRS 10.4: "every subsequent
            // record's previousRecordHash reference breaks").
            boolean valid = brokenAt == null;

            entries.add(new ChainVerificationResponse.Entry(
                    record.getSequenceNumber(),
                    record.getEventType(),
                    record.getFromParty() != null ? record.getFromParty().getEmail() : null,
                    record.getToParty().getEmail(),
                    record.getLocation(),
                    record.getTimestamp(),
                    valid));

            // Propagate the RECOMPUTED hash forward, not the stored one, so
            // forged-but-self-consistent tampering still breaks the next link.
            expectedPrevious = recomputedHash;
        }

        return new ChainVerificationResponse(brokenAt == null, chain.size(), brokenAt, entries);
    }

    /**
     * Admin-only demo hook (SRS 12 risk mitigation): simulates a malicious
     * direct-database edit by mutating a record's location WITHOUT touching
     * its recordHash or any other record's previousRecordHash — exactly what
     * an attacker with raw DB access would do. Never call this from normal
     * application flow; it exists purely so verifyChain's detection can be
     * demonstrated live.
     */
    @Transactional
    public CustodyRecord tamperRecord(java.util.UUID recordId) {
        CustodyRecord record = custodyRecordRepository.findById(recordId)
                .orElseThrow(CustodyRecordNotFoundException::new);
        record.setLocation(record.getLocation() + " [TAMPERED " + Instant.now() + "]");
        return custodyRecordRepository.save(record);
    }

    public ConditionData decryptConditionData(String ciphertext) {
        if (ciphertext == null) {
            return null;
        }
        try {
            return objectMapper.readValue(encryptionService.decrypt(ciphertext), ConditionData.class);
        } catch (Exception e) {
            throw new IllegalStateException("Condition data is corrupted or tampered.", e);
        }
    }

    private String encrypt(ConditionData conditionData) {
        try {
            return encryptionService.encrypt(objectMapper.writeValueAsString(conditionData));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize condition data", e);
        }
    }

    private String genesisHash(String shipmentId) {
        return sha256Hex(GENESIS_PREFIX + shipmentId);
    }

    private String computeHash(
            java.util.UUID shipmentId, int sequenceNumber, java.util.UUID fromPartyId, java.util.UUID toPartyId,
            String location, String eventType, String conditionCiphertext, String incidentNote, Instant timestamp,
            String previousHash) {
        String canonical = String.join("|",
                shipmentId.toString(),
                String.valueOf(sequenceNumber),
                fromPartyId != null ? fromPartyId.toString() : "NONE",
                toPartyId.toString(),
                location,
                eventType,
                conditionCiphertext != null ? conditionCiphertext : "NONE",
                incidentNote != null ? incidentNote : "NONE",
                timestamp.toString(),
                previousHash);
        return sha256Hex(canonical);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
