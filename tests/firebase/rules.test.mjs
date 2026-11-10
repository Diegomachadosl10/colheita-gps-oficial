import { before, after, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, collection, setDoc, getDoc, getDocs, updateDoc, deleteDoc, query, where, serverTimestamp, onSnapshot } from 'firebase/firestore';
import { ref, uploadBytes, getBytes } from 'firebase/storage';

const owner = 'Z8zjZP3iWjRSD6imwiDO4VLboOY2';
const team = 'santo_inacio_A';
let env;
const profile = (papel, turno='A', frenteId='santo_inacio', status='aprovado') => ({nome:'Usuário de teste', matricula:'80911', frenteId, turno, funcao:'Teste', papel, perfil:papel, solicitacaoPerfil:papel==='supervisor'?'supervisor':'operador', status});
const db = uid => env.authenticatedContext(uid, {email:'matricula.80911@acesso.dm-agro.app'}).firestore();
const record = (uid='opA', group='pendencias', revision=1, data={}) => ({equipeId:team, frenteId:'santo_inacio', turno:'A', tipo:'item', grupo:group, chave:'registro1', conteudo:JSON.stringify({id:'registro1', descricao:'Manutenção'}), autorUid:uid, editadoPor:uid, versao:revision, atualizadoEm:serverTimestamp(), excluido:false, ...data});
const pending = (requested='operador', shift='A', front='santo_inacio') => ({...profile('operador',shift,front,'pendente'), solicitacaoPerfil:requested});

before(async () => {
  env = await initializeTestEnvironment({projectId:'demo-dm-agro', firestore:{rules:readFileSync('../../firebase/firestore.rules','utf8')}, storage:{rules:readFileSync('../../firebase/storage.rules','utf8')}});
});
after(async () => { await env?.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async c => {
    const x = c.firestore();
    await Promise.all(Object.entries({[owner]:profile('administrador'),'supA':profile('supervisor'),'supB':profile('supervisor','B'),'supOutra':profile('supervisor','A','colorado'),'opA':profile('operador'),'opA2':profile('operador'),'opB':profile('operador','B'),'fakeAdmin':profile('administrador'),'blocked':profile('operador','A','santo_inacio','bloqueado'),'pending':pending(),'pendingB':pending('operador','B'),'pendingOutra':pending('operador','A','colorado'),'pendingSupervisor':pending('supervisor'),'pendingTratorista':pending('tratorista')}).map(([uid,p]) => setDoc(doc(x,'usuarios',uid),p)));
  });
});

