// Uses the same local Playwright installation and Chrome as scripts/ui_test.cjs.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const {chromium} = require("../.qa/node_modules/playwright");
const base = process.env.TEST_BASE_URL || "http://localhost:5173";
const skip = {"ngrok-skip-browser-warning": "true"};

async function get(path, headers = {}) {
    const r = await fetch(base + path, {headers: {...skip, ...headers}, signal: AbortSignal.timeout(30000)});
    assert.equal(r.status, 200, path);
    return r.json();
}

(async () => {
    const all = await get("/api/courses");
    assert.ok(Array.isArray(all));
    const combinations = ["", "q=Java", "q=%25", "q=_", "q=!", "categoryId=" + all[0].categoryId];
    for (const filters of combinations) {
        const expected = await get("/api/courses?" + filters);
        const collected = [];
        const first = await get("/api/courses?" + filters + "&page=0&size=5");
        assert.equal(first.totalElements, expected.length);
        assert.equal(first.totalPages, Math.ceil(expected.length / 5));
        collected.push(...first.content);
        for (let page = 1; page < first.totalPages; page++) {
            const result = await get("/api/courses?" + filters + "&page=" + page + "&size=5");
            collected.push(...result.content);
        }
        assert.deepEqual(collected.map(c => c.id), expected.map(c => c.id));
    }
    for (const query of ["page=-1", "page=0&size=0", "page=0&size=49"]) {
        assert.equal((await fetch(base + "/api/courses?" + query, {headers: skip})).status, 400);
    }
    assert.deepEqual((await get("/api/courses?page=9999")).content, []);
    console.log("PASS API: pages, counts, order, filters, literal wildcard characters, validation and legacy response");

    const browser = await chromium.launch({headless: true,
        executablePath: process.env.CHROME_PATH || "C:/Program Files/Google/Chrome/Application/chrome.exe"});
    fs.mkdirSync("artifacts/qa", {recursive: true});
    try {
        const page = await browser.newPage({viewport: {width: 1440, height: 1000}});
        const errors = [];
        page.on("pageerror", e => errors.push(e.message));
        await page.goto(base);
        const visit = page.getByRole("button", {name: /Visit Site/i});
        await visit.or(page.locator(".course-card").first()).first().waitFor();
        if (await visit.isVisible()) await visit.click();
        await page.locator(".course-card").first().waitFor();
        assert.equal(await page.locator(".course-card").count(), Math.min(12, all.length));
        await page.getByRole("button", {name: "Trang sau", exact: true}).click();
        await page.waitForFunction(() => document.querySelector('.pagination [aria-current="page"]')?.textContent === "2");
        const ids = await page.locator(".course-card h3 a").evaluateAll(es => es.map(e => Number(e.getAttribute("href").split("/").pop())));
        assert.deepEqual(ids, all.slice(12, 24).map(c => c.id));
        await page.reload();
        await page.getByRole("button", {name: "Trang 2", exact: true}).waitFor();
        await page.getByRole("link", {name: "Xem khóa học ↗"}).first().click();
        await page.getByRole("heading", {name: "Nội dung khóa học"}).waitFor();
        await page.goBack();
        await page.waitForFunction(() => document.querySelector('.pagination [aria-current="page"]')?.textContent === "2");
        await page.getByRole("textbox", {name: "Tìm khóa học"}).fill("Java");
        await Promise.all([
            page.waitForResponse(r => new URL(r.url()).pathname === "/api/courses"
                && new URL(r.url()).searchParams.get("q") === "Java"),
            page.getByRole("button", {name: "Tìm kiếm", exact: true}).click(),
        ]);
        assert.equal(new URL(page.url()).searchParams.get("page"), null);
        assert.equal(new URL(page.url()).searchParams.get("q"), "Java");
        const javaCourses = await get("/api/courses?q=Java");
        const categories = await get("/api/categories");
        const category = categories.find(c => c.id === javaCourses[0].categoryId);
        await Promise.all([
            page.waitForResponse(r => new URL(r.url()).pathname === "/api/courses"
                && new URL(r.url()).searchParams.get("categoryId") === String(category.id)),
            page.getByRole("button", {name: category.name, exact: true}).click(),
        ]);
        assert.equal(new URL(page.url()).searchParams.get("q"), "Java");
        assert.equal(new URL(page.url()).searchParams.get("page"), null);
        assert.equal(new URL(page.url()).searchParams.get("categoryId"), String(category.id));
        await page.getByRole("textbox", {name: "Tìm khóa học"}).fill("no-course-matches-987654321");
        await page.getByRole("button", {name: "Tìm kiếm", exact: true}).click();
        await page.getByText("Chưa có khóa học phù hợp.", {exact: false}).waitFor();
        assert.equal(await page.getByRole("navigation", {name: "Phân trang khóa học"}).count(), 0);
        await page.goto(base + "/?page=9999");
        await page.waitForURL(url => url.searchParams.get("page") === String(Math.ceil(all.length / 12)));
        await page.getByRole("navigation", {name: "Phân trang khóa học"}).waitFor();
        await page.setViewportSize({width: 390, height: 844});
        await page.getByRole("navigation", {name: "Phân trang khóa học"}).scrollIntoViewIfNeeded();
        assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
        await page.screenshot({path: "artifacts/qa/course-pagination-mobile.png"});
        console.log("PASS browser: next page, refresh/back, search reset, empty results, invalid-page recovery and mobile layout");

        await page.setViewportSize({width: 1440, height: 1000});
        await page.goto(base + "/login");
        await page.getByRole("textbox", {name: "Tên đăng nhập"}).fill("admin");
        await page.getByLabel("Mật khẩu", {exact: true}).fill("123456");
        await page.getByRole("button", {name: "Đăng nhập →"}).click();
        await page.getByRole("heading", {name: "Khóa học của tôi"}).waitFor();
        await page.getByRole("link", {name: "Quản trị", exact: true}).click();
        await page.getByRole("button", {name: "Khóa học", exact: true}).click();
        await page.locator("tbody tr").first().waitFor();
        assert.equal(await page.locator("tbody tr").count(), 12);
        await page.getByRole("button", {name: "Trang sau", exact: true}).click();
        await page.waitForFunction(() => document.querySelector('.pagination [aria-current="page"]')?.textContent === "2");
        assert.equal(await page.locator("tbody tr").count(), 12);
        const token = await page.evaluate(() => localStorage.getItem("token"));
        const headers = {Authorization: "Bearer " + token};
        const adminAll = await get("/api/admin/courses", headers);
        const adminPage = await get("/api/admin/courses?page=1&size=12", headers);
        assert.equal(adminPage.totalElements, adminAll.length);
        assert.deepEqual(adminPage.content.map(c => c.id), adminAll.slice(12, 24).map(c => c.id));
        assert.deepEqual(errors, []);
        console.log("PASS admin: paged table and correct total including drafts; no browser JavaScript errors");
    } finally {
        await browser.close();
    }
})().catch(e => {console.error(e); process.exit(1);});
