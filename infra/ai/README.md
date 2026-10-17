# AI local: Vector Search và RAG

## Khởi động

Yêu cầu Docker Desktop Linux containers. Máy tham chiếu: RAM 16 GB, Docker khoảng 8 GB, CPU Intel, không dùng GPU rời. Dành khoảng 10 GB đĩa trống cho image/model/index. Dữ liệu truy vấn và bài học không gửi tới API AI bên ngoài; lần đầu cần Internet tải image và model.

Từ thư mục gốc:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/ai/setup.ps1
```

Nếu ứng dụng đang kết nối bộ giám sát:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra/ai/setup.ps1 -WithObservability
```

Script tạo `infra/ai/.env` chứa khóa Qdrant ngẫu nhiên, khởi động Qdrant/Ollama, tải model chưa có và ghi ba cờ `AI_*_ENABLED=true` vào `.env` gốc. Không thay credential hoặc cấu hình khác. Dùng `-SkipApplication` nếu chỉ muốn dựng hạ tầng, không bật AI hay rebuild ứng dụng.

Sau lần đầu, luôn giữ override AI khi thao tác ứng dụng:

```powershell
docker compose -f docker-compose.yml -f compose.ai.yml up -d --build --wait backend frontend
# Có giám sát: thêm -f compose.observability.yml trước up.
docker compose --env-file infra/ai/.env -f infra/ai/compose.yaml ps
```

Website http://localhost:5173. Chọn **Thông minh** tại danh mục để tìm theo ý nghĩa. Đăng nhập rồi mở trang khóa học hoặc học bài để hỏi trợ lý. **Quản trị → Chỉ mục AI** xem tiến độ, lập lại toàn bộ hoặc thử lại một khóa. Dữ liệu có sẵn tự được lập chỉ mục sau khi backend khởi động; lần đầu phải chờ worker hoàn tất.

Qdrant/Ollama không publish port ra host. Backend kết nối mạng Docker `codepath-ai`. Không chia sẻ mạng này với container không tin cậy: Ollama không có lớp xác thực của ứng dụng. Qdrant yêu cầu API key; giữ hai giá trị key trong file `.env` AI giống nhau và không đưa vào frontend/Git.

## Luồng dữ liệu và quyền

- MySQL là nguồn chính; vector có thể xóa và xây lại. Không đổi MySQL sang PostgreSQL.
- `V6__ai_index_outbox.sql` tạo outbox một hàng/khóa học. Các thao tác lưu/xóa khóa, chương, bài trong lớp nghiệp vụ và demo seeder ghi outbox cùng transaction qua JdbcTemplate/JPA, không cần trigger hoặc quyền SUPER. Nếu chỉnh nội dung bằng SQL trực tiếp ngoài ứng dụng, phải yêu cầu reindex; kiểm tra hash/quyền vẫn loại nguồn cũ khỏi câu trả lời trong thời gian đó.
- Worker poll 5 giây, claim bằng lease 180 giây có heartbeat, chia đoạn tối đa 1.600 ký tự, chồng 200 ký tự, cố gắng ưu tiên đoạn văn. Chia nhỏ tiếp khi embedding báo quá giới hạn; không âm thầm truncate.
- Không giữ transaction MySQL trong lúc gọi model. Revision thay đổi sẽ ngăn worker cũ hoàn tất; dữ liệu vector viết dở không được coi là nguồn hiện hành. ID vector xác định từ nội dung/revision nên retry không tạo bản sao.
- Retry lỗi provider/storage tối đa 5 lần, backoff 15/30/60/120/240 giây. Sau đó ADMIN thử lại. Mã lỗi được làm sạch, không lưu phản hồi model hoặc nội dung bài vào lỗi.
- Tìm kiếm công khai chỉ dùng mô tả khóa đã xuất bản. Kết hợp LIKE hiện có và vector bằng RRF, tối đa 60 ứng viên; không coi số ứng viên là tổng khóa học trong database. Qdrant/Ollama lỗi thì trả kết quả từ khóa.
- Trợ lý giới hạn theo một khóa, cần đăng nhập. ADMIN xem mọi nội dung; học viên đã đăng ký xem nội dung của khóa kể cả khóa ẩn; người khác chỉ xem mô tả/bài preview của khóa đã xuất bản. Khớp chính sách đọc nội dung hiện có.
- Backend lọc trước truy vấn vector, sau đó kiểm tra revision, fingerprint model/chunker, hash và đoạn trích với MySQL. Trước trả câu trả lời, kiểm tra lại nguồn và quyền. Đoạn đã xóa/sửa, khóa đã ẩn hoặc quyền bị thu hồi không được dùng làm câu trả lời.
- Mỗi câu hỏi lấy tối đa 6 đoạn, tổng tối đa 2.400 ký tự. Lịch sử chỉ giúp hiểu câu hỏi nối tiếp, không là bằng chứng. Model không có tool, không chạy code hay truy cập URL.
- Câu trả lời phải có mã dẫn nguồn hợp lệ `[S1]`…; không đủ căn cứ trả `insufficient_context`. Kiểm tra mã nguồn không chứng minh mọi nhận định đều chính xác: đánh giá nội dung thủ công vẫn cần thiết. UI hiển thị text thuần, không render HTML từ model.

