# CodePath — Nền tảng bán khóa học lập trình

Ứng dụng Spring Boot 4.1 / Java 17 và React 19 / Vite 8. Hỗ trợ khám phá khóa học, xem thử, mua qua PayOS, cấp quyền học
và theo dõi tiến độ.

## Chạy bằng Docker

Cài Docker Desktop với Linux containers. Tại thư mục gốc:

```powershell
docker compose up -d --build
docker compose logs -f backend
```

| Địa chỉ               | Chức năng                                      |
|-----------------------|------------------------------------------------|
| http://localhost:5173 | Giao diện học viên và quản trị                 |
| http://localhost:8080 | REST API                                       |
| http://localhost:8025 | Mailpit: xem OTP và email xác nhận trong local |

Compose mặc định bật dữ liệu demo:

| Tài khoản | Mật khẩu  | Vai trò |
|-----------|-----------|---------|
| admin     | 123456    | ADMIN   |
| student   | Learn123! | STUDENT |

Ba khóa học mẫu gồm Java Backend, React và Nhập môn tư duy lập trình (miễn phí). Nội dung bài học là dữ liệu minh họa,
chưa phải giáo trình thực tế.

```powershell
docker compose ps
docker compose logs -f backend
docker compose down
```

Lệnh `down` giữ dữ liệu. `docker compose down -v` xóa toàn bộ dữ liệu khóa học trong volume.

## Cấu hình

Không cần tạo .env để chạy local. Để tùy chỉnh:

```powershell
Copy-Item .env.example .env
docker compose up -d
```

- SMTP mặc định trỏ vào Mailpit; email được giữ ở local, không gửi ra hộp thư thật.
- Với Gmail, đặt MAIL_HOST=smtp.gmail.com, MAIL_PORT=587, MAIL_AUTH=true, MAIL_STARTTLS=true và điền tài khoản, app
  password, MAIL_FROM.
- JWT secret và Google reCAPTCHA mặc định chỉ phục vụ local. Khi dùng Docker, đặt RECAPTCHA_SITE_KEY và
  RECAPTCHA_SECRET_KEY trong .env; Compose truyền site key vào VITE_RECAPTCHA_SITE_KEY của frontend.
- DEMO_DATA=true tạo tài khoản và nội dung mẫu. Dùng DEMO_DATA=false với database mới khi triển khai thực tế, tự
  provision tài khoản ADMIN bằng quy trình quản trị database; không có API đăng ký ADMIN công khai.
- Biến VITE_* được Vite đọc từ thư mục frontend. Nếu chạy bằng image, cần đưa giá trị vào build/runtime tương ứng; .env
  gốc chỉ được Compose dùng cho các biến đã khai báo trong YAML.
- MySQL/Redis chỉ mở trong mạng nội bộ Docker. Frontend, backend và Mailpit gắn cổng host tại 127.0.0.1.
- Mật khẩu MySQL khởi tạo chỉ có hiệu lực với volume mới. Khi đổi DB_PASSWORD với volume đã có, cần đổi mật khẩu user
  trong MySQL tương ứng.

Cấu hình backend mặc định nằm trong `src/main/resources/application.yml`. Docker không copy `application.properties`
hoặc file .env từ máy host vào image.

## Luồng sử dụng

1. Khách xem danh sách, tìm kiếm hoặc lọc danh mục và mở bài xem thử.
2. Đăng ký tài khoản bằng CAPTCHA và OTP email. Trong local, lấy OTP ở Mailpit.
3. Học viên chọn khóa học. Backend lấy giá từ database và lưu snapshot tên/giá vào đơn.
4. Khóa học miễn phí: đơn PAID và quyền học được tạo ngay.
5. Khóa học trả phí: học viên mở Đơn hàng, chọn Thanh toán để chuyển đến PayOS.
6. Webhook được xác minh chữ ký, mã đơn và số tiền. Backend cập nhật PAID, lưu giao dịch và cấp quyền học trong cùng
   transaction.
