package com.logitrust.service;

public interface EmailService {
    void sendOtpEmail(String to, String code);
}