## Cấu hình và giới hạn

| Biến | Mặc định |
|---|---|
| `AI_SEARCH_ENABLED`, `AI_ASSISTANT_ENABLED`, `AI_INDEXING_ENABLED` | `false` trong ứng dụng; setup bật thành `true` |
| `AI_COLLECTION` | `codepath_local_v1` |
| `AI_EMBEDDING_MODEL` | `embeddinggemma:300m`, 768 chiều |
| `AI_CHAT_MODEL` | `qwen3:4b-instruct-2507-q4_K_M`, bản Instruct không thinking |
| `AI_EMBEDDING_DIMENSIONS` | `768` |
| `AI_MIN_SCORE` | `0.35`, cosine; không phải xác suất đúng |
| `AI_NUM_THREADS` | `3`, khớp quota CPU của container Ollama |
| `AI_NETWORK` | `codepath-ai`; phải giống ở Compose AI và Compose ứng dụng |

Model phục vụ đa ngôn ngữ, gồm nội dung lập trình. Model/image được chọn để chạy CPU với tài nguyên máy hiện tại, không yêu cầu GPU. Image ghim tag và digest của `ollama/ollama:0.13.5`, `qdrant/qdrant:v1.15.5`. `models.lock.json` ghi đầy đủ digest đã kiểm thử; setup kiểm tra ID 12 ký tự do `ollama list` cung cấp và từ chối model khác lock. Model đã có không tự tải lại; `artifacts/qa/ai-models.txt` lưu ID của lần setup. Fingerprint embedding trong vector gồm tên và SHA256 digest đầy đủ do Ollama cung cấp (cache 30 giây).

Đổi model embedding/digest/chunker: tạo collection mới, đổi cấu hình, restart backend và chọn lập lại toàn bộ. Không trộn vector của hai mô hình; kết quả không khớp fingerprint bị bỏ qua. Model generation có thể đổi độc lập qua cấu hình sau khi chạy lại evaluation.

Một tác vụ Ollama đang chạy và tối đa ba tác vụ người dùng chờ; worker nhường khi có truy vấn chờ. Embedding giữ ấm tối đa 5 phút để tránh nạp lại khoảng 1 GiB trọng số cho từng đoạn ngắn; chat unload sau request. `OLLAMA_MAX_LOADED_MODELS=1` giới hạn chỉ một mô hình được nạp tại một thời điểm. CPU vẫn phải chuyển mô hình khi hỏi đáp, chậm hơn máy có GPU. Ollama giới hạn RAM 4 GiB, Qdrant 512 MiB, ứng dụng và các stack khác cần RAM riêng. Nếu OOM thì API trả lỗi có kiểm soát, không tự đổi sang dịch vụ cloud.

Trợ lý: 5 câu hỏi/phút/tài khoản bằng Redis, câu hỏi 2.000 ký tự, tối đa ba lượt lịch sử, tối đa 512 output token, context 4.096 token. Timeout mỗi lần gọi Ollama 120 giây; mỗi lần chờ capacity tối đa 120 giây. Một request tổng thể có thể lâu hơn timeout inference do gồm embedding/chờ/chat. Không tự retry sinh câu trả lời. Hủy trên trình duyệt không đảm bảo ngừng inference đang chạy ở server.

Chatbot nổi ở góc dưới bên phải website khi bật tính năng AI. Mục **Tìm khóa học** dùng Vector Search (dự phòng từ khóa), dùng được khi chưa đăng nhập. Mục **Hỏi khóa học** yêu cầu đăng nhập: tự nhận khóa trên trang `/courses/:id` và `/learn/:courseId/:lessonId`, ở trang khác có thể chọn khóa đã đăng ký hoặc chọn kết quả tìm kiếm. Hỏi đáp luôn giới hạn trong một khóa và quyền xem hiện có. Có nút thu gọn, phím Escape và giao diện vừa màn hình điện thoại; thu gọn vẫn giữ câu trả lời đang chạy.

Hội thoại ở `sessionStorage`, tối đa ba lượt, tách theo user/khóa, xóa khi đăng xuất hoặc bấm Xóa hội thoại; không đồng bộ thiết bị và không có bảng chat phía server. Chưa hỗ trợ PDF/DOCX, transcript, timecode video, hỏi đáp tổng hợp nhiều khóa hoặc agent thực hiện hành động.

