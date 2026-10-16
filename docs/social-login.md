# Đăng nhập Google và GitHub

Ứng dụng đã có OAuth Authorization Code cho Google (OpenID Connect) và GitHub.
Mỗi nhà cung cấp chỉ bật khi cấu hình đủ Client ID và Client Secret. Nếu chưa có,
ứng dụng vẫn chạy bình thường; nút tương ứng trên trang đăng nhập bị vô hiệu hóa.
Client Secret chỉ đặt ở backend, không dùng biến `VITE_*`, không commit `.env`.

## Tạo Google OAuth Client

1. Vào [Google Cloud Console](https://console.cloud.google.com/), chọn hoặc tạo project.
2. Mở **Google Auth platform**. Hoàn tất **Branding** và **Audience** cho màn hình đồng ý.
   Với ứng dụng thử nghiệm External, thêm tài khoản thử nghiệm ở mục Test users.
3. Vào **Clients → Create client → Web application**.
4. Thêm Authorized redirect URI chính xác:

   ```text
   http://localhost:5173/api/auth/oauth2/callback/google
   ```

5. Sao chép Client ID và Client Secret vào `.env` theo mẫu bên dưới. Đây là OAuth
   client dạng Web application; không dùng Android/iOS client, API key hoặc reCAPTCHA key.

Luồng này chuyển hướng qua backend, không dùng Google One Tap. Xem hướng dẫn
[tạo OAuth Client của Google](https://developers.google.com/workspace/guides/create-credentials)
và [OpenID Connect](https://developers.google.com/identity/openid-connect/openid-connect).

## Tạo GitHub OAuth App

1. Vào [GitHub Developer settings → OAuth Apps](https://github.com/settings/developers).
2. Chọn **New OAuth App**. Nhập Application name, ví dụ `CodePath Academy Local`.
3. Homepage URL: `http://localhost:5173`.
4. Authorization callback URL:

   ```text
   http://localhost:5173/api/auth/oauth2/callback/github
   ```

5. Register application, sao chép Client ID, tạo Client Secret và điền vào `.env`.

Dùng OAuth App cho cấu hình này. Không dùng Personal Access Token hoặc GitHub App
private key thay cho Client Secret. Scope yêu cầu là `read:user` và `user:email`;
không xin quyền repository. Email chính trên GitHub phải đã xác minh; email để
private vẫn dùng được. Tham khảo [tạo OAuth App](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/creating-an-oauth-app)
và [API email](https://docs.github.com/en/rest/users/emails).

## Cấu hình local

Thêm vào `.env` hiện tại; không ghi đè các cấu hình khác:

```dotenv
FRONTEND_URL=http://localhost:5173
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret
GITHUB_CLIENT_ID=your-github-client-id
GITHUB_CLIENT_SECRET=your-github-client-secret
OAUTH_CALLBACK_BASE_URL=
OAUTH_COOKIE_SECURE=false
```

Có thể chỉ cấu hình một nhà cung cấp, để trống cả hai biến của nhà cung cấp còn lại.
Cấu hình thiếu một trong hai biến sẽ báo lỗi khi khởi động để tránh bật OAuth sai.

```powershell
docker compose up -d --build --wait backend frontend
```

Mở `http://localhost:5173/login` và chọn Google/GitHub. Callback đi qua Vite proxy
`/api` tới backend. Không đăng ký `/oauth/callback` của React làm redirect URI của
nhà cung cấp; đó là trang hoàn tất nội bộ sau bước xác minh ở backend.

Kiểm tra trạng thái cấu hình tại `/api/auth/oauth2/providers`: API chỉ trả tên và
trạng thái bật/tắt, không trả Client ID/Secret.

## Tài khoản và liên kết

- Lần đăng nhập đầu bằng email chưa có sẽ tạo tài khoản STUDENT, tên đăng nhập
  tự sinh, không tạo ADMIN từ quyền Google/GitHub.
- Danh tính gắn bằng `(provider, subject)`, không theo tên GitHub hoặc email.
  Thay đổi email ở nhà cung cấp không chuyển quyền sở hữu tài khoản CodePath.
- Nếu email trùng tài khoản đã tồn tại, đăng nhập bằng cách cũ, vào **Hồ sơ cá nhân
  → Tài khoản liên kết**, nhập mật khẩu và chọn **Liên kết Google/GitHub**.
  Không tự gộp tài khoản theo email; việc liên kết giữ nguyên khóa học và vai trò.
- Một tài khoản CodePath có thể liên kết một Google và một GitHub. Một danh tính
  bên ngoài không được chuyển sang tài khoản CodePath khác qua luồng liên kết.
- Tài khoản mới tạo bằng OAuth chưa có mật khẩu sử dụng được. Trong hồ sơ có thể
  **Thiết lập mật khẩu** trong vòng 5 phút sau khi đăng nhập OAuth, rồi thêm Passkeys.
  Endpoint này chỉ thiết lập lần đầu, không dùng để đổi/reset mật khẩu hiện có.
- Có thể liên kết thêm nhà cung cấp sau khi nhập mật khẩu hoặc trong vòng 5 phút
  sau đăng nhập OAuth. Luồng này chưa có chức năng hủy liên kết nhà cung cấp.

## HTTPS, ngrok, staging và backend riêng

Với ngrok, đăng ký hai callback theo hostname thực tế:

```text
https://YOUR_DOMAIN.ngrok-free.dev/api/auth/oauth2/callback/google
https://YOUR_DOMAIN.ngrok-free.dev/api/auth/oauth2/callback/github
```

```powershell
docker compose -f docker-compose.yml -f compose.ngrok.yml up -d --build --wait
```

Override ngrok tự cấu hình frontend URL và Secure cookie. Để
`OAUTH_CALLBACK_BASE_URL` trống để lấy cùng public origin đó. Nếu callback có hostname
khác website, phải khai báo `OAUTH_CALLBACK_BASE_URL=https://api.example.com`; cấu hình
`FRONTEND_API_URL` tới backend và CORS theo frontend URL. Mọi URL callback là origin
cộng `/api/auth/oauth2/callback/{provider}`.

Với HTTPS production, đặt `OAUTH_COOKIE_SECURE=true`. Reverse proxy phải chuyển
`/api` tới backend và giữ cookie; không cache đường dẫn OAuth. Browser cần cookie
first-party cho phiên OAuth tạm thời. Nếu đổi hostname hoặc port, cập nhật redirect
URI tại nhà cung cấp và restart container. Staging dùng port 15173 phải đăng ký
callback `http://localhost:15173/api/auth/oauth2/callback/{provider}`.

Nên tạo OAuth App/Client riêng cho local và production để không dùng chung secret.

## Luồng và kiểm thử

Frontend tạo verifier ngẫu nhiên trong `sessionStorage`, chuyển challenge SHA-256
cho backend. Spring Security dùng state gắn với HTTP session tạm thời (tối đa 5 phút),
PKCE với nhà cung cấp và nonce cho Google. Google ID token được Spring xác minh;
GitHub email được lấy qua `/user/emails` và yêu cầu primary + verified.

Sau callback, backend giữ thông tin danh tính đã xác minh trong Redis, hủy session
tạm và trả mã dùng một lần trong fragment URL. React xóa fragment và đổi mã kèm
verifier; chỉ sau khi xác minh verifier, backend mới tạo/liên kết tài khoản rồi cấp JWT/refresh token.
Mã hết hạn sau 60 giây, tiêu thụ nguyên tử trong Redis; JWT/refresh token không đặt
trong URL. Không lưu access token của Google/GitHub. Các API ứng dụng tiếp tục dùng
Bearer JWT, không dùng OAuth HTTP session để cấp quyền.

Authorization request tạm lưu trong bộ nhớ HTTP session của backend. Restart giữa
luồng đăng nhập sẽ làm phiên đó mất hiệu lực; chạy nhiều replica cần sticky session
hoặc chuyển session sang kho dùng chung. Chỉ mở một luồng OAuth mỗi browser session
một lúc. Refresh JWT không kéo dài thời hạn xác thực lại 5 phút cho thao tác nhạy cảm.

```powershell
./gradlew.bat test
cd frontend
npm run lint
npm run build
cd ..
node scripts/social_login_test.cjs
```

Backend tests kiểm tra state/PKCE/nonce, callback thiếu phiên hoặc bị hủy, email
chưa xác minh, chống tự gộp/chuyển tài khoản, mã dùng một lần gắn với verifier,
và điều kiện thiết lập mật khẩu. Script UI dùng demo local và mock bước handoff,
không đăng nhập vào Google/GitHub thật. Để kiểm tra thực tế với nhà cung cấp cần
điền OAuth credentials và đăng nhập bằng tài khoản của bạn.

Lỗi thường gặp: `redirect_uri_mismatch` là callback đăng ký không trùng địa chỉ app;
Google `access_denied` có thể do tài khoản chưa được thêm vào Test users; GitHub
thiếu email cần xác minh email chính. Lỗi state/phiên thường do đổi hostname,
chặn cookie, mở nhiều luồng cùng lúc hoặc restart backend giữa quá trình đăng nhập.
