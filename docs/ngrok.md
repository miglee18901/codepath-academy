# Chạy dự án bằng domain HTTPS ngrok

## Luồng truy cập

```text
Trình duyệt / PayOS
        ↓ HTTPS
https://<NGROK_DOMAIN>
        ↓ tunnel ngrok
frontend:5173 (Vite)
        ├── /          → giao diện React
        └── /api/**    → proxy → backend:8080 → MySQL / Redis
```

Chỉ cần một domain ngrok. Browser gọi API cùng origin, không gọi `localhost:8080` trên thiết bị người xem. Video và upload Mux vẫn đi trực tiếp tới Mux. Đây là cấu hình kiểm thử dùng Vite development server.

## 1. Lấy domain và token

Đăng ký/đăng nhập [ngrok Dashboard](https://dashboard.ngrok.com/).

- Vào [Your Authtoken](https://dashboard.ngrok.com/get-started/your-authtoken), copy authtoken.
- Vào [Domains](https://dashboard.ngrok.com/domains), copy hostname được cấp cho tài khoản. Dùng đúng đuôi domain hiển thị trong Dashboard.

Không cần cài ngrok CLI trên Windows vì Compose chạy ngrok trong container. Tài khoản Free dùng domain được cấp theo giới hạn của gói; xem [giới hạn hiện hành](https://ngrok.com/docs/pricing-limits/free-plan-limits).

## 2. Cập nhật `.env`

Nếu đã có `.env`, sửa/thêm các dòng bên dưới; giữ các cấu hình DB, JWT, PayOS và Mux đang dùng. Chỉ copy `.env.example` khi chưa có `.env`.

```dotenv
NGROK_DOMAIN=your-assigned-domain.ngrok-free.app
NGROK_AUTHTOKEN=paste-your-real-authtoken-here
FRONTEND_API_URL=
```

Domain trên là ví dụ. `NGROK_DOMAIN` chỉ chứa hostname, không có `https://`, port hoặc đường dẫn. Không commit authtoken. File override tự đặt origin HTTPS của backend và frontend từ domain này; không cần đổi `FRONTEND_URL` local.

## 3. Chạy Docker

Mở Docker Desktop, chạy tại thư mục gốc dự án:

```powershell
docker compose -f docker-compose.yml -f compose.ngrok.yml up -d --build --wait
docker compose -f docker-compose.yml -f compose.ngrok.yml ps
docker compose -f docker-compose.yml -f compose.ngrok.yml logs --tail=80 ngrok
```

Mở `https://<NGROK_DOMAIN>` theo hostname thật đã điền. Nếu ngrok hiển thị trang thông báo truy cập lần đầu, chọn tiếp tục vào website. `--wait` kiểm tra sức khỏe backend và trạng thái container; cần kiểm tra log ngrok và URL thực tế để xác nhận tunnel đã online.

Khi đang dùng ngrok, tiếp tục dùng cả hai tùy chọn `-f` trong các lệnh rebuild để giữ cấu hình public. Không cần `docker compose down` hoặc xóa volume trước khi chạy. Mailpit vẫn truy cập local tại `http://localhost:8025`.

File override cho phép API nhận cả origin HTTPS ngrok và hai origin local `http://localhost:5173`, `http://127.0.0.1:5173` qua `APP_CORS_ALLOWED_ORIGINS`. Có thể đăng nhập và tải dữ liệu từ cả ba địa chỉ. Mux upload và URL quay về PayOS vẫn dùng domain ngrok, nên dùng domain này khi kiểm thử tích hợp.

## 4. Kiểm thử

1. Mở domain HTTPS, kiểm tra danh sách khóa học và đăng nhập.
2. Trong DevTools → Network, các request dữ liệu phải đi tới `https://<NGROK_DOMAIN>/api/...`.
3. Mở trên điện thoại để xác nhận website không phụ thuộc localhost của thiết bị.
4. Vào khóa Mux, thử phát video. Upload mới cần được tạo khi backend đang dùng domain này để Mux nhận đúng `cors_origin`. Upload đang chờ được tạo với origin cũ cần hủy và tạo lại nếu gặp lỗi CORS.

Domain mới có localStorage riêng: cần đăng nhập lại; dữ liệu tài khoản và tiến độ trong DB vẫn giữ nguyên.

## 5. PayOS và CAPTCHA

Trong cấu hình kênh thanh toán PayOS, đặt webhook URL:

```text
https://<NGROK_DOMAIN>/api/payments/webhook
```

Giữ bộ khóa `PAYOS_*` hợp lệ trong `.env`. Tạo đơn hàng và checkout mới để return/cancel URL dùng domain mới; link thanh toán tạo trước đó có thể vẫn trỏ về địa chỉ cũ. Trang quay về yêu cầu backend đối soát API PayOS; quyền học chỉ cấp sau webhook hợp lệ hoặc kết quả đối soát đúng đơn và đủ tiền, không dựa vào tham số URL. Khi kiểm tra webhook, xem log backend và request trong [ngrok Traffic Inspector](https://dashboard.ngrok.com/traffic-inspector). Không dùng GET trên endpoint webhook để kết luận webhook lỗi: endpoint nhận POST và xác minh chữ ký.

Nếu dùng reCAPTCHA key thật, bổ sung hostname ngrok vào danh sách domain được phép trong cấu hình key của Google. Key test hiện có chỉ dành cho kiểm thử.

## 6. Dừng tunnel hoặc trở về local

Dừng riêng ngrok, giữ ứng dụng đang chạy:

```powershell
docker compose -f docker-compose.yml -f compose.ngrok.yml stop ngrok
```

Chuyển backend/frontend về cấu hình local:

```powershell
docker compose up -d --build --wait backend frontend
```

Mở lại `http://localhost:5173`. Dữ liệu MySQL không bị xóa. Máy chạy Docker cần bật và có Internet khi dùng tunnel.

## Lỗi thường gặp

| Hiện tượng | Cách kiểm tra |
| --- | --- |
| Compose báo thiếu biến | Điền `NGROK_DOMAIN`, `NGROK_AUTHTOKEN` vào `.env` tại thư mục gốc |
| Ngrok báo token/domain không hợp lệ hoặc endpoint đang dùng | Kiểm tra token và domain cùng tài khoản, dừng tunnel khác đang dùng domain đó rồi chạy lại |
| `Blocked request. This host is not allowed` | Chạy lại frontend bằng cả hai file Compose; dùng đúng hostname đã khai báo |
| API vẫn gọi localhost | Dùng lệnh có `compose.ngrok.yml`, kiểm tra Network rồi Ctrl+F5 |
| `/api` trả 502 hoặc proxy error | Kiểm tra `docker compose logs --tail=100 backend frontend` và sức khỏe backend |
| API trả `Invalid CORS request` | Kiểm tra đúng domain HTTPS và rebuild backend bằng cả hai file Compose; local chỉ cho phép cổng 5173 |
| Thanh toán xong vẫn chưa có quyền | Kiểm tra webhook public, chữ ký PayOS và trạng thái đơn; tunnel phải online khi PayOS gửi webhook |

Tài liệu chính thức: [ngrok Docker](https://ngrok.com/docs/using-ngrok-with/docker), [Vite server options](https://vite.dev/config/server-options).
