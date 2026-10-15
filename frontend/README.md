# CodePath frontend

React 19, React Router 7, Vite 8.

```bash
npm ci
npm run dev
npm run build
npm run lint
```

Mặc định API tại http://localhost:8080. Đặt VITE_API_URL trong frontend/.env.local để thay đổi.
Đặt VITE_RECAPTCHA_SITE_KEY khi dùng CAPTCHA thật, cùng secret tương ứng ở backend.

Trang công khai: /, /courses/:id, /learn/:courseId/:lessonId, /login, /register.
Trang học viên: /my-courses, /orders, /profile.
Trang quản trị: /admin, /admin/courses/new, /admin/courses/:id.

Xem README ở thư mục gốc để chạy toàn bộ qua Docker.
