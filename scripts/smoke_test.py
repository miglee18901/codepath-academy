"""Local integration checks. Creates QA courses and hides them afterwards.
Run: python scripts/smoke_test.py --local-webhook-key local-placeholder
Synthetic signed payment events are for the local placeholder PayOS configuration only.
"""
import argparse
import concurrent.futures
import hashlib
import hmac
import json
import time
import urllib.request
import urllib.error

parser = argparse.ArgumentParser()
parser.add_argument("--base", default="http://localhost:8080")
parser.add_argument("--local-webhook-key", help="Opt in to synthetic signed webhook tests against local configuration")
args = parser.parse_args()

def call(method, path, body=None, token=None, expected=200):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(args.base + path, data=json.dumps(body).encode() if body is not None else None, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, raw = error.code, error.read()
    assert status == expected, f"{method} {path}: expected {expected}, got {status}: {raw.decode()[:400]}"
    if not raw:
        return None
    try:
        return json.loads(raw)
    except ValueError:
        return raw

def login(username, password):
    return call("POST", "/api/auth/login", {"username": username, "password": password})["token"]

def sign(order, key, amount=None):
    data = {
        "orderCode": order["id"], "amount": int(order["total"]) if amount is None else amount,
        "description": "COURSE " + str(order["id"]), "accountNumber": "0000000000",
        "reference": "LOCAL-QA-" + str(order["id"]), "transactionDateTime": "2026-10-15 10:00:00",
        "currency": "VND", "paymentLinkId": "local-integration-test",
        "code": "00", "desc": "success", "counterAccountBankId": "", "counterAccountBankName": "",
        "counterAccountName": "", "counterAccountNumber": "", "virtualAccountName": "", "virtualAccountNumber": ""
    }
    text = "&".join(f"{key}={value}" for key, value in sorted(data.items()))
    signature = hmac.new(key.encode(), text.encode(), hashlib.sha256).hexdigest()
    return {"code": "00", "desc": "success", "success": True, "data": data, "signature": signature}

admin = login("admin", "123456")
student = login("student", "Learn123!")
assert len(call("GET", "/api/courses")) >= 3
call("GET", "/api/admin/stats", expected=401)
call("GET", "/api/admin/stats", token=student, expected=403)
call("POST", "/api/payments/webhook", {}, expected=400)
print("PASS: public catalog, authentication and admin authorization")

categories = call("GET", "/api/categories")
category = next((c for c in categories if c["name"] == "Kiểm thử hệ thống"), None)
if not category:
    category = call("POST", "/api/admin/categories", {"name": "Kiểm thử hệ thống"}, admin)
created = []
def course(price, title):
    body = {"categoryId": category["id"], "title": title, "description": "Nội dung kiểm thử tự động; tự ẩn sau kiểm thử.", "price": price, "instructor": "QA", "level": "Cơ bản", "published": False, "thumbnailUrl": ""}
    c = call("POST", "/api/admin/courses", body, admin)
    created.append((c["id"], body))
    call("GET", "/api/courses/" + str(c["id"]), expected=404)
    section = call("POST", f"/api/admin/courses/{c['id']}/sections", {"title": "Chương kiểm thử", "position": 0}, admin)
    ls = []
    for preview in (True, False):
        lesson = call("POST", f"/api/admin/sections/{section['id']}/lessons",
            {"title": "Xem thử" if preview else "Bài học riêng", "content": "PRIVATE-QA-CONTENT", "videoUrl": "", "durationMinutes": 10, "position": len(ls), "preview": preview}, admin)
        ls.append(lesson)
    call("GET", f"/api/lessons/{ls[0]['id']}", expected=403)
    body["published"] = True
    call("PUT", "/api/admin/courses/" + str(c["id"]), body, admin)
    detail = call("GET", "/api/courses/" + str(c["id"]))
    assert "PRIVATE-QA-CONTENT" not in json.dumps(detail)
    call("GET", f"/api/lessons/{ls[0]['id']}")
    call("GET", f"/api/lessons/{ls[1]['id']}", token=student, expected=403)
    call("PUT", f"/api/me/progress/{ls[1]['id']}", {"completed": True}, student, 403)
    return c, ls, body

try:
    free, free_lessons, _ = course(0, "QA · Khóa học miễn phí")
    order = call("POST", "/api/orders", {"courseId": free["id"], "amount": 0}, student)
    assert order["status"] == "PAID"
    call("POST", "/api/orders", {"courseId": free["id"]}, student, 400)
    call("GET", f"/api/lessons/{free_lessons[1]['id']}", token=student)
    for _ in range(2):
        call("PUT", f"/api/me/progress/{free_lessons[1]['id']}", {"completed": True}, student)
    library = call("GET", "/api/me/courses", token=student)
    assert next(c for c in library if c["id"] == free["id"])["completedLessons"] == 1
    print("PASS: free enrollment, private access and idempotent progress")

    paid, paid_lessons, body = course(100000, "QA · Khóa học trả phí")
    order = call("POST", "/api/orders", {"courseId": paid["id"], "amount": 1}, student)
    assert order["total"] == 100000 and order["status"] == "PENDING"
    again = call("POST", "/api/orders", {"courseId": paid["id"]}, student)
    assert again["id"] == order["id"]
    body["price"] = 250000
    call("PUT", "/api/admin/courses/" + str(paid["id"]), body, admin)
    assert call("GET", f"/api/orders/{order['id']}", token=student)["total"] == 100000
    other = call("POST", "/api/orders", {"courseId": paid["id"]}, admin)
    call("GET", f"/api/orders/{other['id']}", token=student, expected=403)
    call("POST", f"/api/orders/{other['id']}/checkout", {}, student, 403)
    print("PASS: server prices, immutable snapshots, order reuse and ownership")

    if args.local_webhook_key:
        wrong = sign(order, args.local_webhook_key, 1)
        call("POST", "/api/payments/webhook", wrong, expected=400)
        invalid = sign(order, args.local_webhook_key)
        invalid["signature"] = "invalid"
        call("POST", "/api/payments/webhook", invalid, expected=400)
        call("GET", f"/api/lessons/{paid_lessons[1]['id']}", token=student, expected=403)
        payload = sign(order, args.local_webhook_key)
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            list(pool.map(lambda _: call("POST", "/api/payments/webhook", payload), range(4)))
        assert call("GET", f"/api/orders/{order['id']}", token=student)["status"] == "PAID"
        call("GET", f"/api/lessons/{paid_lessons[1]['id']}", token=student)
        library = call("GET", "/api/me/courses", token=student)
        assert sum(c["id"] == paid["id"] for c in library) == 1
        print("PASS: signed webhook, amount validation and concurrent duplicate delivery")
    assert call("GET", "/api/admin/orders/export", token=admin)[:2] == b"PK"
    print("PASS: Excel order export")
finally:
    for course_id, body in created:
        body["published"] = False
        call("PUT", "/api/admin/courses/" + str(course_id), body, admin)
print("All local integration checks passed. QA courses are hidden.")
