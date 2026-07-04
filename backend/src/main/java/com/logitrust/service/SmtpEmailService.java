package com.logitrust.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Real OTP delivery over SMTP (Gmail by default — see application.yml).
 * Activated with MAIL_MODE=smtp; otherwise the logging stub is used so the
 * app runs without credentials. Sends async so a slow SMTP round-trip never
 * blocks the login request itself.
 */
@Service
@ConditionalOnProperty(name = "app.mail.mode", havingValue = "smtp")
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailService(JavaMailSender mailSender, @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Async
    @Override
    public void sendOtpEmail(String to, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from, "LogiTrust Security");
            helper.setTo(to);
            helper.setSubject("Your LogiTrust verification code: " + code);
            helper.setText("""
                    <div style="font-family:Arial,Helvetica,sans-serif;max-width:480px;margin:0 auto;\
                    padding:24px;border:1px solid #e4e7ee;border-radius:12px">
                      <h2 style="color:#1e2a45;margin:0 0 8px">LogiTrust verification code</h2>
                      <p style="color:#4a5570;margin:0 0 20px">Use this code to finish signing in. \
                    It expires in 5 minutes.</p>
                      <div style="font-size:32px;font-weight:bold;letter-spacing:8px;color:#2f6df6;\
                    text-align:center;padding:16px;background:#f4f7ff;border-radius:8px">%s</div>
                      <p style="color:#7a849c;font-size:12px;margin:20px 0 0">If you didn't try to \
                    sign in, you can ignore this email — your password was entered correctly, so \
                    consider changing it.</p>
                    </div>
                    """.formatted(code), true);
            mailSender.send(message);
            log.info("OTP email sent to {}", to);
        } catch (Exception e) {
            // Never log the code itself; the user can retry login for a new one.
            log.error("Failed to send OTP email to {}: {}", to, e.getMessage());
        }
    }
}
