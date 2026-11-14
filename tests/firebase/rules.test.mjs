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
test('somente o UID principal aprova outro supervisor e ajusta equipes', async () => {
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
test('pedido de peça usa o grupo de manutenção existente, sem ampliar equipes ou permitir exclusão pelo operador',async()=>{
  const path=['equipes',team,'registros','peca'];
  const content={id:'peca',categoriaRegistro:'peca',codigoPeca:'00081-AB',quantidadePeca:2,descricao:'Filtro',situacaoPeca:'a_pedir'};
  await assertSucceeds(setDoc(doc(db('opA'),...path),record('opA','pendencias',1,{chave:'peca',conteudo:JSON.stringify(content)})));
  const read=await assertSucceeds(getDoc(doc(db('supA'),...path)));
  assert.equal(JSON.parse(read.data().conteudo).codigoPeca,'00081-AB');
  await assertFails(getDoc(doc(db('supB'),...path)));
  await assertFails(updateDoc(doc(db('opA'),...path),{excluido:true,versao:2,editadoPor:'opA',atualizadoEm:serverTimestamp()}));
  await assertSucceeds(updateDoc(doc(db('supA'),...path),{excluido:true,versao:2,editadoPor:'supA',atualizadoEm:serverTimestamp()}));
  assert.equal(JSON.parse((await getDoc(doc(db('supA'),...path))).data().conteudo).quantidadePeca,2);
});
test('somente o principal configura o serviço público e o próprio nome, sem ampliar permissões',async()=>{
  await assertSucceeds(setDoc(doc(db(owner),'configuracao','servico'),{url:'https://dm-agro.usuario.workers.dev'}));
  await assertSucceeds(getDoc(doc(db('opA'),'configuracao','servico')));
  await assertFails(setDoc(doc(db('supA'),'configuracao','servico'),{url:'https://outro.usuario.workers.dev'}));
  await assertFails(setDoc(doc(db(owner),'configuracao','servico'),{url:'https://evil.invalid'}));
  await assertFails(setDoc(doc(db(owner),'configuracao','servico'),{url:'https://dm-agro.usuario.workers.dev',private_key:'proibida'}));
  await assertSucceeds(updateDoc(doc(db(owner),'usuarios',owner),{nome:'Diego Machado'}));
  await assertFails(updateDoc(doc(db(owner),'usuarios',owner),{papel:'operador'}));
  await assertFails(updateDoc(doc(db('opA'),'usuarios','opA'),{nome:'Outro nome'}));
  await assertFails(setDoc(doc(db('opA'),'dispositivos','opA','tokens','falso'),{token:'falso'}));
  await assertFails(getDoc(doc(db('supA'),'dispositivos',owner,'tokens','outro')));
});
test('token antigo de conta apagada não recria o cadastro e um novo UID pode solicitar a matrícula liberada',async()=>{
  await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'contasEncerradas','encerrado'),{por:owner}));
  await assertFails(setDoc(doc(db('encerrado'),'usuarios','encerrado'),pending()));
  await assertSucceeds(setDoc(doc(db('novo-uid'),'usuarios','novo-uid'),pending()));
  await assertFails(getDoc(doc(db('novo-uid'),'contasEncerradas','encerrado')));
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

test('somente gestores excluem e restauram ocorrências e chuva, preservando a edição normal do autor',async()=>{
  for(const id of ['ocorrencia','chuva']) {
    const path=['equipes',team,'registros',id];
    await assertSucceeds(setDoc(doc(db('opA'),...path),record('opA','ocorrencias_campo',1,{chave:id})));
    await assertSucceeds(updateDoc(doc(db('opA'),...path),{conteudo:'{"descricao":"corrigida pelo autor"}',editadoPor:'opA',versao:2,atualizadoEm:serverTimestamp()}));
    await assertFails(updateDoc(doc(db('opA'),...path),{excluido:true,editadoPor:'opA',versao:3,atualizadoEm:serverTimestamp()}));
    await assertSucceeds(updateDoc(doc(db('supA'),...path),{excluido:true,editadoPor:'supA',versao:3,atualizadoEm:serverTimestamp()}));
    await assertFails(updateDoc(doc(db('opA'),...path),{excluido:false,editadoPor:'opA',versao:4,atualizadoEm:serverTimestamp()}));
    await assertFails(updateDoc(doc(db('opA'),...path),{conteudo:'{}',editadoPor:'opA',versao:4,atualizadoEm:serverTimestamp()}));
    await assertFails(updateDoc(doc(db('supB'),...path),{excluido:false,editadoPor:'supB',versao:4,atualizadoEm:serverTimestamp()}));
    await assertSucceeds(updateDoc(doc(db(owner),...path),{excluido:false,editadoPor:owner,versao:4,atualizadoEm:serverTimestamp()}));
    await assertSucceeds(updateDoc(doc(db('opA'),...path),{conteudo:'{"descricao":"editada após restaurar"}',editadoPor:'opA',versao:5,atualizadoEm:serverTimestamp()}));
    assert.equal((await getDoc(doc(db('supA'),...path))).data().autorUid,'opA');
  }
});
test('operador não cria exclusão de registro novo e supervisor restaura o equipamento da própria equipe',async()=>{
  await assertFails(setDoc(doc(db('opA'),'equipes',team,'registros','novo-excluido'),record('opA','ocorrencias_campo',1,{excluido:true})));
  for(const id of ['equipamento','pendencia']) {
    const path=['equipes',team,'registros',id];
    await assertSucceeds(setDoc(doc(db('opA'),...path),record('opA','pendencias',1,{chave:id})));
    await assertSucceeds(updateDoc(doc(db('supA'),...path),{excluido:true,editadoPor:'supA',versao:2,atualizadoEm:serverTimestamp()}));
    await assertFails(updateDoc(doc(db('opA'),...path),{excluido:false,editadoPor:'opA',versao:3,atualizadoEm:serverTimestamp()}));
    await assertSucceeds(updateDoc(doc(db('supA'),...path),{excluido:false,editadoPor:'supA',versao:3,atualizadoEm:serverTimestamp()}));
  }
});
test('o histórico de ordens de serviço é exclusivo dos gestores autorizados',async()=>{
  const closedPath=['equipes',team,'registros','os-encerrada'];
  await assertSucceeds(setDoc(doc(db('supA'),...closedPath),record('supA','historico_os',1,{chave:'os-encerrada',excluido:false,conteudo:JSON.stringify({numeroOs:'K/05',talhoes:'17 ao 68',statusOs:'encerrada',encerradaPorUid:'supA'})})));
  const closed=await assertSucceeds(getDoc(doc(db(owner),...closedPath)));
  assert.equal(JSON.parse(closed.data().conteudo).statusOs,'encerrada');
  for(const uid of ['opA','supB','supOutra']) await assertFails(getDoc(doc(db(uid),...closedPath)));
  await assertSucceeds(updateDoc(doc(db('supA'),...closedPath),{conteudo:JSON.stringify({numeroOs:'K/05',talhoes:'17 ao 68',statusOs:'reaberta'}),editadoPor:'supA',versao:2,atualizadoEm:serverTimestamp()}));
  const path=['equipes',team,'registros','os-excluida'];
  await assertSucceeds(setDoc(doc(db('supA'),...path),record('supA','historico_os',1,{chave:'os-excluida',excluido:true,conteudo:'{"numeroOs":"123","talhoes":"1 a 3"}'})));
  await assertSucceeds(getDoc(doc(db(owner),...path)));
  for(const uid of ['opA','supB','supOutra']) await assertFails(getDoc(doc(db(uid),...path)));
  await assertFails(setDoc(doc(db('opA'),'equipes',team,'registros','os-falsa'),record('opA','historico_os')));
  await assertSucceeds(updateDoc(doc(db('supA'),...path),{excluido:false,editadoPor:'supA',versao:2,atualizadoEm:serverTimestamp()}));
});
test('a exceção de exclusão dos mapas do próprio dono é preservada',async()=>{
  const path=['equipes',team,'registros','mapa-pessoal-legado'];
  await assertSucceeds(setDoc(doc(db('opA'),...path),record('opA','mapas',1,{tipo:'preferencia',chave:'mapa-pessoal-legado',conteudo:'Meu mapa'})));
  await assertSucceeds(updateDoc(doc(db('opA'),...path),{excluido:true,editadoPor:'opA',versao:2,atualizadoEm:serverTimestamp()}));
  await assertFails(updateDoc(doc(db('opA2'),...path),{excluido:false,editadoPor:'opA2',versao:3,atualizadoEm:serverTimestamp()}));
  const painting=['equipes',team,'registros','pintura-legada'];
  await assertSucceeds(setDoc(doc(db('opA'),...painting),record('opA','pinturas',1,{tipo:'preferencia',chave:'pintura-legada',conteudo:'{}'})));
  await assertSucceeds(updateDoc(doc(db('opA2'),...painting),{conteudo:'{"linhas":[]}',editadoPor:'opA2',versao:2,atualizadoEm:serverTimestamp()}));
  await assertFails(updateDoc(doc(db('opA2'),...painting),{excluido:true,editadoPor:'opA2',versao:3,atualizadoEm:serverTimestamp()}));
  await assertSucceeds(updateDoc(doc(db('opA'),...painting),{excluido:true,editadoPor:'opA',versao:3,atualizadoEm:serverTimestamp()}));
  await assertFails(updateDoc(doc(db('opA2'),...painting),{excluido:false,editadoPor:'opA2',versao:4,atualizadoEm:serverTimestamp()}));
});

test('consulta HTTPS com ID token do usuário obedece às regras e não usa acesso de administrador do servidor',async()=>{
  const host=process.env.FIRESTORE_EMULATOR_HOST;
  assert.ok(host,'Run only against the configured demo emulator');
  const project='demo-dm-agro';
  const token=uid=>{
    const now=Math.floor(Date.now()/1000);
    const encode=v=>Buffer.from(JSON.stringify(v)).toString('base64url');
    return encode({alg:'none',typ:'JWT'})+'.'+encode({sub:uid,user_id:uid,aud:project,iss:'https://securetoken.google.com/'+project,iat:now,exp:now+3600,
      firebase:{sign_in_provider:'custom'},email:'matricula.80911@acesso.dm-agro.app'})+'.';
  };
  async function read(actor,path) {
    return fetch(`http://${host}/v1/projects/${project}/databases/(default)/documents/${path}`,{headers:actor?{Authorization:'Bearer '+token(actor)}:{}});
  }
  const own=await read('opA','usuarios/opA');assert.equal(own.status,200);assert.equal((await own.json()).fields.status.stringValue,'aprovado');
  assert.equal((await read('opA','usuarios/opB')).status,403);
  assert.equal((await read(null,'usuarios/opA')).status,403);
  assert.equal((await read(owner,'usuarios/opA')).status,200);
  assert.equal((await read('opA','configuracao/equipes')).status,404);
  const blockedOwn=await read('blocked','usuarios/blocked');assert.equal(blockedOwn.status,200);assert.equal((await blockedOwn.json()).fields.status.stringValue,'bloqueado');
});

test('novo supervisor entra aprovado sem liberar aprovação automática de operadores ou acesso global',async()=>{
  const supervisor={...profile('supervisor'),criadoEm:serverTimestamp()};
  await assertSucceeds(setDoc(doc(db('novoSup'),'usuarios','novoSup'),supervisor));
  await assertSucceeds(updateDoc(doc(db('novoSup'),'usuarios','pending'),{papel:'operador',perfil:'operador',status:'aprovado'}));
  await assertSucceeds(getDocs(collection(db('novoSup'),'equipes',team,'registros')));
  await assertFails(getDocs(collection(db('novoSup'),'equipes','santo_inacio_B','registros')));
  await assertFails(getDocs(collection(db('novoSup'),'usuarios')));
  await assertFails(updateDoc(doc(db('novoSup'),'usuarios','pendingSupervisor'),{papel:'supervisor',perfil:'supervisor',status:'aprovado'}));
  for(const [i,change] of [
    {papel:'administrador',perfil:'administrador'}, {papel:'operador',perfil:'operador'},
    {solicitacaoPerfil:'operador'}, {perfil:'operador'}, {frenteId:'invalida'}, {turno:'D'}, {matricula:'99999'},
    {status:'bloqueado'}
  ].entries()) {
    const uid='supInvalido'+i;
    await assertFails(setDoc(doc(db(uid),'usuarios',uid),{...supervisor,...change}));
  }
  await assertFails(updateDoc(doc(db('novoSup'),'usuarios','novoSup'),{frenteId:'colorado'}));
  await assertFails(updateDoc(doc(db('novoSup'),'usuarios','novoSup'),{turno:'B'}));
  await assertFails(updateDoc(doc(db('novoSup'),'usuarios','novoSup'),{papel:'administrador',perfil:'administrador'}));
  await assertSucceeds(updateDoc(doc(db(owner),'usuarios','novoSup'),{frenteId:'colorado',turno:'B'}));
});

test('pedido antigo de supervisor ativa somente o próprio perfil pendente e preserva equipe e dados',async()=>{
  await assertFails(updateDoc(doc(db('pendingSupervisor'),'usuarios','pendingSupervisor'),{papel:'supervisor',perfil:'supervisor',status:'aprovado',turno:'B'}));
  await assertFails(updateDoc(doc(db('pendingSupervisor'),'usuarios','pendingSupervisor'),{papel:'supervisor',perfil:'supervisor',status:'aprovado',nome:'Outro nome'}));
  await assertSucceeds(updateDoc(doc(db('pendingSupervisor'),'usuarios','pendingSupervisor'),{papel:'supervisor',perfil:'supervisor',status:'aprovado'}));
  const active=(await getDoc(doc(db('pendingSupervisor'),'usuarios','pendingSupervisor'))).data();
  assert.equal(active.frenteId,'santo_inacio');assert.equal(active.turno,'A');assert.equal(active.matricula,'80911');
  await assertSucceeds(updateDoc(doc(db('pendingSupervisor'),'usuarios','pendingTratorista'),{papel:'tratorista',perfil:'tratorista',status:'aprovado'}));
  await assertFails(updateDoc(doc(db('pendingSupervisor'),'usuarios','pendingB'),{papel:'operador',perfil:'operador',status:'aprovado'}));
  await assertFails(updateDoc(doc(db('pendingSupervisor'),'usuarios','pendingSupervisor'),{turno:'B'}));
});

test('ativação própria nunca promove operador nem reativa supervisor bloqueado ou excluído',async()=>{
  const activation={papel:'supervisor',perfil:'supervisor',status:'aprovado'};
  for(const uid of ['opA','pending','pendingTratorista','blocked']) {
    await assertFails(updateDoc(doc(db(uid),'usuarios',uid),activation));
    await assertFails(updateDoc(doc(db(uid),'usuarios',uid),{...activation,solicitacaoPerfil:'supervisor'}));
  }
  await env.withSecurityRulesDisabled(async c=>{
    await Promise.all([
      setDoc(doc(c.firestore(),'usuarios','supBloqueado'),profile('supervisor','A','santo_inacio','bloqueado')),
      setDoc(doc(c.firestore(),'usuarios','supExcluido'),{...pending('supervisor'),status:'excluido'}),
      setDoc(doc(c.firestore(),'contasEncerradas','pendingSupervisor'),{encerrada:true}),
      setDoc(doc(c.firestore(),'contasEncerradas','supRemovido'),{encerrada:true}),
    ]);
  });
  for(const uid of ['supBloqueado','supExcluido','pendingSupervisor']) await assertFails(updateDoc(doc(db(uid),'usuarios',uid),activation));
  await assertFails(setDoc(doc(db('supRemovido'),'usuarios','supRemovido'),profile('supervisor')));
  await assertFails(setDoc(doc(db('supBloqueado'),'usuarios','supBloqueado'),profile('supervisor')));
});

test('PATCH HTTPS de ativação respeita máscara e regras, sem alterar a frente nem reabrir bloqueios',async()=>{
  const host=process.env.FIRESTORE_EMULATOR_HOST;assert.ok(host);
  const project='demo-dm-agro';const now=Math.floor(Date.now()/1000);
  const encode=v=>Buffer.from(JSON.stringify(v)).toString('base64url');
  const token=uid=>encode({alg:'none',typ:'JWT'})+'.'+encode({sub:uid,user_id:uid,aud:project,iss:'https://securetoken.google.com/'+project,iat:now,exp:now+3600,firebase:{sign_in_provider:'custom'},email:'matricula.80911@acesso.dm-agro.app'})+'.';
  const patch=(uid,fields={papel:'supervisor',perfil:'supervisor',status:'aprovado'})=>fetch(
    `http://${host}/v1/projects/${project}/databases/(default)/documents/usuarios/${uid}?updateMask.fieldPaths=papel&updateMask.fieldPaths=perfil&updateMask.fieldPaths=status&currentDocument.exists=true`,
    {method:'PATCH',headers:{Authorization:'Bearer '+token(uid),'Content-Type':'application/json'},body:JSON.stringify({fields:Object.fromEntries(Object.entries(fields).map(([key,value])=>[key,{stringValue:value}]))})});
  const activated=await patch('pendingSupervisor');assert.equal(activated.status,200);
  const data=(await activated.json()).fields;assert.equal(data.status.stringValue,'aprovado');assert.equal(data.frenteId.stringValue,'santo_inacio');assert.equal(data.turno.stringValue,'A');
  assert.equal((await patch('pending')).status,403);
  await assertSucceeds(updateDoc(doc(db(owner),'usuarios','pendingSupervisor'),{status:'bloqueado'}));
  assert.equal((await patch('pendingSupervisor')).status,403);
});
