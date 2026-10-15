"""Add a repeatable demonstration catalog through the academy admin API.

Run: python scripts/seed_courses.py
Override credentials with ACADEMY_ADMIN_USER and ACADEMY_ADMIN_PASSWORD.
Existing course metadata and lesson content are never overwritten.
"""

import argparse
import json
import os
import urllib.error
import urllib.request


# category, title, price (VND), level, four chapter topics
CATALOG = [
    ("Java & Backend", "Java Core: từ cú pháp đến ứng dụng console", 0, "Nhập môn", ["Biến và kiểu dữ liệu", "Điều kiện và vòng lặp", "Phương thức và mảng", "Ứng dụng quản lý công việc"]),
    ("Java & Backend", "Lập trình hướng đối tượng với Java", 499000, "Cơ bản", ["Lớp và đối tượng", "Đóng gói và kế thừa", "Interface và đa hình", "Thiết kế hệ thống thư viện"]),
    ("Java & Backend", "Spring Boot: xây dựng REST API thực tế", 1490000, "Trung cấp", ["Dependency Injection", "Controller và DTO", "Validation và xử lý lỗi", "Kiểm thử REST API"]),
    ("Java & Backend", "Spring Security và xác thực JWT", 1190000, "Nâng cao", ["Authentication và Authorization", "PasswordEncoder và đăng nhập", "Access token và refresh token", "Phân quyền và kiểm thử bảo mật"]),
    ("Java & Backend", "JPA và Hibernate: truy vấn và tối ưu dữ liệu", 1090000, "Trung cấp", ["Entity và ánh xạ bảng", "Quan hệ và vòng đời entity", "JPQL và phân trang", "Transaction và bài toán N+1"]),
    ("Frontend", "HTML và CSS: xây dựng website đầu tiên", 0, "Nhập môn", ["HTML ngữ nghĩa", "Box model và CSS selector", "Flexbox và Grid", "Thiết kế responsive"]),
    ("Frontend", "JavaScript: nền tảng cho lập trình web", 599000, "Cơ bản", ["Biến và hàm", "Array và Object", "DOM và sự kiện", "Promise và async await"]),
    ("Frontend", "TypeScript: xây dựng ứng dụng an toàn kiểu", 699000, "Trung cấp", ["Kiểu dữ liệu và inference", "Interface và type alias", "Generic và utility types", "Tích hợp TypeScript vào dự án"]),
    ("Frontend", "React nâng cao: kiến trúc và hiệu năng", 1290000, "Nâng cao", ["Thiết kế component", "Context và reducer", "Memoization và rendering", "Kiểm thử component"]),
    ("Frontend", "Next.js: xây dựng website full stack", 1590000, "Nâng cao", ["Routing và layout", "Server và client components", "Đọc và cập nhật dữ liệu", "SEO và triển khai"]),
    ("Python", "Python cho người mới bắt đầu", 0, "Nhập môn", ["Cú pháp và kiểu dữ liệu", "List và dictionary", "Hàm và module", "Đọc ghi tệp văn bản"]),
    ("Python", "Python tự động hóa công việc", 799000, "Trung cấp", ["Xử lý đường dẫn và tệp", "CSV và JSON", "Gọi HTTP API", "Lập lịch và ghi log"]),
    ("Python", "FastAPI: phát triển API với Python", 1090000, "Trung cấp", ["Routing và Pydantic", "Dependency và validation", "Kết nối cơ sở dữ liệu", "Kiểm thử và đóng gói API"]),
    ("Cơ sở dữ liệu", "SQL và MySQL cho lập trình viên", 599000, "Cơ bản", ["Bảng và khóa", "SELECT và JOIN", "GROUP BY và subquery", "Index và transaction"]),
    ("Cơ sở dữ liệu", "PostgreSQL: thiết kế và tối ưu truy vấn", 999000, "Trung cấp", ["Thiết kế schema", "CTE và window function", "EXPLAIN và index", "Khóa và tính nhất quán"]),
    ("Cơ sở dữ liệu", "Redis: caching và cấu trúc dữ liệu", 799000, "Trung cấp", ["String và thời gian hết hạn", "Hash Set và Sorted Set", "Cache aside và invalidation", "Rate limiting và vận hành"]),
    ("DevOps", "Git và GitHub: làm việc nhóm hiệu quả", 0, "Nhập môn", ["Commit và lịch sử", "Branch và merge", "Pull request và code review", "Giải quyết conflict"]),
    ("DevOps", "Docker và Docker Compose thực hành", 899000, "Trung cấp", ["Image và container", "Viết Dockerfile", "Network và volume", "Ứng dụng nhiều dịch vụ"]),
    ("DevOps", "CI/CD với GitHub Actions", 1090000, "Trung cấp", ["Workflow và trigger", "Build và kiểm thử", "Artifacts và biến môi trường", "Pipeline triển khai"]),
    ("Mobile", "Flutter: phát triển ứng dụng đa nền tảng", 1390000, "Cơ bản", ["Dart và null safety", "Widget và layout", "State và navigation", "HTTP API và lưu trữ local"]),
    ("Mobile", "React Native: xây dựng ứng dụng di động", 1290000, "Trung cấp", ["Component di động", "Điều hướng màn hình", "Form và HTTP API", "Lưu trữ và đóng gói"]),
    ("Kiểm thử phần mềm", "JUnit và Mockito cho Java Backend", 799000, "Trung cấp", ["Cấu trúc unit test", "Assertion và test case", "Mock và stub", "Kiểm thử service và controller"]),
    ("Kiểm thử phần mềm", "Playwright: tự động hóa kiểm thử giao diện", 999000, "Trung cấp", ["Locator và thao tác", "Assertion và auto waiting", "Fixture và dữ liệu kiểm thử", "Trace và chạy trên CI"]),
    ("Cơ sở lập trình", "Cấu trúc dữ liệu và giải thuật nền tảng", 899000, "Cơ bản", ["Độ phức tạp thuật toán", "Array Stack và Queue", "Tìm kiếm và sắp xếp", "Hash table và cây"]),
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="http://localhost:8080")
    args = parser.parse_args()
    token = None

    def call(method, path, body=None):
        headers = {"Content-Type": "application/json; charset=utf-8"}
        if token:
            headers["Authorization"] = "Bearer " + token
        request = urllib.request.Request(
            args.base.rstrip("/") + path,
            data=json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None,
            headers=headers,
            method=method,
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                raw = response.read()
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as error:
            raise RuntimeError(f"{method} {path}: HTTP {error.code}: {error.read().decode('utf-8')[:300]}") from None

    token = call("POST", "/api/auth/login", {
        "username": os.environ.get("ACADEMY_ADMIN_USER", "admin"),
        "password": os.environ.get("ACADEMY_ADMIN_PASSWORD", "123456"),
    })["token"]
    categories = {c["name"]: c["id"] for c in call("GET", "/api/categories")}
    existing = {}
    for course in call("GET", "/api/admin/courses"):
        existing.setdefault(course["title"], course)
    created = {"categories": 0, "courses": 0, "sections": 0, "lessons": 0}

    for category, title, price, level, topics in CATALOG:
        if category not in categories:
            categories[category] = call("POST", "/api/admin/categories", {"name": category})["id"]
            created["categories"] += 1
        body = {
            "categoryId": categories[category], "title": title, "price": price,
            "description": f"KHÓA HỌC MẪU để trải nghiệm CodePath. Lộ trình gồm: {'; '.join(topics)}. "
                           "Mỗi chương có mục tiêu, hướng dẫn và bài tập. Nội dung cần được biên soạn, thẩm định trước khi bán thực tế.",
            "instructor": "Đội ngũ CodePath", "level": level,
            "thumbnailUrl": "", "published": False,
        }
        is_new = title not in existing
        course = call("POST", "/api/admin/courses", body) if is_new else existing[title]
        created["courses"] += int(is_new)
        detail = call("GET", f"/api/courses/{course['id']}")
        sections = {s["title"]: s for s in detail["sections"]}
        for position, topic in enumerate(topics):
            section = sections.get(topic)
            if section is None:
                section = call("POST", f"/api/admin/courses/{course['id']}/sections", {"title": topic, "position": position})
                section["lessons"] = []
                created["sections"] += 1
            lesson_titles = {lesson["title"] for lesson in section["lessons"]}
            for index, label in enumerate(("Khái niệm và nguyên lý", "Thực hành có hướng dẫn", "Bài tập và tự đánh giá")):
                lesson_title = f"{topic}: {label.lower()}"
                if lesson_title in lesson_titles:
                    continue
                content = (
                    f"NỘI DUNG MINH HỌA — {title}\n\nChương {position + 1}: {topic}\nBài {index + 1}: {label}\n\n"
                    f"Mục tiêu\nGiải thích vai trò của {topic.lower()} và áp dụng vào một bài toán nhỏ.\n\n"
                    "Chuẩn bị\nTạo thư mục thực hành riêng, ghi lại đầu vào, kết quả mong đợi và các giả định.\n\n"
                    + (
                        f"Hướng dẫn\n1. Liệt kê ba khái niệm liên quan đến {topic.lower()}.\n"
                        "2. Diễn giải mỗi khái niệm bằng lời của bạn.\n3. Đưa ra một ví dụ sử dụng và một trường hợp không phù hợp.\n\n"
                        if index == 0 else
                        f"Thực hành\n1. Chọn một chức năng nhỏ có sử dụng {topic.lower()}.\n"
                        "2. Chia chức năng thành các bước và triển khai từng bước.\n3. Chạy với dữ liệu hợp lệ, dữ liệu rỗng và dữ liệu không hợp lệ.\n4. Ghi lại kết quả và sửa lỗi quan sát được.\n\n"
                        if index == 1 else
                        f"Bài tập\nXây dựng một ví dụ độc lập về {topic.lower()} mà không sao chép bài trước.\n"
                        "Tiêu chí tự đánh giá: kết quả đúng, xử lý đầu vào không hợp lệ, tên rõ ràng và có hướng dẫn chạy.\n"
                        "Mở rộng: tìm một hạn chế của lời giải và đề xuất cách cải thiện.\n\n"
                    )
                    + "Ghi chú: đây là dữ liệu mẫu phục vụ trải nghiệm hệ thống, chưa phải giáo trình hoàn chỉnh."
                )
                call("POST", f"/api/admin/sections/{section['id']}/lessons", {
                    "title": lesson_title, "content": content, "videoUrl": "",
                    "durationMinutes": (15, 25, 20)[index], "position": index,
                    "preview": position == 0 and index == 0,
                })
                created["lessons"] += 1
        if is_new or (not course["published"] and course.get("description") == body["description"]):
            body["published"] = True
            call("PUT", f"/api/admin/courses/{course['id']}", body)
        detail = call("GET", f"/api/courses/{course['id']}")
        for topic in topics:
            section = next(s for s in detail["sections"] if s["title"] == topic)
            assert len(section["lessons"]) >= 3, f"Incomplete section: {title} / {topic}"
        print(f"OK: {title}", flush=True)

    public = call("GET", "/api/courses")
    assert {row[1] for row in CATALOG}.issubset({c["title"] for c in public})
    print(json.dumps({"created": created, "public_courses": len(public)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
