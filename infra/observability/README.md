# Giám sát CodePath bằng OpenTelemetry

Bộ Docker Compose độc lập: OpenTelemetry Collector, Prometheus, Loki, Tempo, Grafana và Mailpit nhận cảnh báo local. Agent Java 2.32.0 nằm trong image backend, được kiểm tra SHA-256 khi build; chỉ bật khi dùng `compose.observability.yml`.

## Khởi động trên Windows

Tại thư mục gốc repository, cần Docker Compose và Node.js (để chạy smoke test):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/observability/setup.ps1
```

Script tạo `infra/observability/.env` với mật khẩu Grafana ngẫu nhiên nếu chưa có, khởi động bộ giám sát, build/backend và chạy smoke test. File `.env` được Git bỏ qua; không gửi mật khẩu vào chat hay commit. `-SkipBackend` chỉ khởi động bộ giám sát.

- Grafana: http://localhost:3000 ; username mặc định `admin`, password trong `infra/observability/.env`.
- Dashboard: http://localhost:3000/d/codepath-overview ; năm dashboard trong folder **CodePath**, gồm AI Search & RAG khi dùng tính năng AI local.
- Email cảnh báo local: http://localhost:18026 . Mailpit chỉ giữ thư kiểm thử, không chuyển ra email thật.
- Ứng dụng: http://localhost:5173 . Backend vẫn dùng cổng 8080.

Các lần chạy sau, luôn giữ override để bật agent và network:

```powershell
docker compose --env-file infra/observability/.env -f infra/observability/compose.yaml up -d
docker compose -f docker-compose.yml -f compose.observability.yml up -d --no-build --wait backend frontend
```

Đổi port/network trong `.env` của bộ giám sát nếu bị trùng. Nếu đổi network, đặt cùng `OBSERVABILITY_NETWORK` ở môi trường chạy Compose ứng dụng. Mật khẩu bootstrap Grafana chỉ có tác dụng lần đầu tạo database; đổi mật khẩu tài khoản đã tồn tại qua Grafana Administration hoặc CLI reset-admin-password.

## Luồng dữ liệu

```text
Spring Boot + Java Agent -> OTLP HTTP -> Collector -> Tempo (traces)
                                                 -> Loki (application logs)
