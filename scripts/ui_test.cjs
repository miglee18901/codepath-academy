// Install local test tools: npm install --prefix .qa --no-save --package-lock=false playwright
const { chromium } = require("../.qa/node_modules/playwright");
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
    viewport: { width: 1440, height: 1100 },
  });
  const failures = [];
  page.on("pageerror", (e) => failures.push(e.message));
  fs.mkdirSync("artifacts/qa", { recursive: true });
  try {
    await page.goto("http://localhost:5173");
    await page
      .getByRole("heading", { name: "Chọn hướng đi của bạn" })
      .waitFor();
    await page.locator(".course-card").first().waitFor();
    assert.equal(await page.locator(".course-card").count(), 3);
    await page.screenshot({
      path: "artifacts/qa/catalog-desktop.png",
      fullPage: true,
    });
    await page.getByRole("textbox", { name: "Tìm khóa học" }).fill("Java");
    await page.getByRole("button", { name: "Tìm kiếm", exact: true }).click();
    await page.waitForFunction(
      () => document.querySelectorAll(".course-card").length === 1,
    );
    await page.getByRole("link", { name: "Xem khóa học ↗" }).click();
    await page.getByRole("heading", { name: "Nội dung khóa học" }).waitFor();
    await page.locator(".lesson-link").first().click();
    await page.locator(".lesson-content").waitFor();
    assert.ok(
      (await page.locator(".lesson-content").innerText()).includes(
        "NỘI DUNG MINH HỌA",
      ),
    );
    console.log("PASS: catalog, search, detail and preview in browser");
    await page.goto("http://localhost:5173/login");
    await page.getByRole("textbox", { name: "Tên đăng nhập" }).fill("student");
    await page.getByLabel("Mật khẩu", { exact: true }).fill("Learn123!");
    await page.getByRole("button", { name: "Đăng nhập →" }).click();
    await page.getByRole("heading", { name: "Khóa học của tôi" }).waitFor();
    await page.goto("http://localhost:5173/courses/3");
    const enter = page.getByRole("link", { name: "Vào học ngay →" });
    if (await enter.count()) await enter.click();
    else
      await page.getByRole("button", { name: "Đăng ký học miễn phí" }).click();
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
    await page.getByRole("button", { name: "Đăng xuất" }).click();
    await page.goto("http://localhost:5173/login");
    await page.getByRole("textbox", { name: "Tên đăng nhập" }).fill("admin");
    await page.getByLabel("Mật khẩu", { exact: true }).fill("123456");
    await page.getByRole("button", { name: "Đăng nhập →" }).click();
    await page.getByRole("heading", { name: "Khóa học của tôi" }).waitFor();
    await page.getByRole("link", { name: "Quản trị", exact: true }).click();
    await page.getByRole("heading", { name: "Quản lý nền tảng" }).waitFor();
    await page.getByRole("button", { name: "Khóa học", exact: true }).click();
    await page.getByRole("link", { name: "Chỉnh sửa →" }).first().click();
    await page
      .getByRole("textbox", { name: "Tên khóa học", exact: true })
      .waitFor();
    assert.ok(
      (
        await page
          .getByRole("textbox", { name: "Tên khóa học", exact: true })
          .inputValue()
      ).length > 0,
    );
    await page.screenshot({
      path: "artifacts/qa/admin-editor.png",
      fullPage: true,
    });
    console.log("PASS: admin navigation and course editor");
    await page.setViewportSize({ width: 390, height: 844 });
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
