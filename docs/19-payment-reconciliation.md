# Luồng đối soát thanh toán và khôi phục quyền học

## Mục đích và điều kiện

PayOS có thể đã ghi nhận tiền trong khi webhook chưa đến ứng dụng. Đơn local còn PENDING nên chưa có enrollment; mở lại link thanh toán đã PAID sẽ hiện thành công ngay nhưng chưa sửa trạng thái local. Đối soát xử lý trường hợp này bằng API PayOS từ backend.

API: `POST /api/orders/{id}/reconcile`. Chỉ chủ đơn hoặc ADMIN được thực hiện; không nhận số tiền hoặc trạng thái từ browser. Checkout vẫn chỉ cho chủ đơn.

## Trình tự

1. Khóa dòng order và kiểm tra quyền gọi. Đơn đã kết thúc hoặc chưa có checkout URL thì trả trạng thái local, không gọi PayOS.
2. Backend dùng thông tin xác thực PayOS để lấy payment link theo orderCode.
3. Kiểm tra mã đơn và tổng tiền trả về khớp snapshot order. Sai thông tin thì dừng, không tạo quyền học.
4. Nếu PAID: yêu cầu amountPaid đúng tổng, amountRemaining bằng 0; gọi cùng hàm settle với webhook.
5. Payment `PAYOS-<id>`, enrollment và order PAID được commit trong một transaction; email được queue sau commit. Đối soát lặp hoặc webhook tới sau không tạo bản ghi trùng.
6. Nếu PayOS CANCELLED/EXPIRED, ghi trạng thái tương ứng. Nếu PENDING/UNDERPAID/PROCESSING, giữ PENDING, không cấp quyền.
7. Lỗi truy vấn PayOS trả lỗi gateway; frontend giữ danh sách đơn và hiển thị lỗi để kiểm tra lại.

## Giao diện

- Chi tiết khóa đã có enrollment: “Vào học”; backend chặn đăng ký lại.
- Có đơn PENDING: dẫn tới `/orders?order=<id>` thay cho tạo mới.
- Trang đơn hàng có query order: đối soát đơn đang chờ có checkout URL khi mở và mỗi 10 giây.
- Danh sách có nút “Kiểm tra thanh toán” cho đơn PENDING có link, dùng được cả khi link cũ quay về địa chỉ local.
- Đối soát PAID: hiển thị xác nhận đã thêm khóa vào Học tập và link vào học; không mở lại PayOS.
- Đơn đã hủy/hết hạn: không mở link cũ; dẫn về chi tiết khóa để tạo đơn mới theo giá hiện tại.

## Giới hạn

Không có job nền quét toàn bộ đơn: đối soát do người dùng quay về trang đơn hoặc chọn kiểm tra. Vẫn cần cấu hình webhook public đúng để tự cấp quyền kể cả khi người dùng đóng trình duyệt. Các tham số return như status, code và cancel chỉ là thông tin điều hướng, không chứng minh đã thanh toán. Chưa có hoàn tiền; trạng thái PAID nội bộ không tự bị thu hồi bởi đối soát.

## Mã nguồn

[OrderService](../src/main/java/com/codepath/academy/service/OrderService.java), [PayOsGateway](../src/main/java/com/codepath/academy/service/PayOsGateway.java), [trang đơn hàng](../frontend/src/pages/Account.jsx), [chi tiết khóa học](../frontend/src/pages/Courses.jsx).

API tham chiếu: [PayOS payment link information](https://payos.vn/docs/api/).
