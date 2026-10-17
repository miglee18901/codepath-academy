# Kafka: mua khóa học và hoàn thành học tập

Một codebase/image Java có hai vai trò: API ghi dữ liệu nghiệp vụ và outbox; worker xuất bản Kafka, cập nhật thông báo/báo cáo và gửi email. MySQL vẫn là nguồn sự thật; thanh toán, enrollment và outbox commit cùng nhau. Tắt Kafka không chặn cấp quyền học.

## Chạy local

Từ thư mục gốc, Docker Desktop Linux containers:

```powershell
./infra/kafka/setup.ps1
```

Hoặc chạy riêng từng bước:

```powershell
docker compose -f infra/kafka/compose.yaml up -d --wait
docker compose -f docker-compose.yml -f compose.kafka.yml up -d --build --wait backend frontend worker
```

Frontend: http://localhost:5173, API: http://localhost:8080, Mailpit: http://localhost:8025. Kafka và management worker không có cổng host. Mở chuông thông báo sau đăng nhập; quản trị có tab **Báo cáo** và **Sự kiện**.

`./infra/kafka/setup.ps1 -WithAi -WithObservability` kết hợp các stack hiện có; chuẩn bị model AI và observability bằng setup tương ứng trước lần dùng đầu tiên.

Nếu dùng RAG hiện có, khởi động `infra/ai/compose.yaml` trước và thêm `-f compose.ai.yml` vào lệnh ứng dụng. Không cần đổi model hoặc chỉ mục AI.

Nếu dùng observability, khởi động `infra/observability/compose.yaml` trước rồi chạy:

```powershell
docker compose -f docker-compose.yml -f compose.kafka.yml -f compose.observability.yml -f compose.kafka.observability.yml up -d --build --wait backend frontend worker
```

Thêm `-f compose.ai.yml` nếu đồng thời dùng AI. Reload/restart Prometheus và Grafana để nhận target worker, dashboard **CodePath · Kafka & Business Events** và 7 alert rules mới. Worker down chỉ cảnh báo khi API bật business events.

## Dùng broker cho dự án khác

`infra/kafka/compose.yaml` độc lập với ứng dụng, chứa Kafka Apache 4.1.1 KRaft, volume bền vững, healthcheck và network `codepath-kafka`. Có thể đổi network và cluster ID qua `.env` trong thư mục này. Nối ứng dụng khác vào network, bootstrap `kafka:9092`, dùng topic/group prefix riêng. Các máy local/staging của CodePath phải dùng prefix khác nhau (`KAFKA_TOPIC_PREFIX=codepath.staging`). Không dùng chung consumer group hoặc DB giữa môi trường.

Single broker, replication factor 1, PLAINTEXT chỉ cho mạng Docker local. Production cần broker/controller quorum, replication factor 3, min ISR 2, TLS/SASL/ACL, backup và cấu hình tài nguyên riêng; bộ Compose này chưa triển khai production HA.

## Hợp đồng sự kiện

JSON schema v1: `eventId` UUID, `eventType`, `schemaVersion=1`, `occurredAt` ISO UTC, `aggregateId`, `correlationId`, `userId`, `payload`, `traceContext` (chỉ W3C traceparent/tracestate). Không có OTP, token, email, mật khẩu hoặc nội dung khóa học. Dùng thay đổi bổ sung cho v1; thay đổi không tương thích cần version mới và chiến lược chuyển consumer.

| Type | Topic suffix | Aggregate / payload |
| --- | --- | --- |
| OrderPaid | commerce.v1 | orderId:courseId / orderId, courseId, amount chuỗi thập phân, currency VND |
| EnrollmentGranted | commerce.v1 | userId:courseId / orderId, courseId |
| CourseCompleted | learning.v1 | userId:courseId / courseId |

Prefix mặc định `codepath.local`. Partition key sự kiện nguồn là userId. API checkout hiện có một khóa học/đơn; fact doanh thu lưu theo từng item để cộng tiền theo khóa học và đếm đơn distinct. Khóa học miễn phí có doanh thu 0. Topic chính/retry 3 partition, retention 7 ngày; DLT 30 ngày. Không phụ thuộc thứ tự giữa topic và không dùng distributed transaction MySQL/Kafka.

Outbox relay dùng lease 60 giây, publisher idempotent/acks=all; nhận ACK rồi mới ghi published. Crash sau ACK có thể publish lại; inbox `(consumer,eventId)` và unique business fact ngăn tác dụng phụ MySQL bị nhân đôi. Consumer commit DB trước offset. Các group notifications/reports độc lập.

## Hoàn thành và dữ liệu lịch sử

