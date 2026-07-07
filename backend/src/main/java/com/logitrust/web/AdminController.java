package com.logitrust.web;

import com.logitrust.domain.CustodyRecord;
import com.logitrust.domain.FraudFlag;
import com.logitrust.domain.FraudFlagStatus;
import com.logitrust.dto.FraudFlagResponse;
import com.logitrust.dto.TamperDebugResponse;
import com.logitrust.repository.FraudFlagRepository;
import com.logitrust.service.CustodyChainService;
import com.logitrust.service.FraudScoringService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only fraud review tools. {@link #tamperCustodyRecord} exists solely
 * to make the custody chain's tamper detection demonstrable live (SRS 12):
 * break a record on purpose, then call GET /api/shipments/{id}/custody-chain
 * and watch it get caught. {@link #openFlags} is the read-only queue view
 * (FR-7.1); resolving a flag (confirm fraud / clear false positive, FR-7.3)
 * is a separate, later phase.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final CustodyChainService custodyChainService;
    private final FraudFlagRepository fraudFlagRepository;
    private final FraudScoringService fraudScoringService;

    public AdminController(
            CustodyChainService custodyChainService,
            FraudFlagRepository fraudFlagRepository,
            FraudScoringService fraudScoringService) {
        this.custodyChainService = custodyChainService;
        this.fraudFlagRepository = fraudFlagRepository;
        this.fraudScoringService = fraudScoringService;
    }

    /** Queue of open flags sorted by risk score, each with its full factor breakdown (FR-4.5, FR-7.1). */
    @GetMapping("/flags")
    public ResponseEntity<List<FraudFlagResponse>> openFlags() {
        List<FraudFlag> flags = fraudFlagRepository.findAllByStatusOrderByScoreDesc(FraudFlagStatus.OPEN);
        return ResponseEntity.ok(flags.stream().map(this::toResponse).toList());
    }

    @PostMapping("/custody-records/{id}/tamper")
    public ResponseEntity<TamperDebugResponse> tamperCustodyRecord(@PathVariable UUID id) {
        CustodyRecord record = custodyChainService.tamperRecord(id);
        return ResponseEntity.ok(new TamperDebugResponse(
                record.getId(), record.getShipment().getId(), record.getLocation()));
    }

    private FraudFlagResponse toResponse(FraudFlag flag) {
        return new FraudFlagResponse(
                flag.getId(),
                flag.getShipment().getId(),
                flag.getShipment().getTrackingCode(),
                flag.getCustodyRecord() != null ? flag.getCustodyRecord().getId() : null,
                flag.getScore(),
                fraudScoringService.parseFactors(flag.getFactors()),
                flag.getStatus(),
                flag.getCreatedAt());
    }
}
