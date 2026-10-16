# Jenkins dùng chung trên Docker

Thư mục này có thể copy sang repository hoặc thư mục độc lập khác. Jenkins có volume riêng, không phụ thuộc Compose của CodePath. Cần Docker Desktop Linux containers hoặc Docker Engine Linux, Compose >= 2.24.4, Internet để tải image/plugin/dependencies.

## Khởi động trên Windows

Từ thư mục gốc repository:

```powershell
powershell -NoProfile -File infra/jenkins/setup.ps1
docker compose -f infra/jenkins/compose.yaml config --quiet
docker compose -f infra/jenkins/compose.yaml up -d --build --wait
```

Mở <http://localhost:8090>, đăng nhập `admin`. Đọc mật khẩu local khi cần:

```powershell
Get-Content infra/jenkins/secrets/admin-password.txt
```

Script không ghi đè `.env` hoặc mật khẩu đã có. Mật khẩu ngẫu nhiên được mount bằng Docker secret; thư mục `secrets/` và `.env` không được commit hoặc gửi vào Docker build context. Bảo vệ quyền đọc file này trên máy chủ. Đây là file secret local, không phải kho secret được mã hóa.

Linux/macOS: vào thư mục này, copy `.env.example` thành `.env`, chạy `mkdir -p secrets && (umask 077; openssl rand -hex 32 > secrets/admin-password.txt)`. Chỉ tạo secret khi file chưa tồn tại. Đặt `DOCKER_GID` bằng kết quả `stat -c '%g' /var/run/docker.sock`, sau đó `docker compose up -d --build --wait`.

## Job CodePath Academy

JCasC thiết lập tài khoản, quyền truy cập và Job DSL tạo job `codepath-academy`. Job lấy `Jenkinsfile` từ nhánh `main` của repository cấu hình trong `.env`.

**Phải commit và push các file CI lên nhánh đó trước khi chạy job**. Job không đọc thay đổi chưa push trên máy Windows. Bộ cài không tự push mã nguồn.

Vào job → **Build with Parameters**:

- `DEPLOY_STAGING=false`: test Java, lint/build frontend, tạo hai image local có tag `<commit>-<build-number>`, lưu JUnit/HTML và file `images.env`.
- `DEPLOY_STAGING=true`: chạy các bước trên và triển khai staging khi tất cả thành công. `--wait` chờ health check backend/frontend/MySQL/Redis. Đây là staging local với Vite development server, Mailpit và dữ liệu demo; chưa phải cấu hình production.

Backend test thất bại trong Docker build sẽ làm job thất bại và chặn deploy. Trường hợp này báo cáo JUnit chưa được xuất ra image; đọc chi tiết test trong build log. Khi test thành công, Jenkins xuất báo cáo để xem trong giao diện.

| Thành phần | Địa chỉ |
|---|---|
| Jenkins | http://localhost:8090 |
| Frontend staging | http://localhost:15173 |
| API staging | http://localhost:18080/api/categories |
| Mailpit staging | http://localhost:18025 |

Project staging là `codepath-academy-staging`, có database/Redis volumes riêng; pipeline dùng `ci/staging.env`, không dùng `.env` chứa khóa dịch vụ trên máy. Image nằm trong Docker Engine hiện tại; bộ này không tự publish registry hoặc deploy máy chủ từ xa.

Jenkinsfile dùng Docker CLI và `docker cp`, không mount đường dẫn workspace của container vào container con, nên dùng được với Docker Desktop Windows.

## Webhook và Git riêng tư

Sau lần chạy đầu tiên, Jenkinsfile bật kiểm tra Git mỗi khoảng 5 phút (`pollSCM`), chỉ build khi có thay đổi. Cách này hoạt động ở local mà không cần URL public. Build tự động dùng mặc định `DEPLOY_STAGING=false`; dùng **Build with Parameters** để bật deploy. Nếu muốn tự deploy staging sau mỗi thay đổi, sửa giá trị mặc định thành `true` trong cả Jenkinsfile và jobs.groovy.

Jenkinsfile cũng có trigger GitHub push để nhận thay đổi nhanh hơn. Thêm webhook GitHub: **Repository Settings → Webhooks → Add webhook**, URL `https://<jenkins-domain>/github-webhook/`, content type `application/json`, sự kiện push. Đặt `JENKINS_URL` đúng URL public và cấu hình reverse proxy HTTPS trước khi nhận webhook Internet. `localhost:8090` chỉ dùng được từ máy local, GitHub không truy cập được.