test('cadastro próprio nasce pendente e não pode escolher aprovação, outro UID ou matrícula alheia', async () => {
  await assertSucceeds(setDoc(doc(db('novo'),'usuarios','novo'),pending('tratorista')));
  await assertFails(setDoc(doc(db('novo2'),'usuarios','novo2'),{...pending(),status:'aprovado'}));
  await assertFails(setDoc(doc(db('novo3'),'usuarios','outroUID'),pending()));
  await assertFails(setDoc(doc(db('novo4'),'usuarios','novo4'),{...pending(),matricula:'99999'}));
  await assertFails(updateDoc(doc(db('novo'),'usuarios','novo'),{papel:'administrador',status:'aprovado'}));
});
test('supervisor aprova operador e tratorista somente da mesma frente e turno', async () => {
  await assertSucceeds(updateDoc(doc(db('supA'),'usuarios','pending'),{papel:'operador',perfil:'operador',status:'aprovado'}));
  await assertSucceeds(updateDoc(doc(db('supA'),'usuarios','pendingTratorista'),{papel:'tratorista',perfil:'tratorista',status:'aprovado'}));
  for(const uid of ['pendingB','pendingOutra','pendingSupervisor'])
    await assertFails(updateDoc(doc(db('supA'),'usuarios',uid),{papel:'operador',perfil:'operador',status:'aprovado'}));
});
test('somente o UID principal aprova supervisores e ajusta equipes', async () => {
  await assertSucceeds(updateDoc(doc(db(owner),'usuarios','pendingSupervisor'),{papel:'supervisor',perfil:'supervisor',status:'aprovado'}));
  await assertFails(updateDoc(doc(db('fakeAdmin'),'usuarios','pendingB'),{status:'aprovado'}));
  await assertFails(updateDoc(doc(db('supA'),'usuarios','pending'),{turno:'B'}));
  await assertSucceeds(updateDoc(doc(db(owner),'usuarios','pending'),{turno:'B'}));
  await assertFails(updateDoc(doc(db('supA'),'usuarios',owner),{papel:'operador',perfil:'operador'}));
});
test('consulta do supervisor exige os limites da equipe e não inclui pedidos de supervisor', async () => {
  await assertSucceeds(getDocs(query(collection(db('supA'),'usuarios'),where('frenteId','==','santo_inacio'),where('turno','==','A'),where('papel','in',['operador','funcionario','tratorista']),where('solicitacaoPerfil','in',['operador','tratorista']))));
  await assertFails(getDocs(collection(db('supA'),'usuarios')));
  await assertFails(getDoc(doc(db('supA'),'usuarios','pendingB')));
  await assertSucceeds(getDocs(collection(db(owner),'usuarios')));
});
test('registros do operador aparecem na leitura da equipe e somente o administrador cruza equipes', async () => {
  const path=['equipes',team,'registros','r1'];
  await assertSucceeds(setDoc(doc(db('opA'),...path),record()));
  await assertSucceeds(getDocs(collection(db('supA'),'equipes',team,'registros')));
  await assertSucceeds(getDoc(doc(db('opA2'),...path)));
  await assertSucceeds(getDoc(doc(db('opA'),'equipes',team,'registros','ainda-nao-existe')));
  await assertSucceeds(getDocs(query(collection(db('opA'),'equipes',team,'registros'),where('grupo','in',['pendencias','ocorrencias_campo','mapas','pinturas','arquivos']))));
  await assertSucceeds(getDoc(doc(db(owner),...path)));
  for(const uid of ['supB','supOutra','opB','fakeAdmin','blocked','pending']) await assertFails(getDoc(doc(db(uid),...path)));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(),...path)));
});
test('operador não altera registro alheio, autoria, equipe nem ferramentas de gestor', async () => {
  const path=['equipes',team,'registros','r1'];
  await assertSucceeds(setDoc(doc(db('opA'),...path),record()));
  await assertFails(updateDoc(doc(db('opA2'),...path),{conteudo:'{}',editadoPor:'opA2',versao:2,atualizadoEm:serverTimestamp()}));
  await assertFails(updateDoc(doc(db('opA'),...path),{autorUid:'opA2',versao:2,atualizadoEm:serverTimestamp()}));
  await assertFails(updateDoc(doc(db('opA'),...path),{equipeId:'santo_inacio_B',versao:2,atualizadoEm:serverTimestamp()}));
  await assertFails(setDoc(doc(db('opA'),'equipes',team,'registros','estoque'),record('opA','estoques_salvos')));
  await assertSucceeds(setDoc(doc(db('supA'),'equipes',team,'registros','estoque'),record('supA','estoques_salvos')));
  await assertFails(getDoc(doc(db('opA'),'equipes',team,'registros','estoque')));
});
test('versões antigas não sobrescrevem alterações e exclusão usa marca sincronizada', async () => {
  const path=['equipes',team,'registros','r1'];
  await assertSucceeds(setDoc(doc(db('opA'),...path),record()));
  await assertSucceeds(updateDoc(doc(db('supA'),...path),{conteudo:'{"resolvida":true}',editadoPor:'supA',versao:2,atualizadoEm:serverTimestamp()}));
  await assertFails(updateDoc(doc(db('opA'),...path),{conteudo:'{"antiga":true}',versao:2,editadoPor:'opA',atualizadoEm:serverTimestamp()}));
  await assertFails(deleteDoc(doc(db('supA'),...path)));
  await assertSucceeds(updateDoc(doc(db('supA'),...path),{excluido:true,versao:3,editadoPor:'supA',atualizadoEm:serverTimestamp()}));
});
test('assinante do supervisor recebe a criação do operador', async () => {
  const received = new Promise((resolve,reject) => {
    const timeout=setTimeout(()=>{stop();reject(new Error('Atualização não recebida'));},10000);
    const stop=onSnapshot(collection(db('supA'),'equipes',team,'registros'),snap=>{if(snap.docs.some(x=>x.id==='live')){clearTimeout(timeout);stop();resolve();}},reject);
  });
  await setDoc(doc(db('opA'),'equipes',team,'registros','live'),record());
  await received;
});
test('chuva por turno usa as regras existentes, com correção pelo autor ou gestor e isolamento entre equipes', async () => {
  const key='chuva_2026-11-09';
  const path=['equipes',team,'registros',key];
  const rain=record('opA','ocorrencias_campo',1,{chave:key,conteudo:JSON.stringify({id:key,categoriaRegistro:'chuva',dataCadastro:'09/11/2026',chuvaMm:'0',frenteId:'santo_inacio',turno:'A'})});
  await assertSucceeds(setDoc(doc(db('opA'),...path),rain));
  const snapshot=await assertSucceeds(getDoc(doc(db('supA'),...path)));
  assert.equal(JSON.parse(snapshot.data().conteudo).chuvaMm,'0');
  await assertSucceeds(getDoc(doc(db(owner),...path)));
  await assertSucceeds(getDoc(doc(db('opA2'),...path)));
  await assertFails(getDoc(doc(db('supB'),...path)));
  await assertFails(getDoc(doc(db('supOutra'),...path)));
  await assertFails(updateDoc(doc(db('opA2'),...path),{editadoPor:'opA2',versao:2,atualizadoEm:serverTimestamp(),conteudo:rain.conteudo}));
  await assertSucceeds(updateDoc(doc(db('opA'),...path),{editadoPor:'opA',versao:2,atualizadoEm:serverTimestamp(),conteudo:JSON.stringify({...JSON.parse(rain.conteudo),chuvaMm:'12.5'})}));
  await assertSucceeds(updateDoc(doc(db('supA'),...path),{editadoPor:'supA',versao:3,atualizadoEm:serverTimestamp(),conteudo:JSON.stringify({...JSON.parse(rain.conteudo),chuvaMm:'15'})}));
  const shiftB=['equipes','santo_inacio_B','registros',key];
  await assertSucceeds(setDoc(doc(db('opB'),...shiftB),{...rain,autorUid:'opB',editadoPor:'opB',equipeId:'santo_inacio_B',turno:'B'}));
  assert.equal(JSON.parse((await getDoc(doc(db('supA'),...path))).data().conteudo).chuvaMm,'15');
});
test('pendências antigas sem equipe não são liberadas a todos os supervisores', async () => {
  await env.withSecurityRulesDisabled(c => setDoc(doc(c.firestore(),'pendencias','antiga'),{autorId:'opA',descricao:'Legado'}));
  await assertSucceeds(getDoc(doc(db(owner),'pendencias','antiga')));
  await assertSucceeds(getDoc(doc(db('opA'),'pendencias','antiga')));
  await assertFails(getDoc(doc(db('supA'),'pendencias','antiga')));
  await assertFails(getDoc(doc(db('supB'),'pendencias','antiga')));
});
test('anexos autenticados ficam restritos à equipe; arquivo existente é imutável', async () => {
  const path=`equipes/${team}/arquivos/opA/foto-${Date.now()}`;
  const storage = uid => env.authenticatedContext(uid).storage();
  await assertSucceeds(uploadBytes(ref(storage('opA'),path),new Uint8Array([1,2,3]),{contentType:'image/jpeg'}));
  await assertSucceeds(getBytes(ref(storage('supA'),path)));
  await assertSucceeds(getBytes(ref(storage(owner),path)));
  await assertFails(getBytes(ref(storage('supB'),path)));
  await assertFails(getBytes(ref(storage('fakeAdmin'),path)));
  await assertFails(uploadBytes(ref(storage('opA'),path),new Uint8Array([4]),{contentType:'image/jpeg'}));
  await assertFails(uploadBytes(ref(storage('opB'),`equipes/${team}/arquivos/opB/fora`),new Uint8Array([1]),{contentType:'image/jpeg'}));
});

