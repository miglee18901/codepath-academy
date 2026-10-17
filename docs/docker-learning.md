# Chạy toàn bộ công nghệ bằng một Docker Compose

`compose.learning.yaml` là file độc lập tại thư mục gốc: ứng dụng, Kafka, AI và giám sát chạy chung một project/network. Jenkins bật thêm bằng profile `ci`. Không ghép file này với các override `compose.*.yml` cũ.

Đây là môi trường học/local. Kubernetes là lựa chọn điều phối thay thế Docker Compose; không cần cài Kubernetes bên trong stack này.

## Khởi động

Cần Docker Desktop ở chế độ Linux containers và PowerShell. Stack đầy đủ tương đối nặng, nên dành khoảng 12 GB RAM cho Docker; suy luận AI trên CPU có thể chậm. Lần đầu cần Internet để tải image và hai mô hình AI.

Nếu đang chạy các stack cũ, dừng chúng trước bằng các lệnh `docker compose ... down` tương ứng, không thêm `-v`. Hai cách chạy dùng lại dữ liệu và cổng nên không chạy đồng thời. Không cần dừng các project khác.

```powershell
# Tự tạo cấu hình bí mật, chuẩn bị volumes, tải/kiểm tra model và build ứng dụng
./scripts/learning.ps1 up

# Bao gồm Jenkins
./scripts/learning.ps1 up -WithJenkins

# Khi image đã được build, khởi động nhanh hơn
./scripts/learning.ps1 up -WithJenkins -NoBuild
```

Script giữ nguyên `.env` hiện tại, dùng thêm `.env.learning` cho hai secret của Qdrant/Grafana. Khi chuyển từ stack cũ, script lấy lại secret từ file cấu hình cũ nếu có. Không ghi secret ra terminal. Nếu Grafana volume đã tồn tại, mật khẩu đăng nhập vẫn là mật khẩu trong database Grafana; đổi biến môi trường không tự đổi mật khẩu tài khoản đã có.

Có thể tách bước chuẩn bị khỏi khởi động:

```powershell
./scripts/learning.ps1 init
docker compose --env-file .env.learning -f compose.learning.yaml --profile ci config --quiet
docker compose --env-file .env.learning -f compose.learning.yaml --profile ci up -d --build --wait
./scripts/learning.ps1 models
```

Lệnh Docker trực tiếp trên không đọc cấu hình OAuth/payOS/Mux trong `.env`. Khi cần, thêm `--env-file .env` trước `--env-file .env.learning`. Ưu tiên script `up`: script nạp cả hai file và chuẩn bị model trước khi bật backend.

## Thành phần

| Dịch vụ | Vai trò | Truy cập từ máy |
|---|---|---|
| frontend | React/Vite | http://localhost:5173 |
| backend | Spring Boot API, OTP qua Artemis nhúng, RAG | http://localhost:8080 |
| mysql, redis | Dữ liệu nghiệp vụ và cache | Chỉ trong Docker |
| mailpit | Hộp thư OTP/thông báo | http://localhost:8025 |
| kafka, worker | Sự kiện mua hàng/học tập, thông báo, email, báo cáo | Chỉ trong Docker |
| ollama, qdrant | Model AI và vector search | Chỉ trong Docker |
| otel-collector, prometheus, loki, tempo | Telemetry: metrics/logs/traces | Qua Grafana |
| grafana | Dashboard và cảnh báo | http://localhost:3000 |
| alert-mailpit | Hộp thư cảnh báo | http://localhost:18026 |
| jenkins (profile ci) | CI/CD | http://localhost:8090 |

Grafana: tài khoản `admin` mặc định, mật khẩu trong `.env.learning` (`LEARNING_GRAFANA_PASSWORD`). Jenkins: tài khoản `admin` mặc định, mật khẩu tại `infra/jenkins/secrets/admin-password.txt`. Các file bí mật đều bị Git bỏ qua. Jenkins dùng Docker socket của máy để build và triển khai, chỉ bật cho môi trường học tin cậy.

## Quản lý hằng ngày

```powershell
./scripts/learning.ps1 status
./scripts/learning.ps1 logs -Service backend
./scripts/learning.ps1 logs -Service worker
./scripts/learning.ps1 logs -Service kafka
./scripts/learning.ps1 down
```

`down` bao gồm profile Jenkins và giữ volumes. Kafka, AI, giám sát và Jenkins dùng volumes external cùng tên stack cũ để giữ dữ liệu/model. MySQL và Redis tiếp tục dùng volumes của project `codepath-academy`. Không xóa volumes nếu còn cần dữ liệu.

`up` không bật Jenkins khi chưa có `-WithJenkins`, nhưng cũng không tự dừng một Jenkins đã chạy trước đó. Muốn dừng riêng:

```powershell
docker compose --env-file .env.learning -f compose.learning.yaml --profile ci stop jenkins
```

Các OAuth provider, payOS và Mux vẫn cần credentials riêng trong `.env`. Mailpit chỉ nhận email local. Jenkins vẫn chạy pipeline đã khai báo trong repository; bước `DEPLOY_STAGING` dùng stack staging riêng, không triển khai đè môi trường học này.

## Kiểm tra sau khởi động

```powershell
./scripts/learning.ps1 status
$env:CODEPATH_LEARNING='1'
node scripts/observability_test.cjs
```

Mở website, thử tìm kiếm thông minh và chatbot. Lần đầu backend cần index nội dung; mô hình có thể cần thời gian tải vào RAM. Trong Grafana kiểm tra dashboard API, AI và sự kiện Kafka. Xem thêm các hướng dẫn trong `infra/ai`, `infra/kafka`, `infra/observability` và `infra/jenkins` để hiểu cấu hình từng nhóm.
