# Luồng đăng xuất

## Trình tự hiện tại

1. Người dùng chọn đăng xuất ở frontend.
2. Frontend xóa access token, refresh token và thông tin user khỏi localStorage.
3. Cập nhật trạng thái giao diện rồi điều hướng về trang chính.
4. Request tiếp theo không còn gửi JWT của phiên đó; nội dung cần enrollment phải đăng nhập lại.

## Phạm vi tác động

Chưa có API logout để thu hồi token trên server. Refresh token còn trong Redis đến khi hết TTL hoặc bị tiêu thụ; access JWT và token Mux đã cấp có hiệu lực đến thời điểm hết hạn của chúng. Đăng xuất không xóa enrollment, đơn mua hoặc tiến độ trong database.

Checkpoint video local được phân theo danh tính; người dùng khác không dùng chung checkpoint của tài khoản trước. Cơ chế rời trang cố gắng lưu checkpoint nhưng không thể bảo đảm request cuối cùng khi trình duyệt bị tắt đột ngột.

## Tham chiếu

[Frontend source](../frontend/src), [refresh token](05-refresh-token.md), [lưu tiến độ](15-learning-progress.md).
