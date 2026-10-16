// Local UI integration tests. OAuth handoff is mocked; no real provider credentials are used.
const {chromium} = require("../.qa/node_modules/playwright");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const fs = require("node:fs");
const base = "http://localhost:5173";

(async () => {
    const browser = await chromium.launch({headless: true,
        executablePath: process.env.CHROME_PATH || "C:/Program Files/Google/Chrome/Application/chrome.exe"});
    const context = await browser.newContext({viewport: {width: 1280, height: 1000}});
    const page = await context.newPage();
    page.setDefaultTimeout(15000);
    const errors = [];
    page.on("pageerror", e => errors.push(e.message));
    try {
        const request = context.request;
        const providersResponse = await request.get(base + "/api/auth/oauth2/providers");
        assert.equal(providersResponse.status(), 200);
        const providers = await providersResponse.json();
        assert.deepEqual(providers.map(p => p.id), ["google", "github"]);
        for (const provider of providers) assert.deepEqual(Object.keys(provider).sort(), ["enabled", "id", "name"]);
        assert.equal((await request.get(base + "/api/me/social-links")).status(), 401);
        assert.equal((await request.post(base + "/api/me/initial-password", {data: {password: "Test123456!"}})).status(), 401);
        assert.equal((await request.post(base + "/api/me/social-links/google", {data: {password: "test"}})).status(), 401);
        const unknown = await request.post(base + "/api/auth/oauth2/exchange", {
            data: {code: "A".repeat(43), verifier: "B".repeat(43)},
        });
        assert.equal(unknown.status(), 400);
        console.log("PASS: providers expose no credentials; management requires JWT; fabricated code is rejected");

        await page.goto(base + "/login");
        for (const provider of providers) {
            const disabled = await page.getByRole("button", {name: provider.name, exact: true}).isDisabled();
            assert.equal(disabled, !provider.enabled);
        }
        fs.mkdirSync("artifacts/qa", {recursive: true});
        await page.screenshot({path: "artifacts/qa/social-login-desktop.png", fullPage: true});
        await page.setViewportSize({width: 390, height: 844});
        await page.screenshot({path: "artifacts/qa/social-login-mobile.png", fullPage: true});
        assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
        console.log("PASS: provider button availability and mobile layout");

        const login = await request.post(base + "/api/auth/login", {data: {username: "student", password: "Learn123!"}});
        assert.equal(login.status(), 200);
        const auth = await login.json();
        const headers = {Authorization: "Bearer " + auth.token};
        assert.equal((await request.post(base + "/api/me/initial-password", {
            headers, data: {password: "NeverChangeExisting123!"},
        })).status(), 409);
        const me = await (await request.get(base + "/api/me", {headers})).json();
        assert.equal(me.passwordLoginEnabled, true);
        console.log("PASS: existing users keep password login; initial-password endpoint cannot reset their password");

        // Exercise the real React redirect/callback code without reaching external identity services.
        await page.route("**/api/auth/oauth2/providers", route => route.fulfill({json: providers.map(p => ({...p, enabled: true}))}));
        let observed, exchanges = 0, linkStarts = 0;
        await page.route("**/api/auth/oauth2/authorization/*?*", route => {
            observed = new URL(route.request().url());
            return route.fulfill({contentType: "text/html", body: "<h1>OAuth provider test fixture</h1>"});
        });
        await page.route("**/api/auth/oauth2/exchange", async route => {
            exchanges++;
            const body = route.request().postDataJSON();
            assert.equal(crypto.createHash("sha256").update(body.verifier).digest("base64url"), observed.searchParams.get("challenge"));
            assert.equal(body.code.length, 43);
            await route.fulfill({json: auth});
        });
        await page.goto(base + "/login?next=/orders");
        await page.getByRole("button", {name: "Google", exact: true}).click();
        await page.getByRole("heading", {name: "OAuth provider test fixture"}).waitFor();
        assert.ok(observed.pathname.endsWith("/google"));
        assert.equal(observed.searchParams.get("challenge").length, 43);
        assert.equal(observed.searchParams.has("verifier"), false);
        await page.goto(base + "/oauth/callback#code=" + crypto.randomBytes(32).toString("base64url"));
        await page.waitForURL("**/orders");
        assert.equal(exchanges, 1, "StrictMode must not exchange a code twice");
        assert.equal(await page.evaluate(() => sessionStorage.getItem("oauth_flow")), null);
        assert.equal(await page.evaluate(() => window.location.hash), "");
        assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem("user")).username), "student");
        console.log("PASS: React OAuth handoff binds verifier, consumes once under StrictMode, restores next and clears fragment");

        await page.route("**/api/me/social-links/github", route => {
            linkStarts++;
            assert.equal(route.request().postDataJSON().password, "Learn123!");
            return route.fulfill({json: {ticket: "T".repeat(43)}});
        });
        await page.goto(base + "/profile");
        await page.getByLabel("Mật khẩu hiện tại để liên kết").fill("Learn123!");
        await page.getByRole("button", {name: "Liên kết GitHub", exact: true}).click();
        await page.getByRole("heading", {name: "OAuth provider test fixture"}).waitFor();
        assert.equal(linkStarts, 1);
        assert.ok(observed.pathname.endsWith("/github"));
        assert.equal(observed.searchParams.get("link"), "T".repeat(43));
        await page.goto(base + "/oauth/callback#code=" + crypto.randomBytes(32).toString("base64url"));
        await page.waitForURL("**/profile");
        assert.equal(exchanges, 2);
        console.log("PASS: linking UI starts GitHub flow with server ticket and returns to profile");

        await page.goto(base + "/oauth/callback#error=social_email_exists");
        await page.getByText("Email này đã thuộc tài khoản hiện có.", {exact: false}).waitFor();
        assert.equal(exchanges, 2);
        assert.equal(await page.evaluate(() => location.hash), "");
        await page.getByRole("link", {name: "Quay lại đăng nhập"}).click();
        console.log("PASS: email collision displays actionable error without exchanging tokens");

        // Social-only accounts can see the initial password setup UI.
        await page.route("**/api/me", route => route.fulfill({json: {...me, passwordLoginEnabled: false}}));
        await page.goto(base + "/profile");
        await page.getByRole("heading", {name: "Thiết lập mật khẩu", exact: true}).waitFor();
        assert.equal(await page.getByRole("button", {name: "Thêm Passkey", exact: true}).count(), 0);
        await page.getByLabel("Mật khẩu mới (8–64 ký tự)", {exact: true}).fill("Test123456!");
        await page.getByLabel("Nhập lại mật khẩu mới", {exact: true}).fill("Different123!");
        await page.getByRole("button", {name: "Thiết lập mật khẩu", exact: true}).click();
        await page.getByText("Mật khẩu xác nhận chưa trùng khớp.", {exact: true}).waitFor();
        assert.deepEqual(errors, []);
        console.log("PASS: social-only account setup and password confirmation; no JavaScript errors");
    } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exitCode = 1; });
