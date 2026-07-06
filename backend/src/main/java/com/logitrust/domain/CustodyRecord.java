package com.logitrust.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * One tamper-evident entry in a shipment's custody chain (SRS 7.4 / FR-3.1).
 * {@code recordHash} fingerprints this row's own content at write time;
 * {@code previousRecordHash} pins it to the prior entry for the same
 * shipment. Verification (CustodyChainService) recomputes both from stored
 * data alone — no field here is ever mutated after creation by application
 * code, which is what makes a later direct-DB edit detectable.
 */
@Entity
@Table(name = "custody_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustodyRecord {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    /** Null for the genesis record (initial creation has no prior custodian). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_party_id")
    private User fromParty;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_party_id", nullable = false)
    private User toParty;

    @Column(nullable = false)
    private String location;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    /** AES-256-GCM ciphertext of a small JSON blob; null if not supplied. */
    @Column(name = "condition_data_ciphertext")
    private String conditionDataCiphertext;

    /** Optional free-text note (e.g. "box was wet, seal looked broken"). */
    @Column(name = "incident_note", columnDefinition = "TEXT")
    private String incidentNote;

    @Column(name = "previous_record_hash", nullable = false, length = 64)
    private String previousRecordHash;

    @Column(name = "record_hash", nullable = false, length = 64)
    private String recordHash;

    /** Server-assigned (SRS 9.3) — never accept this from a client payload. */
    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
