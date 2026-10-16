package com.codepath.academy.listener;

import com.codepath.academy.dto.EmailMessageDTO;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailMessageListener {

    private final JavaMailSender sender;
    private final TemplateEngine templates;
    private final MeterRegistry registry;

    @Value("${app.mail-from:no-reply@codepath.local}")
    private String from;

    @JmsListener(destination = "emailQueue")
    public void receiveEmailMessage(EmailMessageDTO email)
            throws jakarta.mail.MessagingException {
        Timer.Sample sample = Timer.start(registry);
        boolean success = false;
        try {
            Context c = new Context();
            c.setVariable("fullName", email.getFullName());
            c.setVariable("otpCode", email.getOtpCode());
            c.setVariable("orderId", email.getOrderId());
            boolean otp = "OTP_VERIFY".equals(email.getType());
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(email.getTo());
            helper.setSubject(
                    otp
                            ? "CodePath · Xác thực tài khoản"
                            : "CodePath · Xác nhận đăng ký khóa học"
            );
            helper.setText(
                    templates.process(otp ? "otp-email" : "purchase-email", c),
                    true
            );
            sender.send(message);
            success = true;
        } finally {
            String outcome = success ? "success" : "failure";
            sample.stop(registry.timer("codepath.email.processing", "outcome", outcome));
            registry.counter("codepath.email.deliveries", "outcome", outcome).increment();
            log.info("Email delivery outcome={}", outcome);
        }
    }
}
