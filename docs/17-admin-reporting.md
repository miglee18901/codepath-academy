# Luồng báo cáo quản trị

## Điều kiện và API

ADMIN đăng nhập. Các endpoint đều thuộc `/api/admin` và được backend kiểm tra role.

| API | Kết quả |
| --- | --- |
| `GET /stats` | Tổng đơn, đơn PAID, doanh thu PAID, số STUDENT, số enrollment |
| `GET /orders` | Mọi đơn, sắp xếp mới trước |
| `GET /students` | Danh sách tài khoản kèm role và số khóa đã đăng ký |
| `GET /orders/export` | Tệp Excel toàn bộ đơn |

## Trình tự

1. Mở trang quản trị; frontend gửi JWT ADMIN và gọi các API cần hiển thị.
2. Backend truy vấn MySQL, không lấy chỉ số thanh toán từ cache trình duyệt.
3. Doanh thu cộng tổng tiền của đơn PAID. Số enrollment là số quyền học, không phải số người dùng duy nhất hay lượt xem video.
4. Endpoint tên students hiện trả mọi user, gồm cả ADMIN; dùng trường role khi trình bày/lọc.
5. Chọn xuất đơn: backend dùng Apache POI tạo workbook, sheet Orders, các cột Order, Student, Total VND, Status, Created, Paid.
6. Trả MIME XLSX và `Content-Disposition` với tên `orders.xlsx`; frontend tải tệp.

## Giới hạn

Danh sách/xuất hiện chưa có bộ lọc khoảng ngày hoặc phân trang. Đơn miễn phí PAID có tổng 0 vẫn nằm trong số đơn thanh toán. Báo cáo không cung cấp analytics Mux hoặc số phút xem thực tế. Với dữ liệu lớn cần bổ sung phân trang/stream export trước khi vận hành quy mô lớn.

## Mã nguồn

[AdminController](../src/main/java/com/codepath/academy/controller/AdminController.java), [OrderService](../src/main/java/com/codepath/academy/service/OrderService.java).
