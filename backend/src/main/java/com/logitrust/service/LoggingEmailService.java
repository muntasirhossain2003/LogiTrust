package com.logitrust.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Dev/demo stand-in for real email delivery: logs the OTP instead of sending
 * it. Active by default (MAIL_MODE unset or "log"); set MAIL_MODE=smtp to
 * switch to {@link SmtpEmailService}.
 */
@Service
@ConditionalOnProperty(name = "app.mail.mode", havingValue = "log", matchIfMissing = true)
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    @Override
    public void sendOtpEmail(String to, String code) {
        log.info("[DEV EMAIL STUB] OTP for {} is: {}", to, code);
    }
}
