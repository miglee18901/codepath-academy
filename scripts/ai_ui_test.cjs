const assert=require('node:assert/strict');
const fs=require('node:fs');
const {chromium}=require('../.qa/node_modules/playwright');
const base=process.env.WEB_URL||'http://localhost:5173';
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 try{
  const context=await browser.newContext({viewport:{width:1440,height:1000}});
  const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
  const login=await context.request.post(base+'/api/auth/login',{data:{username:'student',password:'Learn123!'}});
  assert.equal(login.status(),200);const auth=await login.json();
  await page.goto(base);await page.evaluate(auth=>{
   localStorage.setItem('token',auth.token);localStorage.setItem('refreshToken',auth.refreshToken);localStorage.setItem('user',JSON.stringify(auth));
  },auth);
  const requests=[];
  await page.route('**/api/courses/1/assistant',async route=>{
   requests.push(route.request().postDataJSON());
   await route.fulfill({json:{status:'answered',answer:'Nội dung tham khảo được hiển thị an toàn. [S1] <img src=x onerror="window.aiXss=true">',
    sources:[{id:'S1',title:'Nguồn bài học',url:'/learn/1/1',excerpt:'Đoạn trích để đối chiếu.'}]}});
  });
  await page.goto(base+'/courses/1');
  await page.getByRole('button',{name:'Mở chatbot',exact:true}).click();
  const assistant=page.getByRole('region',{name:'Trợ lý học tập'});
  await assistant.getByLabel('Câu hỏi về khóa học').fill('Khóa học có nội dung gì?');
  await assistant.getByRole('button',{name:'Gửi câu hỏi'}).click();
  await assistant.getByRole('link',{name:'[S1] Nguồn bài học'}).waitFor();
  assert.equal(await page.evaluate(()=>window.aiXss),undefined);
  assert.equal(await assistant.locator('img').count(),0);
  assert.deepEqual(requests[0].history,[]);
  await page.getByRole('button',{name:'Đóng chatbot'}).click();
  await page.getByRole('button',{name:'Mở chatbot',exact:true}).click();
  await assistant.getByText('Khóa học có nội dung gì?',{exact:true}).waitFor();
  await page.reload();
  await page.getByRole('button',{name:'Mở chatbot',exact:true}).click();
  await assistant.getByText('Khóa học có nội dung gì?',{exact:true}).waitFor();
  await assistant.getByLabel('Câu hỏi về khóa học').fill('Giải thích thêm nội dung đó.');
  await assistant.getByRole('button',{name:'Gửi câu hỏi'}).click();
  await assistant.getByText('Giải thích thêm nội dung đó.',{exact:true}).waitFor();
  assert.equal(requests[1].history.length,2);
  fs.mkdirSync('artifacts/qa',{recursive:true});
  await page.screenshot({path:'artifacts/qa/ai-assistant-desktop.png',fullPage:true});
  await page.setViewportSize({width:390,height:844});
  await page.screenshot({path:'artifacts/qa/ai-assistant-mobile.png',fullPage:true});
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Mobile horizontal overflow');
  await page.goto(base+'/courses/2');
  await page.getByRole('button',{name:'Mở chatbot',exact:true}).click();
  await assistant.getByLabel('Câu hỏi về khóa học').waitFor();
  assert.equal(await page.getByText('Khóa học có nội dung gì?',{exact:true}).count(),0);
  await page.getByRole('button',{name:'Đăng xuất',exact:true}).click();
  assert.equal(await page.evaluate(()=>Object.keys(sessionStorage).filter(k=>k.startsWith('codepath:assistant:')).length),0);
  const publicCourses=await (await context.request.get(base+'/api/courses?page=0&size=12')).json();
  let search;
  await page.route('**/api/search/courses?**',route=>{
   search=new URL(route.request().url());return route.fulfill({json:{content:publicCourses.content.slice(0,1),hasMore:false,offset:0,mode:'hybrid'}});
  });
  await page.goto(base+'/?q=tim%20theo%20y%20nghia&mode=smart');
  await page.getByLabel('Chế độ tìm kiếm').waitFor();
  await page.waitForFunction(()=>document.body.textContent.includes('Tối đa 60 kết quả phù hợp'));
  assert.equal(search.searchParams.get('q'),'tim theo y nghia');
  assert.equal(search.searchParams.get('limit'),'12');
  await page.getByRole('button',{name:'Mở chatbot',exact:true}).click();
  const chatbot=page.getByRole('dialog',{name:'Trợ lý CodePath'});
  await chatbot.getByRole('link',{name:'Đăng nhập',exact:true}).waitFor();
  assert.equal(await chatbot.getByRole('button',{name:'Gửi câu hỏi'}).count(),0);
  await chatbot.getByRole('button',{name:'Tìm khóa học',exact:true}).click();
  await chatbot.getByLabel('Bạn muốn học gì?').fill('học Java');
  await chatbot.getByRole('button',{name:'Tìm khóa phù hợp'}).click();
  await chatbot.getByRole('link',{name:publicCourses.content[0].title,exact:true}).waitFor();
  assert.equal(search.searchParams.get('q'),'học Java');
  assert.equal(search.searchParams.get('limit'),'5');
  await page.keyboard.press('Escape');
  await chatbot.waitFor({state:'hidden'});
  assert.equal(await chatbot.isVisible(),false);
  assert.equal(await page.getByRole('button',{name:'Mở chatbot',exact:true}).evaluate(el=>el===document.activeElement),true);
  // Signed-in users can select a search result outside a course page.
  await page.evaluate(auth=>{
   localStorage.setItem('token',auth.token);localStorage.setItem('refreshToken',auth.refreshToken);localStorage.setItem('user',JSON.stringify(auth));
  },auth);
  await page.goto(base);
  await page.getByRole('button',{name:'Mở chatbot',exact:true}).click();
  await chatbot.getByRole('button',{name:'Tìm khóa học',exact:true}).click();
  await chatbot.getByLabel('Bạn muốn học gì?').fill('học Java');
  await chatbot.getByRole('button',{name:'Tìm khóa phù hợp'}).click();
  await chatbot.getByRole('button',{name:'Hỏi về khóa này'}).click();
  await assistant.getByLabel('Câu hỏi về khóa học').waitFor();
  assert.equal(await chatbot.getByLabel('Khóa học đang hỏi').inputValue(),String(publicCourses.content[0].id));
  const bounds=await chatbot.boundingBox();
  assert(bounds.x>=0 && bounds.y>=0 && bounds.x+bounds.width<=390 && bounds.y+bounds.height<=844,'Widget outside mobile viewport');
  await page.screenshot({path:'artifacts/qa/ai-widget-mobile.png',fullPage:true});
  // Client-side navigation automatically changes the course context.
  await page.locator('.brand').first().click();
  const courseLink=page.locator(`main a[href="/courses/${publicCourses.content[0].id}"]`).first();
  await courseLink.click();
  await assistant.getByLabel('Câu hỏi về khóa học').waitFor();
  await chatbot.getByText('Đang hỗ trợ khóa học trên trang này.',{exact:true}).waitFor();
  assert.equal(await chatbot.getByLabel('Khóa học đang hỏi').count(),0);
  const adminLogin=await context.request.post(base+'/api/auth/login',{data:{username:process.env.AI_ADMIN_USER||'admin',password:process.env.AI_ADMIN_PASSWORD||'123456'}});
  assert.equal(adminLogin.status(),200);const admin=await adminLogin.json();
  await page.evaluate(auth=>{
   localStorage.setItem('token',auth.token);localStorage.setItem('refreshToken',auth.refreshToken);localStorage.setItem('user',JSON.stringify(auth));
  },admin);
  await page.goto(base+'/admin');
  await page.getByRole('button',{name:'Chỉ mục AI',exact:true}).click();
  await page.getByRole('heading',{name:'Chỉ mục AI',exact:true}).waitFor();
  await page.getByText('Hiển thị tối đa 200 khóa.',{exact:false}).waitFor();
  assert(await page.locator('tbody tr').count()>0);
  await page.getByRole('button',{name:'Cập nhật trạng thái',exact:true}).click();
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Admin mobile overflow');
  await page.screenshot({path:'artifacts/qa/ai-admin-mobile.png',fullPage:true});
  assert.deepEqual(errors,[]);
  console.log('PASS AI UI: floating widget, close/reopen, Escape/focus, public search, login gate, course selection/automatic context, citation/text safety, follow-up history, reload, isolation, logout, mobile, smart URL and admin index');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