7. Học viên vào Học tập, đọc bài/xem video và đánh dấu hoàn thành.
8. ADMIN quản lý danh mục, khóa học, chương/bài học, xuất bản/ẩn khóa học, xem tài khoản, đơn hàng, doanh thu và xuất
   Excel.

Ẩn khóa học sẽ gỡ khỏi danh sách công khai nhưng giữ quyền học của người đã mua. Khi xóa chương/bài học, tiến độ liên
quan cũng bị xóa. UI quản trị có bước xác nhận.

## PayOS

Điền PAYOS_CLIENT_ID, PAYOS_API_KEY, PAYOS_CHECKSUM_KEY vào .env rồi khởi động lại backend:

```powershell
docker compose up -d backend
```

Đăng ký webhook HTTPS trỏ đến `/api/payments/webhook` của backend qua domain hoặc tunnel của bạn. Không dùng URL
frontend.

- Không có endpoint giả lập thanh toán công khai.
- Return/cancel URL chỉ đưa học viên về Đơn hàng. Quyền học do webhook hợp lệ quyết định.
- Gọi lại tạo đơn cho cùng khóa học sẽ dùng đơn PENDING hiện có.
- Giá đã lưu trong đơn không đổi khi quản trị viên cập nhật giá khóa học.
- Webhook gửi lặp được xử lý bằng khóa dòng đơn hàng, khóa user và unique constraints.
- Nếu chưa cấu hình PayOS, checkout trả 503 với thông báo rõ ràng; khóa miễn phí vẫn hoạt động.
- Chưa xác minh giao dịch ngân hàng thật vì dự án chưa có bộ khóa PayOS thật.

## Dữ liệu và chuyển đổi từ cầu lông

Project Compose mới có tên `codepath-academy`, database `academy_db`, volume riêng. Không tái sử dụng hoặc tự động
chuyển đổi database cầu lông. Các volume `badminton-management_*` cũ được giữ nguyên.

Flyway quản lý schema bằng `src/main/resources/db/migration`; Hibernate dùng ddl-auto=validate. Không sửa migration đã
áp dụng: tạo V2, V3… cho thay đổi tiếp theo. Không bật baseline tự động trên database cầu lông.

Khi chạy Java trực tiếp, cấu hình database mới qua biến môi trường. File application.properties cũ trên máy có thể ghi
đè application.yml; cần bỏ cấu hình cũ khỏi classpath hoặc chạy với
`--spring.config.location=classpath:/application.yml`.

Mã nguồn giữ package `com.example.java_basic` để hạn chế thay đổi kỹ thuật ngoài nghiệp vụ. Mã nghiệp vụ cầu lông, tài
liệu cũ và project ví dụ đã được loại bỏ. Tài liệu cấu hình hiện tại nằm trong README này và `.env.example`.

## Mô hình dữ liệu và API

Các bảng: users, categories, courses, course_sections, lessons, orders, order_items, payments, enrollments,
lesson_progress.

