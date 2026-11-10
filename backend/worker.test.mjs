import {test,before,beforeEach} from 'node:test';
import assert from 'node:assert/strict';
import worker,{PROJECT,OWNER,alias,recipients,wantsNotice,safeTarget} from './worker.js';
let privateKey,jwk,env,docs,accounts,sent,failCorrection,operations,fcmError;
const base=`projects/${PROJECT}/databases/(default)/documents/`;
const pack=v=>typeof v==='string'?{stringValue:v}:typeof v==='boolean'?{booleanValue:v}:typeof v==='number'?{integerValue:String(v)}:Array.isArray(v)?{arrayValue:{values:v.map(pack)}}:{mapValue:{fields:fields(v)}};
const fields=v=>Object.fromEntries(Object.entries(v).map(([k,v])=>[k,pack(v)]));
const put=(path,data)=>{ const doc={name:base+path,fields:fields(data),updateTime:new Date(Date.now()+operations.length).toISOString()}; docs.set(path,doc); return doc; };
const json=(v,status=200)=>Response.json(v,{status});
const encode=v=>Buffer.from(JSON.stringify(v)).toString('base64url');
const profile=(papel='operador',frenteId='santo_inacio',turno='C',status='aprovado')=>({nome:'Pessoa',papel,frenteId,turno,status,matricula:'080911'});
before(async()=>{
  const pair=await crypto.subtle.generateKey({name:'RSASSA-PKCS1-v1_5',modulusLength:2048,publicExponent:new Uint8Array([1,0,1]),hash:'SHA-256'},true,['sign','verify']);
  privateKey=pair.privateKey; jwk=await crypto.subtle.exportKey('jwk',pair.publicKey); jwk.kid='fixture-key';
  const pem='-----BEGIN PRIVATE KEY-----\n'+Buffer.from(await crypto.subtle.exportKey('pkcs8',privateKey)).toString('base64')+'\n-----END PRIVATE KEY-----';
  env={FIREBASE_SERVICE_ACCOUNT:JSON.stringify({project_id:PROJECT,client_email:'fixture@example.invalid',private_key:pem})};
  globalThis.fetch=async(input,init={})=>{
    const url=new URL(input); const body=init.body?JSON.parse(typeof init.body==='string'?init.body:'{}'):{};
    if(url.pathname.includes('/service_accounts/v1/jwk/')) return json({keys:[jwk]});
    if(url.hostname==='oauth2.googleapis.com') return json({access_token:'fixture-oauth',expires_in:3600});
    if(url.hostname==='fcm.googleapis.com') { if(fcmError) return json(fcmError.body,fcmError.status); sent.push(body.message); return json({name:'fixture-message'}); }
    if(url.hostname==='identitytoolkit.googleapis.com') {
      if(url.pathname.endsWith(':lookup')) return json({users:body.localId.map(uid=>accounts.get(uid)).filter(Boolean)});
      if(url.pathname.endsWith(':update')) {
        if([...accounts.values()].some(a=>a.localId!==body.localId&&a.email===body.email)) return json({error:{message:'EMAIL_EXISTS'}},400);
        Object.assign(accounts.get(body.localId),{email:body.email}); operations.push('auth-update'); return json({localId:body.localId});
      }
      if(url.pathname.endsWith(':delete')) {
        assert.equal(docs.get('usuarios/'+body.localId).fields.status.stringValue,'excluido');
        accounts.delete(body.localId); operations.push('auth-delete'); return json({});
      }
    }
    if(url.hostname==='firestore.googleapis.com') {
      const marker='/documents'; const suffix=decodeURIComponent(url.pathname.slice(url.pathname.indexOf(marker)+marker.length));
      if(suffix.endsWith(':runQuery')) {
        const parent=suffix.slice(1,-':runQuery'.length); const q=body.structuredQuery; const collection=q.from[0].collectionId;
        let entries=[...docs.values()].filter(d=>{ const path=d.name.slice(base.length); const segments=path.split('/'); return segments.at(-2)===collection&&segments.slice(0,-2).join('/')===parent; });
        if(q.where) { const f=q.where.fieldFilter; entries=entries.filter(d=>JSON.stringify(d.fields[f.field.fieldPath])===JSON.stringify(f.value)); }
        return json(entries.slice(0,q.limit).map(document=>({document})));
      }
      const path=suffix.slice(1); const existing=docs.get(path);
      if(init.method==='PATCH') {
        if(url.searchParams.get('currentDocument.exists')==='false'&&existing) return json({error:{}},409);
        if(url.searchParams.has('currentDocument.updateTime')&&existing?.updateTime!==url.searchParams.get('currentDocument.updateTime')) return json({error:{}},412);
        if(failCorrection&&path==='usuarios/target'&&body.fields.matricula) return json({error:{}},503);
        const merged=url.searchParams.has('updateMask.fieldPaths')?{...existing?.fields,...body.fields}:body.fields;
        const doc={name:base+path,fields:merged,updateTime:new Date(Date.now()+operations.length+1).toISOString()};
        docs.set(path,doc); operations.push('firestore-patch:'+path); return json(doc);
      }
      if(init.method==='DELETE') {docs.delete(path);operations.push('firestore-delete:'+path);return json({});}
      return existing?json(existing):json({error:{}},404);
    }
    throw new Error('Unexpected URL '+url);
  };
});
beforeEach(()=>{
  docs=new Map(); accounts=new Map(); sent=[]; failCorrection=false; operations=[]; fcmError=null;
  for(const uid of [OWNER,'opA','supA','supB','target']) accounts.set(uid,{localId:uid,email:uid==='target'?alias('080911'):uid+'@example.invalid',validSince:'0',passwordHash:'unchanged'});
  put('usuarios/'+OWNER,{nome:'Diego'}); put('usuarios/opA',profile()); put('usuarios/supA',profile('supervisor')); put('usuarios/supB',profile('supervisor','colorado'));
  put('usuarios/target',profile());
});
async function request(uid,path,body,extraClaims={}) {
  const now=Math.floor(Date.now()/1000);
  const claims={sub:uid,aud:PROJECT,iss:`https://securetoken.google.com/${PROJECT}`,iat:now,exp:now+3600,auth_time:now,...extraClaims};
  const unsigned=encode({alg:'RS256',kid:jwk.kid})+'.'+encode(claims);
  const signed=unsigned+'.'+Buffer.from(await crypto.subtle.sign('RSASSA-PKCS1-v1_5',privateKey,new TextEncoder().encode(unsigned))).toString('base64url');
  return worker.fetch(new Request('https://fixture.workers.dev'+path,{method:'POST',headers:{Authorization:'Bearer '+signed,'Content-Type':'application/json'},body:JSON.stringify(body)}),env);
}
test('matrícula preserves leading zeros and protects the owner',()=>{
  assert.equal(alias('00080911'),'matricula.00080911@acesso.dm-agro.app');
  assert.throws(()=>alias('8a')); assert.throws(()=>safeTarget('supA','target')); assert.throws(()=>safeTarget(OWNER,OWNER));
});
test('owner notifications follow Santo Inácio while supervisors follow their authorized team',()=>{
  const same={...profile('supervisor'),uid:'supA'},other={...profile('supervisor','colorado'),uid:'supB'};
  assert.deepEqual(recipients('opA','santo_inacio_C',[same,other]),[OWNER,'supA']);
  assert.equal(wantsNotice(OWNER,{front:'santo_inacio'},'santo_inacio_A',{}),true);
  assert.equal(wantsNotice(OWNER,{front:'santo_inacio'},'colorado_C',{}),false);
  assert.equal(wantsNotice('supA',{},'santo_inacio_A',same),false);
  assert.deepEqual(recipients('opA','santo_inacio_C',[same],true),[OWNER]);
});
test('operator and supervisor cannot correct or delete any account',async()=>{
  for(const uid of ['opA','supA']) for(const path of ['/accounts/correct','/accounts/delete']) {
    const result=await request(uid,path,{uid:'target',matricula:'99999'}); assert.equal(result.status,403);
  }
  assert.equal(accounts.get('target').email,alias('080911')); assert.ok(docs.has('usuarios/target'));
});
test('owner corrects Authentication and Firestore preserving UID, password and work records',async()=>{
  put('equipes/santo_inacio_C/registros/original',{autorUid:'target',conteudo:'Pneu'});
  const result=await request(OWNER,'/accounts/correct',{uid:'target',matricula:'0099911'});
  assert.equal(result.status,200); assert.equal(accounts.get('target').email,alias('0099911'));
  assert.equal(accounts.get('target').passwordHash,'unchanged'); assert.equal(docs.get('usuarios/target').fields.matricula.stringValue,'0099911');
  assert.ok(docs.has('equipes/santo_inacio_C/registros/original'));
});
test('duplicate matrícula is rejected and a failed profile update restores the original login',async()=>{
  accounts.set('collision',{localId:'collision',email:alias('12345')});
  assert.equal((await request(OWNER,'/accounts/correct',{uid:'target',matricula:'12345'})).status,409);
  assert.equal(accounts.get('target').email,alias('080911'));
  failCorrection=true;
  assert.equal((await request(OWNER,'/accounts/correct',{uid:'target',matricula:'67890'})).status,503);
  assert.equal(accounts.get('target').email,alias('080911')); assert.equal(docs.get('usuarios/target').fields.matricula.stringValue,'080911');
});
test('permanent deletion releases the credential and profile but retains operational history',async()=>{
  put('equipes/santo_inacio_C/registros/kept',{autorUid:'target',conteudo:'Histórico'});
  const result=await request(OWNER,'/accounts/delete',{uid:'target'});
  assert.equal(result.status,200); assert.equal(accounts.has('target'),false); assert.equal(docs.has('usuarios/target'),false);
  assert.ok(docs.has('equipes/santo_inacio_C/registros/kept'));
  assert.ok(docs.has('contasEncerradas/target'));
  assert.ok(operations.indexOf('auth-delete')<operations.indexOf('firestore-delete:usuarios/target'));
});
test('administrative mutations require recent authentication and cannot target the owner',async()=>{
  assert.equal((await request(OWNER,'/accounts/delete',{uid:'target'},{auth_time:Math.floor(Date.now()/1000)-3600})).status,401);
  assert.equal((await request(OWNER,'/accounts/delete',{uid:OWNER})).status,403);
  assert.ok(accounts.has('target')); assert.ok(accounts.has(OWNER));
});
test('push is derived from the saved record, excludes other teams and is deduplicated',async()=>{
  const key='a'.repeat(64);
  put(`equipes/santo_inacio_C/registros/${key}`,{tipo:'item',grupo:'pendencias',autorUid:'opA',versao:1,excluido:false,conteudo:JSON.stringify({equipamento:'21569',descricao:'Pneu'})});
  put(`dispositivos/${OWNER}/tokens/a`,{token:'token-owner',front:'santo_inacio',seen:1});
  put('dispositivos/supA/tokens/a',{token:'token-supA',front:'santo_inacio',seen:1});
  put('dispositivos/supB/tokens/a',{token:'token-supB',front:'colorado',seen:1});
  const body={kind:'record',team:'santo_inacio_C',key};
  assert.equal((await request('opA','/events/notify',body)).status,200);
  assert.deepEqual(sent.map(m=>m.token).sort(),['token-owner','token-supA']);
  assert.ok(sent.every(m=>m.android.priority==='HIGH'&&!m.notification));
  assert.equal((await request('opA','/events/notify',body)).status,200); assert.equal(sent.length,2);
});
test('maps never produce team notifications and an owner device for another front gets no alert',async()=>{
  const key='b'.repeat(64); const path=`equipes/santo_inacio_C/registros/${key}`;
  put(path,{tipo:'preferencia',grupo:'mapas',autorUid:'opA',excluido:false,conteudo:'privado'});
  assert.equal((await request('opA','/events/notify',{kind:'record',team:'santo_inacio_C',key})).status,403);
  put(path,{tipo:'item',grupo:'pendencias',autorUid:'opA',excluido:false,conteudo:'{}'});
  put(`dispositivos/${OWNER}/tokens/a`,{token:'owner-other-front',front:'colorado',seen:1});
  assert.equal((await request('opA','/events/notify',{kind:'record',team:'santo_inacio_C',key})).status,200);
  assert.equal(sent.length,0);
});
test('unsigned or wrong-project tokens are rejected before account changes',async()=>{
  assert.equal((await worker.fetch(new Request('https://fixture.workers.dev/accounts/delete',{method:'POST',body:'{}'}),env)).status,401);
  assert.equal((await request(OWNER,'/accounts/delete',{uid:'target'},{aud:'another-project'})).status,401);
});
test('a supervisor cannot subscribe a device to another front',async()=>{
  const response=await request('supA','/devices/register',{token:'token-fixture-1234567890',front:'colorado'});
  assert.equal(response.status,200);
  const device=[...docs.values()].find(d=>d.name.includes('/dispositivos/supA/tokens/'));
  assert.equal(device.fields.front.stringValue,'santo_inacio');
});
test('health exposes setup status without disclosing any private credential',async()=>{
  const response=await worker.fetch(new Request('https://fixture.workers.dev/health'),env);
  const body=await response.text(); assert.ok(body.includes(PROJECT)); assert.equal(body.includes('PRIVATE KEY'),false); assert.equal(body.includes('client_email'),false);
});