Spring Boot Actuator :9091 -> Prometheus -> Grafana dashboards / alert rules
Collector :8888 -----------> Prometheus
Grafana Alerting -> SMTP -> Mailpit local hoặc SMTP thực đã cấu hình
```

Metrics dùng Micrometer/Actuator, agent tắt metrics exporter để tránh đếm trùng. Spring tracing tắt để agent chịu trách nhiệm tracing. Logback appender được agent instrument tự động, kèm trace/span ID; không thu thêm stdout cùng lúc.

Management port 9091 không publish ra host và chỉ cho phép `/actuator/health`, `/actuator/prometheus`. Cổng ứng dụng không cho truy cập các endpoint này. Collector, Loki, Tempo và Prometheus chỉ ở Docker network nội bộ. Network này dành cho dịch vụ được tin cậy; bất kỳ container được nối vào đó có thể truy cập telemetry nội bộ. Hai UI được bind vào loopback.

Override chuyển healthcheck Docker sang management health để probe không tạo lưu lượng giả trên API danh mục. Dashboard HTTP loại route `/actuator.*` khỏi request rate/p95.

## Dashboard và metrics

| Dashboard | Nội dung |
|---|---|
| Application overview | Backend up, request/s, 5xx, p95 theo route, logs |
| JVM and database | Heap, GC, CPU process, threads, Hikari active/pending |
| Authentication and email | Kết quả HTTP đăng nhập password/Passkey/OAuth/xác minh đăng ký, email và thời gian xử lý |
| Telemetry infrastructure | Trạng thái scrape, spans nhận được, lỗi xuất logs/traces |
| AI Search & RAG | Độ trễ, kết quả truy vấn, fallback, backlog và lỗi lập chỉ mục; [hướng dẫn AI](../ai/README.md) |

Các counter nghiệp vụ:

- `codepath_auth_requests_total{operation,outcome}`: kết quả endpoint xác thực; đây là kết quả HTTP, không phải số người dùng duy nhất.
- `codepath_email_deliveries_total{outcome}`: số lần thử gửi email, gồm retry.
- `codepath_email_processing_seconds_*{outcome}`: thời gian xử lý gửi email.

Nhãn chỉ dùng tập hữu hạn, không có email, user ID hay token. Metrics có `application`, `environment`, `version`; nhãn scrape `deployment` dùng lọc dashboard. Chỉ số email bắt đầu xuất hiện sau lần gửi đầu tiên; panel chưa có dữ liệu trước đó là bình thường. HTTP p95 cần có request và đủ mẫu scrape; đồ thị lỗi hiển thị 0 nếu có request nhưng chưa có lỗi.

Dùng **Explore > Tempo** tìm `service.name=codepath-academy-backend` để xem HTTP, JDBC, Redis, HTTP client và JMS được agent hỗ trợ. Dùng **Explore > Loki** tìm `{service_name="codepath-academy-backend"}`; trường TraceID liên kết sang Tempo. Tempo cấu hình liên kết ngược sang logs. Bản này chưa có telemetry trình duyệt, exporter máy chủ/MySQL/Redis, payment business metrics, cảnh báo backlog/DLQ email hay dashboard Jenkins. Artemis vẫn là embedded queue theo kiến trúc hiện tại.

## Cảnh báo

Grafana provision bảy rule, đánh giá mỗi 30 giây:

| Rule | Ngưỡng ban đầu |
|---|---|
| Backend unavailable | Scrape backend không thành công liên tục 2 phút |
| HTTP 5xx | Trên 5%, tối thiểu 100 request/5 phút, kéo dài 5 phút |
| HTTP p95 | Trên 2 giây, tối thiểu 100 request/5 phút, kéo dài 10 phút; loại API trợ lý/tìm kiếm AI có ngân sách latency riêng |
| Database pool | Active/max trên 90% trong 5 phút |
| Email failures | Có lần gửi thất bại trong cửa sổ 5 phút, kéo dài 1 phút |
| Collector unavailable | Scrape Collector không thành công liên tục 2 phút |
| Collector export failures | Có lỗi export trong cửa sổ 5 phút, kéo dài 5 phút |

Backend/Collector mất dữ liệu được xem là sự cố; các rule lưu lượng thấp/metric nghiệp vụ chưa xuất hiện không phát cảnh báo NoData. Lỗi truy vấn datasource được xử lý như cảnh báo. Ngưỡng cần điều chỉnh sau khi có tải thực. Group wait 30s, group interval 5m, repeat 4h; có gửi thông báo phục hồi. Giai đoạn khởi động có thể tạo cảnh báo tạm nếu backend chưa sẵn sàng.

Để gửi email thực, sửa `SMTP_HOST`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_FROM`, `SMTP_STARTTLS_POLICY` và `ALERT_EMAIL` trong `.env` rồi `up -d` lại Grafana. Ví dụ máy chủ STARTTLS thường dùng `MandatoryStartTLS`; xác nhận với nhà cung cấp SMTP. Không gửi ra người nhận thật khi chưa cấu hình/chỉ định người trực. Cấu hình Slack/Telegram có thể bổ sung contact point sau.

Khi cảnh báo: mở dashboard tương ứng, chọn đúng environment và thời gian; kiểm tra request rate/error/p95, Hikari và JVM; tìm trace chậm/lỗi trong Tempo rồi logs cùng trace ID. Với lỗi telemetry, kiểm tra `docker compose --env-file infra/observability/.env -f infra/observability/compose.yaml logs --tail 100 otel-collector` và trạng thái Loki/Tempo trước.

## Kiểm thử

```powershell
node scripts/observability_test.cjs
node scripts/observability_alert_test.cjs
node scripts/observability_resilience_test.cjs
```

- Smoke test kiểm tra targets, histogram HTTP, auth counter, năm dashboard và toàn bộ PromQL, chín rule, logs/trace, redaction log giả lập và loại query string nhạy cảm khỏi trace. Nó gửi một lần login không hợp lệ và telemetry giả lập. Hai rule AI theo dõi backlog quá 10 phút và job hết lượt thử, chỉ hoạt động khi worker được bật; xem [runbook AI](../ai/README.md).
- Alert test tạo rule tạm `vector(1)`, chờ email vào Mailpit rồi xóa rule trong `finally`. Chỉ chạy với SMTP local mặc định, có thể mất khoảng 2 phút. Không sửa rule provision chính.
- Resilience test dừng Collector vài giây, kiểm tra API vẫn trả 200, khởi động lại Collector trong `finally`. Chạy trên local khi chủ động kiểm thử.

## Jenkins và staging

