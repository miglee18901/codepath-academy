"""Local OTP integration check. Requires Google test CAPTCHA key and Mailpit."""
import argparse
import json
import re
import time
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument("--base", default="http://localhost:8080")
parser.add_argument("--mail", default="http://localhost:8025")
args = parser.parse_args()
BASE = args.base
MAIL = args.mail
def request(url, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=json.dumps(body).encode() if body is not None else None, headers=headers)
    with urllib.request.urlopen(req, timeout=30) as response:
        return json.load(response)

username = "qa" + str(int(time.time()))
email = username + "@codepath.local"
password = "Long-password-for-OTP-123!"
request(BASE + "/api/auth/register/init", {"username": username, "fullName": "QA OTP Student", "email": email, "password": password, "role": "ADMIN", "recaptchaToken": "local-test-token"})
otp = None
for _ in range(15):
    messages = request(MAIL + "/api/v1/messages")
    for message in messages.get("messages", []):
        if any(recipient.get("Address") == email for recipient in message.get("To", [])):
            detail = request(MAIL + "/api/v1/message/" + message["ID"])
            codes = re.findall(r"<h2[^>]*>\s*([0-9]{6})\s*</h2>", detail.get("HTML", ""))
            if codes:
                otp = codes[0]
                break
    if otp:
        break
    time.sleep(1)
assert otp, "OTP was not delivered to Mailpit"
request(BASE + "/api/auth/register/verify", {"email": email, "otp": otp})
login = request(BASE + "/api/auth/login", {"username": username, "password": password})
assert login["role"] == "ROLE_STUDENT", "Public registration must never grant ADMIN"
profile = request(BASE + "/api/me", token=login["token"])
assert profile["email"] == email
print("PASS: Google test CAPTCHA, local OTP email, verification, long password login and forced STUDENT role")
