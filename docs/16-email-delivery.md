# Luồng gửi email

## Loại thư

| Sự kiện | Loại message | Nội dung |
| --- | --- | --- |
| Khởi tạo/gửi lại đăng ký | OTP_VERIFY | Mã xác thực đăng ký |
| Đơn miễn phí/trả phí đã commit | PURCHASE | Xác nhận đăng ký khóa và thông tin đơn |

## Trình tự

1. Service nghiệp vụ tạo `EmailMessageDTO` rồi gửi đến Artemis `emailQueue` qua `JmsTemplate`.
2. Với thanh toán, sự kiện `PurchaseConfirmed` chỉ enqueue sau commit để không gửi thư cho giao dịch rollback.
3. `EmailMessageListener` nhận message, chọn template OTP hoặc purchase.
4. Render email HTML UTF-8 và gửi bằng `JavaMailSender`, dùng địa chỉ `app.mail.from`.
5. Local SMTP trỏ Mailpit: thư xuất hiện ở `http://localhost:8025`, không tự chuyển đến hộp thư bên ngoài.

## Lỗi và bảo đảm

Gửi mail bất đồng bộ không đồng nghĩa người nhận đã nhận thành công ngay khi API trả về. Kiểm tra log listener, SMTP và spam khi không thấy thư. Lỗi enqueue sau thanh toán được ghi log, không rollback quyền học đã commit.

Broker embedded và cấu hình hiện tại chưa cung cấp quy trình outbox, quản trị dead-letter hoặc bảo đảm gửi đúng một lần. Restart có thể làm mất message đang chờ. Không có luồng quên mật khẩu/reset mật khẩu bằng email trong phiên bản này.

## Mã nguồn

[EmailServiceImpl](../src/main/java/com/codepath/academy/service/impl/EmailServiceImpl.java), [EmailMessageListener](../src/main/java/com/codepath/academy/listener/EmailMessageListener.java), [OrderService](../src/main/java/com/codepath/academy/service/OrderService.java), [templates](../src/main/resources/templates).