test('pinturas e imagens derivadas permitem colaboração sem liberar mapas e tipos inválidos', async () => {
  const path=['equipes',team,'registros','pintura'];
  await assertSucceeds(setDoc(doc(db('opA'),...path),record('opA','pinturas',1,{tipo:'preferencia'})));
  await assertSucceeds(updateDoc(doc(db('opA2'),...path),{conteudo:'{}',versao:2,editadoPor:'opA2',atualizadoEm:serverTimestamp()}));
  const image=['equipes',team,'registros','imagem-derivada'];
  await assertSucceeds(setDoc(doc(db('supA'),...image),record('supA','arquivos',1,{tipo:'arquivo',chave:'mapa_relatorio_'+ 'a'.repeat(64) + '.png'})));
  await assertSucceeds(updateDoc(doc(db('opA'),...image),{conteudo:'{}',versao:2,editadoPor:'opA',atualizadoEm:serverTimestamp()}));
  await assertFails(setDoc(doc(db('opA'),'equipes',team,'registros','tipo-invalido'),record('opA','pendencias',1,{tipo:'preferencia'})));
  const name=['equipes',team,'registros','nome-mapa'];
  await assertSucceeds(setDoc(doc(db('supA'),...name),record('supA','mapas',1,{tipo:'preferencia'})));
  await assertFails(updateDoc(doc(db('opA'),...name),{conteudo:'renomeado',versao:2,editadoPor:'opA',atualizadoEm:serverTimestamp()}));
});

