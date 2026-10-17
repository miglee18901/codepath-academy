# CodePath Academy

Nền tảng bán và học khóa học lập trình, với đăng ký OTP email, thanh toán PayOS, video Mux riêng tư và đồng bộ tiến độ xem.

## Công nghệ và cấu trúc

| Thành phần | Công nghệ |
|---|---|
| Backend | Java 17, Spring Boot 4.1, Spring Security, JPA, Flyway |
| Frontend | React 19, React Router 7, Vite 8 |
| Dữ liệu | MySQL 8, Redis 7 |
| Email | Artemis nhúng, Thymeleaf, SMTP; Mailpit cho local |
| Thanh toán và video | PayOS, Mux, Mux Player, UpChunk |

```text
src/main/java/com/codepath/academy/   Backend; CodePathAcademyApplication là lớp khởi động
src/main/resources/                 Cấu hình, migration và mẫu email
src/test/java/com/codepath/academy/   Unit test
frontend/                           Giao diện học viên và quản trị
scripts/                            Seed và kiểm thử tích hợp
docs/                               Kiến trúc, cấu hình và từng luồng xử lý
```

## Chạy nhanh bằng Docker

Muốn học và quản lý **ứng dụng + Kafka + AI + giám sát + Jenkins trong một Compose**, dùng:

```powershell
./scripts/learning.ps1 up -WithJenkins
./scripts/learning.ps1 status
```

File chung: [`compose.learning.yaml`](compose.learning.yaml). Xem [hướng dẫn chạy, cấu hình và quản lý](docs/docker-learning.md). Các lệnh bên dưới vẫn là chế độ ứng dụng cơ bản.

Cần Docker Desktop với Linux containers. Chạy tại thư mục gốc:

```powershell
docker compose up -d --build --wait
docker compose ps
```

| Địa chỉ | Mục đích |
|---|---|
| http://localhost:5173 | Website |
| http://localhost:8080 | Backend API |
| http://localhost:8025 | Xem OTP và email local trên Mailpit |

Compose mặc định bật `DEMO_DATA=true`. Trên database trống, ứng dụng tạo ba khóa học minh họa và hai tài khoản:

| Tài khoản | Mật khẩu | Vai trò |
|---|---|---|
| `admin` | `123456` | ADMIN |
| `student` | `Learn123!` | STUDENT |

Dữ liệu và tài khoản trên dành cho trải nghiệm local. Bổ sung 24 khóa mẫu bằng `python scripts/seed_courses.py` khi backend sẵn sàng. Video không được upload tự động khi khởi động.

## Cấu hình

Google/GitHub: thêm OAuth Client ID/Secret vào `.env`, rồi restart backend.
Xem [hướng dẫn tạo OAuth App và liên kết tài khoản](docs/social-login.md).

Passkeys: đăng nhập rồi vào **Hồ sơ cá nhân → Passkeys** để thêm/xóa; trang đăng
nhập có nút **Đăng nhập bằng Passkey**. Xem [cấu hình và kiểm thử Passkeys](docs/passkeys.md).

Lần đầu cần tùy chỉnh, copy `.env.example` thành `.env`; nếu đã có `.env`, cập nhật từng biến cần thiết. Sau thay đổi, chạy lại `docker compose up -d` để container nhận environment mới.

- Mailpit nhận email local; gửi email thật cần cấu hình SMTP.
- Khóa miễn phí hoạt động khi chưa cấu hình PayOS. Khóa trả phí cần bộ khóa PayOS và webhook HTTPS.
- Bài văn bản/MP4 hoạt động độc lập. Upload Mux và signed playback cần bốn biến `MUX_*` ở backend.
- `FRONTEND_URL` là origin frontend cho CORS, URL chuyển về sau thanh toán và origin upload Mux.
- `FRONTEND_API_URL` để trống thì browser gọi `/api` cùng domain, Vite proxy tới backend. Có thể đặt URL API riêng khi cần. Vite trực tiếp dùng `VITE_API_URL` trong `frontend/.env.local`.

Xem [cấu hình đầy đủ](docs/configuration.md) và [thiết lập Mux](docs/mux.md). Database `academy_db` được Flyway quản lý; không sửa migration đã áp dụng.

Để dùng một domain HTTPS cho cả website, API và webhook PayOS, xem [hướng dẫn ngrok bằng Docker](docs/ngrok.md).

## Chức năng

- Khách tìm khóa, xem mục lục và bài xem thử đã xuất bản.
- Danh sách khóa học và bảng khóa học quản trị phân trang 12 khóa/trang; trang công khai lưu bộ lọc và trang trên URL.
- Học viên đăng ký CAPTCHA/OTP, đăng nhập, mua khóa và theo dõi đơn hàng.
- Khóa miễn phí cấp quyền ngay; khóa trả phí cấp quyền sau webhook PayOS hợp lệ hoặc đối soát API PayOS xác nhận đủ tiền.
- Video Mux dùng token có hạn; dữ liệu video không đi qua backend.
- Checkpoint lưu mỗi 10 giây và khi dừng/tua/rời trang. Popup khi mở lại cho chọn tiếp tục hoặc xem từ đầu.
- ADMIN quản lý nội dung, video; xem tài khoản, doanh thu và xuất Excel đơn hàng.

