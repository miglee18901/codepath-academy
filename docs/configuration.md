# Cấu hình và vận hành

## Chạy local

Copy `.env.example` thành `.env`, điền giá trị phù hợp rồi chạy tại thư mục gốc:

```powershell
docker compose up -d --build --wait
docker compose ps
```

Frontend: `http://localhost:5173`; API: `http://localhost:8080`; Mailpit: `http://localhost:8025`. Port local được bind về loopback trong Compose. MySQL dùng database `academy_db`. Không dùng `docker compose down -v` nếu muốn giữ dữ liệu: tùy chọn `-v` xóa volume.

## Nhóm cấu hình

Đối chiếu tên biến và giá trị mẫu trực tiếp tại [`.env.example`](../.env.example) và [Compose](../docker-compose.yml).

| Nhóm | Mục đích |
| --- | --- |
| MySQL / Redis | Kết nối và mật khẩu các dịch vụ dữ liệu |
| Demo data | Cho phép tạo tài khoản và khóa mẫu khi khởi động |
| JWT | Khóa ký Base64, thời hạn access và refresh token |
| SMTP | Host, port, user, password, TLS và địa chỉ gửi |
| reCAPTCHA | Site key công khai ở frontend; secret chỉ backend |
| PayOS | Client ID, API key và checksum key |
| Mux | API token ID/secret, signing key ID/private key |
| FRONTEND_URL | Origin frontend cho backend, CORS upload Mux và URL quay lại PayOS |
| FRONTEND_API_URL | Để trống để dùng `/api` cùng domain; đặt URL nếu cần API riêng |

Compose truyền `FRONTEND_URL` vào `APP_FRONTENDURL`; frontend dùng cấu hình Vite tại lúc chạy/build theo thiết lập Compose. Nếu đổi host/domain, kiểm tra cả origin frontend, địa chỉ API và callback thanh toán. Không thay `localhost` bằng tên service Docker trong địa chỉ mà trình duyệt phải truy cập.

Khi `FRONTEND_URL` dùng `localhost` hoặc `127.0.0.1`, CORS của API chấp nhận cả hai tên với cùng giao thức và port. Với domain triển khai, API chỉ chấp nhận origin đã cấu hình. CORS upload trực tiếp Mux và URL quay lại PayOS vẫn dùng chính xác `FRONTEND_URL`; nên dùng địa chỉ này khi kiểm thử đầy đủ các tích hợp.

Có thể khai báo thêm các origin cụ thể bằng `APP_CORS_ALLOWED_ORIGINS` (thuộc tính `app.cors.allowed-origins`), phân tách bằng dấu phẩy; mặc định trống. Override ngrok thêm hai origin local cổng 5173 để vẫn đăng nhập được qua localhost trong khi tunnel đang chạy.

Spring Boot chạy trực tiếp không tự nạp file `.env`: truyền biến môi trường phù hợp hoặc cấu hình profile. Các tên thuộc tính thực tế nằm trong [application.yml](../src/main/resources/application.yml). Không đưa secret vào biến `VITE_*` vì giá trị đó có thể xuất hiện trong mã gửi đến trình duyệt.

Vite proxy `/api` tới `API_PROXY_TARGET` (Compose: `http://backend:8080`; chạy Vite trực tiếp: `http://localhost:8080`). `FRONTEND_PUBLIC_URL` cho phép đúng hostname public trong Vite. Hai biến này chỉ dùng ở server, không chứa secret. Khi build frontend để triển khai ngoài Vite, web server cũng cần route `/api` tới backend nếu dùng cùng domain. Xem [ngrok](ngrok.md) để chạy tunnel HTTPS qua Docker.

## Tích hợp tùy chọn

Mailpit nhận thư local, xem trong web UI; không tự chuyển thư đến hộp thư Gmail. Thiếu PayOS hợp lệ thì checkout trả lỗi dịch vụ chưa cấu hình. Thiếu Mux thì vẫn dùng bài văn bản và URL video trực tiếp; thao tác Mux trả thông báo cấu hình. Xem [hướng dẫn Mux](mux.md).

## Kiểm tra và vận hành

```powershell
docker compose logs --tail 100 backend
docker build --target test -t codepath-academy-tests .
npm --prefix frontend run lint
npm --prefix frontend run build
```

Flyway tự áp dụng migration khi khởi động; không sửa nội dung migration đã áp dụng. Sao lưu MySQL và thông tin liên kết Mux trước thao tác dữ liệu lớn. Sau thay đổi `.env`, tạo lại container liên quan bằng `docker compose up -d backend`; rebuild frontend khi thay cấu hình build. Không đăng toàn bộ `.env` hay log chứa token để chẩn đoán lỗi.
