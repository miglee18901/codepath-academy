package com.codepath.academy.listener;

import com.codepath.academy.dto.EmailMessageDTO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailMessageListenerTest {
    @Test void deliveryMetricsRecordSuccessAndFailureWithoutSwallowingErrors() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        TemplateEngine templates = mock(TemplateEngine.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        when(sender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        when(templates.process(anyString(), any(IContext.class))).thenReturn("test");
        EmailMessageListener listener = new EmailMessageListener(sender, templates, registry);
        ReflectionTestUtils.setField(listener, "from", "test@codepath.local");
        EmailMessageDTO email = EmailMessageDTO.builder().to("qa@codepath.local").type("OTP_VERIFY").build();
        listener.receiveEmailMessage(email);
        doThrow(new MailSendException("synthetic failure")).when(sender).send(any(MimeMessage.class));
        assertThrows(MailSendException.class, () -> listener.receiveEmailMessage(email));
        for (String outcome : new String[]{"success", "failure"}) {
            assertEquals(1, registry.get("codepath.email.deliveries").tag("outcome", outcome).counter().count());
            assertEquals(1, registry.get("codepath.email.processing").tag("outcome", outcome).timer().count());
        }
    }
}