## Tài liệu luồng

Danh mục đầy đủ: [docs/README.md](docs/README.md). Mỗi luồng có một file riêng, gồm API, điều kiện truy cập, trình tự, dữ liệu thay đổi và lỗi.

Bắt đầu với [đăng ký OTP](docs/02-registration-otp.md), [thanh toán](docs/09-paid-checkout.md), [webhook và cấp quyền](docs/10-payment-webhook.md), [phát video](docs/13-video-playback.md), [popup xem tiếp](docs/14-resume-confirmation.md).

## Jenkins CI/CD

Bộ Jenkins Docker dùng chung nằm tại [`infra/jenkins`](infra/jenkins/README.md), tách khỏi Compose của ứng dụng. Chạy `powershell -NoProfile -File infra/jenkins/setup.ps1`, rồi `docker compose -f infra/jenkins/compose.yaml up -d --build --wait`; truy cập http://localhost:8090.

[`Jenkinsfile`](Jenkinsfile) chạy test backend, lint/build frontend và đóng gói Docker image. Tham số `DEPLOY_STAGING` bật triển khai staging riêng tại http://localhost:15173. Commit/push cấu hình lên repository trước khi chạy job lấy pipeline từ Git. Xem hướng dẫn về tài khoản, webhook, credential và thêm dự án khác trong tài liệu Jenkins.

## Kiểm thử

Tìm kiếm ngữ nghĩa và trợ lý hỏi đáp theo quyền học bằng AI local (Ollama + Qdrant): xem [`infra/ai/README.md`](infra/ai/README.md). Chạy `powershell -NoProfile -ExecutionPolicy Bypass -File infra/ai/setup.ps1 -WithObservability` nếu đang dùng bộ giám sát. Mặc định AI tắt; script bật và tải mô hình. Không cần API key của nhà cung cấp AI.

Giám sát Docker bằng OpenTelemetry, Prometheus, Loki, Tempo và Grafana: xem [`infra/observability/README.md`](infra/observability/README.md). Chạy `powershell -NoProfile -ExecutionPolicy Bypass -File infra/observability/setup.ps1`; Grafana tại http://localhost:3000, hộp thư cảnh báo local tại http://localhost:18026. Dashboard/rule cảnh báo được quản lý trong Git; mật khẩu nằm trong file `.env` được bỏ qua.

```powershell
docker build --target test -t codepath-academy-tests .
docker compose exec -T frontend npm run lint
docker compose exec -T frontend npm run build
```

Python 3 và backend local đang chạy:

```powershell
python scripts/registration_test.py
python scripts/smoke_test.py --local-webhook-key local-placeholder
```

Registration test cần Google test CAPTCHA và Mailpit. Smoke test tạo dữ liệu QA, kiểm tra quyền/đơn hàng rồi ẩn khóa QA. Chỉ dùng tùy chọn `--local-webhook-key` trên môi trường local có checksum key tương ứng; script gửi webhook mô phỏng có chữ ký, không tạo giao dịch ngân hàng thật.

Kiểm thử UI với Chrome cài sẵn:

```powershell
npm install --prefix .qa --no-save --package-lock=false playwright
node scripts/ui_test.cjs
```

`.qa` chứa công cụ/tệp kiểm thử; ảnh ở `artifacts/qa`. Git bỏ qua cả hai thư mục. Xem [luồng dữ liệu demo](docs/18-demo-data.md) trước khi chạy script upload video thật.

## Vận hành và phạm vi

`docker compose down` dừng ứng dụng và giữ volume. `docker compose down -v` xóa dữ liệu volume của project; chỉ dùng khi chủ động reset dữ liệu.

Compose dùng Vite development server cho local. Triển khai thực tế cần build frontend, HTTPS, thông tin SMTP/PayOS/Mux, secret riêng, sao lưu và theo dõi lỗi. Artemis nhúng chưa lưu bền queue email và chưa có transactional outbox.

Chưa có giỏ hàng, mã giảm giá, hoàn tiền, chứng chỉ, bài tập tự chấm, phân trang đơn hàng/tài khoản hoặc giảng viên tự quản trị. Signed video không phải DRM; URL MP4 bên ngoài phụ thuộc quyền truy cập của nơi lưu trữ.
# Kafka và nghiệp vụ bất đồng bộ

Thông báo mua hàng/hoàn thành khóa học, báo cáo doanh thu và worker Kafka: xem [hướng dẫn triển khai và khôi phục](infra/kafka/README.md). Chạy `./infra/kafka/setup.ps1` để bật local; hạ tầng Kafka có Docker Compose riêng dùng chung cho dự án khác.
