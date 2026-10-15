# CodePath Academy Web

Giao diện React của CodePath Academy. Package: `codepath-academy-web`.

## Chạy độc lập

Cần Node.js 22 tương thích Vite và backend tại `http://localhost:8080`:

```powershell
npm ci
npm run dev
```

Cấu hình `frontend/.env.local`:

```dotenv
VITE_API_URL=http://localhost:8080
VITE_RECAPTCHA_SITE_KEY=your-site-key
```

Các biến `VITE_*` công khai trong browser. Mux token/private key, PayOS secret và SMTP password chỉ nằm ở backend.

## Trang và quyền

| Route | Mục đích |
|---|---|
| `/` | Tìm kiếm và lọc khóa học |
| `/courses/:id` | Chi tiết và đăng ký khóa học |
| `/learn/:courseId/:lessonId?` | Đọc bài, xem video, lưu tiến độ |
| `/login`, `/register` | Đăng nhập và đăng ký OTP |
| `/my-courses`, `/orders`, `/profile` | Khu vực tài khoản |
| `/admin`, `/admin/courses/new`, `/admin/courses/:id` | Quản trị nội dung |

`Guard` hỗ trợ điều hướng UI; backend thực thi quyền truy cập thật.

## Tổ chức mã

- `src/api.js`: Axios, Bearer token, refresh một lần khi nhận 401.
- `src/pages/Auth.jsx`: đăng ký OTP và đăng nhập.
- `src/pages/Courses.jsx`: catalog, chi tiết, thư viện, màn hình học.
- `src/pages/Account.jsx`: đơn hàng và hồ sơ.
- `src/pages/Admin.jsx`, `ContentEditor.jsx`: quản trị khóa/chương/bài.
- `src/components/MuxVideo.jsx`: upload Mux, Mux Player và MP4.
- `src/components/useVideoProgress.js`: checkpoint, lưu local và đồng bộ backend.
- `src/components/ResumeVideoDialog.jsx`: popup thời gian đã xem, tiếp tục hoặc xem từ đầu.

## Kiểm tra và tài liệu

```powershell
npm run lint
npm run build
```

[Chạy toàn hệ thống](../README.md) · [Danh mục tài liệu](../docs/README.md) · [Cấu hình](../docs/configuration.md) · [Upload video](../docs/12-video-upload.md) · [Phát video](../docs/13-video-playback.md) · [Popup xem tiếp](../docs/14-resume-confirmation.md).
