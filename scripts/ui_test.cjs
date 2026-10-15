// Install local test tools: npm install --prefix .qa --no-save --package-lock=false playwright
const {chromium} = require("../.qa/node_modules/playwright");
const assert = require("node:assert/strict");
const fs = require("node:fs");
(async () => {
    const browser = await chromium.launch({
        headless: true,
        executablePath:
            process.env.CHROME_PATH ||
            "C:/Program Files/Google/Chrome/Application/chrome.exe",
    });
    const page = await browser.newPage({
        viewport: {width: 1440, height: 1100},
    });
    const failures = [];
    page.on("pageerror", (e) => failures.push(e.message));
    fs.mkdirSync("artifacts/qa", {recursive: true});
    try {
        await page.goto("http://localhost:5173");
        await page
            .getByRole("heading", {name: "Chọn hướng đi của bạn"})
            .waitFor();
        await page.locator(".course-card").first().waitFor();
        assert.ok(await page.locator(".course-card").count() >= 3);
        await page.screenshot({
            path: "artifacts/qa/catalog-desktop.png",
            fullPage: true,
        });
        await page.getByRole("textbox", {name: "Tìm khóa học"}).fill("Java");
        await page.getByRole("button", {name: "Tìm kiếm", exact: true}).click();
        await page.waitForFunction(
            () => document.querySelectorAll(".course-card").length >= 1 &&
                [...document.querySelectorAll(".course-card")].every((card) => card.textContent.includes("Java")),
        );
        await page.getByRole("link", {name: "Xem khóa học ↗"}).first().click();
        await page.getByRole("heading", {name: "Nội dung khóa học"}).waitFor();
        await page.locator(".lesson-link").first().click();
        await page.locator(".lesson-content").waitFor();
        assert.ok(
            (await page.locator(".lesson-content").innerText()).includes(
                "NỘI DUNG MINH HỌA",
            ),
        );
        console.log("PASS: catalog, search, detail and preview in browser");
        const catalogResponse = await page.request.get("http://localhost:8080/api/courses");
        const catalog = await catalogResponse.json();
        const detailResponse = await page.request.get("http://localhost:8080/api/courses/" + catalog[0].id);
        const detail = await detailResponse.json();
        const privateLesson = detail.sections.flatMap((section) => section.lessons).find((lesson) => !lesson.preview);
        const uploadEndpoint = `http://localhost:8080/api/admin/lessons/${privateLesson.id}/mux/upload`;
        assert.equal((await page.request.post(uploadEndpoint)).status(), 401);
        assert.equal((await page.request.get(`http://localhost:8080/api/lessons/${privateLesson.id}/playback`)).status(), 403);
        const adminLogin = await page.request.post("http://localhost:8080/api/auth/login", {data: {username: "admin", password: "123456"}});
        const adminAuth = {Authorization: "Bearer " + (await adminLogin.json()).token};
        const studentLogin = await page.request.post("http://localhost:8080/api/auth/login", {data: {username: "student", password: "Learn123!"}});
        const studentAuth = {Authorization: "Bearer " + (await studentLogin.json()).token};
        assert.equal((await page.request.post(uploadEndpoint, {headers: studentAuth})).status(), 403);
        const videoState = await page.request.post(`http://localhost:8080/api/admin/lessons/${privateLesson.id}/mux/refresh`, {headers: adminAuth});
        assert.equal(videoState.status(), 200);
        if (!(await videoState.json()).configured) {
            assert.equal((await page.request.post(uploadEndpoint, {headers: adminAuth})).status(), 503);
        }
        console.log("PASS: Mux API authorization and missing-configuration handling");
        await page.goto("http://localhost:5173/login");
        await page.getByRole("textbox", {name: "Tên đăng nhập"}).fill("student");
        await page.getByLabel("Mật khẩu", {exact: true}).fill("Learn123!");
        await page.getByRole("button", {name: "Đăng nhập →"}).click();
        await page.getByRole("heading", {name: "Khóa học của tôi"}).waitFor();
        await page.goto("http://localhost:5173/courses/3");
        await page.getByRole("heading", {name: "Nội dung khóa học"}).waitFor();
        const enter = page.getByRole("link", {name: "Vào học ngay →"});
        if (await enter.count()) await enter.click();
        else
            await page.getByRole("button", {name: "Đăng ký học miễn phí"}).click();
        await page.locator(".lesson-content").waitFor();
        const complete = page.getByRole("button", {
            name: /Đánh dấu hoàn thành|Đã hoàn thành/,
        });
        await complete.click();
        await page.waitForTimeout(500);
        await page.screenshot({
            path: "artifacts/qa/lesson-desktop.png",
            fullPage: true,
        });
        console.log("PASS: student login, free enrollment and learning");
        await page.getByRole("button", {name: "Đăng xuất"}).click();
        await page.goto("http://localhost:5173/login");
        await page.getByRole("textbox", {name: "Tên đăng nhập"}).fill("admin");
        await page.getByLabel("Mật khẩu", {exact: true}).fill("123456");
        await page.getByRole("button", {name: "Đăng nhập →"}).click();
        await page.getByRole("heading", {name: "Khóa học của tôi"}).waitFor();
        await page.getByRole("link", {name: "Quản trị", exact: true}).click();
        await page.getByRole("heading", {name: "Quản lý nền tảng"}).waitFor();
        await page.getByRole("button", {name: "Khóa học", exact: true}).click();
        await page.getByRole("link", {name: "Chỉnh sửa →"}).first().click();
        await page
            .getByRole("textbox", {name: "Tên khóa học", exact: true})
            .waitFor();
        assert.ok(
            (
                await page
                    .getByRole("textbox", {name: "Tên khóa học", exact: true})
                    .inputValue()
            ).length > 0,
        );
        await page.screenshot({
            path: "artifacts/qa/admin-editor.png",
            fullPage: true,
        });
        console.log("PASS: admin navigation and course editor");
        await page.locator(".section-editor").first().getByRole("button", {name: "Sửa", exact: true}).first().click();
        await page.getByRole("heading", {name: "Video Mux", exact: true}).waitFor();
        await page.getByText("Chưa kết nối dịch vụ video.", {exact: false}).waitFor();
        console.log("PASS: Mux configuration state in lesson editor");

        // Isolate upload UI verification from the real database and Mux account.
        let uploaded = false;
        await page.route("**/api/admin/lessons/*/mux/**", async (route) => {
            if (route.request().url().endsWith("/upload"))
                return route.fulfill({json: {uploadUrl: "https://mux-upload.example.test/video", status: "waiting"}});
            return route.fulfill({json: {configured: true, status: uploaded ? "ready" : "none", durationMinutes: 1}});
        });
        await page.route("https://mux-upload.example.test/**", async (route) => {
            uploaded = true;
            return route.fulfill({status: 200, headers: {"access-control-allow-origin": "*"}, body: ""});
        });
        await page.getByRole("button", {name: "Cập nhật trạng thái", exact: true}).click();
        await page.locator('.mux-upload input[type="file"]').setInputFiles({
            name: "fixture.mp4", mimeType: "video/mp4", buffer: Buffer.from("mock video fixture"),
        });
        await page.getByText("Video sẵn sàng", {exact: true}).waitFor();
        assert.equal(uploaded, true);
        await page.screenshot({path: "artifacts/qa/mux-upload.png", fullPage: true});
        console.log("PASS: chunked upload UI and ready state with mocked Mux transport");
        await page.unroute("**/api/admin/lessons/*/mux/**");
        await page.unroute("https://mux-upload.example.test/**");
        await page.setViewportSize({width: 390, height: 844});
        await page.goto("http://localhost:5173");
        await page.locator(".course-card").first().waitFor();
        assert.ok(
            await page.evaluate(
                () => document.documentElement.scrollWidth <= window.innerWidth,
            ),
        );
        await page.screenshot({
            path: "artifacts/qa/catalog-mobile.png",
            fullPage: true,
        });
        assert.deepEqual(failures, []);
        console.log("PASS: mobile layout and no browser JavaScript errors");
    } finally {
        await browser.close();
    }
})().catch((e) => {
    console.error(e);
    process.exit(1);
});
