/* DM AGRO Beta — Cloudflare Workers Free. Single file for the mobile editor.
 * No deployment is performed by this module. Never replace the stable Worker with it.
 */
export const PROJECT = 'dm-agro-c0e04';
export const OWNER = 'Z8zjZP3iWjRSD6imwiDO4VLboOY2';
export const ROOT = 'ambientes/beta';
export const PROTOCOL = 1;
export const VERSION = 'beta-1.0.0';
const PREFIX = '/beta/v1';
const FRONTS = ['santo_inacio','santa_ines','itororo','cafeara','lupionopolis','colorado','sandovalina','jardim_olinda','itaguaje'];
const LEAVES = ['operador','funcionario','tratorista','ajudante'];
const GROUPS = {pendencias:'Nova pendência',ocorrencias_campo:'Nova ocorrência de campo',estoques_salvos:'Novo estoque',historico_estoque:'Movimentação de estoque',relatorios_turno:'Novo relatório de turno',caminhoes:'Novo registro de caminhão',movimentos:'Nova movimentação',historico_os:'Ordem de serviço atualizada'};
const PAGE = 12, MAX_SEND = 6, MAX_SCAN = 8, MAX_RECIPIENTS = 4;
class Failure extends Error { constructor(status,message) {super(message);this.status=status;} }
const check=(ok,message='Operação não autorizada',status=403)=>{if(!ok)throw new Failure(status,message);};
const uidValid=uid=>typeof uid==='string'&&/^[A-Za-z0-9_-]{1,128}$/.test(uid);
const team=p=>p&&FRONTS.includes(p.frenteId)&&['A','B','C'].includes(p.turno)?`${p.frenteId}_${p.turno}`:null;
const approved=p=>p?.status==='aprovado'&&[...LEAVES,'supervisor'].includes(p.papel)&&team(p)!==null;
const manager=(uid,p)=>uid===OWNER||approved(p)&&p.papel==='supervisor';
const sourceFront=value=>value.slice(0,value.lastIndexOf('_'));
const inFront=(uid,p,source)=>uid===OWNER||approved(p)&&p.papel==='supervisor'&&p.frenteId===sourceFront(source);
const tokenValid=t=>typeof t==='string'&&t.length>=20&&t.length<=4096&&!/\s/.test(t);
const text=v=>typeof v==='string'?v.replace(/[\u0000-\u001f\u007f]/g,' ').trim():'';
const b64=bytes=>btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/=/g,'').replace(/\+/g,'-').replace(/\//g,'_');
const un64=value=>Uint8Array.from(atob(value.replace(/-/g,'+').replace(/_/g,'/')),c=>c.charCodeAt(0));
const encoded=v=>b64(new TextEncoder().encode(JSON.stringify(v)));
export function unpack(value) {
  if('stringValue'in value)return value.stringValue;
  if('booleanValue'in value)return value.booleanValue;
  if('integerValue'in value)return Number(value.integerValue);
  if('doubleValue'in value)return value.doubleValue;
  if('timestampValue'in value)return value.timestampValue;
  if('nullValue'in value)return null;
  if('arrayValue'in value)return(value.arrayValue.values||[]).map(unpack);
  return Object.fromEntries(Object.entries(value.mapValue?.fields||{}).map(([k,v])=>[k,unpack(v)]));
}
function pack(v) {
  if(v===null)return{nullValue:null};
  if(typeof v==='boolean')return{booleanValue:v};
  if(typeof v==='number'){check(Number.isFinite(v),'Dado inválido',400);return Number.isInteger(v)?{integerValue:String(v)}:{doubleValue:v};}
  if(typeof v==='string')return{stringValue:v};
  if(Array.isArray(v))return{arrayValue:{values:v.map(pack)}};
  check(v&&typeof v==='object','Dado inválido',400);
  return{mapValue:{fields:Object.fromEntries(Object.entries(v).map(([k,x])=>[k,pack(x)]))}};
}
async function readJson(stream,limit,status=400) {
  const reader=stream?.getReader();let size=0;const chunks=[];
  if(reader)try {while(true){const part=await reader.read();if(part.done)break;size+=part.value.byteLength;check(size<=limit,'Pedido ou resposta acima do limite',status===400?413:status);chunks.push(part.value);}}
  finally{reader.releaseLock();}
  const bytes=new Uint8Array(size);let offset=0;for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.byteLength;}
  try{return JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(bytes));}catch{throw new Failure(status,'Conteúdo JSON inválido');}
}

