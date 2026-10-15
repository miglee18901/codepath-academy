# Luồng tìm và xem khóa học

## API và tác nhân

Khách hoặc người dùng đăng nhập gọi `GET /api/categories`, `GET /api/courses?q=java&categoryId=1&page=0&size=12`, `GET /api/courses/{id}`.

## Trình tự

1. Frontend tải danh mục và danh sách khóa học.
2. Catalog công khai lọc khóa `published=true`, tìm chuỗi trong tên/mô tả không phân biệt hoa thường và lọc category nếu truyền.
3. MySQL lọc trước khi phân trang; sắp xếp ID giảm dần để kết quả ổn định. Backend chỉ tạo card cho các khóa trên trang, gồm metadata, giá, số bài và thời lượng; không trả nội dung riêng hay URL video trong catalog.
4. Mở chi tiết khóa: tải danh sách chương/bài, trạng thái enrollment, hoàn thành và bài tiếp tục gần nhất khi có user.
5. Khóa chưa xuất bản chỉ được xem bởi ADMIN hoặc người đã có enrollment; người khác nhận 404.
6. Chọn bài học gọi `GET /api/lessons/{id}`; backend kiểm tra quyền lần nữa.
7. Cho xem nội dung nếu ADMIN, đã đăng ký hoặc bài preview thuộc khóa đã xuất bản. Trường hợp khác nhận 403.

## Quy tắc dữ liệu

Chi tiết khóa và nội dung bài là các response riêng. Biết ID bài không đồng nghĩa có quyền đọc bài. Thư viện `GET /api/me/courses` yêu cầu đăng nhập và chỉ trả enrollment của user hiện tại, kèm số bài hoàn thành.

## Phân trang

API dùng `page` từ 0, `size` mặc định 12, hợp lệ từ 1 đến 48. Response gồm `content`, `page`, `size`, `totalElements`, `totalPages`; tổng số được tính sau bộ lọc. Trang vượt phạm vi có content rỗng. `page` âm hoặc size không hợp lệ trả 400. Các ký tự `%`, `_`, `!` trong từ khóa được coi là ký tự thường, không phải wildcard SQL.

Nếu bỏ `page`, API giữ response mảng đầy đủ để tương thích script cũ. Giao diện luôn dùng API phân trang 12 khóa/trang. URL frontend dùng số trang từ 1 (`/?page=2&q=java&categoryId=1`); tải lại/Quay lại giữ trang và bộ lọc. Tìm kiếm/đổi danh mục về trang 1; nếu trang không còn tồn tại thì tự chuyển về trang cuối hợp lệ. Hiển thị tổng số, phạm vi hiện tại, nút trước/sau và số trang; không có kết quả thì hiện thông báo rỗng.

Quản trị gọi `GET /api/admin/courses?page=0&size=12` với cùng cấu trúc, gồm cả khóa chưa xuất bản; chỉ ADMIN được truy cập. Thư viện Học tập không thay đổi trong luồng này.

## Mã nguồn và bước tiếp theo

[AcademyController](../src/main/java/com/codepath/academy/controller/AcademyController.java), [CourseService](../src/main/java/com/codepath/academy/service/CourseService.java), [đăng ký miễn phí](08-free-enrollment.md), [mua khóa](09-paid-checkout.md).
