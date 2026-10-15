# Luồng upload và quản lý video Mux

## Điều kiện

ADMIN đăng nhập, bài học đã lưu và backend cấu hình đủ bốn khóa Mux. Xem [thiết lập Mux](mux.md). Mỗi bài gắn một phiên/video Mux tại một thời điểm.

## Trình tự upload

1. Chọn tệp trong trình sửa bài; frontend chấp nhận tệp lớn hơn 0 và tối đa 2 GB.
2. Gọi `POST /api/admin/lessons/{id}/mux/upload`.
3. Backend khóa lesson. Nếu đã có upload ID thì trả 409, yêu cầu xóa video trước khi thay.
4. Backend gọi Mux bằng API token, tạo direct upload với CORS origin frontend, timeout 3600 giây, video quality `basic`, playback policy `signed`, passthrough `lesson:<id>`.
5. Lưu upload ID và trạng thái chờ vào MySQL; trả URL upload tạm cho trình duyệt.
6. UpChunk gửi trực tiếp đến Mux theo chunk 5120 KB, hiển thị phần trăm và thử lại trong phiên trang. Tệp không đi qua backend.
7. Giữ trang mở đến khi upload hoàn tất. Đóng/reload trang không giữ phiên upload phía trình duyệt.
8. Frontend gọi `POST /api/admin/lessons/{id}/mux/refresh` để đồng bộ upload/asset. Khi chờ xử lý và không đang upload, giao diện poll mỗi 5 giây.
9. Mux ready: backend lưu signed playback ID và thời lượng làm tròn lên theo phút. Chỉ nhận playback policy signed; thiếu signed ID không coi là thành công.

## Trạng thái và xóa

Trạng thái có thể là waiting, asset_created, preparing, ready, errored, timed_out, cancelled hoặc deleted. Chưa có webhook Mux; rời trang lúc xử lý thì mở lại trình sửa và cập nhật trạng thái.

`DELETE /api/admin/lessons/{id}/mux` xác định asset để xóa hoặc hủy upload chờ; thành công mới xóa các ID/trạng thái trong database. Lỗi API giữ tham chiếu để thử lại. Asset đã bị xóa bên ngoài được xử lý qua trạng thái không còn tồn tại. Xóa video trước khi xóa bài/chương.

## Lỗi và dữ liệu

Thiếu cấu hình/khóa RSA không hợp lệ trả lỗi dịch vụ; Mux từ chối quyền, vượt hạn mức hoặc video không hợp lệ cần xử lý theo thông báo và Dashboard. Không ghi private key vào lesson hoặc gửi xuống browser. URL upload tạm cũng cần giữ kín.

## Mã nguồn

[MuxVideoController](../src/main/java/com/codepath/academy/controller/MuxVideoController.java), [MuxVideoService](../src/main/java/com/codepath/academy/service/MuxVideoService.java), [MuxGateway](../src/main/java/com/codepath/academy/service/MuxGateway.java), [phát video](13-video-playback.md).
