# Nghiệm thu AI local — 17/10/2026

## Cấu hình được kiểm tra

- Java 17 / Spring Boot 4.1, MySQL 8, Redis 7; Qdrant và Ollama theo digest trong `infra/ai/models.lock.json`.
- Embedding `embeddinggemma:300m`, 768 chiều; chat `qwen3:4b-instruct-2507-q4_K_M`, context 4096, tối đa 512 output token, 3 CPU thread.
- Host RAM khoảng 16 GB, Docker khoảng 8 GB, CPU Intel và không dùng GPU rời. Jenkins, staging và observability vẫn chạy cùng máy.
- Cosine tối thiểu 0,35. Bản đầu 0,55 loại nhiều kết quả đúng, nên đã hiệu chỉnh bằng điểm similarity thực tế. Giữ ấm embedding 5 phút; chat unload sau request, chỉ nạp một model cùng lúc.

## Kết quả

| Kiểm tra | Kết quả |
|---|---|
| Backend test trong Docker, cùng target Jenkins | 108 test, 0 failure, 0 error |
| Frontend lint / production build | Qua; cảnh báo kích thước bundle video hiện có |
| Tìm kiếm trên 30 truy vấn có nhãn | Recall@5 = 29/30, 96,7% |
| 20 câu hỏi có dữ liệu | 20/20 trả `answered`, dẫn nguồn đúng khóa |
| Đối chiếu từng câu trả lời với nguồn | 19/20 diễn đạt chính xác; một câu JWT diễn đạt chưa đủ rõ, xem bên dưới |
| Câu hỏi thiếu dữ liệu trên 10 khóa | 10/10 trả `insufficient_context` |
| Smoke test RAG thực | Qua outbox, rollback, cập nhật/xóa, reindex, preview/enrollment, hỏi tiếp và chỉ dẫn giả |
| UI Chrome desktop / mobile | Qua dẫn nguồn, text safety, lịch sử, tách khóa, logout và URL tìm kiếm |
| Migration / seed mới | V1–V6 thành công, 3 khóa demo có outbox; health UP khi AI tắt |
| Dừng Qdrant/Ollama | Danh mục, đăng nhập, đơn hàng hoạt động; tìm kiếm fallback, trợ lý trả 503; dịch vụ được khởi động lại |
| Observability | 5 dashboard và 9 rule có truy vấn hợp lệ; logs/traces và redaction qua smoke test |

Câu JWT trả “Chữ ký JWT bảo vệ việc token không bị sửa đổi”. Nguồn diễn đạt chính xác hơn: chữ ký giúp **phát hiện** token bị sửa, không ngăn người khác sửa chuỗi token. Đánh giá thận trọng tính câu này chưa đạt; chất lượng nội dung trên tập QA là 19/20 = 95%, không đồng nhất với tỷ lệ trạng thái `answered` 100%.

Truy vấn chưa vào top 5: “chỉ trừ tiền chưa cộng tiền xử lý thế nào”. Không hạ tiếp ngưỡng chỉ để đạt 100% trên mẫu nhỏ.

## Độ trễ quan sát

| Luồng | Trung vị | p95 | Min–max |
|---|---:|---:|---:|
| Tìm khóa thông minh | 158 ms | 195 ms | 139–234 ms |
| Hỏi đáp có dữ liệu | 37,2 giây | 67,3 giây | 25,9–78,2 giây |
| Từ chối thiếu dữ liệu | 2,9 giây | 11,4 giây | 1,3–11,4 giây |

Đây là số đo trên máy phát triển đang chạy nhiều stack; một phần lúc kiểm tra cài mới và smoke test diễn ra đồng thời. Không coi đây là SLA hoặc benchmark production. Model CPU phải chuyển giữa embedding và chat, nên hỏi đáp chậm hơn tìm kiếm nhiều lần. Model chat Instruct được dùng vì tag `qwen3:4b` đã thử sinh suy luận dài và timeout trên cấu hình này.

## Phương pháp và giới hạn

`scripts/ai-fixtures.json` có 10 chủ đề lập trình. Mỗi chủ đề có ba truy vấn tìm kiếm và hai câu hỏi có đáp án tham chiếu. Câu hỏi thông tin ngân hàng không có trong nguồn được thử lại trên cả 10 khóa để kiểm tra phạm vi truy xuất và từ chối. Đây là 30 trường hợp hỏi đáp, gồm 20 câu có dữ liệu khác nhau và một câu ngoài nguồn lặp trên 10 ngữ cảnh; chưa đánh giá đa dạng câu ngoài nguồn hoặc bộ tài liệu production lớn.

Nguồn QA ngắn, nhiều mô tả và bài học chứa cùng thông tin, chưa đại diện cho bài học dài, nội dung mâu thuẫn hoặc mọi kiểu prompt injection. Các khóa QA được ẩn sau kiểm thử. Không suy rộng tỷ lệ đúng này thành cam kết về mọi câu hỏi thực tế; UI luôn có dẫn nguồn để đối chiếu.

Báo cáo chi tiết và ảnh nằm trong `artifacts/qa` (Git bỏ qua): `ai-evaluation.json`, `ai-smoke.json`, `ai-backend-tests`, `ai-assistant-desktop.png`, `ai-assistant-mobile.png`. Để chạy lại trên local/demo, xem [hướng dẫn AI](../infra/ai/README.md).