Repository private: thêm **Username with password** (username + token đọc repo) trong **Manage Jenkins → Credentials**, đặt ID vào `SEED_CREDENTIALS_ID`, rồi recreate Jenkins. Với SSH cần thêm SSH private key và quản lý known hosts phù hợp. Không đưa token vào URL Git hoặc Jenkinsfile.

## Dùng cho các dự án khác

Một Jenkins có thể quản lý nhiều dự án:

1. Giữ nguyên Compose và volume Jenkins.
2. Trong giao diện chọn **New Item → Pipeline → Pipeline script from SCM → Git**.
3. Nhập URL, branch, credential nếu cần và đường dẫn `Jenkinsfile` của dự án mới.
4. Mỗi dự án dùng tên image, Compose project, cổng và credentials riêng.

Muốn đổi job khởi tạo: sửa `SEED_JOB_NAME`, `SEED_REPO_URL`, `SEED_REPO_BRANCH`, `SEED_SCRIPT_PATH` trong `infra/jenkins/.env`; chạy lại `docker compose -f infra/jenkins/compose.yaml up -d --force-recreate`. Job DSL cập nhật job được chỉ định khi Jenkins khởi động; các job thêm bằng UI vẫn được giữ. Đổi tên seed job không tự xóa job cũ.

Muốn chạy một Jenkins độc lập khác: copy cả thư mục, tạo secret riêng, đổi `JENKINS_COMPOSE_PROJECT`, cổng và `JENKINS_URL`. Compose project khác tạo volume Jenkins khác. Không dùng chung Docker Compose project name cho hai Jenkins độc lập.

## Vận hành

```powershell
docker compose -f infra/jenkins/compose.yaml ps
docker compose -f infra/jenkins/compose.yaml logs --tail 100 jenkins
docker compose -f infra/jenkins/compose.yaml down
```

`down` giữ dữ liệu Jenkins. Sao lưu volume `jenkins_home` khi Jenkins đã dừng, đồng thời giữ secret và `.env` an toàn. Không dùng `down -v` khi cần giữ jobs/builds/credentials.

Pipeline ngăn hai lần deploy của cùng job chạy đồng thời. Không tạo job khác deploy vào cùng staging project. Image ứng dụng và build cache được giữ để tái sử dụng; kiểm tra dung lượng và chỉ dọn các image cũ đã xác nhận không còn cần. Không tự chạy prune trên Docker host dùng chung.

Để triển khai lại image local còn giữ: tải artifact `images.env` của build trước, lấy `IMAGE_TAG`, đặt biến đó trong shell rồi chạy Compose staging với `--no-build --wait`. Ví dụ PowerShell tại repository:

```powershell
$env:IMAGE_TAG = '<commit>-<build-number>'
docker compose --env-file ci/staging.env -f docker-compose.yml -f ci/compose.staging.yaml up -d --no-build --wait --wait-timeout 240
```

Thao tác này chỉ đổi phiên bản ứng dụng; không hoàn tác Flyway migration hay dữ liệu. Cần đánh giá tương thích schema trước khi quay lại phiên bản cũ.

## Phạm vi quyền và nâng cấp

Bộ local này chạy build trên controller (hai executors) và mount Docker socket. Người sửa Jenkinsfile hoặc chạy code trong job có thể điều khiển Docker host; chỉ cấp quyền cho người và repository tin cậy, không chạy pull request không tin cậy. Không mở signup hoặc truy cập anonymous. Trên máy chủ dùng chung/production, chuyển build sang agent riêng, controller về 0 executors, phân quyền theo nhóm và tách credentials/máy deploy.

Image `lts-jdk21`, Docker CLI `29-cli` và plugin dùng phiên bản từ upstream tại lần build. Sau khi kiểm chứng ở môi trường của bạn, có thể pin digest image và phiên bản plugin để tái lập chính xác. Khi nâng cấp, sao lưu trước rồi build/recreate lại; không tắt CSRF hoặc kiểm tra chữ ký plugin để khắc phục lỗi.

Tham khảo: [Jenkins Docker](https://www.jenkins.io/doc/book/installing/docker/), [Configuration as Code](https://plugins.jenkins.io/configuration-as-code/), [Job DSL](https://plugins.jenkins.io/job-dsl/).
