# Luồng xác nhận tiếp tục xem

## Mục tiêu

Khi quay lại bài đang xem dở, hiển thị tên bài, mốc đã xem và tổng thời lượng, cho chọn **Tiếp tục xem** hoặc **Xem lại từ đầu**.

## Trình tự

1. Mở khóa ở `/learn/:courseId` chưa chọn bài: ưu tiên `resumeLessonId` backend, sau đó con trỏ bài local, cuối cùng bài đầu tiên. Điều hướng về URL bài cụ thể.
2. Tải checkpoint của user từ response bài học và localStorage được phân theo username/guest và lesson ID.
3. So sánh `updatedAt` local với `positionUpdatedAt` backend; chọn checkpoint mới hơn. Không tự gộp checkpoint từ tài khoản khác.
4. Chờ video có metadata/thời lượng hợp lệ.
5. Vị trí lớn hơn 0 và nhỏ hơn 95% thời lượng: pause video và mở modal. Không ghi checkpoint 0 trong lúc chờ lựa chọn.
6. Modal hiển thị tên bài và thời gian dạng phút:giây. Khóa cuộn nền, tập trung vào nút; ESC không tự đóng popup.
7. Chọn tiếp tục: đặt currentTime bằng mốc đã lưu. Chọn xem lại: đặt currentTime bằng 0.
8. Đánh dấu player sẵn sàng, ghi lựa chọn và yêu cầu phát. Nếu trình duyệt chặn autoplay thì dùng nút điều khiển player.
9. Không có mốc dở hoặc đã đạt 95% thì bắt đầu từ đầu, không hiện popup.

## Dữ liệu và tình huống biên

User đã đăng ký lưu cả local và database. Khách/ADMIN chưa có enrollment chỉ lưu local. Xóa bộ nhớ trình duyệt làm mất checkpoint local, nhưng enrollment và checkpoint database vẫn tồn tại với user đã đăng ký. Mất mạng lúc ghi có thể khiến thiết bị khác đọc mốc cũ; cùng trình duyệt vẫn có bản sao local mới hơn.

Thông tin hoàn thành tách khỏi vị trí xem lại. Chọn xem lại từ đầu không tự xóa trạng thái đã hoàn thành.

## Tham chiếu

[Frontend source](../frontend/src), [lưu tiến độ](15-learning-progress.md), [CourseService](../src/main/java/com/codepath/academy/service/CourseService.java).
