package com.logitrust.web;

import com.logitrust.security.JwtService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Returns the identity carried by the presented access token. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    @GetMapping
    public ResponseEntity<Map<String, String>> me(
            @AuthenticationPrincipal JwtService.AccessTokenClaims claims) {
        return ResponseEntity.ok(Map.of(
                "id", claims.userId().toString(),
                "email", claims.email(),
                "role", claims.role().name()));
    }
}
