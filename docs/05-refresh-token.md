# Luồng làm mới phiên đăng nhập

## Kích hoạt

API trả 401 khi access token không còn hợp lệ. Axios interceptor của frontend có thể làm mới rồi thử lại request một lần. Không áp dụng vòng lặp refresh cho chính endpoint xác thực.

## Trình tự

1. Frontend đọc refresh token của phiên hiện tại.
2. Gửi `POST /api/auth/refresh` với `{"refreshToken":"<token>"}`.
3. Backend thực hiện Redis `getAndDelete` trên `refresh:<token>`: đọc và tiêu thụ token trong một thao tác.
4. Không có giá trị: từ chối 401, không cấp phiên mới.
5. Có username: tạo access token và refresh token mới, lưu token mới cùng TTL.
6. Frontend thay hai token trong localStorage, thử lại request gốc bằng access token mới.
7. Nhiều request 401 trong một trang chia sẻ cùng promise refresh để giảm tranh chấp.

## Bất biến và lỗi

Refresh token đã tiêu thụ không được tái sử dụng. Hai tab độc lập vẫn có thể tranh chấp token; không có cơ chế phối hợp cross-tab riêng. Nếu refresh thất bại, frontend xóa phiên và chuyển về đăng nhập. Request đã retry không được refresh lần hai để tránh lặp vô hạn.

Access JWT và token phát Mux là hai loại token khác nhau; refresh đăng nhập không trực tiếp thay token phát video.

## Mã nguồn

[AuthServiceImpl](../src/main/java/com/codepath/academy/service/impl/AuthServiceImpl.java), [frontend source](../frontend/src), [đăng nhập](04-login.md), [phát video](13-video-playback.md).
