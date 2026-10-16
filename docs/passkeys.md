# Đăng nhập bằng Passkeys

CodePath Academy hỗ trợ Passkeys bên cạnh mật khẩu. Backend dùng Yubico
`webauthn-server-core` để xác minh WebAuthn và tiếp tục cấp cùng JWT/refresh token
như đăng nhập bằng mật khẩu. Không lưu khóa bí mật hoặc dữ liệu sinh trắc học.

## Sử dụng

1. Mở `http://localhost:5173`, đăng nhập bằng mật khẩu.
2. Vào **Hồ sơ cá nhân → Passkeys**, nhập tên gợi nhớ và mật khẩu hiện tại,
   chọn **Thêm Passkey**, xác nhận trên thiết bị hoặc trình quản lý Passkeys.
3. Đăng xuất, chọn **Đăng nhập bằng Passkey**. Không cần điền tên đăng nhập.
4. Trong hồ sơ, chọn **Xóa** và nhập lại mật khẩu để thu hồi Passkey.
   Việc thu hồi không xóa bản lưu trong trình quản lý Passkeys của thiết bị.

Cho phép tối đa 10 Passkey mỗi tài khoản. Giữ mật khẩu để đăng nhập khi mất
thiết bị; dự án chưa có luồng đặt lại mật khẩu/quy trình khôi phục tài khoản mới.
Xóa Passkey chặn lần đăng nhập tiếp theo bằng credential đó; JWT và refresh token
đã cấp vẫn theo thời hạn và cơ chế hiện tại.

## Cấu hình Docker và tên miền

Chạy `docker compose up -d --build --wait backend frontend` sau khi cập nhật mã.
Flyway tự áp dụng `V4__passkeys.sql`; các credential nằm trong MySQL volume hiện tại.
Challenge ngẫu nhiên lưu Redis 5 phút, tiêu thụ nguyên tử một lần bằng GETDEL.
Đăng ký gắn challenge với tài khoản đã xác thực lại bằng mật khẩu. Bắt buộc
user verification cho cả tạo và đăng nhập Passkey.

Mặc định RP ID lấy hostname của `FRONTEND_URL`, và chỉ cho phép origin chính xác
của URL đó. Có thể ghi đè bằng các biến môi trường:

```dotenv
FRONTEND_URL=https://academy.example.com
PASSKEY_RP_ID=academy.example.com
PASSKEY_ORIGINS=https://academy.example.com
```

RP ID không có scheme, port hoặc path. `PASSKEY_ORIGINS` là danh sách origin
được phép phân cách bằng dấu phẩy, bao gồm scheme và port nếu có.
Khi chạy staging cổng 15173, đặt `FRONTEND_URL=http://localhost:15173` và để hai
biến PASSKEY trống. Docker ngrok override tự lấy hostname từ URL ngrok;
nếu đã đặt PASSKEY_RP_ID/PASSKEY_ORIGINS trong `.env`, phải cập nhật tương ứng.

Passkeys cần secure context: HTTPS hoặc localhost khi phát triển. Dùng nhất quán
`localhost`, tránh chuyển sang `127.0.0.1`. Passkey localhost không dùng cho
production; đổi hostname ngrok cũng có thể yêu cầu đăng ký lại. Không đưa domain
không tin cậy vào danh sách origin. Backend kiểm tra chữ ký, challenge, RP ID,
origin, user verification và signature counter bằng thư viện WebAuthn.

API quản lý dùng Bearer JWT, không dùng cookie session. Thêm/xóa yêu cầu mật khẩu
hiện tại, giới hạn 10 yêu cầu/phút/tài khoản. API đăng nhập giới hạn tổng 60 yêu
cầu/phút/địa chỉ remote của backend. Reverse proxy có thể khiến người dùng chung
một bucket; khi mở rộng nên giới hạn tại proxy theo địa chỉ client đáng tin cậy.

## Kiểm thử

```powershell
./gradlew.bat test
cd frontend
npm run lint
npm run build
cd ..
npm install --prefix .qa --no-save --package-lock=false playwright
node scripts/passkey_test.cjs
```

Script Chrome dùng WebAuthn virtual authenticator và hai tài khoản demo local
`student`/`admin`. Tự tạo và thu hồi Passkey QA, không dùng Passkey thật của máy.
Kiểm tra đăng ký/đăng nhập trên UI, chữ ký thật, origin/challenge/RP ID sai, thiếu
user verification, replay, refresh token và quyền xóa. Ảnh ở `artifacts/qa/`.

## API endpoints

| Method | Endpoint | Quyền |
|---|---|---|
| POST | `/api/auth/passkeys/options` | Công khai, tạo challenge đăng nhập |
| POST | `/api/auth/passkeys/verify` | Công khai, xác minh rồi cấp token |
| GET | `/api/me/passkeys` | Đã đăng nhập, chỉ liệt kê của chính mình |
| POST | `/api/me/passkeys/options` | Đã đăng nhập; body `{label, password}` |
| POST | `/api/me/passkeys/verify` | Đã đăng nhập, hoàn tất đăng ký |
| DELETE | `/api/me/passkeys/{id}` | Đã đăng nhập; body `{password}` |

Options trả `{requestId, options}`; `options.publicKey` dùng cho WebAuthn sau khi
giải mã Base64URL các trường nhị phân. Verify nhận `{requestId, credential}`,
trong đó `credential` là chuỗi JSON WebAuthn đã encode Base64URL.
Không ghi log mật khẩu, token, requestId hoặc toàn bộ response WebAuthn.

Tham khảo: [Yubico Java WebAuthn](https://developers.yubico.com/java-webauthn-server/),
[WebAuthn](https://www.w3.org/TR/webauthn-2/).