## API

- `GET /api/ai/capabilities`: cờ `search`, `assistant`, công khai.
- `GET /api/search/courses?q=&categoryId=&offset=0&limit=12`: `{content, hasMore, offset, mode}`; offset 0–59, limit 1–24. `mode=hybrid|keyword`. API `/api/courses` giữ nguyên.
- `POST /api/courses/{id}/assistant`: JWT, body `{question, history:[{role:"user|assistant",content}]}`; lịch sử luân phiên user/assistant. Trả `{status,answer,sources:[{id,title,url,excerpt}]}`. `status=answered|insufficient_context`.
- `GET /api/admin/ai/index`: ADMIN, trạng thái tối đa 200 khóa, số backlog và job hết lượt thử.
- `POST /api/admin/ai/reindex?courseId=`: ADMIN, bỏ courseId để lập lại toàn bộ; trả 202.
- 400: dữ liệu không hợp lệ; 401/403: xác thực/quyền; 429: rate/capacity; 503: trợ lý tắt hoặc AI chưa sẵn sàng.

## Kiểm thử và giám sát

Kết quả cấu hình đã chạy, độ trễ và giới hạn tập đánh giá: [nghiệm thu AI local](../../docs/ai-evaluation.md).

```powershell
$env:JAVA_HOME='C:\Users\admin\.jdks\ms-17.0.20'
.\gradlew.bat test --no-daemon
node scripts/ai_test.cjs
node scripts/ai_eval.cjs
```

Các script tích hợp chỉ dùng với database local/demo: tạo nội dung QA và ẩn khóa sau khi hoàn thành. Tài khoản demo mặc định admin/student; mật khẩu quản trị khác dùng `AI_ADMIN_USER`/`AI_ADMIN_PASSWORD` trong environment, không ghi vào script. Evaluation gồm 30 truy vấn và 30 câu hỏi có nhãn; báo cáo JSON trong `artifacts/qa` để kiểm tra câu trả lời với nguồn. Rate limit được giữ nguyên nên evaluation có thể mất nhiều phút. Mục tiêu: Recall@5 ≥85%, trả lời đúng có nguồn ≥90%, từ chối câu thiếu tài liệu ≥90%; không dùng keyword check tự động để thay thế đánh giá thủ công.

Khi bật observability: dashboard **CodePath · AI Search & RAG** tại `/d/codepath-ai`, theo dõi latency, fallback, thiếu context, backlog, lỗi retry và lần thành công gần nhất. Hai cảnh báo chỉ bật khi worker được bật: backlog cũ >10 phút hoặc có job hết lượt thử, giữ điều kiện 1 phút. AI không được đưa vào healthcheck chính của backend. Không ghi prompt, context, answer vào log/trace; không bật HTTP body logging hoặc thêm tag nội dung/user vào metric.

Runbook khi cảnh báo: xem ADMIN để biết khóa lỗi, kiểm tra container Ollama/Qdrant và `docker stats`, xác nhận model đã tải/dung lượng đĩa, rồi thử lại khóa. Nếu model/schema vector đổi thì dùng collection mới và reindex. Không liên tục retry khi thiếu RAM/đĩa. Đây là bản local một host, chưa cung cấp HA hoặc SLA latency.

## Sao lưu, tái sử dụng và rollback

- Sao lưu MySQL như dữ liệu nghiệp vụ. Với Qdrant, dùng snapshot API hoặc dừng dịch vụ rồi sao lưu volume `qdrant_data`; không copy dữ liệu đang ghi tùy tiện. Ollama model có thể tải lại; không cần coi vector/model là nguồn dữ liệu duy nhất.
- Dùng cho ứng dụng khác: kết nối mạng AI, cấp API key phù hợp và đặt collection riêng. Môi trường cần cách ly quyền mạnh phải dùng instance/network và credential riêng, không chỉ đổi collection. Không dùng chung collection local và staging.
- Staging/Jenkins chưa được bật AI trong đợt này. Khi bổ sung phải cấp tài nguyên, key và collection riêng, không dùng volume local.
- Rollback: đặt ba cờ `AI_*_ENABLED=false` trong `.env`, chạy lại Compose ứng dụng với các override đang dùng. Có thể dừng Compose AI sau đó; giữ volume. Không xóa migration đã áp dụng. Đăng nhập, thanh toán, video và tìm từ khóa vẫn hoạt động.

Tài liệu nền: [Ollama embed](https://docs.ollama.com/api/embed), [Ollama chat](https://docs.ollama.com/api/chat), [Qdrant filtering](https://qdrant.tech/documentation/search/filtering/).
