import {before,after,beforeEach,test} from 'node:test';
import {readFileSync} from 'node:fs';
import {initializeTestEnvironment,assertSucceeds,assertFails} from '@firebase/rules-unit-testing';
import {doc,setDoc,updateDoc,getDoc,getDocs,deleteDoc,collection,query,where,serverTimestamp} from 'firebase/firestore';
const root='ambientes/beta';
const owner='Z8zjZP3iWjRSD6imwiDO4VLboOY2';
const team='santo_inacio_A';
let env;
const profile=(role='operador',shift='A',front='santo_inacio',status='aprovado',request=role)=>({nome:'Pessoa fictícia',matricula:'00123',frenteId:front,turno:shift,funcao:'Teste',papel:role,perfil:role,solicitacaoPerfil:request,status});
const db=uid=>env.authenticatedContext(uid,{email:'matricula.00123@acesso.dm-agro.app'}).firestore();
const path=(id='p',selected=team)=>[root,'equipes',selected,'registros',id];
const row=(uid='op',id='p',extra={})=>({equipeId:team,frenteId:'santo_inacio',turno:'A',tipo:'item',grupo:'pendencias',chave:id,dados:{id,descricao:'Trocar rolamento',concluida:false},formato:2,autorUid:uid,editadoPor:uid,versao:1,excluido:false,atualizadoEm:serverTimestamp(),...extra});
const edit=(uid,data,version=2)=>({dados:{id:'p',descricao:'Trocar rolamento',concluida:false,...data},editadoPor:uid,versao:version,atualizadoEm:serverTimestamp()});
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-dm-agro',firestore:{rules:readFileSync('../../firebase/firestore.beta.rules','utf8')}});});
after(async()=>{await env?.cleanup();});
beforeEach(async()=>{await env.clearFirestore();await env.withSecurityRulesDisabled(async c=>{
 const admin=c.firestore();
 for(const [uid,p] of Object.entries({op:profile(),other:profile(),sup:profile('supervisor'),supB:profile('supervisor','B'),otherFront:profile('supervisor','A','colorado'),helper:profile('ajudante'),blocked:profile('operador','A','santo_inacio','bloqueado'),pending:profile('operador','A','santo_inacio','pendente'),pendingB:profile('operador','B','santo_inacio','pendente','ajudante'),pendingSup:profile('operador','A','santo_inacio','pendente','supervisor')}))await setDoc(doc(admin,root,'usuarios',uid),p);
 await setDoc(doc(admin,'usuarios','prodOnly'),profile('supervisor'));
 await setDoc(doc(admin,root,'equipes',team,'registros','p'),row());
});});
test('every profile waits for approval and a candidate cannot approve themselves',async()=>{
 for(const requested of ['operador','tratorista','ajudante','supervisor']) {
  const uid='new_'+requested;
  await assertSucceeds(setDoc(doc(db(uid),root,'usuarios',uid),profile('operador','A','santo_inacio','pendente',requested)));
  await assertFails(updateDoc(doc(db(uid),root,'usuarios',uid),{papel:requested,perfil:requested,status:'aprovado'}));
 }
 await assertFails(setDoc(doc(db('fakeSup'),root,'usuarios','fakeSup'),profile('supervisor')));
});
test('only Diego approves supervisor and supervisors approve helpers across their front',async()=>{
 await assertFails(updateDoc(doc(db('sup'),root,'usuarios','pendingSup'),{papel:'supervisor',perfil:'supervisor',status:'aprovado'}));
 await assertSucceeds(updateDoc(doc(db(owner),root,'usuarios','pendingSup'),{papel:'supervisor',perfil:'supervisor',status:'aprovado'}));
 await assertSucceeds(updateDoc(doc(db('sup'),root,'usuarios','pendingB'),{papel:'ajudante',perfil:'ajudante',status:'aprovado'}));
 await assertFails(updateDoc(doc(db('otherFront'),root,'usuarios','pending'),{status:'aprovado'}));
});
test('supervisor lists candidates only in their front without including supervisor candidates',async()=>{
 await assertSucceeds(getDocs(query(collection(db('sup'),root,'usuarios'),where('frenteId','==','santo_inacio'),where('papel','in',['operador','funcionario','tratorista','ajudante']),where('solicitacaoPerfil','in',['operador','tratorista','ajudante']))));
 await assertFails(getDocs(collection(db('sup'),root,'usuarios')));
 await assertFails(updateDoc(doc(db('sup'),root,'usuarios','pending'),{turno:'C'}));
});
test('production approval never substitutes Beta approval or grants another environment',async()=>{
 await assertFails(getDoc(doc(db('prodOnly'),...path())));
 await assertFails(setDoc(doc(db('prodOnly'),...path('new')),row('prodOnly','new')));
 await assertFails(getDocs(collection(db('op'),'equipes',team,'registros')));
 await assertFails(setDoc(doc(db(owner),'ambientes','outra_empresa','usuarios','one'),profile()));
 await assertFails(setDoc(doc(db(owner),root,'empresas','extra'),{nome:'Outra empresa fictícia'}));
});
test('only approved members read the Beta team and supervisors can follow all their front shifts',async()=>{
 await assertSucceeds(getDoc(doc(db('supB'),...path())));
 await assertSucceeds(getDoc(doc(db('helper'),...path())));
 for(const uid of ['blocked','pending','otherFront'])await assertFails(getDoc(doc(db(uid),...path())));
 await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(),...path())));
});
test('an operator edits their description but cannot conclude, reopen or fabricate a closed record',async()=>{
 await assertSucceeds(updateDoc(doc(db('op'),...path()),edit('op',{descricao:'Descrição corrigida'})));
 await assertFails(updateDoc(doc(db('op'),...path()),edit('op',{concluida:true},3)));
 await assertFails(setDoc(doc(db('op'),...path('closed')),row('op','closed',{dados:{id:'closed',concluida:true}})));
 await assertFails(updateDoc(doc(db('other'),...path()),edit('other',{} ,3)));
});
test('the owning front manager concludes with a responsible person and can reopen without deleting history',async()=>{
 await assertFails(updateDoc(doc(db('otherFront'),...path()),edit('otherFront',{concluida:true})));
 await assertFails(updateDoc(doc(db('sup'),...path()),edit('sup',{concluida:true,concluidoPorUid:'other',dataConclusao:'26/11/2026 10:00'})));
 await assertSucceeds(updateDoc(doc(db('sup'),...path()),edit('sup',{concluida:true,concluidoPorUid:'sup',dataConclusao:'26/11/2026 10:00'})));
 await assertFails(updateDoc(doc(db('op'),...path()),edit('op',{concluida:false},3)));
 await assertSucceeds(updateDoc(doc(db('sup'),...path()),edit('sup',{concluida:false},3)));
});
test('opaque JSON and contradictory dual payloads cannot bypass typed status controls',async()=>{
 await assertFails(updateDoc(doc(db('op'),...path()),{conteudo:'{"id":"p","concluida":true}',editadoPor:'op',versao:2,atualizadoEm:serverTimestamp()}));
 await assertFails(updateDoc(doc(db('op'),...path()),edit('op',{id:'another'})));
 await assertFails(updateDoc(doc(db('op'),...path()),{formato:1,editadoPor:'op',versao:2,atualizadoEm:serverTimestamp()}));
 await assertFails(updateDoc(doc(db('op'),...path()),edit('op',{dataConclusao:'forged'})));
 await assertFails(updateDoc(doc(db('op'),...path()),edit('op',{__arquivado:true})));
 await assertFails(updateDoc(doc(db('op'),...path()),edit('op',{historicoExclusoes:[{acao:'excluido',porUid:'sup'}]})));
});
test('equipment management and deletion remain manager only; restore preserves author and revision',async()=>{
 await assertFails(setDoc(doc(db('op'),...path('equipment')),row('op','equipment',{dados:{id:'equipment',categoriaRegistro:'equipamento',equipamento:'FICTICIO'}})));
 await assertFails(updateDoc(doc(db('op'),...path()),{excluido:true,editadoPor:'op',versao:2,atualizadoEm:serverTimestamp()}));
 await assertFails(updateDoc(doc(db('sup'),...path()),{excluido:true,editadoPor:'sup',versao:2,atualizadoEm:serverTimestamp()}));
 await assertSucceeds(updateDoc(doc(db('sup'),...path()),{...edit('sup',{__arquivado:true}),excluido:true}));
 await assertFails(updateDoc(doc(db('op'),...path()),{excluido:false,editadoPor:'op',versao:3,atualizadoEm:serverTimestamp()}));
 await assertSucceeds(updateDoc(doc(db('sup'),...path()),{...edit('sup',{},3),excluido:false}));
 await assertFails(deleteDoc(doc(db('sup'),...path())));
});
test('revision, ownership and scope cannot be replaced and stock stays manager only',async()=>{
 await assertFails(updateDoc(doc(db('op'),...path()),{autorUid:'other',editadoPor:'op',versao:2,atualizadoEm:serverTimestamp()}));
 await assertFails(updateDoc(doc(db('op'),...path()),{equipeId:'colorado_A',editadoPor:'op',versao:2,atualizadoEm:serverTimestamp()}));
 await assertFails(updateDoc(doc(db('op'),...path()),{editadoPor:'op',versao:99,atualizadoEm:serverTimestamp()}));
 await assertFails(setDoc(doc(db('op'),...path('stock')),row('op','stock',{grupo:'estoques_salvos'})));
 await assertSucceeds(setDoc(doc(db('sup'),...path('stock')),row('sup','stock',{grupo:'estoques_salvos'})));
});
test('only Diego can configure a Beta Worker URL; production endpoints and secrets are refused',async()=>{
 const ref=uid=>doc(db(uid),root,'configuracao','servico');
 await assertSucceeds(setDoc(ref(owner),{url:'https://dm-agro-beta-servico.exemplo.workers.dev'}));
 await assertFails(setDoc(ref('sup'),{url:'https://outro-beta.exemplo.workers.dev'}));
 await assertFails(setDoc(ref(owner),{url:'https://dm-agro-servico.exemplo.workers.dev'}));
 await assertFails(setDoc(ref(owner),{url:'https://dm-agro-beta-servico.exemplo.workers.dev',FIREBASE_BETA_SERVICE_ACCOUNT:'never-store-secrets-here'}));
 await assertFails(setDoc(doc(db(owner),root,'configuracao','qualquer'),{url:'https://dm-agro-beta-servico.exemplo.workers.dev'}));
 await assertFails(deleteDoc(ref(owner)));
});
test('notification tokens and delivery checkpoints cannot be read or forged by application accounts',async()=>{
 const paths=['dispositivos/manager/tokens/fictitious','vinculosTokens/fictitious','eventosNotificacao/fictitious'];
 await env.withSecurityRulesDisabled(async c=>{for(const p of paths)await setDoc(doc(c.firestore(),root,...p.split('/')),{fixture:true});});
 for(const uid of [owner,'sup','op'])for(const p of paths){
  await assertFails(getDoc(doc(db(uid),root,...p.split('/'))));
  await assertFails(setDoc(doc(db(uid),root,...p.split('/')),{forged:true}));
 }
});
