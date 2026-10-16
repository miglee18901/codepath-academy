// Creates a temporary firing Grafana rule, verifies delivery to LOCAL Mailpit, then removes it.
const assert = require('node:assert/strict');
const {api,eventually,config} = require('./observability_test.cjs');
async function main() {
 assert.equal(config.SMTP_HOST||'alert-mailpit:1025','alert-mailpit:1025','This test only supports the local test inbox.');
 const uid='qa-alert-'+Date.now();
 const dashboard=await api('/api/dashboards/uid/codepath-overview');
 const model=JSON.parse(require('node:fs').readFileSync('infra/observability/grafana/provisioning/alerting/rules.yaml','utf8')).groups[0].rules[0];
 const rule={...model,uid,title:uid,folderUID:dashboard.meta.folderUid,ruleGroup:'smoke-test',for:'0s',noDataState:'OK',labels:{severity:'info',test:uid},annotations:{summary:uid,description:'Synthetic local observability pipeline test.'},data:structuredClone(model.data)};
 rule.data[0].model.expr='vector(1)';
 let created=false;
 try {
  await api('/api/v1/provisioning/alert-rules',{method:'POST',body:JSON.stringify(rule)});created=true;
  await eventually('Real Grafana alert delivered to local Mailpit',async()=>{
   const response=await fetch(`http://127.0.0.1:${config.ALERT_MAILPIT_PORT||18026}/api/v1/messages`);
   assert(response.ok);const inbox=await response.json();assert(inbox.messages.some(m=>m.Subject.includes(uid)));
  },180000);
 } finally {if(created)await api('/api/v1/provisioning/alert-rules/'+uid,{method:'DELETE'});}
 console.log('PASS temporary alert rule removed');
}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
