// Local integration: uses real MySQL/Kafka/SMTP, creates a free QA course, hides it afterwards.
// Requires compose.kafka.yml. Temporarily stops only this project's worker, broker and mailpit.
const assert = require('node:assert/strict');
const {spawnSync} = require('node:child_process');
const {setTimeout: sleep} = require('node:timers/promises');
const {randomUUID} = require('node:crypto');
const fs = require('node:fs');
const base = process.env.APP_URL || 'http://127.0.0.1:8080';
function docker(args, input) { const r = spawnSync('docker', args, {input, encoding:'utf8', timeout:60000}); if(r.status!==0)throw Error(r.stderr || r.stdout); return r.stdout.trim(); }
const compose = (...args) => docker(['compose','-f','docker-compose.yml','-f','compose.kafka.yml',...args]);
const broker = (...args) => docker(['compose','-f','infra/kafka/compose.yaml',...args]);
const sql = query => docker(['compose','exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" -N -B'], query);
async function api(path, method='GET', data, token) {
 const r=await fetch(base+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},...(data===undefined?{}:{body:JSON.stringify(data)}),signal:AbortSignal.timeout(30000)});
 const raw=await r.text(); return {status:r.status,body:raw?JSON.parse(raw):null};
}
async function wait(label, check, ms=120000) { const end=Date.now()+ms; while(Date.now()<end){if(await check())return; await sleep(1500);}throw Error('Timeout: '+label); }
function produce(topic,event) {docker(['compose','-f','infra/kafka/compose.yaml','exec','-T','kafka','/opt/kafka/bin/kafka-console-producer.sh','--bootstrap-server','kafka:9092','--topic',topic],JSON.stringify(event)+'\n');}
(async()=>{
 const admin=(await api('/api/auth/login','POST',{username:process.env.QA_ADMIN_USER||'admin',password:process.env.QA_ADMIN_PASSWORD||'123456'})).body;
 const student=(await api('/api/auth/login','POST',{username:'student',password:'Learn123!'})).body;
 assert.ok(admin.token&&student.token);
 await wait('historical backfill',()=>Number(sql('SELECT COUNT(*) FROM business_backfill WHERE done=0'))===0);
 const categories=(await api('/api/categories')).body;
 const input={categoryId:categories[0].id,title:'QA Kafka '+Date.now(),description:'Nội dung kiểm thử nghiệp vụ Kafka.',price:0,thumbnailUrl:'',instructor:'QA',level:'BEGINNER',published:true};
 const created=await api('/api/admin/courses','POST',input,admin.token);assert.equal(created.status,200);
 const id=created.body.id; const checks=[];
 let stoppedBroker=false,stoppedWorker=false,stoppedMail=false;
 try {
  const section=(await api(`/api/admin/courses/${id}/sections`,'POST',{title:'Kafka',position:0},admin.token)).body;
  const lesson=(await api(`/api/admin/sections/${section.id}/lessons`,'POST',{title:'Sự kiện',content:'Kafka nghiệp vụ',videoUrl:'',durationMinutes:1,position:0,preview:false},admin.token)).body;
  assert.ok(lesson.id);
  // Verify source DB transaction rolls back business records and outbox together.
  const before=Number(sql('SELECT COUNT(*) FROM business_outbox'));
  sql(`START TRANSACTION; INSERT INTO business_outbox(id,event_type,user_id,payload) VALUES ('${randomUUID()}','OrderPaid',${student.id},JSON_OBJECT('rollback',true)); ROLLBACK;`);
  assert.equal(Number(sql('SELECT COUNT(*) FROM business_outbox')),before);checks.push('outbox rollback');
  compose('stop','worker');stoppedWorker=true;
  const paid=await api('/api/orders','POST',{courseId:id},student.token);assert.equal(paid.status,200);assert.equal(paid.body.status,'PAID');
  const order=paid.body.id;
  assert.equal((await api(`/api/lessons/${lesson.id}`,'GET',undefined,student.token)).status,200,'Immediate enrollment without worker');
  assert.equal(Number(sql(`SELECT COUNT(*) FROM business_outbox WHERE JSON_UNQUOTE(JSON_EXTRACT(payload,'$.payload.courseId'))='${id}' AND published_at IS NULL`)),2);
  compose('start','worker');stoppedWorker=false;
  await wait('purchase notification',()=>Number(sql(`SELECT COUNT(*) FROM notifications WHERE user_id=${student.id} AND message LIKE '%QA Kafka%' AND event_id IN(SELECT id FROM business_outbox WHERE JSON_UNQUOTE(JSON_EXTRACT(payload,'$.payload.courseId'))='${id}')`))===1);
  await wait('purchase email',()=>Number(sql(`SELECT COUNT(*) FROM business_email_jobs WHERE id IN(SELECT id FROM business_outbox WHERE JSON_UNQUOTE(JSON_EXTRACT(payload,'$.payload.courseId'))='${id}') AND status='SENT'`))===1);
  checks.push('worker outage preserves purchase; immediate access; durable email');
  // Replay the same outbox and confirm all DB effects remain unique.
  sql(`UPDATE business_outbox SET published_at=NULL WHERE JSON_UNQUOTE(JSON_EXTRACT(payload,'$.payload.courseId'))='${id}'`);
  await wait('republish',()=>Number(sql(`SELECT COUNT(*) FROM business_outbox WHERE JSON_UNQUOTE(JSON_EXTRACT(payload,'$.payload.courseId'))='${id}' AND published_at IS NULL`))===0);
  await sleep(2000);
  assert.equal(Number(sql(`SELECT COUNT(*) FROM business_report_facts WHERE course_id=${id} AND kind='paid'`)),1);
  assert.equal(Number(sql(`SELECT COUNT(*) FROM business_report_facts WHERE course_id=${id} AND kind='enrollment'`)),1);
  checks.push('duplicate Kafka delivery is idempotent');
  broker('stop','kafka');stoppedBroker=true;
  const updates=await Promise.all(Array.from({length:4},()=>api(`/api/me/progress/${lesson.id}`,'PUT',{completed:true},student.token)));
  assert.ok(updates.every(r=>r.status===200),JSON.stringify(updates));
  await api(`/api/me/progress/${lesson.id}`,'PUT',{completed:false},student.token);
  await api(`/api/me/progress/${lesson.id}`,'PUT',{positionSeconds:95,durationSeconds:100},student.token);
  assert.equal(Number(sql(`SELECT COUNT(*) FROM course_completions WHERE user_id=${student.id} AND course_id=${id}`)),1);
  assert.equal(Number(sql(`SELECT COUNT(*) FROM business_outbox WHERE event_type='CourseCompleted' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.payload.courseId'))='${id}'`)),1);
  assert.equal((await api('/api/categories')).status,200);
  broker('start','kafka');stoppedBroker=false;
  await wait('completion report',()=>Number(sql(`SELECT COUNT(*) FROM business_report_facts WHERE course_id=${id} AND kind='completion'`))===1);
  checks.push('broker outage; concurrent completion; uncheck/recomplete; video 95%');
  const list=await api('/api/me/notifications','GET',undefined,student.token);assert.equal(list.status,200);
  const notification=list.body.content.find(n=>n.message.includes(input.title));assert.ok(notification);
  assert.equal((await api(`/api/me/notifications/${notification.id}/read`,'PUT',undefined,admin.token)).status,404);
  assert.equal((await api(`/api/me/notifications/${notification.id}/read`,'PUT',undefined,student.token)).status,200);
  assert.equal((await api(`/api/me/notifications/${notification.id}/read`,'PUT',undefined,student.token)).status,200);
  assert.equal((await api('/api/admin/events/status','GET',undefined,student.token)).status,403);
  assert.equal((await api('/api/me/notifications')).status,401);
  assert.equal((await api('/api/admin/reports/overview?from=2020-01-01&to=2026-01-01','GET',undefined,admin.token)).status,400);
  checks.push('notification ownership/idempotent read; API security/date validation');
  const report=(await api(`/api/admin/reports/overview?courseId=${id}`,'GET',undefined,admin.token)).body.content[0];
  assert.equal(Number(report.revenue),0);assert.equal(Number(report.paid_orders),1);assert.equal(Number(report.enrollments),1);assert.equal(Number(report.completions),1);
  // Durable historical backfill can safely rerun against live facts, without emails.
  const emailCount=Number(sql('SELECT COUNT(*) FROM business_email_jobs'));
  sql('UPDATE business_backfill SET cursor_id=0,done=0');
  await wait('backfill rerun',()=>Number(sql('SELECT COUNT(*) FROM business_backfill WHERE done=0'))===0);
  assert.equal(Number(sql(`SELECT COUNT(*) FROM business_report_facts WHERE course_id=${id}`)),3);
  assert.equal(Number(sql('SELECT COUNT(*) FROM business_email_jobs')),emailCount);
  checks.push('historical rerun races safely with live facts; no historic messages');
  // Invalid version goes to both DLTs. Correct only the notifications failure in the QA DB and replay it.
  const event={eventId:randomUUID(),eventType:'CourseCompleted',schemaVersion:2,occurredAt:new Date().toISOString(),aggregateId:`${student.id}:${id}`,correlationId:'qa',userId:student.id,payload:{courseId:String(id)},traceContext:{}};
  produce('codepath.local.learning.v1',event);
  await wait('DLT',()=>Number(sql(`SELECT COUNT(*) FROM business_failures WHERE event_id='${event.eventId}' AND status='FAILED'`))===2);
  sql(`UPDATE business_failures SET payload=JSON_SET(payload,'$.schemaVersion',1) WHERE event_id='${event.eventId}' AND consumer='notifications'`);
  const failed=JSON.parse(sql(`SELECT JSON_OBJECT('id',id) FROM business_failures WHERE event_id='${event.eventId}' AND consumer='notifications'`));
  assert.equal((await api(`/api/admin/events/failures/${failed.id}/retry`,'POST',undefined,admin.token)).status,202);
  await wait('targeted replay',()=>sql(`SELECT status FROM business_failures WHERE id=${failed.id}`)==='RESOLVED');
  assert.equal(sql(`SELECT status FROM business_failures WHERE event_id='${event.eventId}' AND consumer='reports'`),'FAILED');
  assert.equal(Number(sql(`SELECT COUNT(*) FROM business_retry_audit WHERE failure_id=${failed.id}`)),1);
  checks.push('invalid schema DLT; targeted replay and audit');
  // Isolate a QA email job, force the final retry while SMTP is down, then retry through the admin API.
  await wait('QA email delivered',()=>sql(`SELECT status FROM business_email_jobs WHERE id='${event.eventId}'`)==='SENT');
  compose('stop','mailpit');stoppedMail=true;
  sql(`UPDATE business_email_jobs SET status='PENDING',attempts=4,available_at=UTC_TIMESTAMP(6),sent_at=NULL WHERE id='${event.eventId}'`);
  await wait('email failure',()=>sql(`SELECT status FROM business_email_jobs WHERE id='${event.eventId}'`)==='FAILED');
  compose('start','mailpit');stoppedMail=false;
  assert.equal((await api(`/api/admin/events/emails/${event.eventId}/retry`,'POST',undefined,admin.token)).status,202);
  await wait('email restored',()=>sql(`SELECT status FROM business_email_jobs WHERE id='${event.eventId}'`)==='SENT');
  assert.equal(Number(sql(`SELECT COUNT(*) FROM business_email_retry_audit WHERE email_id='${event.eventId}'`)),1);
  checks.push('SMTP outage, bounded retry and manual recovery');
  // Remove just the intentionally malformed QA message/failures, leaving real test purchases inspectable.
  sql(`DELETE FROM business_failures WHERE event_id='${event.eventId}';`);
  fs.mkdirSync('artifacts/qa',{recursive:true});fs.writeFileSync('artifacts/qa/kafka-test.json',JSON.stringify({courseId:id,orderId:order,checks},null,2));
  console.log('PASS Kafka integration: '+checks.join('; '));
 } finally {
  if(stoppedBroker)broker('start','kafka');if(stoppedWorker)compose('start','worker');if(stoppedMail)compose('start','mailpit');
  sql(`DELETE FROM business_failures WHERE JSON_UNQUOTE(JSON_EXTRACT(payload,'$.payload.courseId'))='${id}' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.correlationId'))='qa'`);
  await api(`/api/admin/courses/${id}`,'PUT',{...input,published:false},admin.token);
 }
})().catch(e=>{console.error(e);process.exitCode=1;});
