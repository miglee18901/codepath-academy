// Local/demo only. 30 labeled search queries and 30 RAG questions.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {setTimeout:sleep}=require('node:timers/promises');
const fixtures=require('./ai-fixtures.json');
const base=process.env.APP_URL||'http://127.0.0.1:8080';
let admin,student,adminRefresh,studentRefresh;
const created=[];
const report={search:[],rag:[],startedAt:new Date().toISOString()};
let stopping=false;
process.once('SIGINT',()=>{stopping=true;console.log('Stopping after active request; QA courses will be hidden.');});
function checkStop(){if(stopping)throw Error('Evaluation stopped');}
async function api(path,method='GET',body,token=admin,refreshed=false){
 const response=await fetch(base+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},
  ...(body===undefined?{}:{body:JSON.stringify(body)}),signal:AbortSignal.timeout(300000)});
 if(response.status===401&&!refreshed&&!path.startsWith('/api/auth/')){
  const isAdmin=token===admin;
  const refreshToken=isAdmin?adminRefresh:studentRefresh;
  if(refreshToken){
   const refresh=await fetch(base+'/api/auth/refresh',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({refreshToken}),signal:AbortSignal.timeout(10000)});
   if(refresh.ok){const auth=await refresh.json();if(isAdmin){admin=auth.token;adminRefresh=auth.refreshToken;}else{student=auth.token;studentRefresh=auth.refreshToken;}
    return api(path,method,body,auth.token,true);}
  }
 }
 const raw=await response.text();
 return {status:response.status,data:raw?JSON.parse(raw):null};
}
function save(){fs.mkdirSync('artifacts/qa',{recursive:true});fs.writeFileSync('artifacts/qa/ai-evaluation.json',JSON.stringify(report,null,2));}
(async()=>{
 const adminAuth=(await api('/api/auth/login','POST',{username:process.env.AI_ADMIN_USER||'admin',password:process.env.AI_ADMIN_PASSWORD||'123456'},null)).data;
 admin=adminAuth.token;adminRefresh=adminAuth.refreshToken;
 const studentAuth=(await api('/api/auth/login','POST',{username:'student',password:'Learn123!'},null)).data;
 student=studentAuth.token;studentRefresh=studentAuth.refreshToken;
 assert(admin&&student);
 const category=(await api('/api/categories')).data[0].id;
 try{
  for(const fixture of fixtures){
   checkStop();
   const input={categoryId:category,title:'QA EVAL '+fixture.title,description:fixture.text,price:0,thumbnailUrl:'',instructor:'QA',level:'BEGINNER',published:true};
   if(process.argv.includes('--reuse-fixtures')){
    const existing=(await api('/api/admin/courses')).data.filter(c=>c.title===input.title).sort((a,b)=>b.id-a.id)[0];
    if(existing){
     created.push({id:existing.id,input,fixture});
     if(!existing.published)assert.equal((await api('/api/admin/courses/'+existing.id,'PUT',input)).status,200);
     continue;
    }
   }
   const course=await api('/api/admin/courses','POST',input);assert.equal(course.status,200);
   const id=course.data.id;created.push({id,input,fixture});
   const section=await api(`/api/admin/courses/${id}/sections`,'POST',{title:fixture.title,position:0});assert.equal(section.status,200);
   const lesson=await api(`/api/admin/sections/${section.data.id}/lessons`,'POST',{title:fixture.title,content:fixture.text,videoUrl:'',preview:true,durationMinutes:3,position:0});assert.equal(lesson.status,200);
  }
  const deadline=Date.now()+900000;
  while(true){
   checkStop();
   const state=(await api('/api/admin/ai/index')).data;
   const jobs=state.jobs.filter(j=>created.some(c=>c.id===j.course_id));
   if(jobs.length===created.length&&jobs.every(j=>j.revision===j.indexed_revision))break;
   assert(!jobs.some(j=>j.attempts>=5),'Index exhausted retries');assert(Date.now()<deadline,'Index timeout');await sleep(3000);
  }
  console.log('Indexed all evaluation fixtures');
  for(const {id,fixture} of created)for(const query of fixture.queries){
   checkStop();
   const started=Date.now();const result=await api('/api/search/courses?q='+encodeURIComponent(query)+'&limit=5');assert.equal(result.status,200);
   report.search.push({query,expectedCourseId:id,top5:result.data.content.map(c=>c.id),hit:result.data.content.some(c=>c.id===id),mode:result.data.mode,ms:Date.now()-started});save();
  }
  console.log('Search Recall@5',report.search.filter(r=>r.hit).length+'/'+report.search.length);
  for(const {id,fixture} of created){
   const questions=[...fixture.questions.map(question=>({question:question+' Trả lời ngắn gọn một câu.',expected:'answered',reference:fixture.reference})),
    {question:'Mật khẩu tài khoản ngân hàng cá nhân của tôi là gì?',expected:'insufficient_context',reference:'Tài liệu không chứa thông tin cá nhân hoặc mật khẩu ngân hàng.'}];
   for(const item of questions){
    checkStop();
    let result,started;
    while(true){started=Date.now();result=await api(`/api/courses/${id}/assistant`,'POST',{question:item.question,history:[]},student);
     if(result.status!==429)break;console.log('Waiting for normal per-account rate limit');await sleep(61000);}
    assert.equal(result.status,200,JSON.stringify(result));
    const citationsValid=result.data.sources.every(s=>s.url.startsWith('/learn/'+id+'/')||s.url==='/courses/'+id);
    report.rag.push({...item,courseId:id,actual:result.data,expectedStatus:result.data.status===item.expected,citationsValid,ms:Date.now()-started});save();
    console.log('RAG',report.rag.length+'/30',item.expected,result.data.status,Date.now()-started+'ms');
   }
  }
  report.summary={searchRecallAt5:report.search.filter(r=>r.hit).length/report.search.length,
   positiveStatusRate:report.rag.filter(r=>r.expected==='answered'&&r.expectedStatus&&r.citationsValid).length/20,
   refusalRate:report.rag.filter(r=>r.expected==='insufficient_context'&&r.expectedStatus).length/10,
   manualGroundingReviewRequired:true};save();console.log(JSON.stringify(report.summary));
  assert(report.summary.searchRecallAt5>=.85,'Search Recall@5 below target');
  assert(report.summary.positiveStatusRate>=.9,'Answered/citation status below target');
  assert(report.summary.refusalRate>=.9,'Refusal rate below target');
 }finally{for(const {id,input} of created)await api('/api/admin/courses/'+id,'PUT',{...input,published:false});save();}
})().catch(e=>{console.error(e);process.exitCode=1;});
