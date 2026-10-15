# Luồng tạo dữ liệu kiểm thử

## Dữ liệu nền khi khởi động

Khi bật demo data, `DatabaseSeeder` tạo tài khoản và các khóa mẫu phục vụ local. Thông tin đăng nhập mẫu tại [README](../README.md) chỉ dùng cho phát triển. Database đã có dữ liệu được giữ lại; không cần xóa volume để chạy lại.

## Tạo nhiều khóa học

1. Khởi động backend và đăng nhập được bằng ADMIN.
2. Chạy `python scripts/seed_courses.py` tại thư mục gốc. Dùng biến `ACADEMY_ADMIN_USER` và `ACADEMY_ADMIN_PASSWORD` khi thay tài khoản mẫu; xem tùy chọn của script trước khi chạy ở môi trường khác.
3. Script gọi API quản trị, dùng lại danh mục/khóa đã tồn tại theo nhận diện của nó.
4. Tạo bộ 24 khóa, mỗi khóa 4 chương × 3 bài: tổng 96 chương và 288 bài khi chưa có bộ dữ liệu này.
5. Khóa mới tạo ở trạng thái nháp, hoàn thiện nội dung rồi xuất bản.
6. Chạy lại để bổ sung phần thiếu; script giữ nội dung/metadata đã có thay vì ghi đè tùy tiện.

Mỗi request là một giao dịch riêng, không có transaction bao toàn bộ script. Nếu gián đoạn, kiểm tra kết quả rồi chạy lại để hoàn thiện. Không coi script seed là công cụ đồng bộ hoặc xóa dữ liệu production.

## Video thực tế để thử giao diện

[seed_mux_demo.py](../scripts/seed_mux_demo.py) cần thư mục clip đầu vào qua `--clips`, quyền ADMIN, Mux cấu hình hợp lệ và hạn mức còn trống. Chuẩn bị video có quyền sử dụng; script có thông tin nguồn/credit mẫu Big Buck Bunny. Tệp nguồn và báo cáo local nằm trong `.qa`, không đi cùng Git.

Script tạo/liên kết bài, upload qua API, chờ ready và kiểm tra signed manifest. Theo dõi checkpoint của script để tránh tạo asset lặp. Tài khoản đang đầy hạn mức cần giải phóng asset hoặc dùng bộ video đã có, không upload thêm mù quáng. Xóa asset bằng luồng quản trị để đồng bộ liên kết database.

## Kiểm tra kết quả

Mở catalog, chi tiết khóa, thư viện và bài học; thử vai trò khách/STUDENT/ADMIN, preview và bài riêng. Với video, thử [phát](13-video-playback.md), [popup](14-resume-confirmation.md) và [lưu tiến độ](15-learning-progress.md). Không commit secret, video nguồn, token, ảnh chụp chứa dữ liệu nhạy cảm hoặc output kiểm thử trong `.qa`.

## Mã nguồn

[DatabaseSeeder](../src/main/java/com/codepath/academy/component/DatabaseSeeder.java), [seed_courses.py](../scripts/seed_courses.py), [seed_mux_demo.py](../scripts/seed_mux_demo.py).
