# Luồng xác nhận thanh toán

## Đầu vào

PayOS gọi `POST /api/payments/webhook`. Endpoint không dùng JWT người dùng; độ tin cậy dựa trên xác minh chữ ký bằng SDK và checksum key ở backend.

## Trình tự

1. `PayOsGateway` xác minh payload/chữ ký. Dữ liệu không hợp lệ bị từ chối 400.
2. Nếu mã kết quả khác `00`, không cấp quyền hoặc thay đổi đơn.
3. Tìm và khóa order theo `orderCode`.
4. Kiểm tra số tiền nhận đúng tổng tiền. Sai số tiền bị từ chối, kể cả callback gửi lại cho đơn đã thanh toán.
5. Nếu order đã PAID và số tiền đúng, trả thành công mà không tạo enrollment/payment lần nữa.
6. Nếu trạng thái không phù hợp, từ chối. Với PENDING, khóa user và tạo payment có external reference `PAYOS-<orderId>`.
7. Tạo enrollment cho từng item nếu chưa có; cập nhật PAID và paidAt trong cùng transaction.
8. Publish sự kiện xác nhận; listener chỉ queue email sau khi transaction commit.
9. API trả `{"message":"success"}` khi xử lý hợp lệ.

## Bất biến

Không lấy số tiền hoặc trạng thái từ URL return của trình duyệt để cấp quyền. Khóa dòng, unique constraint và kiểm tra PAID bảo vệ callback lặp. Payment, enrollment và trạng thái order cùng commit hoặc rollback; SMTP nằm ngoài giao dịch thanh toán.

Local muốn nhận callback thật cần URL backend mà PayOS truy cập được và cấu hình webhook tương ứng. Không đưa endpoint chưa xác thực hay secret thật vào script giả lập. Bài kiểm thử webhook cục bộ dùng checksum key kiểm thử.

## Mã nguồn

[AcademyController](../src/main/java/com/codepath/academy/controller/AcademyController.java), [OrderService](../src/main/java/com/codepath/academy/service/OrderService.java), [PayOsGateway](../src/main/java/com/codepath/academy/service/PayOsGateway.java), [gửi thư](16-email-delivery.md).
