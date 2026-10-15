# Luồng gửi lại OTP

## Điều kiện và API

Đã khởi tạo [đăng ký](02-registration-otp.md) và phiên theo email vẫn còn. Gửi `POST /api/auth/register/resend` với `{"email":"learner@example.com"}`.

## Trình tự

1. Backend tìm phiên OTP trong cache theo email.
2. Không có phiên: từ chối, yêu cầu đăng ký lại; API không tạo tài khoản hoặc phiên mới từ email đơn lẻ.
3. Kiểm tra thời gian gửi gần nhất; chưa đủ 60 giây thì từ chối.
4. Sinh OTP sáu chữ số mới, đặt hạn 5 phút, reset số lần thử và cập nhật thời điểm gửi.
5. Ghi lại cache rồi gửi yêu cầu email vào queue.
6. Người dùng dùng mã mới ở API verify; mã trước đó không còn hợp lệ.

## Dữ liệu và lỗi

Chỉ thay đổi phiên cache, chưa ghi user vào MySQL. Hộp thư có thể nhận nhiều thư; cần dùng thư mới nhất. Nếu email giao chậm, kiểm tra Mailpit/SMTP và log listener trước khi yêu cầu mã liên tục. Cache local không chia sẻ giữa nhiều backend.

## Mã nguồn

[AuthServiceImpl](../src/main/java/com/codepath/academy/service/impl/AuthServiceImpl.java), [EmailServiceImpl](../src/main/java/com/codepath/academy/service/impl/EmailServiceImpl.java).
