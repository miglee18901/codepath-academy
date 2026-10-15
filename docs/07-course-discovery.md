# Luồng tìm và xem khóa học

## API và tác nhân

Khách hoặc người dùng đăng nhập gọi `GET /api/categories`, `GET /api/courses?q=java&categoryId=1`, `GET /api/courses/{id}`.

## Trình tự

1. Frontend tải danh mục và danh sách khóa học.
2. Catalog công khai lọc khóa `published=true`, tìm chuỗi trong tên/mô tả không phân biệt hoa thường và lọc category nếu truyền.
3. Backend trả card gồm metadata, giá, số bài và thời lượng; không trả nội dung riêng hay URL video trong catalog.
4. Mở chi tiết khóa: tải danh sách chương/bài, trạng thái enrollment, hoàn thành và bài tiếp tục gần nhất khi có user.
5. Khóa chưa xuất bản chỉ được xem bởi ADMIN hoặc người đã có enrollment; người khác nhận 404.
6. Chọn bài học gọi `GET /api/lessons/{id}`; backend kiểm tra quyền lần nữa.
7. Cho xem nội dung nếu ADMIN, đã đăng ký hoặc bài preview thuộc khóa đã xuất bản. Trường hợp khác nhận 403.

## Quy tắc dữ liệu

Chi tiết khóa và nội dung bài là các response riêng. Biết ID bài không đồng nghĩa có quyền đọc bài. Thư viện `GET /api/me/courses` yêu cầu đăng nhập và chỉ trả enrollment của user hiện tại, kèm số bài hoàn thành.

## Mã nguồn và bước tiếp theo

[AcademyController](../src/main/java/com/codepath/academy/controller/AcademyController.java), [CourseService](../src/main/java/com/codepath/academy/service/CourseService.java), [đăng ký miễn phí](08-free-enrollment.md), [mua khóa](09-paid-checkout.md).
