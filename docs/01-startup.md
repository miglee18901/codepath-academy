# Luồng khởi động ứng dụng

## Điều kiện

Cài Docker Engine/Compose, tạo `.env` từ mẫu và bảo đảm các port local chưa bị chiếm. Tham khảo [cấu hình](configuration.md).

## Trình tự

1. Chạy `docker compose up -d --build --wait` từ thư mục gốc.
2. Compose khởi động MySQL và Redis; backend chờ các dependency theo cấu hình healthcheck.
3. Backend nạp cấu hình Spring, kết nối MySQL và áp dụng Flyway V1, V2, V3 còn thiếu.
4. JPA kiểm tra schema; security, cache OTP và Artemis embedded được khởi tạo.
5. Nếu bật demo data, `DatabaseSeeder` tạo các bản ghi mẫu cần thiết. Không cần reset database cho mỗi lần chạy.
6. Frontend phục vụ React; trình duyệt gọi API qua địa chỉ đã cấu hình.
7. Kiểm tra `docker compose ps`, log backend và mở catalog. Email local xuất hiện tại Mailpit.

## Dữ liệu và lỗi

MySQL/Redis sử dụng volume Compose; restart container không tương đương khởi tạo database trắng. Lỗi kết nối cần kiểm tra tên service, mật khẩu và healthcheck. Lỗi checksum Flyway cần đối chiếu migration với database; không sửa checksum hoặc xóa dữ liệu để bỏ qua lỗi.

OTP và hàng đợi embedded có thể mất khi backend restart. Video trên Mux độc lập với vòng đời container. Khi đổi biến môi trường cần tạo lại container liên quan.

## Mã nguồn

- [Compose](../docker-compose.yml)
- [Điểm khởi động](../src/main/java/com/codepath/academy/CodePathAcademyApplication.java)
- [Seeder](../src/main/java/com/codepath/academy/component/DatabaseSeeder.java)
- [Luồng dữ liệu mẫu](18-demo-data.md)