API flush tiến độ và kiểm tra tất cả bài học trong giao dịch dưới khóa học viên. Có ít nhất một bài học, tất cả completed và chưa có mốc `(userId,courseId)` mới phát CourseCompleted. Việc đạt 95% video, đánh dấu thủ công hoặc cập nhật đồng thời dùng cùng cơ chế. Bỏ đánh dấu, thêm bài hoặc học lại không xóa mốc đầu tiên và không gửi thêm chúc mừng.

Worker nhập lịch sử theo batch 50 với checkpoint `business_backfill`: đơn đã thanh toán, enrollment và học viên đã hoàn thành hiện tại. Unique keys dùng chung consumer trực tiếp nên có thể chạy lại an toàn; không phát sự kiện/thông báo/email lịch sử. Ngày hoàn thành đầu tiên trước triển khai không có trong dữ liệu cũ: các mốc này gắn `historical`, thời điểm là lúc nhập, hiển thị riêng với hoàn thành mới. Thời gian DATETIME cũ được hiểu UTC theo deployment Docker hiện tại; nếu nhập DB từ timezone khác cần chuẩn hóa trước.

Ngày báo cáo là Asia/Ho_Chi_Minh, khoảng ngày inclusive tối đa 366 ngày. Báo cáo nhất quán sau khi worker xử lý, không dùng làm nguồn xác nhận thanh toán. Dữ kiện không bị mất khi khóa học thay tên hoặc xóa bài. API thống kê cũ giữ nguyên.

## Khôi phục và vận hành

- Broker down: API tiếp tục commit; outbox giữ việc và thử lại 15 giây. Kiểm tra `docker compose -f infra/kafka/compose.yaml ps` và log broker; start lại, theo dõi pending/lag giảm.
- Worker down: `docker compose -f docker-compose.yml -f compose.kafka.yml up -d worker`. Lease hết hạn thì việc được nhận lại. Backend chạy Flyway trước worker; worker không seed, không migrate, không chạy OTP/AI jobs. Chỉ chạy worker với biến APP_ROLE=worker như Compose đã cấu hình.
- Consumer lỗi tạm: retry 1/5/15 giây rồi DLT riêng consumer. Schema không hợp lệ chuyển DLT ngay. DLT ghi lỗi vào MySQL rồi commit offset; nếu ghi DB/DLT thất bại thì không bỏ bản ghi. Sửa nguyên nhân trước khi chọn **Thử lại** trong tab Sự kiện; API trả 202, giữ eventId, gửi vào retry topic đúng consumer. Không chỉnh payload bằng UI. Audit lưu admin và thời điểm.
- SMTP lỗi: email job retry tối đa 5 lần, backoff tối đa 300 giây; exhausted chuyển FAILED. Sau khi sửa SMTP, chọn **Gửi lại**. Địa chỉ email được đọc từ tài khoản lúc gửi. SMTP không hỗ trợ idempotency: crash sau send trước lưu SENT vẫn có thể gửi trùng; không cam kết exactly-once email.
- Replay qua broker ngoài retention không tự khôi phục dữ liệu đã mất; giữ DB outbox, inbox và facts, không reset offsets hoặc xóa volume khi khắc phục lỗi thông thường. Không có job tự xóa lịch sử trong v1; cần hoạch định archive khi dữ liệu tăng.
- Rollback ứng dụng: dừng worker trước, giữ migration và dữ liệu. Không bật lại email mua hàng cũ cho những giao dịch đang nằm trong outbox. `EVENTS_ENABLED=false` dùng cho cài đặt không bật Kafka, không phải thao tác đổi kênh gửi tùy tiện khi còn backlog.

OTP tiếp tục chạy Artemis nhúng. `EVENTS_ENABLED=true` bỏ listener email mua hàng cũ, thay bằng durable jobs; không gửi hai kênh. RAG giữ nguyên outbox/chỉ mục riêng. Không triển khai hoàn tiền hoặc chứng chỉ trong phiên bản này.

## Kiểm thử

```powershell
$env:JAVA_HOME='C:\Users\admin\.jdks\ms-17.0.20'
./gradlew.bat test
cd frontend
npm run lint
npm run build
cd ..
node scripts/kafka_test.cjs
node scripts/kafka_ui_test.cjs
node scripts/kafka_observability_test.cjs
./scripts/kafka_fresh_test.ps1
```

Integration script chỉ chạy ở môi trường local demo: tạo khóa học QA rồi ẩn ở finally, tạm dừng/khôi phục worker, Kafka và Mailpit. Kiểm tra mua miễn phí, quyền học ngay, replay trùng, hoàn thành đồng thời, broker down, backfill chạy lại, DLT/retry đúng consumer, audit và SMTP recovery. Không dùng script trên production. Kết quả lưu tại `artifacts/qa/kafka-test.json` (ignored).