| API                                                  | Quyền                                                                |
|------------------------------------------------------|----------------------------------------------------------------------|
| GET /api/categories, /api/courses, /api/courses/{id} | Công khai với nội dung đã xuất bản                                   |
| GET /api/lessons/{id}                                | Bài xem thử công khai; bài riêng cần quyền học hoặc ADMIN            |
| POST /api/auth/register/init, /verify, /resend       | Đăng ký OTP; các đường dẫn verify/resend nằm dưới /api/auth/register |
| POST /api/auth/login, /api/auth/refresh              | Xác thực                                                             |
| GET /api/me, /api/me/courses                         | Tài khoản hiện tại                                                   |
| PUT /api/me/progress/{lessonId}                      | Học viên đã đăng ký khóa học                                         |
| POST /api/orders                                     | Tạo đơn, body: courseId                                              |
| GET /api/orders, /api/orders/{id}                    | Đơn thuộc tài khoản hiện tại                                         |
| POST /api/orders/{id}/checkout                       | Chủ đơn hàng                                                         |
| POST /api/payments/webhook                           | Xác minh chữ ký PayOS                                                |
| /api/admin/**                                        | ADMIN                                                                |
| GET /api/admin/orders/export                         | Excel đơn hàng                                                       |

Nội dung bài học và URL video không xuất hiện trong API mục lục. Đăng ký công khai luôn tạo STUDENT. JWT sống 15 phút,
refresh token xoay vòng và lưu trong Redis.

## Kiểm thử

Không cần cài Java để chạy unit test:

```powershell
docker build --target test -t codepath-academy-tests .
docker compose exec -T frontend npm run build
docker compose exec -T frontend npm run lint
```

Kiểm thử tích hợp với Python 3:

```powershell
python scripts/smoke_test.py --local-webhook-key local-placeholder
python scripts/registration_test.py
```

Chỉ dùng tùy chọn local-webhook-key trên môi trường local có checksum key placeholder. Script gửi sự kiện thanh toán mô
phỏng có chữ ký; không gọi API tạo liên kết hoặc phát sinh giao dịch ngân hàng thật. Script tạo khóa học QA rồi ẩn sau
kiểm thử, giữ lịch sử đơn hàng/quyền học để kiểm tra.

Kiểm thử giao diện trên Chrome cài sẵn:

```powershell
npm install --prefix .qa --no-save --package-lock=false playwright
node scripts/ui_test.cjs
```

Có thể chỉ định CHROME_PATH. Screenshot được lưu vào artifacts/qa (git bỏ qua).

## Video Mux Free

Đã tích hợp upload video trực tiếp, xử lý trạng thái và phát video riêng tư bằng token có hạn.
Xem [hướng dẫn cấu hình Mux](docs/mux.md) để tạo khóa, điền `.env` và upload từ trang quản trị.
Không cần webhook HTTPS để trải nghiệm local. Nếu chưa cấu hình Mux, bài học văn bản và video MP4 cũ vẫn hoạt động.

Video Mux và MP4 hỗ trợ xem tiếp: lưu vị trí mỗi 10 giây, khi tạm dừng, tua hoặc chuyển trang.
Học viên đã đăng ký đồng bộ vị trí theo tài khoản/bài học vào database; bài xem thử lưu trên trình duyệt.
Mở lại khóa học sẽ chọn bài xem gần nhất. Nếu có vị trí đã lưu, popup hiển thị mốc thời gian và cho chọn tiếp tục xem hoặc xem lại từ đầu; chọn xong video bắt đầu phát.
Xem từ 95% video trở lên sẽ đánh dấu hoàn thành và đặt vị trí mở lại về đầu.
Đóng tab đột ngột hoặc mất mạng có thể làm bản đồng bộ chậm tối đa khoảng một chu kỳ lưu; trình duyệt vẫn giữ bản local khi storage khả dụng.

## Phạm vi phiên bản đầu

- Mỗi đơn mua một khóa học; schema order_items cho phép mở rộng sau này.
- Nội dung bài học là văn bản thuần; video hỗ trợ Mux riêng tư hoặc URL file phát trực tiếp (ví dụ MP4).
- Mux dùng signed playback, không phải DRM. Với URL MP4 bên ngoài, quyền truy cập video vẫn phụ thuộc nơi lưu trữ.
- Artemis chạy nhúng, không lưu bền queue email qua việc thay container. Email xác nhận được gửi sau khi đơn commit;
  chưa có transactional outbox.
- Chưa có giỏ hàng, mã giảm giá, hoàn tiền, chứng chỉ, bài tập chấm tự động, phân trang danh sách hoặc giảng viên tự
  quản trị.
- Frontend Docker chạy Vite development server cho local; khi triển khai production cần build frontend và phục vụ qua
  web server/hosting phù hợp.

Flyway dùng starter tương ứng Spring Boot 4
theo [tài liệu Spring Boot](https://docs.spring.io/spring-boot/how-to/data-initialization.html).
