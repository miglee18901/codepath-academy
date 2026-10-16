const assert = require('node:assert/strict');
const {spawnSync}=require('node:child_process');
const compose=['compose','--env-file','infra/observability/.env','-f','infra/observability/compose.yaml'];
function docker(...args) {const r=spawnSync('docker',[...compose,...args],{encoding:'utf8'});assert.equal(r.status,0,r.stderr);}
(async()=>{
 try {
  docker('stop','otel-collector');
  for(let i=0;i<3;i++) {
   const r=await fetch((process.env.APP_URL||'http://127.0.0.1:8080')+'/api/categories',{signal:AbortSignal.timeout(5000)});
   assert.equal(r.status,200);
  }
  console.log('PASS backend remains available while Collector is stopped');
 } finally {docker('start','otel-collector');console.log('Collector restored');}
})().catch(e=>{console.error(e.message);process.exitCode=1});
