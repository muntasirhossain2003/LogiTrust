package com.logitrust.web;

import com.logitrust.domain.User;
import com.logitrust.dto.AuthTokensResponse;
import com.logitrust.dto.LoginRequest;
import com.logitrust.dto.MessageResponse;
import com.logitrust.dto.OtpRequiredResponse;
import com.logitrust.dto.RefreshRequest;
import com.logitrust.dto.RegisterRequest;
import com.logitrust.dto.VerifyOtpRequest;
import com.logitrust.service.AuthService;
import com.logitrust.service.LoginOutcome;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MessageResponse("Account created for " + user.getEmail() + ". Please log in."));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        LoginOutcome outcome = authService.login(request);
        if (outcome instanceof LoginOutcome.Authenticated authenticated) {
            return ResponseEntity.ok(authenticated.tokens());
        }
        LoginOutcome.OtpRequired otpRequired = (LoginOutcome.OtpRequired) outcome;
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(otpRequired.challenge());
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<AuthTokensResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(authService.verifyOtp(request.otpToken(), request.code()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokensResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok(new MessageResponse("Logged out."));
    }
}
