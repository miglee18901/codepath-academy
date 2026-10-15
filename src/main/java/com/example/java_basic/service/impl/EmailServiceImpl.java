package com.example.java_basic.service.impl;

import com.example.java_basic.dto.EmailMessageDTO;
import com.example.java_basic.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

  private final JmsTemplate jms;

  @Override
  public void sendOtpEmail(String to, String name, String code) {
    jms.convertAndSend(
      "emailQueue",
      EmailMessageDTO.builder()
        .to(to)
        .fullName(name)
        .type("OTP_VERIFY")
        .otpCode(code)
        .build()
    );
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void purchased(OrderService.PurchaseConfirmed event) {
    try {
      jms.convertAndSend(
        "emailQueue",
        EmailMessageDTO.builder()
          .to(event.email())
          .fullName(event.fullName())
          .type("PURCHASE")
          .orderId(event.orderId())
          .build()
      );
    } catch (Exception e) {
      log.error("Không gửi được email xác nhận đơn {}", event.orderId(), e);
    }
  }
}
