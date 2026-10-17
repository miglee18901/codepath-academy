const assert=require('node:assert/strict');
const fs=require('node:fs');
const {spawnSync}=require('node:child_process');
const {api,eventually,prom,proxy}=require('./observability_test.cjs');
(async()=>{
 await eventually('worker scrape and four listeners',async()=>{
  assert((await prom('up{job="codepath-worker"}')).some(v=>v.value[1]==='1'));
  assert((await prom('codepath_events_consumers_running{job="codepath-worker"}')).some(v=>Number(v.value[1])===4));
  assert((await prom('codepath_events_broker_available{job="codepath-worker"}')).some(v=>v.value[1]==='1'));
  const lag=await prom('codepath_events_consumer_lag{job="codepath-worker"}');assert.equal(lag.length,2);assert(lag.every(v=>Number(v.value[1])===0));
 });
 const dashboard=await api('/api/dashboards/uid/codepath-events');assert.equal(dashboard.dashboard.panels.length,7);
 for(const p of dashboard.dashboard.panels)await prom(p.targets[0].expr.replaceAll('$deployment','.*'));
 const rules=(await api('/api/v1/provisioning/alert-rules')).filter(r=>r.uid.startsWith('codepath-events-'));assert.equal(rules.length,7);
 for(const r of rules)await prom(r.data.find(d=>d.refId==='A').model.expr);
 console.log('PASS events dashboard and seven alert queries');
 const qa=JSON.parse(fs.readFileSync('artifacts/qa/kafka-test.json','utf8'));
 const result=spawnSync('docker',['compose','exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" -N -B'],{input:`SELECT JSON_UNQUOTE(JSON_EXTRACT(payload,'$.traceContext.traceparent')) FROM business_outbox WHERE event_type='OrderPaid' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.payload.courseId'))='${Number(qa.courseId)}' LIMIT 1;`,encoding:'utf8'});
 assert.equal(result.status,0);const parent=result.stdout.trim();assert.match(parent,/^00-[a-f0-9]{32}-[a-f0-9]{16}-[a-f0-9]{2}$/);
 const traceId=parent.split('-')[1];
 await eventually('API to outbox to Kafka worker trace continuity',async()=>{
  const trace=await api(proxy('tempo','/api/traces/'+traceId));const text=JSON.stringify(trace);
  assert(text.includes('codepath-academy-backend'));assert(text.includes('codepath-academy-worker'));assert(text.includes('kafka'));
 });
})().catch(e=>{console.error(e);process.exitCode=1;});
