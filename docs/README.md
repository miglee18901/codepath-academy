# Tài liệu CodePath Academy

Tài liệu mô tả hành vi đang triển khai. Mỗi luồng có một file riêng, gồm điều kiện, bước xử lý, dữ liệu và tình huống lỗi.

- [Kiến trúc](architecture.md)
- [Cấu hình và vận hành](configuration.md)
- [Mô hình dữ liệu](data-model.md)
- [Thiết lập Mux](mux.md)

| Luồng | Tài liệu |
| --- | --- |
| Khởi động | [01](01-startup.md) |
| Đăng ký và xác thực OTP | [02](02-registration-otp.md) |
| Gửi lại OTP | [03](03-resend-otp.md) |
| Đăng nhập | [04](04-login.md) |
| Làm mới phiên | [05](05-refresh-token.md) |
| Đăng xuất | [06](06-logout.md) |
| Tìm và xem khóa học | [07](07-course-discovery.md) |
| Đăng ký khóa miễn phí | [08](08-free-enrollment.md) |
| Mua khóa trả phí | [09](09-paid-checkout.md) |
| Xác nhận thanh toán | [10](10-payment-webhook.md) |
| Quản trị nội dung | [11](11-content-management.md) |
| Upload và quản lý video | [12](12-video-upload.md) |
| Phát video | [13](13-video-playback.md) |
| Xác nhận tiếp tục xem | [14](14-resume-confirmation.md) |
| Lưu tiến độ | [15](15-learning-progress.md) |
| Gửi email | [16](16-email-delivery.md) |
| Báo cáo quản trị | [17](17-admin-reporting.md) |
| Tạo dữ liệu kiểm thử | [18](18-demo-data.md) |

Khi đổi API hoặc quy tắc nghiệp vụ, cập nhật luồng tương ứng cùng mã nguồn. Không đưa mật khẩu thật, token hoặc khóa riêng vào tài liệu.
