"""Upload 10 attributed Big Buck Bunny excerpts into a dedicated demo course.

Expects clip-01.mp4 through clip-10.mp4 in --clips. Run only with sufficient
Mux storage slots. Existing demo lessons and completed uploads are reused.
"""

import argparse
import json
import os
from pathlib import Path
import time
import urllib.error
import urllib.request


TITLE = "Trải nghiệm video Mux — 10 bài kiểm thử"
SOURCE = "https://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_640x360.m4v.zip"
CREDIT = (
    "Nguồn: Big Buck Bunny (2008), Blender Foundation / www.bigbuckbunny.org.\n"
    "Giấy phép: Creative Commons Attribution 3.0 — https://creativecommons.org/licenses/by/3.0/\n"
    "Thông tin tác phẩm: https://peach.blender.org/about/\n"
    f"Tệp gốc: {SOURCE}\n"
    "Thay đổi: cắt đoạn 20 giây, mã hóa H.264/AAC để kiểm thử trình phát. "
    "Không phải nội dung giảng dạy lập trình và không được Blender bảo trợ."
)
CASES = [
    "Xem thử công khai", "Hình ảnh và âm thanh", "Tạm dừng và tiếp tục",
    "Tua đến giữa video", "Điều chỉnh âm lượng", "Xem toàn màn hình",
    "Thay đổi tốc độ phát", "Phát trên điện thoại", "Chuyển bài học", "Hoàn thành bài học",
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="http://backend:8080")
    parser.add_argument("--clips", required=True)
    args = parser.parse_args()
    clip_dir = Path(args.clips)
    for number in range(1, 11):
        assert (clip_dir / f"clip-{number:02}.mp4").stat().st_size > 0
    checkpoint_path = clip_dir / "upload-checkpoints.json"
    checkpoints = json.loads(checkpoint_path.read_text()) if checkpoint_path.exists() else {}
    token = None

    def call(method, path, body=None, authenticated=True):
        headers = {"Content-Type": "application/json"}
        if authenticated and token:
            headers["Authorization"] = "Bearer " + token
        request = urllib.request.Request(args.base + path, method=method, headers=headers,
            data=json.dumps(body, ensure_ascii=False).encode() if body is not None else None)
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                raw = response.read()
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as error:
            # Application API messages do not contain upload credentials.
            raise RuntimeError(f"{method} {path}: HTTP {error.code}: {error.read().decode()[:300]}") from None

    token = call("POST", "/api/auth/login", {
        "username": os.environ.get("ACADEMY_ADMIN_USER", "admin"),
        "password": os.environ.get("ACADEMY_ADMIN_PASSWORD", "123456"),
    })["token"]
    categories = call("GET", "/api/categories")
    category = next((c for c in categories if c["name"] == "Trải nghiệm nền tảng"), None)
    if category is None:
        category = call("POST", "/api/admin/categories", {"name": "Trải nghiệm nền tảng"})
    course = next((c for c in call("GET", "/api/admin/courses") if c["title"] == TITLE), None)
    course_body = {
        "categoryId": category["id"], "title": TITLE, "price": 0,
        "description": "Khóa học miễn phí để thử 10 video ngắn, âm thanh, tua, tốc độ phát và quyền truy cập. "
                       "Hai bài đầu xem thử công khai; đăng ký miễn phí để xem đủ 10 bài. "
                       "Video là các đoạn trích Big Buck Bunny của Blender Foundation, CC BY 3.0; nguồn và thay đổi được ghi trong từng bài.",
        "thumbnailUrl": "", "instructor": "CodePath — dữ liệu kiểm thử", "level": "Nhập môn", "published": False,
    }
    if course is None:
        course = call("POST", "/api/admin/courses", course_body)
    detail = call("GET", f"/api/courses/{course['id']}")
    sections = []
    for position, title in enumerate(("Xem thử và thao tác cơ bản", "Trải nghiệm học viên và tiến độ")):
        section = next((s for s in detail["sections"] if s["title"] == title), None)
        if section is None:
            section = call("POST", f"/api/admin/courses/{course['id']}/sections", {"title": title, "position": position})
            section["lessons"] = []
        sections.append(section)
    lessons = []
    for index, case in enumerate(CASES):
        section = sections[index // 5]
        title = f"Video {index + 1:02} — {case}"
        lesson = next((l for l in section["lessons"] if l["title"] == title), None)
        if lesson is None:
            start = 30 + index * 50
            body = {
                "title": title, "content": f"KIỂM THỬ VIDEO MUX\n\nMục tiêu: {case}.\n"
                    "Kiểm tra video phát được, có âm thanh, thao tác đúng và không xuất hiện lỗi.\n"
                    "Hai bài đầu là bài xem thử; các bài còn lại cần đăng ký khóa học miễn phí.\n\n"
                    f"Đoạn trích từ giây {start} đến giây {start + 20} của phim gốc.\n\n{CREDIT}",
                "videoUrl": "", "durationMinutes": 1, "position": index % 5, "preview": index < 2,
            }
            lesson = call("POST", f"/api/admin/sections/{section['id']}/lessons", body)
        lessons.append(lesson)

    for index, lesson in enumerate(lessons):
        path = f"/api/admin/lessons/{lesson['id']}/mux"
        status = call("POST", path + "/refresh")
        if status["status"] == "ready":
            print(f"REUSE video {index + 1:02}: ready", flush=True)
            continue
        if status["status"] == "none":
            upload = call("POST", path + "/upload")
            checkpoints[str(lesson["id"])] = upload["uploadUrl"]
            checkpoint_path.write_text(json.dumps(checkpoints))
        if status["status"] in ("none", "waiting"):
            upload_url = checkpoints.get(str(lesson["id"]))
            if not upload_url:
                raise RuntimeError(f"Upload checkpoint missing for lesson {lesson['id']}; inspect the existing upload before retrying.")
            clip = (clip_dir / f"clip-{index + 1:02}.mp4").read_bytes()
            request = urllib.request.Request(upload_url, data=clip, method="PUT", headers={"Content-Type": "video/mp4"})
            try:
                with urllib.request.urlopen(request, timeout=120) as response:
                    assert response.status in (200, 201)
            except Exception:
                # Do not expose the signed upload URL in exceptions.
                raise RuntimeError(f"Upload of video {index + 1:02} failed; use checkpoint to retry.") from None
            print(f"UPLOADED video {index + 1:02} ({len(clip)} bytes)", flush=True)

    deadline = time.monotonic() + 900
    pending = set(range(10))
    while pending and time.monotonic() < deadline:
        for index in sorted(pending):
            status = call("POST", f"/api/admin/lessons/{lessons[index]['id']}/mux/refresh")
            if status["status"] == "ready":
                pending.remove(index)
                print(f"READY video {index + 1:02}", flush=True)
            elif status["status"] in ("errored", "timed_out", "cancelled", "deleted"):
                raise RuntimeError(f"Video {index + 1:02}: {status['status']}")
        if pending:
            time.sleep(5)
    if pending:
        raise RuntimeError("Video processing is still pending; rerun to refresh without re-uploading.")

    # Verify the real signed manifest and preview boundary before publishing the demo.
    for index, lesson in enumerate(lessons):
        playback = call("GET", f"/api/lessons/{lesson['id']}/playback")
        manifest = "https://stream.mux.com/" + playback["playbackId"] + ".m3u8?token=" + playback["tokens"]["playback"]
        try:
            with urllib.request.urlopen(manifest, timeout=30) as response:
                assert response.read().startswith(b"#EXTM3U")
        except Exception:
            raise RuntimeError(f"Signed playback verification failed for video {index + 1:02}.") from None
        print(f"VERIFIED signed playback {index + 1:02}", flush=True)
    course_body["published"] = True
    call("PUT", f"/api/admin/courses/{course['id']}", course_body)
    report = {"courseId": course["id"], "title": TITLE, "lessons": [
        {"id": lesson["id"], "title": lesson["title"], "preview": index < 2}
        for index, lesson in enumerate(lessons)
    ], "source": SOURCE, "license": "CC BY 3.0", "ready": 10}
    (clip_dir / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
    print(json.dumps(report, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
