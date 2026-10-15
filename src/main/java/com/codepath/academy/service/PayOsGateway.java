package com.codepath.academy.service;

import com.codepath.academy.entity.*;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.*;
import vn.payos.model.webhooks.*;

@Component
@RequiredArgsConstructor
public class PayOsGateway {

    private final PayOS payOS;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${client.id:local-placeholder}")
    private String clientId;

    public String checkout(CourseOrder order, List<OrderItem> items) {
        if (
                clientId.isBlank() || clientId.equals("local-placeholder")
        ) throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Chưa cấu hình PayOS. Vui lòng liên hệ quản trị viên."
        );
        try {
            var request = CreatePaymentLinkRequest.builder()
                    .orderCode(order.getId())
                    .amount(order.getTotal().longValueExact())
                    .description("COURSE " + order.getId())
                    .returnUrl(frontendUrl + "/orders?order=" + order.getId())
                    .cancelUrl(frontendUrl + "/orders?order=" + order.getId())
                    .items(
                            items
                                    .stream()
                                    .map(i ->
                                            PaymentLinkItem.builder()
                                                    .name(i.getCourseTitle())
                                                    .quantity(1)
                                                    .price(i.getPrice().longValueExact())
                                                    .build()
                                    )
                                    .toList()
                    )
                    .build();
            return payOS.paymentRequests().create(request).getCheckoutUrl();
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Không tạo được liên kết PayOS. Hãy thử lại hoặc liên hệ quản trị viên."
            );
        }
    }

    public WebhookData verify(Webhook webhook) {
        try {
            return payOS.webhooks().verify(webhook);
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chữ ký webhook không hợp lệ"
            );
        }
    }

    public PaymentLink payment(Long orderId) {
        try {
            return payOS.paymentRequests().get(orderId);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Chưa kiểm tra được thanh toán với PayOS. Vui lòng thử lại.");
        }
    }
}
