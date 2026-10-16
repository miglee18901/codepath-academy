// Local demo only. Install Playwright as documented in docs/passkeys.md.
const {chromium} = require("../.qa/node_modules/playwright");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const fs = require("node:fs");
const base = process.env.PASSKEY_TEST_URL || "http://localhost:5173";
if (new URL(base).hostname !== "localhost") throw new Error("Run against localhost demo only.");
const username = process.env.PASSKEY_TEST_USERNAME || "student";
const password = process.env.PASSKEY_TEST_PASSWORD || "Learn123!";
const label = "QA Passkey " + Date.now();

(async () => {
    const browser = await chromium.launch({headless: true,
        executablePath: process.env.CHROME_PATH || "C:/Program Files/Google/Chrome/Application/chrome.exe"});
    const context = await browser.newContext({viewport: {width: 1280, height: 1000}});
    const page = await context.newPage();
    page.setDefaultTimeout(20000);
    const errors = [];
    page.on("pageerror", e => errors.push(e.message));
    let token, credentialId;
    const request = context.request;
    const url = path => base + path;
    const headers = () => ({Authorization: "Bearer " + token});
    try {
        const cdp = await context.newCDPSession(page);
        await cdp.send("WebAuthn.enable");
        const {authenticatorId} = await cdp.send("WebAuthn.addVirtualAuthenticator", {options: {
            protocol: "ctap2", transport: "internal", hasResidentKey: true, hasUserVerification: true,
            isUserVerified: true, automaticPresenceSimulation: true,
        }});
        assert.equal((await request.get(url("/api/me/passkeys"))).status(), 401);
        assert.equal((await request.post(url("/api/me/passkeys/options"), {
            data: {label, password},
        })).status(), 401);
        console.log("PASS: anonymous users cannot manage Passkeys");

        await page.goto(url("/login"));
        await page.getByLabel("Tên đăng nhập", {exact: true}).fill(username);
        await page.getByLabel("Mật khẩu", {exact: true}).fill(password);
        await page.getByRole("button", {name: "Đăng nhập →", exact: true}).click();
        await page.waitForURL("**/my-courses");
        token = await page.evaluate(() => localStorage.getItem("token"));
        await page.goto(url("/profile"));
        await page.getByRole("heading", {name: "Passkeys", exact: true}).waitFor();
        assert.equal((await request.post(url("/api/me/passkeys/options"), {
            headers: headers(), data: {label, password: "incorrect"},
        })).status(), 400);

        await page.getByLabel("Tên Passkey", {exact: true}).fill(label);
        await page.getByLabel("Mật khẩu hiện tại để xác nhận").fill(password);
        const registration = page.waitForResponse(r => r.url().endsWith("/api/me/passkeys/verify"));
        await page.getByRole("button", {name: "Thêm Passkey", exact: true}).click();
        const registered = await registration;
        assert.equal(registered.status(), 200, await registered.text());
        await page.getByText("Đã thêm Passkey.", {exact: false}).waitFor();
        const list = await (await request.get(url("/api/me/passkeys"), {headers: headers()})).json();
        const entry = list.find(c => c.label === label);
        assert.ok(entry);
        credentialId = entry.id;
        assert.deepEqual(Object.keys(entry).sort(), ["createdAt", "id", "label", "lastUsedAt"]);
        assert.equal((await request.post(url("/api/me/passkeys/verify"), {
            headers: headers(), data: registered.request().postDataJSON(),
        })).status(), 400);
        assert.equal((await request.delete(url("/api/me/passkeys/" + credentialId), {
            headers: headers(), data: {password: "incorrect"},
        })).status(), 400);
        console.log("PASS: real WebAuthn registration, password recheck, safe listing and registration replay rejection");

        await page.evaluate(() => localStorage.clear());
        await page.goto(url("/login"));
        await page.getByRole("button", {name: "Đăng nhập bằng Passkey", exact: true}).click();
        await page.waitForURL("**/my-courses");
        token = await page.evaluate(() => localStorage.getItem("token"));
        assert.ok(token);
        const me = await request.get(url("/api/me"), {headers: headers()});
        assert.equal(me.status(), 200);
        assert.equal((await me.json()).username, username);
        const {credentials} = await cdp.send("WebAuthn.getCredentials", {authenticatorId});
        const key = credentials.find(c => c.rpId === "localhost");
        assert.ok(key && key.isResidentCredential);
        console.log("PASS: username-less browser login issues working JWT");

        // Sign actual assertions with the virtual authenticator's test-only key.
        // Negative tests preserve a valid signature so server checks for UV/origin/challenge are exercised.
        function assertion(challenge, changes = {}) {
            const clientDataJSON = Buffer.from(JSON.stringify({type: "webauthn.get", challenge,
                origin: base, crossOrigin: false, ...changes.client}));
            const counter = Buffer.alloc(4);
            counter.writeUInt32BE(changes.counter ?? key.signCount + 1);
            const authenticatorData = Buffer.concat([
                crypto.createHash("sha256").update(changes.rpId || "localhost").digest(),
                Buffer.from([changes.flags ?? 5]), counter,
            ]);
            const signature = crypto.sign("sha256", Buffer.concat([authenticatorData,
                crypto.createHash("sha256").update(clientDataJSON).digest()]),
                crypto.createPrivateKey({key: Buffer.from(key.privateKey, "base64"), format: "der", type: "pkcs8"}));
            if (changes.badSignature) signature[signature.length - 1] ^= 1;
            return JSON.stringify({id: Buffer.from(key.credentialId, "base64").toString("base64url"),
                rawId: Buffer.from(key.credentialId, "base64").toString("base64url"), type: "public-key",
                response: {clientDataJSON: clientDataJSON.toString("base64url"),
                    authenticatorData: authenticatorData.toString("base64url"), signature: signature.toString("base64url"),
                    userHandle: Buffer.from(key.userHandle, "base64").toString("base64url")}, clientExtensionResults: {}});
        }
        async function verify(changes = {}) {
            const response = await request.post(url("/api/auth/passkeys/options"));
            assert.equal(response.status(), 200);
            const {requestId, options} = await response.json();
            const data = {requestId, credential: assertion(options.publicKey.challenge, changes)};
            return {data, response: await request.post(url("/api/auth/passkeys/verify"), {data})};
        }
        for (const [name, changes] of [
            ["missing user verification", {flags: 1}],
            ["missing user presence", {flags: 4}],
            ["stale signature counter", {counter: key.signCount}],
            ["wrong origin", {client: {origin: "https://attacker.example"}}],
            ["wrong challenge", {client: {challenge: crypto.randomBytes(32).toString("base64url")}}],
            ["wrong RP ID", {rpId: "attacker.example"}],
            ["invalid signature", {badSignature: true}],
        ]) {
            assert.equal((await verify(changes)).response.status(), 400, name);
            console.log("PASS: rejects " + name);
        }
        const valid = await verify();
        assert.equal(valid.response.status(), 200, await valid.response.text());
        const auth = await valid.response.json();
        assert.ok(auth.token && auth.refreshToken);
        assert.equal((await request.post(url("/api/auth/passkeys/verify"), {data: valid.data})).status(), 400);
        const refreshed = await request.post(url("/api/auth/refresh"), {data: {refreshToken: auth.refreshToken}});
        assert.equal(refreshed.status(), 200);
        token = (await refreshed.json()).token;
        assert.equal((await request.post(url("/api/auth/refresh"), {data: {refreshToken: auth.refreshToken}})).status(), 401);
        console.log("PASS: signed assertion succeeds, replay rejected, refresh token rotates");

        // A second demo account cannot revoke the student's credential.
        const other = await request.post(url("/api/auth/login"), {data: {username: "admin", password: "123456"}});
        assert.equal(other.status(), 200);
        assert.equal((await request.delete(url("/api/me/passkeys/" + credentialId), {
            headers: {Authorization: "Bearer " + (await other.json()).token}, data: {password: "123456"},
        })).status(), 404);
        console.log("PASS: another account cannot delete the credential");

        await page.goto(url("/profile"));
        const row = page.locator(".profile-row").filter({hasText: label});
        await row.getByRole("button", {name: "Xóa", exact: true}).click();
        await page.getByLabel("Mật khẩu hiện tại để xác nhận").fill(password);
        await page.getByRole("button", {name: "Xác nhận xóa Passkey", exact: true}).click();
        await page.getByText("Đã xóa Passkey khỏi tài khoản.", {exact: false}).waitFor();
        assert.equal((await verify()).response.status(), 400);
        credentialId = null;
        fs.mkdirSync("artifacts/qa", {recursive: true});
        await page.screenshot({path: "artifacts/qa/passkey-profile.png", fullPage: true});
        await page.setViewportSize({width: 390, height: 844});
        await page.screenshot({path: "artifacts/qa/passkey-profile-mobile.png", fullPage: true});
        assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth));
        assert.deepEqual(errors, []);
        console.log("PASS: UI revocation prevents future login; desktop/mobile have no JS errors or horizontal overflow");
    } finally {
        if (credentialId && token) {
            const cleanup = await request.delete(url("/api/me/passkeys/" + credentialId), {
                headers: headers(), data: {password},
            });
            if (!cleanup.ok()) console.error("Cleanup failed; remove test Passkey from the profile.");
        }
        await browser.close();
    }
})().catch(e => { console.error(e); process.exitCode = 1; });
