# Luồng quản trị nội dung

## Điều kiện và API

Mọi endpoint `/api/admin/**` yêu cầu ADMIN. Học viên không thể gọi trực tiếp API để nâng quyền.

| Nội dung | API |
| --- | --- |
| Danh sách mọi khóa, gồm bản nháp | `GET /api/admin/courses` |
| Tạo / sửa khóa | `POST /api/admin/courses`, `PUT /api/admin/courses/{id}` |
| Tạo / sửa / xóa danh mục | `POST /api/admin/categories`, `PUT` hoặc `DELETE /api/admin/categories/{id}` |
| Tạo / sửa chương | `POST /api/admin/courses/{courseId}/sections`, `PUT /api/admin/courses/{courseId}/sections/{id}` |
| Xóa chương | `DELETE /api/admin/sections/{id}` |
| Tạo / sửa bài | `POST /api/admin/sections/{sectionId}/lessons`, `PUT /api/admin/sections/{sectionId}/lessons/{id}` |
| Xóa bài | `DELETE /api/admin/lessons/{id}` |

## Trình tự đề xuất

1. Tạo hoặc chọn danh mục.
2. Tạo khóa ở trạng thái nháp, điền tiêu đề, mô tả, giá, giảng viên, cấp độ và thumbnail.
3. Tạo chương với thứ tự; thêm bài vào đúng chương.
4. Điền nội dung, thời lượng, preview và URL video HTTP/HTTPS hoặc [upload Mux](12-video-upload.md).
5. Backend kiểm tra bản ghi cha, quan hệ course/section/lesson và validation DTO trước khi lưu.
6. Kiểm tra bài ở giao diện học rồi xuất bản khóa. Catalog công khai chỉ hiển thị khóa xuất bản.

## Ràng buộc và xóa dữ liệu

Không xóa danh mục đang chứa khóa. Không xóa bài hoặc chương có bài còn gắn Mux: xóa video qua API Mux trước để giải phóng tài nguyên ngoài ứng dụng. Xóa bài/chương sẽ xóa dữ liệu tiến độ liên quan theo xử lý service. Chưa có API xóa toàn bộ khóa học.

URL video phải có HTTP/HTTPS, host hợp lệ và không chứa userinfo. Giá và trường đầu vào được kiểm tra ở backend; biểu mẫu frontend không phải lớp kiểm tra duy nhất. Việc thay metadata khóa không thay snapshot của các đơn đã tạo.

## Mã nguồn

[AdminController](../src/main/java/com/codepath/academy/controller/AdminController.java), [CourseService](../src/main/java/com/codepath/academy/service/CourseService.java), [AcademyRequests](../src/main/java/com/codepath/academy/dto/AcademyRequests.java).