test('apenas o principal exclui acesso e um supervisor não pode reativá-lo', async () => {
  await assertFails(updateDoc(doc(db('supA'),'usuarios','opA'),{status:'excluido'}));
  await assertSucceeds(updateDoc(doc(db(owner),'usuarios','opA'),{status:'excluido',excluidoPor:owner,excluidoEm:serverTimestamp()}));
  await assertFails(updateDoc(doc(db('supA'),'usuarios','opA'),{status:'aprovado',papel:'operador',perfil:'operador'}));
  await assertFails(updateDoc(doc(db('supA'),'usuarios','opA'),{status:'bloqueado',papel:'operador',perfil:'operador'}));
  await assertFails(updateDoc(doc(db(owner),'usuarios',owner),{status:'excluido'}));
  await assertSucceeds(getDoc(doc(db(owner),'configuracao','exclusaoUsuarios')));
  await assertFails(getDoc(doc(db('supA'),'configuracao','exclusaoUsuarios')));
  await assertFails(setDoc(doc(db('opA'),'equipes',team,'registros','apos-exclusao'),record()));
});
test('catálogo de equipamento usa os mesmos limites de equipe das pendências', async () => {
  const item=record('opA','pendencias',1,{chave:'equipamento_21569',conteudo:JSON.stringify({id:'equipamento_21569',categoriaRegistro:'equipamento',equipamento:'21569'})});
  await assertSucceeds(setDoc(doc(db('opA'),'equipes',team,'registros','equipamento'),item));
  await assertSucceeds(getDoc(doc(db('supA'),'equipes',team,'registros','equipamento')));
  await assertFails(getDoc(doc(db('supB'),'equipes',team,'registros','equipamento')));
});
test('somente o gestor exclui equipamentos e pendências, preservando os documentos para o histórico', async () => {
  const entries=[['equipamento-historico',{id:'equipamento-historico',categoriaRegistro:'equipamento',equipamento:'21569'}],
    ['pendencia-historico',{id:'pendencia-historico',equipamento:'21569',descricao:'Pneu furado'}]];
  for(const [key,content] of entries) {
    const path=['equipes',team,'registros',key];
    const item=record('opA','pendencias',1,{chave:key,conteudo:JSON.stringify(content)});
    await assertSucceeds(setDoc(doc(db('opA'),...path),item));
    await assertFails(updateDoc(doc(db('opA'),...path),{excluido:true,versao:2,editadoPor:'opA',atualizadoEm:serverTimestamp()}));
    await assertFails(updateDoc(doc(db('supB'),...path),{excluido:true,versao:2,editadoPor:'supB',atualizadoEm:serverTimestamp()}));
    await assertSucceeds(updateDoc(doc(db('supA'),...path),{excluido:true,versao:2,editadoPor:'supA',atualizadoEm:serverTimestamp()}));
    const history=await assertSucceeds(getDoc(doc(db('opA'),...path)));
    assert.equal(history.data().excluido,true);
    assert.deepEqual(JSON.parse(history.data().conteudo),content);
    await assertSucceeds(getDoc(doc(db(owner),...path)));
    await assertFails(updateDoc(doc(db('opA'),...path),{excluido:false,versao:3,editadoPor:'opA',atualizadoEm:serverTimestamp()}));
    await assertFails(updateDoc(doc(db('opA'),...path),{conteudo:'{}',versao:3,editadoPor:'opA',atualizadoEm:serverTimestamp()}));
    await assertFails(deleteDoc(doc(db('supA'),...path)));
    await assertFails(deleteDoc(doc(db(owner),...path)));
  }
  await assertFails(setDoc(doc(db('opA'),'equipes',team,'registros','forjado'),record('opA','pendencias',1,{excluido:true})));
  await assertSucceeds(setDoc(doc(db(owner),'equipes',team,'registros','arquivo-administrador'),record(owner,'pendencias',1,{excluido:true})));
});