/** Dependencies are injectable for offline tests; the deployed default pins the real project. */
export function createBetaWorker({project=PROJECT,fetcher=(...args)=>fetch(...args),cryptoImpl=crypto,clock=()=>Date.now()}={}) {
  const docs=`https://firestore.googleapis.com/v1/projects/${project}/databases/(default)/documents`;
  const auth=`https://identitytoolkit.googleapis.com/v1/projects/${project}/accounts:lookup`;
  const hash=async v=>[...new Uint8Array(await cryptoImpl.subtle.digest('SHA-256',new TextEncoder().encode(v)))].map(x=>x.toString(16).padStart(2,'0')).join('');
  let oauthCache,keyCache;
  function config(env) {
    check(env.DM_AGRO_ENVIRONMENT==='beta','Configure o ambiente Beta do serviço',503);
    let sa;try{sa=JSON.parse(env.FIREBASE_BETA_SERVICE_ACCOUNT||'');}catch{throw new Failure(503,'Configure a credencial exclusiva do serviço Beta');}
    check(sa.project_id===project&&typeof sa.client_email==='string'&&sa.client_email.length>3&&typeof sa.private_key==='string'&&sa.private_key.includes('BEGIN PRIVATE KEY'),'Credencial Beta incompleta ou de outro projeto',503);
    return sa;
  }
  function apiFor(env,sa) {
    let calls=0;const signal=AbortSignal.timeout(25000);
    const network=async(url,init={})=>{check(++calls<=45,'Envio aguardando continuação',503);return fetcher(url,{...init,redirect:'error',signal});};
    async function oauth() {
      if(oauthCache?.credential===env.FIREBASE_BETA_SERVICE_ACCOUNT&&oauthCache.until>clock()+60000)return oauthCache.value;
      const key=await cryptoImpl.subtle.importKey('pkcs8',un64(sa.private_key.replace(/-----[^-]+-----|\s/g,'')),{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['sign']);
      const now=Math.floor(clock()/1000);
      const unsigned=encoded({alg:'RS256',typ:'JWT'})+'.'+encoded({iss:sa.client_email,scope:'https://www.googleapis.com/auth/cloud-platform',aud:'https://oauth2.googleapis.com/token',iat:now,exp:now+3600});
      const jwt=unsigned+'.'+b64(await cryptoImpl.subtle.sign('RSASSA-PKCS1-v1_5',key,new TextEncoder().encode(unsigned)));
      const response=await network('https://oauth2.googleapis.com/token',{method:'POST',body:new URLSearchParams({grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',assertion:jwt})});
      check(response.ok,'Não foi possível autorizar o serviço Beta',503);const result=await readJson(response.body,256000,503);
      check(typeof result.access_token==='string'&&Number.isFinite(result.expires_in),'Resposta de autorização inválida',503);
      oauthCache={credential:env.FIREBASE_BETA_SERVICE_ACCOUNT,value:result.access_token,until:clock()+result.expires_in*1000};return result.access_token;
    }
    async function google(url,method='GET',body,missing=false) {
      const response=await network(url,{method,headers:{Authorization:'Bearer '+await oauth(),'Content-Type':'application/json'},...(body!==undefined?{body:JSON.stringify(body)}:{})});
      if(missing&&response.status===404)return null;
      if([409,412].includes(response.status))throw new Failure(409,'Registro alterado. Tente novamente.');
      check(response.ok,'Serviço Firebase indisponível ou sem permissão',503);
      return response.status===204?{}:readJson(response.body,1500000,503);
    }
    const relative=path=>{check(typeof path==='string'&&path.split('/').every(p=>p&&p!=='.'&&p!=='..'&&!/[?#%]/.test(p)),'Caminho inválido',400);return `${ROOT}/${path}`;};
    function document(raw,expected) {
      if(raw===null)return null;
      check(raw.name===expected&&typeof raw.updateTime==='string','Resposta de documento inválida',503);
      return{name:raw.name,updateTime:raw.updateTime,data:Object.fromEntries(Object.entries(raw.fields||{}).map(([k,v])=>[k,unpack(v)]))};
    }
    const full=path=>`${docs}/${relative(path)}`;
    async function get(path){const url=full(path);return document(await google(url,'GET',undefined,true),url.slice('https://firestore.googleapis.com/v1/'.length));}
    async function put(path,value,condition,merge=false) {
      const url=full(path);const params=new URLSearchParams();
      if(condition===false)params.set('currentDocument.exists','false');
      if(typeof condition==='string')params.set('currentDocument.updateTime',condition);
      if(merge)for(const key of Object.keys(value))params.append('updateMask.fieldPaths',key);
      const raw=await google(`${url}?${params}`,'PATCH',{fields:pack(value).mapValue.fields});
      return document(raw,url.slice('https://firestore.googleapis.com/v1/'.length));
    }
    async function remove(path,condition) {
      await google(`${full(path)}?currentDocument.updateTime=${encodeURIComponent(condition)}`,'DELETE');
    }
    async function query(parent,collection,filters=[],cursor='',limit=PAGE+1) {
      const fieldFilters=filters.map(([key,value])=>({fieldFilter:{field:{fieldPath:key},op:'EQUAL',value:pack(value)}}));
      const structuredQuery={from:[{collectionId:collection}],orderBy:[{field:{fieldPath:'__name__'},direction:'ASCENDING'}],limit,
        ...(fieldFilters.length?{where:fieldFilters.length===1?fieldFilters[0]:{compositeFilter:{op:'AND',filters:fieldFilters}}}:{}),
        ...(cursor?{startAt:{values:[{referenceValue:cursor}],before:false}}:{})};
      const prefix=full((parent?parent+'/':'')+collection).slice('https://firestore.googleapis.com/v1/'.length)+'/';
      const rows=await google(`${docs}/${ROOT}${parent?'/'+parent:''}:runQuery`,'POST',{structuredQuery});
      check(Array.isArray(rows),'Resposta de consulta inválida',503);
      return rows.filter(row=>row.document).map(({document:raw})=>{
        check(raw.name?.startsWith(prefix)&&!raw.name.slice(prefix.length).includes('/'),'Consulta fora do Beta',503);return document(raw,raw.name);
      });
    }
    async function identity(request) {
      const bearer=request.headers.get('Authorization')||'';check(bearer.startsWith('Bearer ')&&bearer.length<20000,'Entre novamente no aplicativo',401);
      const parts=bearer.slice(7).split('.');let head,claims,signature;
      try{check(parts.length===3,'Token inválido',401);head=JSON.parse(new TextDecoder().decode(un64(parts[0])));claims=JSON.parse(new TextDecoder().decode(un64(parts[1])));signature=un64(parts[2]);}catch{throw new Failure(401,'Token inválido');}
      const now=Math.floor(clock()/1000);
      check(head.alg==='RS256'&&typeof head.kid==='string'&&head.kid.length<=128&&uidValid(claims.sub)&&claims.aud===project&&claims.iss===`https://securetoken.google.com/${project}`
        &&[claims.exp,claims.iat,claims.auth_time].every(Number.isSafeInteger)&&claims.exp>now&&claims.iat<=now+60&&claims.auth_time<=now+60,'Token inválido ou expirado',401);
      if(!keyCache||keyCache.until<clock()||!keyCache.keys.some(k=>k.kid===head.kid)&&keyCache.fetched<clock()-5000) {
        const response=await network('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com');check(response.ok,'Verificação de identidade indisponível',503);
        const keys=(await readJson(response.body,256000,503)).keys;check(Array.isArray(keys),'Chaves de identidade indisponíveis',503);
        keyCache={keys,until:clock()+3600000,fetched:clock()};
      }
      const jwk=keyCache.keys.find(k=>k.kid===head.kid);check(jwk,'Token não reconhecido',401);
      let verified=false;try {
        const key=await cryptoImpl.subtle.importKey('jwk',jwk,{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['verify']);
        verified=await cryptoImpl.subtle.verify('RSASSA-PKCS1-v1_5',key,signature,new TextEncoder().encode(parts[0]+'.'+parts[1]));
      }catch{ /* Reject malformed keys/signatures without exposing cryptographic details. */ }
      check(verified,'Token inválido',401);
      const account=(await google(auth,'POST',{localId:[claims.sub]})).users?.[0];
      check(account?.localId===claims.sub&&!account.disabled&&Number.isFinite(Number(account.validSince||0))&&Number(account.validSince||0)<=claims.auth_time,'Sessão revogada. Entre novamente.',401);
      return{uid:claims.sub,profile:(await get(`usuarios/${claims.sub}`))?.data||{}};
    }
    async function send(device,uid,payload) {
      const tokenHash=device.name.split('/').pop();const owner=await get(`vinculosTokens/${tokenHash}`);
      if(owner?.data.uid!==uid||device.data.environment!=='beta'||!tokenValid(device.data.token)||await hash(device.data.token)!==tokenHash)return false;
      const response=await network(`https://fcm.googleapis.com/v1/projects/${project}/messages:send`,{method:'POST',headers:{Authorization:'Bearer '+await oauth(),'Content-Type':'application/json'},body:JSON.stringify({message:{token:device.data.token,data:{...payload,environment:'beta',protocol:String(PROTOCOL),workspace:ROOT,targetUid:uid},android:{priority:'HIGH',ttl:'3600s'}}})});
      if(response.ok)return true;
      let error={};try{error=await readJson(response.body,256000,503);}catch{ /* Keep device on ambiguous failures. */ }
      if(error.error?.details?.some(detail=>detail.errorCode==='UNREGISTERED')) {
        const path=device.name.split('/documents/'+ROOT+'/')[1];check(path,'Aparelho fora do Beta',503);
        await remove(path,device.updateTime);
        try{await remove(`vinculosTokens/${tokenHash}`,owner.updateTime);}catch(e){if(e.status!==409)throw e;}
        return false;
      }
      throw new Failure(503,'O Firebase não aceitou o aviso. Confira as permissões de Cloud Messaging do serviço Beta.');
    }
    return{get,put,remove,query,identity,send};
  }
  async function notify(api,actor,body) {
    let eventId,sourceTeam,title,description,onlyOwner=false;
    if(body.kind==='user') {
      check(body.uid===actor.uid&&actor.profile.status==='pendente'&&['operador','tratorista','ajudante','supervisor'].includes(actor.profile.solicitacaoPerfil));
      sourceTeam=team(actor.profile);check(sourceTeam);onlyOwner=actor.profile.solicitacaoPerfil==='supervisor';
      eventId='user_'+actor.uid;title=onlyOwner?'Solicitação de supervisor':'Nova solicitação de acesso';description=text(actor.profile.nome).slice(0,170);
    }else {
      check(body.kind==='record'&&typeof body.team==='string'&&FRONTS.some(f=>['A','B','C'].some(t=>body.team===f+'_'+t))&&/^[a-f0-9]{64}$/.test(body.key)
        &&Number.isSafeInteger(body.revision)&&body.revision>0,'Evento inválido',400);
      check(actor.uid===OWNER||approved(actor.profile)&&(actor.profile.papel==='supervisor'?actor.profile.frenteId===sourceFront(body.team):team(actor.profile)===body.team));
      const record=await api.get(`equipes/${body.team}/registros/${body.key}`);check(record,'Registro ainda não disponível',409);const d=record.data;
      check(d.formato===2&&d.tipo==='item'&&GROUPS[d.grupo]&&!('conteudo'in d)&&d.dados&&typeof d.dados==='object'&&!Array.isArray(d.dados)
        &&d.excluido===false&&!d.dados.__arquivado&&d.editadoPor===actor.uid&&d.equipeId===body.team&&team(d)===body.team&&Number.isSafeInteger(d.versao)
        &&typeof d.chave==='string'&&d.dados.id===d.chave&&typeof(d.dados.concluida??false)==='boolean','Registro não autorizado');
      check(await hash('item|'+d.grupo+'|'+d.chave)===body.key,'Identificador de registro inválido',400);
      if(actor.uid!==OWNER&&actor.profile.papel!=='supervisor')check(['pendencias','ocorrencias_campo'].includes(d.grupo)&&d.autorUid===actor.uid&&d.dados.categoriaRegistro!=='equipamento');
      if(body.revision<d.versao)return{ok:true,superseded:true};check(body.revision===d.versao,'Versão ainda não disponível',409);
      check(Number.isFinite(Date.parse(record.updateTime)),'Data do registro inválida',400);
      if(Date.parse(record.updateTime)<clock()-86400000)return{ok:true,expired:true};
      const content=d.dados;
      title=content.categoriaRegistro==='equipamento'?'Novo equipamento':content.categoriaRegistro==='chuva'?'Chuva registrada':content.categoriaRegistro==='peca'?'Peça a pedir':GROUPS[d.grupo];
      if(d.versao>1)title='Registro atualizado';
      description=[actor.profile.nome||'Administrador',content.os,content.equipamento,content.descricao].map(text).filter(Boolean).join(' · ').slice(0,170);
      sourceTeam=body.team;eventId=await hash('record/'+body.team+'/'+body.key+(d.versao>1?'/'+d.versao:''));
    }
    const path=`eventosNotificacao/${eventId}`;let existing=await api.get(path);
    if(existing?.data.status==='concluido')return{ok:true,pending:false,acceptedCount:existing.data.acceptedCount||0};
    if(existing?.data.lease>clock())return{ok:false,pending:true};
    let state=existing?.data||{actorUid:actor.uid,sourceTeam,title,description,targets:actor.uid===OWNER?[]:[OWNER],discoveryDone:onlyOwner,discoveryCursor:'',targetIndex:0,deviceCursor:'',acceptedCount:0};
    check(state.actorUid===actor.uid&&state.sourceTeam===sourceTeam,'Evento de outra origem');
    let locked=await api.put(path,{...state,lease:clock()+90000,status:'enviando'},existing?.updateTime||false);state=locked.data;
    async function save(delta) {locked=await api.put(path,delta,locked.updateTime,true);state={...state,...delta};}
    try {
      if(!state.discoveryDone) {
        const page=await api.query('','usuarios',[['frenteId',sourceFront(sourceTeam)],['papel','supervisor']],state.discoveryCursor);
        const taken=page.slice(0,PAGE);const found=taken.filter(p=>approved(p.data)).map(p=>p.name.split('/').pop()).filter(uid=>uidValid(uid)&&uid!==actor.uid);
        const targets=[...new Set([...state.targets,...found])];check(targets.length<=2000,'Limite de destinatários desta Beta atingido',429);
        await save({targets,discoveryDone:page.length<=PAGE,discoveryCursor:taken.at(-1)?.name||state.discoveryCursor});
      }
      let sent=0,scanned=0,recipients=0;
      while(state.targetIndex<state.targets.length&&sent<MAX_SEND&&scanned<MAX_SCAN&&recipients++<MAX_RECIPIENTS) {
        const uid=state.targets[state.targetIndex];const profile=uid===OWNER?{}:(await api.get(`usuarios/${uid}`))?.data;
        if(uid===actor.uid||!inFront(uid,profile,sourceTeam)) {await save({targetIndex:state.targetIndex+1,deviceCursor:''});continue;}
        const devices=await api.query(`dispositivos/${uid}`,'tokens',[],state.deviceCursor,MAX_SCAN+1);
        const taken=devices.slice(0,MAX_SCAN);let processed=0;
        for(const device of taken) {
          if(sent>=MAX_SEND||scanned>=MAX_SCAN)break;
          scanned++;processed++;
          if(device.data.front===sourceFront(sourceTeam)&&device.data.seen>clock()-30*86400000&&tokenValid(device.data.token)) {
            const accepted=await api.send(device,uid,{eventId,team:sourceTeam,title:state.title,body:state.description});
            if(accepted){sent++;await save({acceptedCount:state.acceptedCount+1,deviceCursor:device.name});continue;}
          }
          await save({deviceCursor:device.name});
        }
        if(processed===devices.length)await save({targetIndex:state.targetIndex+1,deviceCursor:''});
      }
      const done=state.discoveryDone&&state.targetIndex>=state.targets.length;
      await save({lease:0,status:done?'concluido':'pendente'});
      return{ok:done,pending:!done,acceptedCount:state.acceptedCount};
    }catch(error){try{await save({lease:0,status:'pendente'});}catch{ /* Existing lease expires; never claim success after an uncertain checkpoint. */ }throw error;}
  }
  async function route(request,env) {
    const url=new URL(request.url);
    if(url.pathname==='/health'&&request.method==='GET') {
      let configured=false;try{config(env);configured=true;}catch{ /* Readiness is not an FCM delivery test. */ }
      return{ok:true,project,workspace:ROOT,environment:'beta',protocol:PROTOCOL,serviceVersion:VERSION,configured,notificationsOnly:true};
    }
    const routes=['/devices/register','/devices/unregister','/devices/test','/events/notify'];
    const path=url.pathname.startsWith(PREFIX)?url.pathname.slice(PREFIX.length):'';
    check(routes.includes(path),'Rota Beta indisponível',404);check(request.method==='POST','Método indisponível',405);
    const sa=config(env);const size=request.headers.get('Content-Length');check(size===null||/^\d+$/.test(size)&&Number(size)<=12000,'Pedido acima do limite',413);
    const body=await readJson(request.body,12000);check(body&&typeof body==='object'&&!Array.isArray(body)&&body.environment==='beta'&&body.protocol===PROTOCOL,'Protocolo Beta obrigatório',400);
    const keys=path==='/events/notify'?['environment','protocol','kind','uid','team','key','revision']:['environment','protocol','token','front'];
    check(Object.keys(body).every(k=>keys.includes(k)),'Campo de pedido inválido',400);
    const api=apiFor(env,sa);const actor=await api.identity(request);
    if(path==='/events/notify')return notify(api,actor,body);
    check(manager(actor.uid,actor.profile));check(tokenValid(body.token),'Token de aparelho inválido',400);
    const key=await hash(body.token);const devicePath=`dispositivos/${actor.uid}/tokens/${key}`;
    if(path==='/devices/register') {
      const front=actor.uid===OWNER?body.front:actor.profile.frenteId;check(FRONTS.includes(front),'Escolha a frente de notificações',400);
      if(actor.uid!==OWNER&&body.front!==undefined)check(body.front===front,'Frente não autorizada');
      await api.put(`vinculosTokens/${key}`,{uid:actor.uid,front,seen:clock()});
      await api.put(devicePath,{token:body.token,front,seen:clock(),environment:'beta'});return{ok:true};
    }
    const device=await api.get(devicePath);const owner=await api.get(`vinculosTokens/${key}`);
    if(path==='/devices/unregister') {
      if(owner?.data.uid===actor.uid)await api.remove(`vinculosTokens/${key}`,owner.updateTime);
      if(device)await api.remove(devicePath,device.updateTime);return{ok:true};
    }
    check(device&&owner?.data.uid===actor.uid&&device.data.token===body.token&&device.data.environment==='beta','Ative os avisos neste aparelho antes de testar',400);
    check(FRONTS.includes(device.data.front)&&(actor.uid===OWNER||device.data.front===actor.profile.frenteId),'Frente não autorizada');
    check(!device.data.testAt||device.data.testAt<=clock()-30000,'Aguarde 30 segundos antes de testar novamente',429);
    const testedDevice=await api.put(devicePath,{testAt:clock()},device.updateTime,true);
    const sourceTeam=actor.uid===OWNER?device.data.front+'_A':team(actor.profile);
    const accepted=await api.send(testedDevice,actor.uid,{eventId:'test_'+cryptoImpl.randomUUID(),team:sourceTeam,title:'Teste de notificação DM AGRO Beta',body:'O serviço Beta enviou este teste ao seu aparelho.'});
    check(accepted,'O registro deste aparelho expirou. Ative os avisos novamente.',409);
    return{ok:true,acceptedByFcm:true,message:'O Firebase aceitou o teste. Confira se o Android exibiu a notificação.'};
  }
  return{async fetch(request,env={}) {
    try{return Response.json({environment:'beta',protocol:PROTOCOL,...await route(request,env)},{headers:{'Cache-Control':'no-store'}});}
    catch(error){return Response.json({ok:false,environment:'beta',protocol:PROTOCOL,message:error instanceof Failure?error.message:'O serviço Beta não conseguiu concluir o pedido.'},{status:error instanceof Failure?error.status:503,headers:{'Cache-Control':'no-store'}});}
  }};
}
export default createBetaWorker();
