const assert=require('node:assert/strict');
const fs=require('node:fs');
const {chromium}=require('../.qa/node_modules/playwright');
const base=process.env.WEB_URL||'http://localhost:5173';
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 try {
  const context=await browser.newContext({viewport:{width:1440,height:1000}});
  const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
  async function login(username,password) {
   const r=await context.request.post(base+'/api/auth/login',{data:{username,password}});assert.equal(r.status(),200);
   const auth=await r.json();await page.goto(base);await page.evaluate(auth=>{localStorage.setItem('token',auth.token);localStorage.setItem('refreshToken',auth.refreshToken);localStorage.setItem('user',JSON.stringify(auth));},auth);return auth;
  }
  await login('student','Learn123!');await page.goto(base+'/notifications');
  await page.getByRole('heading',{name:'Thông báo',exact:true}).waitFor();
  await page.getByRole('heading',{name:'Đăng ký khóa học thành công',exact:true}).first().waitFor();
  await Promise.all([page.waitForResponse(r=>r.url().includes('/notifications/unread-count')&&r.status()===200),
      page.getByRole('button',{name:'Đánh dấu tất cả đã đọc'}).click()]);
  await page.getByRole('link',{name:'Thông báo',exact:true}).waitFor();
  await page.setViewportSize({width:390,height:844});
  await page.screenshot({path:'artifacts/qa/kafka-notifications-mobile.png',fullPage:true});
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'Notifications overflow');
  await page.getByRole('button',{name:'Đăng xuất',exact:true}).click();
  await page.waitForURL(base+'/');
  await page.getByRole('link',{name:/Thông báo/}).waitFor({state:'hidden'});
  assert.equal(await page.getByRole('link',{name:/Thông báo/}).count(),0);
  await page.goto(base+'/notifications');await page.waitForURL(/login/);
  await login('admin','123456');await page.goto(base+'/admin');
  await page.getByRole('button',{name:'Báo cáo',exact:true}).click();
  await page.getByRole('heading',{name:'Báo cáo doanh thu và học tập'}).waitFor();
  await page.getByLabel('Nhóm theo').selectOption('courses');
  await page.getByRole('columnheader',{name:'Khóa học',exact:true}).waitFor();
  await page.getByRole('button',{name:'Làm mới',exact:true}).click();
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'Reports overflow');
  await page.screenshot({path:'artifacts/qa/kafka-reports-mobile.png',fullPage:true});
  await page.getByRole('button',{name:'Sự kiện',exact:true}).click();
  await page.getByRole('heading',{name:'Vận hành sự kiện'}).waitFor();
  await page.getByText('Outbox chờ gửi:',{exact:false}).waitFor();
  await page.getByRole('heading',{name:'Email gửi thất bại'}).waitFor();
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'Events overflow');
  await page.screenshot({path:'artifacts/qa/kafka-events-mobile.png',fullPage:true});
  assert.deepEqual(errors,[]);console.log('PASS Kafka UI: real notifications, read-all, bell, logout/login gate, admin reports/grouping, event operations, mobile overflow');
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
