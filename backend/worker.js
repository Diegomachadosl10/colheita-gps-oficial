/* DM AGRO: Cloudflare Workers Free + Firebase Spark. No private credential belongs in the APK. */
export const PROJECT = 'dm-agro-c0e04';
export const OWNER = 'Z8zjZP3iWjRSD6imwiDO4VLboOY2';
const FRONTS = ['santo_inacio','santa_ines','itororo','cafeara','lupionopolis','colorado','sandovalina','jardim_olinda','itaguaje'];
const GROUPS = {pendencias:'Nova pendência',ocorrencias_campo:'Nova ocorrência de campo',estoques_salvos:'Novo estoque',historico_estoque:'Movimentação de estoque',relatorios_turno:'Novo relatório de turno',caminhoes:'Novo registro de caminhão',movimentos:'Nova movimentação'};
const DOCS = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/(default)/documents`;
const AUTH = `https://identitytoolkit.googleapis.com/v1/projects/${PROJECT}/accounts:`;
let oauthCache, keyCache;
class Failure extends Error { constructor(status, message) { super(message); this.status=status; } }
const check = (condition, message='Operação não autorizada', status=403) => { if (!condition) throw new Failure(status,message); };
export const alias = value => { check(typeof value==='string' && /^[0-9]{1,20}$/.test(value),'Matrícula inválida',400); return `matricula.${value}@acesso.dm-agro.app`; };
const uidValid = value => typeof value==='string' && /^[A-Za-z0-9_-]{1,128}$/.test(value);
export const team = p => p && FRONTS.includes(p.frenteId) && ['A','B','C'].includes(p.turno) ? `${p.frenteId}_${p.turno}` : null;
export const approved = p => p?.status==='aprovado' && ['operador','funcionario','tratorista','supervisor'].includes(p.papel) && team(p)!=null;
export const manages = (uid,p) => uid===OWNER || approved(p) && p.papel==='supervisor';
export const recipients = (actor, sourceTeam, supervisors, supervisorRequest=false) => [...new Set([
  OWNER, ...(!supervisorRequest ? supervisors.filter(p=>approved(p)&&p.papel==='supervisor'&&team(p)===sourceTeam).map(p=>p.uid):[])
])].filter(uid=>uid!==actor);
export const safeTarget = (actor,target) => { check(actor===OWNER); check(uidValid(target)&&target!==OWNER&&target!==actor,'A conta administradora não pode ser alterada'); };
export const wantsNotice = (uid,device,sourceTeam,profile) => uid===OWNER
  ? device.front===sourceTeam.slice(0,sourceTeam.lastIndexOf('_'))
  : manages(uid,profile)&&team(profile)===sourceTeam;
