package com.logitrust.web;

import com.logitrust.domain.CustodyRecord;
import com.logitrust.dto.TamperDebugResponse;
import com.logitrust.service.CustodyChainService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only debug tools. {@link #tamperCustodyRecord} exists solely to make
 * the custody chain's tamper detection demonstrable live (SRS 12): break a
 * record on purpose, then call GET /api/shipments/{id}/custody-chain and
 * watch it get caught.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final CustodyChainService custodyChainService;

    public AdminController(CustodyChainService custodyChainService) {
        this.custodyChainService = custodyChainService;
    }

    @PostMapping("/custody-records/{id}/tamper")
    public ResponseEntity<TamperDebugResponse> tamperCustodyRecord(@PathVariable UUID id) {
        CustodyRecord record = custodyChainService.tamperRecord(id);
        return ResponseEntity.ok(new TamperDebugResponse(
                record.getId(), record.getShipment().getId(), record.getLocation()));
    }
}