test('each committed revision alerts once and an authorized editor can notify their change',async()=>{
  const key='c'.repeat(64); const path=`equipes/santo_inacio_C/registros/${key}`;
  put(`dispositivos/${OWNER}/tokens/a`,{token:'owner-token',front:'santo_inacio',seen:1});
  const record={tipo:'item',grupo:'pendencias',autorUid:'opA',editadoPor:'opA',versao:1,excluido:false,conteudo:JSON.stringify({descricao:'Filtro'})};
  put(path,record);
  assert.equal((await request('opA','/events/notify',{kind:'record',team:'santo_inacio_C',key,revision:1})).status,200);
  put(path,{...record,editadoPor:'supA',versao:2});
  const body={kind:'record',team:'santo_inacio_C',key,revision:2};
  assert.equal((await request('opA','/events/notify',body)).status,403);
  assert.equal((await request('supA','/events/notify',body)).status,200);
  assert.equal((await request('supA','/events/notify',body)).status,200);
  assert.equal(sent.length,2); assert.notEqual(sent[0].data.eventId,sent[1].data.eventId);
  assert.equal(sent[1].data.title,'Registro atualizado');
});
test('a superseded revision does not notify with the wrong contents',async()=>{
  const key='d'.repeat(64);
  put(`equipes/santo_inacio_C/registros/${key}`,{tipo:'item',grupo:'pendencias',autorUid:'opA',versao:3,excluido:false,conteudo:'{}'});
  const response=await request('opA','/events/notify',{kind:'record',team:'santo_inacio_C',key,revision:2});
  assert.equal(response.status,200); assert.equal((await response.json()).superseded,true);assert.equal(sent.length,0);
});
test('delivery test only reaches the authenticated managers registered device and is rate limited',async()=>{
  const token='test-fixture-device-token-123456';
  assert.equal((await request(OWNER,'/devices/register',{token,front:'santo_inacio'})).status,200);
  assert.equal((await request('supA','/devices/test',{token})).status,400);
  assert.equal((await request('opA','/devices/test',{token})).status,403);
  assert.equal((await request(OWNER,'/devices/test',{token})).status,200);
  assert.equal(sent.length,1);assert.equal(sent[0].data.targetUid,OWNER);assert.equal(sent[0].data.team,'santo_inacio_C');
  assert.equal((await request(OWNER,'/devices/test',{token})).status,429);
});
test('a failed Firebase delivery is diagnosed instead of reporting success',async()=>{
  const token='test-fixture-device-token-123456';
  await request(OWNER,'/devices/register',{token,front:'santo_inacio'});
  fcmError={status:403,body:{error:{message:'permission denied'}}};
  const response=await request(OWNER,'/devices/test',{token});
  assert.equal(response.status,503);assert.match((await response.json()).message,/Cloud Messaging HTTP v1/);assert.equal(sent.length,0);
});
test('an unregistered device is removed without deleting devices on unrelated FCM failures',async()=>{
  const token='test-fixture-device-token-123456';
  await request(OWNER,'/devices/register',{token,front:'santo_inacio'});
  const path=[...docs.keys()].find(k=>k.startsWith(`dispositivos/${OWNER}/tokens/`));
  fcmError={status:404,body:{error:{message:'API not found'}}};
  assert.equal((await request(OWNER,'/devices/test',{token})).status,503);assert.ok(docs.has(path));
  docs.get(path).fields.testAt={integerValue:'0'};
  fcmError={status:404,body:{error:{details:[{errorCode:'UNREGISTERED'}]}}};
  assert.equal((await request(OWNER,'/devices/test',{token})).status,409);assert.equal(docs.has(path),false);
});
