# Luồng đăng ký khóa miễn phí

## Điều kiện

User đã đăng nhập; khóa tồn tại, đã xuất bản và có giá bằng 0. Gửi `POST /api/orders` với `{"courseId":1}`; ID chỉ là ví dụ.

## Trình tự

1. Backend khóa bản ghi user để tuần tự hóa giao dịch liên quan.
2. Kiểm tra khóa xuất bản và user chưa có enrollment; đã có quyền học thì từ chối yêu cầu mua lại.
3. Dùng đơn PENDING cùng khóa nếu có, hoặc tạo đơn và order item với giá/tên snapshot từ database.
4. Vì tổng tiền bằng 0, gọi xử lý thanh toán nội bộ với reference `FREE-<orderId>` và số tiền 0.
5. Tạo payment và enrollment, chuyển order sang PAID, ghi thời điểm thanh toán trong cùng giao dịch.
6. Sau commit, gửi sự kiện xác nhận để queue email.
7. Frontend mở thư viện/trang học; các API bài học nhìn thấy enrollment mới.

## Kết quả và lỗi

Không cần PayOS cho khóa miễn phí. Client không được tự truyền giá 0 để biến khóa có phí thành miễn phí. Khóa ẩn/không tồn tại bị từ chối; lỗi giao dịch không được để lại quyền học một phần. Email gửi lỗi không thu hồi enrollment đã commit.

## Mã nguồn

[OrderService](../src/main/java/com/codepath/academy/service/OrderService.java), [email xác nhận](16-email-delivery.md), [dữ liệu](data-model.md).
