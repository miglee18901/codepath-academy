# Luồng lưu tiến độ học

## API và điều kiện

`PUT /api/me/progress/{lessonId}` yêu cầu đăng nhập và enrollment của chính user, kể cả ADMIN. Payload có `completed` và/hoặc `positionSeconds`; khi gửi vị trí cần `durationSeconds` hợp lệ.

```json
{"positionSeconds":42,"durationSeconds":600}
```

## Ghi từ player

1. Khi player sẵn sàng và đã xử lý lựa chọn resume, theo dõi timeupdate.
2. Cập nhật bản sao local khi giây thay đổi; gửi backend khoảng mỗi 10 giây và tại pause, seek, ended, visibility hidden hoặc unmount.
3. Tuần tự hóa request để checkpoint cũ không ghi đè checkpoint mới do phản hồi đảo thứ tự.
4. Khi pagehide, thử gửi keepalive nếu không có request đang chạy; đây là best effort, không bảo đảm khi thiết bị tắt đột ngột.
5. Khách hoặc user chưa enrolled chỉ ghi local, không gọi API lưu tiến độ database.

## Xử lý backend

1. Tìm lesson, kiểm tra enrollment và validation.
2. Phải có completed hoặc position; vị trí nằm từ 0 đến duration, duration từ 1 đến 604800 giây. Giá trị âm/vượt giới hạn bị từ chối 400.
3. Khóa user, tìm hoặc tạo bản ghi duy nhất theo user/lesson.
4. Cập nhật trường được gửi; bỏ qua completed thì giữ trạng thái hiện tại.
5. Vị trí đạt ít nhất 95% thời lượng tự đánh dấu hoàn thành và reset checkpoint về 0.
6. Đánh dấu completed=true trực tiếp cũng reset vị trí về 0.
7. Lưu thời điểm cập nhật và trả completed, vị trí, duration, epoch checkpoint.

## Đọc lại

API bài học chỉ trả tiến độ của user hiện tại. Chi tiết khóa dùng checkpoint có thời điểm gần nhất để chọn bài tiếp tục; thư viện tính số bài completed. Thời lượng checkpoint là giây, khác thời lượng lesson hiển thị theo phút.

## Mã nguồn

[CourseService](../src/main/java/com/codepath/academy/service/CourseService.java), [LessonProgress](../src/main/java/com/codepath/academy/entity/LessonProgress.java), [popup resume](14-resume-confirmation.md).