export function unpack(value) {
  if ('stringValue' in value) return value.stringValue;
  if ('booleanValue' in value) return value.booleanValue;
  if ('integerValue' in value) return Number(value.integerValue);
  if ('doubleValue' in value) return value.doubleValue;
  if ('timestampValue' in value) return value.timestampValue;
  if ('nullValue' in value) return null;
  if ('arrayValue' in value) return (value.arrayValue.values||[]).map(unpack);
  return Object.fromEntries(Object.entries(value.mapValue?.fields||{}).map(([k,v])=>[k,unpack(v)]));
}
function pack(value) {
  if (value==null) return {nullValue:null};
  if (typeof value==='boolean') return {booleanValue:value};
  if (typeof value==='number') return Number.isInteger(value)?{integerValue:String(value)}:{doubleValue:value};
  if (typeof value==='string') return {stringValue:value};
  if (Array.isArray(value)) return {arrayValue:{values:value.map(pack)}};
  return {mapValue:{fields:fields(value)}};
}
const fields = value => Object.fromEntries(Object.entries(value).map(([k,v])=>[k,pack(v)]));
const fromDoc = doc => doc?{data:Object.fromEntries(Object.entries(doc.fields||{}).map(([k,v])=>[k,unpack(v)])),updateTime:doc.updateTime,name:doc.name}:null;
const b64 = bytes => btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/=/g,'').replace(/\+/g,'-').replace(/\//g,'_');
const decode64 = value => Uint8Array.from(atob(value.replace(/-/g,'+').replace(/_/g,'/')),c=>c.charCodeAt(0));
const encoded = value => b64(new TextEncoder().encode(JSON.stringify(value)));
const digest = async value => [...new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(value)))].map(x=>x.toString(16).padStart(2,'0')).join('');
async function token(env) {
  if (oauthCache?.until>Date.now()+60000) return oauthCache.value;
  let sa; try { sa=JSON.parse(env.FIREBASE_SERVICE_ACCOUNT||''); } catch { throw new Failure(503,'Configure o segredo FIREBASE_SERVICE_ACCOUNT na Cloudflare'); }
  check(sa.project_id===PROJECT&&sa.private_key&&sa.client_email,'Chave de outro projeto ou incompleta',503);
  const pem=sa.private_key.replace(/-----[^-]+-----|\s/g,'');
  const privateKey=await crypto.subtle.importKey('pkcs8',decode64(pem),{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['sign']);
  const now=Math.floor(Date.now()/1000);
  const unsigned=encoded({alg:'RS256',typ:'JWT'})+'.'+encoded({iss:sa.client_email,scope:'https://www.googleapis.com/auth/cloud-platform',aud:'https://oauth2.googleapis.com/token',iat:now,exp:now+3600});
  const jwt=unsigned+'.'+b64(await crypto.subtle.sign('RSASSA-PKCS1-v1_5',privateKey,new TextEncoder().encode(unsigned)));
  const response=await fetch('https://oauth2.googleapis.com/token',{method:'POST',body:new URLSearchParams({grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',assertion:jwt})});
  check(response.ok,'Não foi possível autorizar o serviço Firebase',503);
  const data=await response.json(); oauthCache={value:data.access_token,until:Date.now()+data.expires_in*1000}; return data.access_token;
}
async function google(env,url,method='GET',body,missing=false) {
  const response=await fetch(url,{method,headers:{Authorization:'Bearer '+await token(env),'Content-Type':'application/json'},...(body!==undefined?{body:JSON.stringify(body)}:{})});
  if (missing&&response.status===404) return null;
  const result=response.status===204?{}:await response.json().catch(()=>({}));
  if (!response.ok) {
    const code=result.error?.message||'';
    if (code.includes('EMAIL_EXISTS')) throw new Failure(409,'A matrícula correta já possui outra conta. Nada foi misturado.');
    if (code.includes('USER_NOT_FOUND')) throw new Failure(404,'Credencial não encontrada');
    if ([409,412].includes(response.status)) throw new Failure(409,'Registro alterado durante a operação. Atualize e tente novamente.');
    throw new Failure(503,'Serviço Firebase indisponível ou sem permissão administrativa');
  }
  return result;
}
async function get(env,path) { return fromDoc(await google(env,`${DOCS}/${path}`,'GET',undefined,true)); }
async function put(env,path,value,precondition,mask=false) {
  const params=new URLSearchParams();
  if (precondition===false) params.set('currentDocument.exists','false');
  else if (typeof precondition==='string') params.set('currentDocument.updateTime',precondition);
  if (mask) for (const key of Object.keys(value)) params.append('updateMask.fieldPaths',key);
  return fromDoc(await google(env,`${DOCS}/${path}?${params}`,'PATCH',{fields:fields(value)}));
}
async function query(env,parent,collection,filter,order,limit=100) {
  const structuredQuery={from:[{collectionId:collection}],limit,...(filter?{where:{fieldFilter:{field:{fieldPath:filter[0]},op:'EQUAL',value:pack(filter[1])}}}:{}),...(order?{orderBy:[{field:{fieldPath:order},direction:'DESCENDING'}]}:{})};
  const rows=await google(env,`${DOCS}${parent?'/'+parent:''}:runQuery`,'POST',{structuredQuery});
  return rows.filter(r=>r.document).map(r=>fromDoc(r.document));
}
async function identity(env,request) {
  const bearer=request.headers.get('Authorization')||'';
  check(bearer.startsWith('Bearer ')&&bearer.length<20000,'Entre novamente no aplicativo',401);
  const parts=bearer.slice(7).split('.'); check(parts.length===3,'Token inválido',401);
  let head,claims;
  try { head=JSON.parse(new TextDecoder().decode(decode64(parts[0]))); claims=JSON.parse(new TextDecoder().decode(decode64(parts[1]))); } catch { throw new Failure(401,'Token inválido'); }
  const now=Math.floor(Date.now()/1000);
  check(head.alg==='RS256'&&typeof head.kid==='string'&&claims.aud===PROJECT&&claims.iss===`https://securetoken.google.com/${PROJECT}`&&uidValid(claims.sub)&&claims.exp>now&&claims.iat<=now+60,'Entre novamente no aplicativo',401);
  if (!keyCache||keyCache.until<Date.now()||!keyCache.keys.some(k=>k.kid===head.kid)) {
    const response=await fetch('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com');
    check(response.ok,'Verificação de identidade indisponível',503);
    keyCache={keys:(await response.json()).keys,until:Date.now()+3600000};
  }
  const jwk=keyCache.keys.find(k=>k.kid===head.kid); check(jwk,'Token não reconhecido',401);
  const key=await crypto.subtle.importKey('jwk',jwk,{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['verify']);
  check(await crypto.subtle.verify('RSASSA-PKCS1-v1_5',key,decode64(parts[2]),new TextEncoder().encode(parts[0]+'.'+parts[1])),'Token inválido',401);
  const auth=await google(env,AUTH+'lookup','POST',{localId:[claims.sub]});
  const account=auth.users?.[0]; check(account&&!account.disabled&&Number(account.validSince||0)<=claims.auth_time,'Sessão revogada. Entre novamente.',401);
  return {uid:claims.sub,claims,profile:(await get(env,`usuarios/${claims.sub}`))?.data||{}};
}
async function accountAction(env,actor,body,path) {
  safeTarget(actor.uid,body.uid);
  check(actor.claims.auth_time>Math.floor(Date.now()/1000)-900,'Confirme sua senha de administrador para continuar',401);
  const target=await get(env,`usuarios/${body.uid}`); check(target,'Cadastro não encontrado',404);
  const auth=await google(env,AUTH+'lookup','POST',{localId:[body.uid]}); const user=auth.users?.[0];
  const audit=`auditoriaContas/${crypto.randomUUID()}`;
  if (path==='/accounts/correct') {
    const email=alias(body.matricula); check(user,'Credencial não encontrada',404);
    check(user.email?.endsWith('@acesso.dm-agro.app'),'A correção por matrícula não altera contas com e-mail pessoal',400);
    await put(env,audit,{acao:'corrigir_matricula',uid:body.uid,anterior:target.data.matricula||'',nova:body.matricula,por:actor.uid,em:new Date().toISOString(),status:'iniciada'},false);
    await google(env,AUTH+'update','POST',{localId:body.uid,email});
    try {
      await put(env,`usuarios/${body.uid}`,{matricula:body.matricula,...(target.data.status==='excluido'?{status:'pendente'}:{})},target.updateTime,true);
    } catch (error) {
      await google(env,AUTH+'update','POST',{localId:body.uid,email:user.email});
      throw error;
    }
    await put(env,audit,{status:'concluida'},undefined,true);
    return {ok:true,message:'Matrícula corrigida no Authentication e no cadastro. A senha e os registros foram preservados.'};
  }
  await put(env,audit,{acao:'excluir_conta',uid:body.uid,matricula:target.data.matricula||'',por:actor.uid,em:new Date().toISOString(),status:'iniciada'},false);
  const blocked=await put(env,`usuarios/${body.uid}`,{status:'excluido',excluidoPor:actor.uid,excluidoEm:new Date().toISOString()},target.updateTime,true);
  if (user) await google(env,AUTH+'delete','POST',{localId:body.uid});
  // A deleted Auth token may remain valid briefly. Keep an inaccessible UID-only closure marker
  // before removing the profile so that token can never recreate its former cadastro.
  await put(env,`contasEncerradas/${body.uid}`,{por:actor.uid,em:new Date().toISOString()});
  await google(env,`${DOCS}/usuarios/${body.uid}?currentDocument.updateTime=${encodeURIComponent(blocked.updateTime)}`,'DELETE');
  await put(env,audit,{status:'concluida'},undefined,true);
  return {ok:true,message:'Conta removida do Authentication e do cadastro. A matrícula foi liberada e os registros de trabalho foram preservados.'};
}
async function notify(env,actor,body) {
  let eventId,sourceTeam,title,description,requestSupervisor=false;
  if (body.kind==='user') {
    check(body.uid===actor.uid&&actor.profile.status==='pendente'); sourceTeam=team(actor.profile); check(sourceTeam);
    requestSupervisor=actor.profile.solicitacaoPerfil==='supervisor';
    title=requestSupervisor?'Solicitação de supervisor':'Nova solicitação de acesso'; description=actor.profile.nome||'Pessoa da equipe'; eventId='user_'+actor.uid;
  } else {
    check(actor.uid===OWNER||approved(actor.profile));
    check(typeof body.team==='string'&&FRONTS.some(f=>['A','B','C'].some(s=>body.team===f+'_'+s))&&/^[a-f0-9]{64}$/.test(body.key),'Evento inválido',400);
    check(actor.uid===OWNER||team(actor.profile)===body.team);
    const record=await get(env,`equipes/${body.team}/registros/${body.key}`); check(record,'Registro ainda não disponível',409);
    const d=record.data; check(d.tipo==='item'&&GROUPS[d.grupo]&&!d.excluido&&d.autorUid===actor.uid&&Date.parse(record.updateTime)>Date.now()-3600000);
    let content; try {content=JSON.parse(d.conteudo);} catch {throw new Failure(400,'Conteúdo inválido');}
    title=content.categoriaRegistro==='equipamento'?'Novo equipamento':content.categoriaRegistro==='chuva'?'Chuva registrada':GROUPS[d.grupo];
    description=[actor.profile.nome||'Administrador',content.equipamento,content.descricao].filter(Boolean).join(' · ').slice(0,170);
    sourceTeam=body.team; eventId=await digest('record/'+body.team+'/'+body.key);
  }
  const path=`eventosNotificacao/${eventId}`;
  let event=await get(env,path);
  if (event?.data.status==='concluido') return {ok:true};
  if (event?.data.lease>Date.now()) return {ok:false,pending:true};
  const supervisors=event?[]:(await query(env,'','usuarios',['papel','supervisor'])).map(p=>({...p.data,uid:p.name.split('/').pop()}));
  const targets=event?.data.targets||recipients(actor.uid,sourceTeam,supervisors,requestSupervisor);
  const lease=await put(env,path,{targets,done:event?.data.done||[],lease:Date.now()+120000,status:'enviando'},event?.updateTime||false);
  const done=[...(event?.data.done||[])]; let pending=false;
  try {
    for (const uid of targets.filter(u=>!done.includes(u)).slice(0,3)) {
      const p=(await get(env,`usuarios/${uid}`))?.data;
      if (!manages(uid,p)||uid!==OWNER&&team(p)!==sourceTeam) {done.push(uid);continue;}
      const devices=await query(env,`dispositivos/${uid}`,'tokens',null,'seen',3);
      for (const device of devices.filter(device=>wantsNotice(uid,device.data,sourceTeam,p))) {
        const response=await fetch(`https://fcm.googleapis.com/v1/projects/${PROJECT}/messages:send`,{method:'POST',headers:{Authorization:'Bearer '+await token(env),'Content-Type':'application/json'},body:JSON.stringify({message:{token:device.data.token,data:{targetUid:uid,eventId,team:sourceTeam,title,body:description},android:{priority:'HIGH',ttl:'3600s'}}})});
        if (response.status===404) { await google(env,device.name.replace('projects/', 'https://firestore.googleapis.com/v1/projects/'),'DELETE'); continue; }
        if (!response.ok) throw new Failure(503,'Envio de avisos pendente');
      }
      done.push(uid);
    }
    pending=targets.some(uid=>!done.includes(uid));
  } finally {
    await put(env,path,{done,lease:0,status:targets.every(uid=>done.includes(uid))?'concluido':'pendente'},lease.updateTime,true);
  }
  return {ok:!pending,pending};
}
export async function route(request,env) {
  const url=new URL(request.url);
  if (url.pathname==='/health'&&request.method==='GET') return {ok:true,project:PROJECT,configured:!!env.FIREBASE_SERVICE_ACCOUNT};
  check(request.method==='POST','Método indisponível',405);
  check(Number(request.headers.get('Content-Length')||0)<=12000,'Pedido acima do limite',413);
  const reader=request.body?.getReader(); const chunks=[]; let size=0;
  if(reader) while(true) { const part=await reader.read(); if(part.done) break; size+=part.value.byteLength; check(size<=12000,'Pedido acima do limite',413); chunks.push(part.value); }
  const bytes=new Uint8Array(size); let offset=0; for(const part of chunks) { bytes.set(part,offset); offset+=part.byteLength; }
  const raw=new TextDecoder().decode(bytes);
  let body; try {body=JSON.parse(raw);} catch {throw new Failure(400,'Pedido inválido');}
  const actor=await identity(env,request);
  if (url.pathname==='/devices/register') {
    check(manages(actor.uid,actor.profile)); check(typeof body.token==='string'&&body.token.length>=20&&body.token.length<=4096&&!/\s/.test(body.token),'Token de aparelho inválido',400);
    const front=actor.uid===OWNER?body.front:actor.profile.frenteId;
    check(FRONTS.includes(front),'Escolha a frente das notificações',400);
    await put(env,`dispositivos/${actor.uid}/tokens/${await digest(body.token)}`,{token:body.token,front,seen:Date.now()}); return {ok:true};
  }
  if (url.pathname==='/events/notify') return notify(env,actor,body);
  if (['/accounts/correct','/accounts/delete'].includes(url.pathname)) {
    safeTarget(actor.uid,body.uid);
    const lockPath=`operacoesConta/${body.uid}`; const existing=await get(env,lockPath);
    check(!existing||existing.data.lease<Date.now(),'Já existe uma alteração nesta conta. Aguarde e tente novamente.',409);
    const lock=await put(env,lockPath,{lease:Date.now()+120000,actor:actor.uid},existing?.updateTime||false);
    try { return await accountAction(env,actor,body,url.pathname); }
    finally { await put(env,lockPath,{lease:0},lock.updateTime,true); }
  }
  throw new Failure(404,'Rota indisponível');
}
export default {
  async fetch(request,env) {
    try { return Response.json(await route(request,env),{headers:{'Cache-Control':'no-store'}}); }
    catch (error) { return Response.json({ok:false,message:error instanceof Failure?error.message:'Operação não concluída. Tente novamente.'},{status:error instanceof Failure?error.status:503,headers:{'Cache-Control':'no-store'}}); }
  }
};
