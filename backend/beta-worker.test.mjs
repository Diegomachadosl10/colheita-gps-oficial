import {test,before,beforeEach} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createBetaWorker,OWNER,ROOT,PROTOCOL,unpack} from './beta-worker.js';

// Every network call is intercepted. No real Firebase project, user or FCM device is contacted.
const PROJECT='demo-dm-agro';
const base=`projects/${PROJECT}/databases/(default)/documents/`;
let key,jwk,secret,env,worker,docs,accounts,calls,sent,attempts,now,sequence,fcmError,failToken;
const encode=v=>Buffer.from(JSON.stringify(v)).toString('base64url');
const hash=async v=>Buffer.from(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(v))).toString('hex');
const fields=v=>Object.fromEntries(Object.entries(v).map(([k,x])=>[k,pack(x)]));
function pack(v){return v===null?{nullValue:null}:typeof v==='string'?{stringValue:v}:typeof v==='boolean'?{booleanValue:v}:typeof v==='number'?{integerValue:String(v)}:Array.isArray(v)?{arrayValue:{values:v.map(pack)}}:{mapValue:{fields:fields(v)}};}
const raw=doc=>Object.fromEntries(Object.entries(doc.fields).map(([k,v])=>[k,unpack(v)]));
function put(path,data){const doc={name:base+path,fields:fields(data),updateTime:new Date(now+(++sequence)).toISOString()};docs.set(path,doc);return doc;}
const beta=(path,data)=>put(`${ROOT}/${path}`,data);
function profile(papel='operador',front='santo_inacio',shift='C',status='aprovado',requested=papel){return{nome:'Pessoa fictícia',papel,perfil:papel,frenteId:front,turno:shift,status,solicitacaoPerfil:requested};}
function user(uid,p=profile()){accounts.set(uid,{localId:uid,disabled:false,validSince:'0',passwordHash:'fixture-unchanged'});if(p)beta(`usuarios/${uid}`,p);}
const json=(data,status=200)=>Response.json(data,{status});
function predicate(doc,where){if(where.compositeFilter)return where.compositeFilter.filters.every(f=>predicate(doc,f));const f=where.fieldFilter;return JSON.stringify(doc.fields[f.field.fieldPath])===JSON.stringify(f.value);}
async function fakeFetch(input,init={}) {
  const url=new URL(input);const body=typeof init.body==='string'?JSON.parse(init.body):{};
  calls.push({method:init.method||'GET',host:url.hostname,path:url.pathname});
  if(url.hostname==='www.googleapis.com'&&url.pathname.includes('/service_accounts/v1/jwk/'))return json({keys:[jwk]});
  if(url.hostname==='oauth2.googleapis.com')return json({access_token:'offline-oauth-fixture',expires_in:3600});
  if(url.hostname==='identitytoolkit.googleapis.com') {
    assert.ok(url.pathname.endsWith(':lookup'),'The Beta cannot change global Authentication accounts');
    return json({users:body.localId.map(uid=>accounts.get(uid)).filter(Boolean)});
  }
  if(url.hostname==='fcm.googleapis.com') {
    assert.equal(url.pathname,`/v1/projects/${PROJECT}/messages:send`);attempts.push(body.message.token);
    if(fcmError&&(!failToken||failToken===body.message.token))return json(fcmError.body,fcmError.status);
    sent.push(body.message);return json({name:'offline-message'});
  }
  if(url.hostname==='firestore.googleapis.com') {
    assert.ok(url.pathname.startsWith('/v1/'+base+ROOT+'/')||url.pathname==='/v1/'+base+ROOT+':runQuery','Every Firestore request must stay inside the Beta namespace');
    const path=decodeURIComponent(url.pathname.slice(('/v1/'+base).length));
    if(path.endsWith(':runQuery')) {
      const parent=path.slice(0,-':runQuery'.length);const q=body.structuredQuery;const collection=q.from[0].collectionId;
      let rows=[...docs.values()].filter(d=>{const segments=d.name.slice(base.length).split('/');return segments.at(-2)===collection&&segments.slice(0,-2).join('/')===parent;});
      if(q.where)rows=rows.filter(d=>predicate(d,q.where));
      rows.sort((a,b)=>a.name<b.name?-1:a.name>b.name?1:0);
      if(q.startAt)rows=rows.filter(d=>d.name>q.startAt.values[0].referenceValue);
      return json(rows.slice(0,q.limit).map(document=>({document})));
    }
    const existing=docs.get(path);const condition=url.searchParams.get('currentDocument.updateTime');
    if(condition&&existing?.updateTime!==condition)return json({error:{}},412);
    if(init.method==='PATCH') {
      if(url.searchParams.get('currentDocument.exists')==='false'&&existing)return json({error:{}},409);
      const merged=url.searchParams.has('updateMask.fieldPaths')?{...existing?.fields,...body.fields}:body.fields;
      const doc={name:base+path,fields:merged,updateTime:new Date(now+(++sequence)).toISOString()};docs.set(path,doc);return json(doc);
    }
    if(init.method==='DELETE'){docs.delete(path);return new Response(null,{status:204});}
    return existing?json(existing):json({error:{}},404);
  }
  throw new Error('Network outside the offline fixture is forbidden');
}
before(async()=>{
  const pair=await crypto.subtle.generateKey({name:'RSASSA-PKCS1-v1_5',modulusLength:2048,publicExponent:new Uint8Array([1,0,1]),hash:'SHA-256'},true,['sign','verify']);
  key=pair.privateKey;jwk={...await crypto.subtle.exportKey('jwk',pair.publicKey),kid:'ephemeral-fixture-key'};
  secret=JSON.stringify({project_id:PROJECT,client_email:'offline-fixture@example.invalid',private_key:'-----BEGIN PRIVATE KEY-----\n'+Buffer.from(await crypto.subtle.exportKey('pkcs8',key)).toString('base64')+'\n-----END PRIVATE KEY-----'});
});
beforeEach(()=>{
  now=Date.now();sequence=0;docs=new Map();accounts=new Map();calls=[];sent=[];attempts=[];fcmError=null;failToken=null;
  env={DM_AGRO_ENVIRONMENT:'beta',FIREBASE_BETA_SERVICE_ACCOUNT:secret};
  worker=createBetaWorker({project:PROJECT,fetcher:fakeFetch,clock:()=>now});
  user(OWNER,{nome:'Administrador fictício'});user('op');user('supA',profile('supervisor','santo_inacio','A'));user('otherFront',profile('supervisor','colorado'));
  put('usuarios/prodOnly',profile('supervisor'));user('prodOnly',null);
  put('equipes/santo_inacio_C/registros/stable-sentinel',{preserve:true});
});
async function jwt(uid,extra={}) {
  const second=Math.floor(now/1000);const unsigned=encode({alg:'RS256',kid:jwk.kid})+'.'+encode({sub:uid,aud:PROJECT,iss:`https://securetoken.google.com/${PROJECT}`,iat:second,exp:second+3600,auth_time:second,...extra});
  return unsigned+'.'+Buffer.from(await crypto.subtle.sign('RSASSA-PKCS1-v1_5',key,new TextEncoder().encode(unsigned))).toString('base64url');
}
async function post(uid,path,data={},options={}) {
  const start=calls.length;const response=await worker.fetch(new Request('https://dm-agro-beta.offline.workers.dev'+(options.rawPath?'':'/beta/v1')+path,
    {method:'POST',headers:{Authorization:'Bearer '+(options.token||await jwt(uid,options.claims)), 'Content-Type':'application/json'},body:JSON.stringify({environment:'beta',protocol:PROTOCOL,...data})}),options.env||env);
  return{status:response.status,data:await response.json(),used:calls.length-start};
}
async function device(uid,front='santo_inacio',label=uid) {
  const token='fictitious-fcm-token-'+label.padEnd(15,'0');const id=await hash(token);
  beta(`dispositivos/${uid}/tokens/${id}`,{token,front,seen:now,environment:'beta'});beta(`vinculosTokens/${id}`,{uid,front,seen:now});return token;
}
async function record({id='issue',group='pendencias',uid='op',team='santo_inacio_C',version=1,...extra}={}) {
  const key=await hash('item|'+group+'|'+id);
  beta(`equipes/${team}/registros/${key}`,{formato:2,tipo:'item',grupo:group,chave:id,dados:{id,equipamento:'FICTICIO',descricao:'Revisar rolamento',concluida:false},autorUid:uid,editadoPor:uid,equipeId:team,frenteId:team.slice(0,team.lastIndexOf('_')),turno:team.split('_').at(-1),versao:version,excluido:false,...extra});
  return{kind:'record',team,key,revision:version};
}
async function finish(uid,event) {
  let result;
  for(let i=0;i<60;i++){result=await post(uid,'/events/notify',event);assert.equal(result.status,200);assert.ok(result.used<=45);if(!result.data.pending)return result;}
  assert.fail('A paginated event must eventually complete');
}

