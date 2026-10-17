// Read-only smoke test, except for safe API requests and a synthetic redaction log.
const fs = require('node:fs');
const assert = require('node:assert/strict');
const {spawnSync} = require('node:child_process');
const learning = process.env.CODEPATH_LEARNING === '1';
const readConfig = path => fs.existsSync(path) ? Object.fromEntries(fs.readFileSync(path,'utf8').split(/\r?\n/).filter(l=>l && !l.startsWith('#') && l.includes('=')).map(l=>{const i=l.indexOf('=');return [l.slice(0,i),l.slice(i+1)];})) : {};
const config = learning ? {...readConfig('.env'), ...readConfig('.env.learning')} : readConfig('infra/observability/.env');
if (learning) config.GRAFANA_ADMIN_PASSWORD = config.LEARNING_GRAFANA_PASSWORD;
const compose = learning ? ['compose', ...(fs.existsSync('.env') ? ['--env-file','.env'] : []), '--env-file','.env.learning','-f','compose.learning.yaml'] : ['compose','--env-file','infra/observability/.env','-f','infra/observability/compose.yaml'];
const base = process.env.GRAFANA_URL || `http://127.0.0.1:${config.GRAFANA_PORT||3000}`;
const app = process.env.APP_URL || 'http://127.0.0.1:8080';
const headers = {Authorization:'Basic '+Buffer.from(`${config.GRAFANA_ADMIN_USER||'admin'}:${config.GRAFANA_ADMIN_PASSWORD}`).toString('base64')};
const delay = ms => new Promise(r=>setTimeout(r,ms));
async function api(path,options={}) {
 const response=await fetch(base+path,{...options,headers:{...headers,'Content-Type':'application/json',...options.headers},signal:AbortSignal.timeout(20000)});
 const body=await response.text(); assert(response.ok,`${path.split('?')[0]}: HTTP ${response.status} ${body.slice(0,300)}`); return body ? JSON.parse(body) : null;
}
async function eventually(label,fn,timeout=120000) { const end=Date.now()+timeout;let error; do {try {const v=await fn(); console.log('PASS '+label);return v;}catch(e){error=e;await delay(3000);}}while(Date.now()<end);throw error; }
const proxy=(uid,path)=>`/api/datasources/proxy/uid/${uid}${path}`;
async function prom(query) {const r=await api(proxy('prometheus','/api/v1/query?query='+encodeURIComponent(query))); assert.equal(r.status,'success');return r.data.result;}
async function main() {
 await eventually('Grafana ready',()=>api('/api/health'));
 assert.equal((await fetch(app+'/api/categories')).status,200);
 const login=await fetch(app+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'telemetry-invalid-user',password:'invalid-test-password'})});
 assert(login.status>=400 && login.status<500);
 const denied=await fetch(app+'/actuator/prometheus');assert([401,403,404].includes(denied.status));console.log('PASS management endpoint is not available on application port');
 await eventually('Prometheus backend and collector targets healthy',async()=>{const r=await prom('up{job=~"codepath-backend|otel-collector"}');assert(r.length>=2);assert(r.every(v=>v.value[1]==='1'));});
 await eventually('HTTP histogram and authentication metrics',async()=>{assert((await prom('http_server_requests_seconds_bucket{job="codepath-backend"}')).length);assert((await prom('codepath_auth_requests_total{operation="password",outcome="failure"}')).length);});
 const dashboards=await api('/api/search?tag=codepath');assert(dashboards.length>=4);
 for(const d of dashboards) {const model=await api('/api/dashboards/uid/'+d.uid);for(const p of model.dashboard.panels)if(p.datasource?.uid==='prometheus')for(const t of p.targets)await prom(t.expr.replaceAll('$deployment','.*'));}console.log(`PASS ${dashboards.length} dashboards and all PromQL expressions`);
 const rules=await api('/api/v1/provisioning/alert-rules');assert(rules.length>=9);for(const rule of rules)await prom(rule.data.find(d=>d.refId==='A').model.expr);console.log(`PASS ${rules.length} provisioned alert rules and queries`);
 const logs=await eventually('Application logs in Loki with trace IDs',async()=>{const r=await api(proxy('loki','/loki/api/v1/query_range?query='+encodeURIComponent('{service_name="codepath-academy-backend"} |= "HTTP method="')+'&limit=20'));assert(r.data.result.length);assert(JSON.stringify(r.data.result).includes('trace_id'));return r;});
 await eventually('Backend traces in Tempo',async()=>{const r=await api(proxy('tempo','/api/search?tags='+encodeURIComponent('service.name=codepath-academy-backend')+'&limit=5'));assert(r.traces?.length); const detail=await api(proxy('tempo','/api/traces/'+r.traces[0].traceID));assert(detail.batches?.length || detail.resourceSpans?.length);});
 const marker='otel-redaction-'+Date.now();const body={resourceLogs:[{resource:{attributes:[{key:'service.name',value:{stringValue:'observability-smoke-test'}}]},scopeLogs:[{logRecords:[{timeUnixNano:String(BigInt(Date.now())*1000000n),severityText:'INFO',body:{stringValue:`${marker} Bearer SYNTHETIC_TOKEN password=SYNTHETIC_PASSWORD`}}]}]}]};
 const sent=spawnSync('docker',[...compose,'exec','-T','grafana','wget','-q','-O','-','--header=Content-Type: application/json','--post-file=/dev/stdin','http://otel-collector:4318/v1/logs'],{input:JSON.stringify(body),encoding:'utf8'});assert.equal(sent.status,0,sent.stderr);
 await eventually('Collector redacts synthetic credentials',async()=>{const r=await api(proxy('loki','/loki/api/v1/query_range?query='+encodeURIComponent(`{service_name="observability-smoke-test"} |= "${marker}"`)));assert(r.data.result.length);const s=JSON.stringify(r.data.result);assert(!s.includes('SYNTHETIC_TOKEN'));assert(!s.includes('SYNTHETIC_PASSWORD'));});
 const traceId=require('node:crypto').randomBytes(16).toString('hex');
 await fetch(app+'/api/categories?access_token=SYNTHETIC_URL_SECRET',{headers:{traceparent:`00-${traceId}-0123456789abcdef-01`}});
 await eventually('Query-string secrets are absent from exported spans',async()=>{
  const detail=await api(proxy('tempo','/api/traces/'+traceId));
  assert(detail.batches?.length || detail.resourceSpans?.length);
  assert(!JSON.stringify(detail).includes('SYNTHETIC_URL_SECRET'));
 });
 console.log('Observability smoke test passed.');
}
module.exports = {api, eventually, prom, proxy, config};
if (require.main === module) main().catch(e=>{console.error(e.message);process.exitCode=1;});