`Jenkinsfile` build image với `APP_VERSION=$IMAGE_TAG` và thêm tham số `ENABLE_OBSERVABILITY`. Bật cùng `DEPLOY_STAGING` khi shared stack đã chạy trên cùng Docker host. Backend staging dùng alias `codepath-backend-staging`, environment `staging`, tỷ lệ lấy trace 20%; version theo commit/build. Các tham số xuất hiện sau khi Jenkins đọc Jenkinsfile mới từ Git.

Bỏ comment target staging trong `prometheus.yaml` rồi reload bằng:

```powershell
docker compose --env-file infra/observability/.env -f infra/observability/compose.yaml restart prometheus
```

Nếu triển khai staging thủ công, giữ nguyên các biến:

```powershell
$env:DEPLOYMENT_ENVIRONMENT = 'staging'
$env:APP_VERSION = $env:IMAGE_TAG
$env:OTEL_SAMPLE_RATIO = '0.2'
docker compose --env-file ci/staging.env -f docker-compose.yml -f ci/compose.staging.yaml -f compose.observability.yml up -d --no-build --wait
```

`IMAGE_TAG` phải là image đã kiểm thử. Đặt các biến này trong shell riêng hoặc xóa sau khi triển khai để tránh gắn nhãn sai cho local. Pipeline có smoke check management health sau deploy; bản này chưa chạy kiểm thử giám sát hay deploy staging tự động từ workspace chưa commit.

## Dùng cho dự án khác

Copy `infra/observability`, tạo `.env` riêng và chạy stack. Nếu dùng chung stack hiện có thì chỉ nối backend mới vào network. Gắn agent với Java 17, đặt `OTEL_SERVICE_NAME` khác, `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318`, protocol `http/protobuf`, resource attributes environment/version. Thêm target Prometheus của ứng dụng và dashboard/rule tương ứng; không dùng alias hoặc tên service trùng. Backend khác không dùng Micrometer có thể gửi metrics OTLP nhưng phải thêm pipeline metrics vào Collector; cấu hình hiện tại chỉ nhận logs/traces.

## Tài nguyên, lưu trữ và giới hạn

- Cố định phiên bản từng image trong Compose. Nâng từng thành phần và chạy smoke test trước khi dùng staging.
- Volume độc lập lưu Prometheus, Loki, Tempo và Grafana; `down` giữ dữ liệu. Không dùng `down -v` trừ khi chủ động xóa toàn bộ lịch sử.
- Prometheus: giữ tối đa 7 ngày hoặc 2 GB TSDB; Loki: retention 72 giờ; Tempo: 24 giờ. Retention Loki/Tempo chạy bất đồng bộ và không phải quota ổ đĩa cứng; cần giám sát dung lượng Docker host.
- Giới hạn RAM cho từng dịch vụ trong Compose; trên máy phát triển lưu lượng thấp, lần kiểm tra đầu bộ giám sát dùng khoảng 0.7–0.9 GiB RAM, chưa gồm backend. Nhu cầu tăng theo lưu lượng và nhãn.
- Sampling local 100%, staging đề xuất 20%; metrics không sampling. Logs vẫn được xuất đầy đủ. Không bảo đảm giữ mọi trace lỗi khi dùng head sampling.
- Batch/queue có giới hạn và nằm trong bộ nhớ: sự cố dài hoặc restart có thể làm mất telemetry. Ứng dụng không chờ đồng bộ Collector để xử lý request.
- Collector loại URL/query, SQL text, một số trường định danh và exception nhạy cảm khỏi traces; che các mẫu credential phổ biến trong log body. Đây là lớp phòng vệ bổ sung, không thay việc không ghi secret/PII tại nguồn. Không bật capture payload/header tùy ý. Stack này không thay thế audit log nghiệp vụ bền vững.
- Mô hình single-node, local filesystem, không HA. Khi dùng production: xác thực/TLS cho truy cập từ xa, lưu trữ phù hợp, sao lưu Grafana/config, capacity planning và kiểm thử khôi phục. Nếu Docker host/Grafana cùng dừng thì bộ cảnh báo này cũng dừng; cần giám sát bên ngoài cho sự cố toàn host.

## Tham khảo chính thức

- https://opentelemetry.io/docs/zero-code/java/agent/getting-started/
- https://opentelemetry.io/docs/zero-code/java/agent/supported-libraries/
- https://grafana.com/docs/loki/latest/send-data/otel/
- https://grafana.com/docs/grafana/latest/alerting/set-up/provision-alerting-resources/file-provisioning/
