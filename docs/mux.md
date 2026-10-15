# Mux Free cho CodePath Academy

Ứng dụng hỗ trợ upload trực tiếp từ trình duyệt lên Mux bằng UpChunk, phát HLS bằng Mux Player và signed playback. Mux là tùy chọn: thiếu khóa cấu hình sẽ không ảnh hưởng đăng nhập, học bài văn bản hoặc video từ URL trực tiếp.

## 1. Tạo tài khoản và khóa

1. Đăng ký [Mux](https://dashboard.mux.com/) và chọn gói **Free** nếu tài khoản có tùy chọn này. Kiểm tra hạn mức và Usage trên Dashboard cùng [bảng giá Mux](https://www.mux.com/pricing) trước khi upload; hạn mức của dịch vụ có thể thay đổi. Ứng dụng không tự đăng ký hoặc thay đổi gói dịch vụ.
2. Trong Settings → API Access Tokens, tạo token cho environment muốn sử dụng với quyền **Mux Video Read và Write**. Lưu Token ID và Token Secret.
3. Trong Settings → Signing Keys, tạo URL signing key trong **cùng environment**. Lưu Signing Key ID và private key Base64 do Mux trả về. Đây là khóa khác với API token.
4. Không bật các dịch vụ trả phí bổ sung để thử tính năng này. Upload từ ứng dụng dùng `video_quality=basic` và chỉ tạo playback policy `signed`; không tạo MP4 tải xuống, DRM hoặc tác vụ AI.

Tài liệu chính thức: [API requests](https://www.mux.com/docs/core/make-api-requests), [signed playback và signing key](https://www.mux.com/docs/guides/secure-video-playback).

## 2. Điền cấu hình local

Nếu chưa có `.env`, copy `.env.example` thành `.env`. Nếu đã có, chỉ thêm/cập nhật các biến dưới đây, giữ nguyên cấu hình database và các dịch vụ khác:

```dotenv
MUX_TOKEN_ID=your-token-id
MUX_TOKEN_SECRET=your-token-secret
MUX_SIGNING_KEY_ID=your-signing-key-id
MUX_SIGNING_PRIVATE_KEY=your-base64-private-key
```

Dán private key Base64 trên **một dòng**, không phải đường dẫn tới file. Backend hỗ trợ PEM RSA PKCS#1 và PKCS#8 được mã hóa Base64. Không đưa khóa vào `VITE_*`, mã frontend, Git hoặc tin nhắn chat. `.env` đã được Git và Docker build bỏ qua.

```powershell
docker compose up -d --build backend frontend
docker compose logs --tail 80 backend
```

Flyway tự áp dụng các migration schema còn thiếu khi backend khởi động. Giữ nguyên migration đã áp dụng và volume dữ liệu. Chạy Java trực tiếp thì truyền bốn biến MUX qua environment; Spring không tự đọc `.env`.

## 3. Upload và xem thử

1. Mở http://localhost:5173, đăng nhập ADMIN và vào **Quản trị → sửa khóa học → sửa bài học**.
2. Với bài mới, lưu và mở lại bài học trước khi upload.
3. Tại **Video Mux**, chọn một tệp video, tối đa 2 GB theo giới hạn giao diện. Giữ trình sửa bài học mở đến khi upload hoàn tất. UpChunk chia khối và tự thử lại khi mạng gián đoạn trong phiên trang hiện tại; tải lại/đóng trang sẽ không giữ phiên upload phía trình duyệt.
4. Giao diện tự cập nhật trạng thái mỗi 5 giây trong khi chờ xử lý. Khi **Video sẵn sàng**, thời lượng được lấy từ Mux. Đóng và mở lại trình sửa bài học để đọc thời lượng mới trước khi sửa metadata khác.
5. Mở bài học trên trang học. ADMIN và học viên đã đăng ký được xem bài riêng. Bài **xem thử** của khóa đã xuất bản được xem công khai.
6. Muốn thay video, dùng **Xóa video Mux** rồi upload lại. Nút này xóa asset trên Mux hoặc hủy upload đang chờ. Cần xóa video Mux trước khi xóa bài học/chương để không bỏ lại video chiếm hạn mức.

Video Mux được ưu tiên khi bài học có cả Mux và URL MP4. Sau khi xóa liên kết Mux, player có thể sử dụng URL MP4 đã cấu hình trong bài.

## 4. Cách hoạt động và giới hạn

- Chỉ ADMIN được tạo upload, cập nhật trạng thái hoặc xóa video. Browser nhận URL upload tạm thời, không nhận API secret hay signing private key.
- Không cần webhook/tunnel để trải nghiệm local. Trạng thái được đồng bộ khi mở trình sửa bài học hoặc chọn **Cập nhật trạng thái**. Nếu rời trang khi Mux đang xử lý, mở lại trình sửa bài học để hoàn tất đồng bộ trước khi cho học viên xem. Production có thể bổ sung webhook đã xác minh chữ ký.
- Endpoint phát kiểm tra quyền học hiện tại trước khi cấp token. Response dùng `Cache-Control: no-store`; token có hạn ít nhất 1 giờ hoặc thời lượng bài + 15 phút. Khi trang học còn mở, frontend xin token mới mỗi 15 phút; nút **Tải lại quyền phát video** giúp thử lại sau lỗi.
- Vị trí xem được lưu mỗi 10 giây, khi pause/seek và khi rời trang. Học viên đã đăng ký lưu vào `lesson_progress`; khách hoặc ADMIN chưa đăng ký lưu trên trình duyệt theo danh tính hiện tại. Lần mở sau hiển thị popup với tên bài, mốc đã xem và hai lựa chọn: tiếp tục hoặc xem lại từ đầu. Video chờ quyết định, bắt đầu phát sau khi chọn. Video đã xem từ 95% trở lên mở lại từ đầu và được đánh dấu hoàn thành cho học viên đã đăng ký.
- Signed playback hạn chế truy cập qua URL; không phải DRM, không ngăn quay màn hình. Token đã cấp còn hiệu lực đến khi hết hạn.
- Hạn mức Free áp dụng trên tài khoản Mux, bao gồm video upload ngoài ứng dụng. Xóa video không hoàn lại số phút đã truyền phát. Kiểm tra Usage trên Dashboard; code không bảo đảm một tài khoản đã nâng cấp sẽ không phát sinh phí.
- Chưa tự nhập video đã upload bằng Dashboard. Để liên kết bài học, upload qua trang quản trị của ứng dụng.

## 5. Xử lý lỗi

- **Chưa cấu hình Mux**: kiểm tra bốn biến trong `.env`, rồi tạo lại container backend bằng `docker compose up -d backend`.
- **HTTP 401/403 từ Mux**: token sai, thiếu quyền Video hoặc dùng sai environment.
- **Không upload được từ browser**: địa chỉ mở frontend phải khớp `app.frontend-url` (local mặc định `http://localhost:5173`), không dùng `127.0.0.1` thay thế. Khi đổi domain, cập nhật `FRONTEND_URL` trong `.env`; Compose truyền giá trị này vào `APP_FRONTENDURL`. Tạo lại backend sau thay đổi.
- **Timed out/errored**: xóa phiên/video Mux rồi thử lại với video hợp lệ; nếu hết hạn mức lưu trữ, giải phóng asset phù hợp trong Dashboard và đồng bộ lại liên kết bài học.
- **Video ready nhưng không phát**: kiểm tra signing key và API token thuộc cùng environment; thử **Tải lại quyền phát video**. Nếu đã xóa asset trên Dashboard, cập nhật trạng thái và xóa liên kết cũ trong trình sửa bài học.

Kiểm tra phần mềm bằng `docker build --target test -t codepath-academy-tests .`, `npm --prefix frontend run lint` và `npm --prefix frontend run build`. Các test tự động không upload video hoặc sử dụng tài khoản Mux thật.

Luồng chi tiết: [upload và xóa video](12-video-upload.md), [cấp token và phát video](13-video-playback.md), [popup tiếp tục xem](14-resume-confirmation.md), [lưu tiến độ](15-learning-progress.md). Xem thêm [cấu hình](configuration.md) và [mô hình dữ liệu](data-model.md).
