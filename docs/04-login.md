# Luồng đăng nhập

## API

`POST /api/auth/login` nhận `username`, `password`. Tài khoản phải tồn tại; đăng ký OTP chưa xác minh chưa tạo tài khoản để đăng nhập.

## Trình tự

1. Frontend gửi thông tin đăng nhập qua API; production phải dùng HTTPS.
2. `AuthenticationManager` xác thực user và BCrypt password.
3. Backend tạo access JWT có `sub=username`, `jti` ngẫu nhiên, thời điểm cấp và hết hạn. Thời hạn mặc định là 15 phút theo cấu hình.
4. Tạo refresh token UUID, lưu Redis với key `refresh:<token>`, value username; TTL mặc định 7 ngày.
5. Trả thông tin user và hai token. Frontend lưu phiên trong localStorage.
6. Các request cần quyền gửi `Authorization: Bearer <access-token>`.
7. JWT filter kiểm tra chữ ký/hạn và tải user từ database; quyền ADMIN/STUDENT lấy từ user hiện tại.

## Lỗi và bảo mật

Sai tài khoản/mật khẩu trả lỗi xác thực; không cấp token. Redis phải sẵn sàng để lưu refresh token. Giữ an toàn localStorage trước XSS vì token tại đó có thể bị đọc bởi JavaScript cùng origin. Không đưa token vào tài liệu hoặc log chia sẻ.

## Mã nguồn và tiếp theo

[AuthServiceImpl](../src/main/java/com/codepath/academy/service/impl/AuthServiceImpl.java), [JwtService](../src/main/java/com/codepath/academy/security/JwtService.java), [JwtAuthenticationFilter](../src/main/java/com/codepath/academy/security/JwtAuthenticationFilter.java), [làm mới phiên](05-refresh-token.md).
