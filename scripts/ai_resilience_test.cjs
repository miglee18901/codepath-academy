// Local only: briefly stops AI dependencies and always restarts them.
const assert=require('node:assert/strict');
const {execFileSync}=require('node:child_process');
const base=process.env.APP_URL||'http://127.0.0.1:8080';
const compose=['compose','--env-file','infra/ai/.env','-f','infra/ai/compose.yaml'];
function docker(args){execFileSync('docker',[...compose,...args],{stdio:'pipe',timeout:60000});}
(async()=>{
 try{
  docker(['stop','qdrant','ollama']);
  const categories=await fetch(base+'/api/categories',{signal:AbortSignal.timeout(5000)});assert.equal(categories.status,200);
  const search=await fetch(base+'/api/search/courses?q=Java',{signal:AbortSignal.timeout(10000)});assert.equal(search.status,200);
  assert.equal((await search.json()).mode,'keyword');
  const login=await fetch(base+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'student',password:'Learn123!'}),signal:AbortSignal.timeout(5000)});
  assert.equal(login.status,200);const auth=await login.json();
  const orders=await fetch(base+'/api/orders',{headers:{Authorization:'Bearer '+auth.token},signal:AbortSignal.timeout(5000)});assert.equal(orders.status,200);
  const ask=await fetch(base+'/api/courses/1/assistant',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+auth.token},
   body:JSON.stringify({question:'Khóa học giải thích Java ra sao?',history:[]}),signal:AbortSignal.timeout(15000)});assert.equal(ask.status,503);
  console.log('PASS AI outage: catalog, login, orders and keyword fallback remain available; assistant returns controlled 503');
 }finally{docker(['start','qdrant','ollama']);}
})().catch(e=>{console.error(e);process.exitCode=1;});