test('health identifies only the Beta protocol and never exposes its credential',async()=>{
  const response=await worker.fetch(new Request('https://offline.workers.dev/health'),env);const body=await response.json();
  assert.equal(body.environment,'beta');assert.equal(body.protocol,1);assert.equal(body.workspace,ROOT);assert.equal(body.project,PROJECT);assert.equal(body.configured,true);
  assert.equal(body.notificationsOnly,true);assert.equal(calls.length,0);assert.equal(JSON.stringify(body).includes('PRIVATE KEY'),false);
});
test('legacy secrets, wrong projects and absent Beta configuration fail before any network request',async()=>{
  for(const config of [{DM_AGRO_ENVIRONMENT:'beta',FIREBASE_SERVICE_ACCOUNT:secret},{...env,DM_AGRO_ENVIRONMENT:'production'},{...env,FIREBASE_BETA_SERVICE_ACCOUNT:JSON.stringify({...JSON.parse(secret),project_id:'demo-other-project'})}]) {
    assert.equal((await post('supA','/devices/register',{token:'fictitious-valid-token-12345'},{env:config})).status,503);
  }
  assert.equal(calls.length,0);
});
test('all legacy and account mutation routes are unavailable, including to the owner',async()=>{
  for(const path of ['/accounts/delete','/accounts/correct','/devices/register'])assert.equal((await post(OWNER,path,{uid:'op'},{rawPath:true})).status,404);
  for(const path of ['/accounts/delete','/accounts/correct'])assert.equal((await post(OWNER,path,{uid:'op'})).status,404);
  assert.equal(calls.length,0);assert.ok(accounts.has('op'));assert.ok(docs.has('equipes/santo_inacio_C/registros/stable-sentinel'));
});
test('requests require the Beta marker and reject foreign namespaces and oversized payloads',async()=>{
  assert.equal((await post('supA','/devices/register',{environment:undefined,token:'fictitious-valid-token-12345'})).status,400);
  assert.equal((await post('supA','/devices/register',{protocol:2,token:'fictitious-valid-token-12345'})).status,400);
  assert.equal((await post('supA','/devices/register',{workspace:'equipes',token:'fictitious-valid-token-12345'})).status,400);
  assert.equal((await post('supA','/devices/register',{token:'x'.repeat(13000)})).status,413);assert.equal(calls.length,0);
});
test('signature, issuer, audience, expiration, disabled accounts and revoked sessions are checked',async()=>{
  for(const claims of [{aud:'demo-another-project'},{iss:'https://example.invalid'},{exp:Math.floor(now/1000)-1},{auth_time:Math.floor(now/1000)+600}])
    assert.equal((await post('supA','/devices/register',{token:'fictitious-valid-token-12345'},{claims})).status,401);
  const signed=await jwt('supA');const parts=signed.split('.');parts[1]=encode({...JSON.parse(Buffer.from(parts[1],'base64url').toString()),sub:OWNER});
  assert.equal((await post('supA','/devices/register',{token:'fictitious-valid-token-12345'},{token:parts.join('.')})).status,401);
  assert.equal(calls.some(call=>call.host==='identitytoolkit.googleapis.com'),false);
  accounts.get('supA').disabled=true;assert.equal((await post('supA','/devices/register',{token:'fictitious-valid-token-12345'})).status,401);
  accounts.get('supA').disabled=false;accounts.get('supA').validSince=String(Math.floor(now/1000)+100);
  assert.equal((await post('supA','/devices/register',{token:'fictitious-valid-token-12345'})).status,401);
});
test('production approval does not grant Beta access and only approved managers register devices',async()=>{
  user('pending',profile('supervisor','santo_inacio','C','pendente'));user('blocked',profile('supervisor','santo_inacio','C','bloqueado'));
  for(const uid of ['prodOnly','op','pending','blocked'])assert.equal((await post(uid,'/devices/register',{token:'fictitious-valid-token-12345'})).status,403);
  assert.equal((await post('supA','/devices/register',{token:'fictitious-valid-token-12345',front:'colorado'})).status,403);
  const result=await post('supA','/devices/register',{token:'fictitious-valid-token-12345'});assert.equal(result.status,200);
  const id=await hash('fictitious-valid-token-12345');assert.equal(raw(docs.get(`${ROOT}/dispositivos/supA/tokens/${id}`)).front,'santo_inacio');
});
test('all shifts of the same front receive records; another front and the authors own device do not',async()=>{
  const own=await device(OWNER);const same=await device('supA');await device('otherFront','colorado');await device('op');
  const event=await record();await finish('op',event);
  assert.deepEqual(sent.map(m=>m.token).sort(),[own,same].sort());
  assert.ok(sent.every(m=>m.data.environment==='beta'&&m.data.protocol==='1'&&m.data.workspace===ROOT&&m.data.team==='santo_inacio_C'&&m.android.priority==='HIGH'&&!m.notification));
  assert.ok(sent.every(m=>m.data.body.includes('Revisar rolamento')));
  const contract=JSON.parse(readFileSync(new URL('../app/src/test/resources/beta-notification.json',import.meta.url),'utf8'));
  assert.deepEqual(sent.find(m=>m.data.targetUid==='supA').data,contract);
  const count=sent.length;await finish('op',event);assert.equal(sent.length,count);
});
test('the administrator receives only the selected front and devices expire after 30 days',async()=>{
  await device(OWNER,'colorado');await device('supA');
  for(const [path,doc] of docs)if(path.includes('/dispositivos/supA/'))doc.fields.seen=pack(now-31*86400000);
  await finish('op',await record());assert.equal(sent.length,0);
});
test('a supervisor can notify an edit in another shift of their front but cannot notify another front',async()=>{
  await device(OWNER);await device('supA');const own=await record({uid:'supA'});await finish('supA',own);
  assert.equal(sent.length,1);assert.equal(sent[0].data.targetUid,OWNER);
  const foreign=await record({uid:'supA',team:'colorado_C'});assert.equal((await post('supA','/events/notify',foreign)).status,403);
});
test('pending supervisor requests reach only the owner; helper requests reach their front supervisors',async()=>{
  await device(OWNER);await device('supA');user('newSup',profile('operador','santo_inacio','C','pendente','supervisor'));
  await finish('newSup',{kind:'user',uid:'newSup'});assert.deepEqual(sent.map(m=>m.data.targetUid),[OWNER]);
  sent=[];user('newHelper',profile('operador','santo_inacio','B','pendente','ajudante'));
  await finish('newHelper',{kind:'user',uid:'newHelper'});assert.deepEqual(sent.map(m=>m.data.targetUid).sort(),[OWNER,'supA'].sort());
  assert.equal((await post('newHelper','/events/notify',{kind:'user',uid:'newSup'})).status,403);
});
test('opaque contents, contradictory fields, personal maps and events from another editor are rejected',async()=>{
  for(const extra of [{conteudo:'{"descricao":"forged"}'},{formato:1},{excluido:true},{editadoPor:'other'},{dados:{id:'issue',__arquivado:true}}])
    assert.equal((await post('op','/events/notify',await record(extra))).status,403);
  assert.equal((await post('op','/events/notify',await record({group:'mapas'}))).status,403);
  assert.equal(sent.length,0);
});
test('stale revisions are superseded, future revisions retry, and new revisions have independent deduplication',async()=>{
  await device(OWNER);const first=await record();await finish('op',first);
  const second=await record({version:2});assert.equal((await post('op','/events/notify',first)).data.superseded,true);
  assert.equal((await post('op','/events/notify',{...second,revision:3})).status,409);
  await finish('op',second);assert.equal(sent.length,2);assert.notEqual(sent[0].data.eventId,sent[1].data.eventId);assert.equal(sent[1].data.title,'Registro atualizado');
});
test('pagination reaches more than twelve supervisors and more than three devices per supervisor within bounded requests',async()=>{
  for(let i=0;i<16;i++){const uid='manager_'+String(i).padStart(2,'0');user(uid,profile('supervisor'));await device(uid);}
  for(let i=0;i<8;i++)await device('supA','santo_inacio','many_'+i);
  const result=await finish('op',await record());assert.equal(result.data.acceptedCount,24);assert.equal(sent.length,24);assert.equal(new Set(sent.map(m=>m.token)).size,24);
});
test('successful device checkpoints are not resent when a later device fails',async()=>{
  const tokens=[await device(OWNER,'santo_inacio','device1'),await device(OWNER,'santo_inacio','device2')];
  const sorted=await Promise.all(tokens.map(async token=>({token,id:await hash(token)})));sorted.sort((a,b)=>a.id<b.id?-1:1);
  failToken=sorted[1].token;fcmError={status:500,body:{error:{message:'offline failure'}}};const event=await record();
  assert.equal((await post('op','/events/notify',event)).status,503);assert.equal(sent.length,1);
  fcmError=null;await finish('op',event);assert.equal(sent.length,2);assert.equal(attempts.filter(t=>t===sorted[0].token).length,1);
});
test('generic FCM failures keep the device; only UNREGISTERED removes its Beta registration',async()=>{
  const token=await device(OWNER);const id=await hash(token);const path=`${ROOT}/dispositivos/${OWNER}/tokens/${id}`;
  const event=await record();fcmError={status:404,body:{error:{message:'unrelated 404'}}};
  assert.equal((await post('op','/events/notify',event)).status,503);assert.ok(docs.has(path));
  fcmError={status:404,body:{error:{details:[{errorCode:'UNREGISTERED'}]}}};await finish('op',event);assert.equal(docs.has(path),false);
  assert.ok(docs.has('equipes/santo_inacio_C/registros/stable-sentinel'));
});
test('changed permissions are checked again during retry and a transferred device cannot receive the previous account data',async()=>{
  const token=await device('supA');const id=await hash(token);beta(`vinculosTokens/${id}`,{uid:'otherFront',front:'colorado',seen:now});
  await finish('op',await record());assert.equal(sent.length,0);
  beta('usuarios/supA',profile('supervisor','colorado'));await finish('op',await record({id:'another'}));assert.equal(sent.length,0);
});
test('test delivery and unregister operate only on the signed-in managers own Beta device',async()=>{
  const token=await device('supA');assert.equal((await post('op','/devices/test',{token})).status,403);
  assert.equal((await post(OWNER,'/devices/test',{token})).status,400);
  const result=await post('supA','/devices/test',{token});assert.equal(result.status,200);assert.equal(result.data.acceptedByFcm,true);
  assert.equal((await post('supA','/devices/test',{token})).status,429);
  assert.equal((await post('supA','/devices/unregister',{token})).status,200);
  assert.equal((await post('supA','/devices/test',{token})).status,400);
  assert.equal(accounts.get('supA').passwordHash,'fixture-unchanged');assert.ok(docs.has('usuarios/prodOnly'));
});
test('an active lease defers concurrent delivery and an expired lease resumes without repeating checkpointed devices',async()=>{
  for(let i=0;i<9;i++)await device(OWNER,'santo_inacio','lease_'+i);
  const event=await record();const first=await post('op','/events/notify',event);
  assert.equal(first.status,200);assert.equal(first.data.pending,true);assert.equal(sent.length,6);
  const path=[...docs.keys()].find(p=>p.startsWith(ROOT+'/eventosNotificacao/'));
  put(path,{...raw(docs.get(path)),lease:now+90000});
  assert.equal((await post('op','/events/notify',event)).data.pending,true);assert.equal(sent.length,6);
  now+=91000;await finish('op',event);assert.equal(sent.length,9);assert.equal(new Set(sent.map(m=>m.token)).size,9);
});
test('old committed records do not create stale alerts after the delivery window',async()=>{
  await device(OWNER);const event=await record();docs.get(`${ROOT}/equipes/${event.team}/registros/${event.key}`).updateTime=new Date(now-2*86400000).toISOString();
  const result=await post('op','/events/notify',event);assert.equal(result.status,200);assert.equal(result.data.expired,true);assert.equal(sent.length,0);
});
test('the caller cannot supply notification text or notify a forged duplicate document key',async()=>{
  const event=await record();assert.equal((await post('op','/events/notify',{...event,title:'Caller supplied title'})).status,400);
  const data=raw(docs.get(`${ROOT}/equipes/${event.team}/registros/${event.key}`));const other='a'.repeat(64);
  beta(`equipes/${event.team}/registros/${other}`,data);
  assert.equal((await post('op','/events/notify',{...event,key:other})).status,400);assert.equal(sent.length,0);
});
test('an expired device can also be removed during the delivery test using its updated checkpoint',async()=>{
  const token=await device('supA');const id=await hash(token);
  fcmError={status:404,body:{error:{details:[{errorCode:'UNREGISTERED'}]}}};
  const result=await post('supA','/devices/test',{token});assert.equal(result.status,409);
  assert.equal(docs.has(`${ROOT}/dispositivos/supA/tokens/${id}`),false);
  assert.equal(docs.has(`${ROOT}/vinculosTokens/${id}`),false);
  assert.ok(docs.has('equipes/santo_inacio_C/registros/stable-sentinel'));
});
