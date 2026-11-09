# Ativar equipes no Firebase — DM AGRO 1.1.0

**Plano obrigatório nesta entrega: Spark gratuito. Não fazer upgrade nem ativar faturamento.** O envio automático de anexos está desativado no APK.

O código foi preparado para o projeto **dm-agro-c0e04**. O administrador principal é exclusivamente o UID **Z8zjZP3iWjRSD6imwiDO4VLboOY2**, informado pelo proprietário nesta conversa. Esse UID é um identificador público de autorização, não uma senha nem uma chave de serviço.

**As regras deste pacote são uma proposta testada localmente. Ainda não foram publicadas no projeto real.** Não instale o APK 1.1.0 para uso diário antes de concluir esta configuração. A versão 1.0.3 continua disponível na conversa.

## Por que as regras precisam mudar

As regras enviadas pelo proprietário conferiam somente a frente ao permitir aprovações por supervisores. Também permitiam que qualquer supervisor lesse as pendências da coleção antiga de todas as equipes. Isso não atende à separação confirmada: **frente + turno**.

As novas regras:

- Reservam o acesso geral ao UID principal, mesmo se outra conta tiver um campo `papel: administrador`.
- Permitem que supervisores aprovados aprovem/bloqueiem apenas operadores e tratoristas da própria frente e turno. Somente o principal aprova supervisores ou ajusta equipes.
- Mantêm o cadastro próprio como solicitação pendente. O cadastro não pode autoaprovar nem alterar a própria equipe após criação.
- Isolam registros em `equipes/<frente>_<turno>/registros/<id>`, conferindo equipe, autoria e revisão em cada gravação. Alterações antigas não sobrescrevem versões novas.
- Nesta versão gratuita, fotos e PDFs ficam no aparelho. O usuário pode compartilhar fotos pelas opções existentes e PDFs por **Compartilhar PDF** no menu do mapa. Os registros de texto, aprovações e pinturas continuam sendo sincronizados pelo Firestore.
- Preservam a coleção antiga `pendencias`, com leitura do próprio autor e do principal. Como os registros antigos não têm equipe confiável, o acesso amplo dos supervisores foi removido; os dados não são apagados.
- Não apagam usuários nem registros existentes. Exclusões da nova coleção são marcas sincronizadas, e os objetos de anexos são preservados no Storage.

## 1. Firestore: publicar as regras

1. No Chrome, abra https://console.firebase.google.com/project/dm-agro-c0e04/firestore e entre em **Regras**.
2. Guarde uma cópia das regras atuais. A versão fornecida na conversa também está preservada em `firestore.anterior.rules` neste pacote.
3. Copie **todo** o conteúdo de `firestore.rules` deste pacote e substitua o texto do editor de regras.
4. Toque em **Publicar** e confirme que o console não aponta erro.

O aplicativo consulta `configuracao/equipes` apenas para verificar que as regras novas estão ativas. **Não é necessário criar esse documento**. O aplicativo não pode gravá-lo.

## 2. Firestore: criar o índice de aprovações

Em **Índices → Composto → Adicionar índice**, crie:

| Campo | Ordem |
|---|---|
| frenteId | Crescente |
| turno | Crescente |
| papel | Crescente |
| solicitacaoPerfil | Crescente |

Coleção: **usuarios**. Escopo da consulta: **Coleção**. Aguarde o estado **Ativado/Pronto**. O mesmo índice está descrito em `firestore.indexes.json`. Se o console informar que o índice já existe, mantenha o existente.

## 3. Manter o Storage sem ativação

A tela **Fazer upgrade do projeto** é do Storage. Não clique nesse botão. O proprietário confirmou que o plano deve continuar gratuito. Não é necessário publicar regras do Storage nem cadastrar uma conta de faturamento para esta versão.

**Limite desta entrega:** o texto das pendências e ocorrências aparece automaticamente na mesma equipe; fotos e PDFs precisam ser enviados manualmente. Quem recebe um mapa PDF pode importá-lo com o botão **+**; sendo o mesmo PDF, as pinturas e os demais dados associados ao identificador do mapa são compartilhados pela equipe. O aplicativo preserva fotos locais quando recebe alterações de texto de outro aparelho e não envia caminhos privados de fotos ao servidor.

O arquivo `storage.rules` permanece somente como preparação testada em emulador, sem publicação ou uso pelo APK gratuito. Qualquer envio automático de anexos por outro serviço exigirá uma implementação específica; não está ativado nesta versão.

## 4. Instalar e testar em dois celulares

Atualize sobre o APK anterior, sem desinstalar nem limpar os dados. A assinatura foi mantida.

1. Entre como principal, escolha uma equipe e aprove um supervisor da frente e turno correspondentes.
2. No supervisor, confirme que só aparecem solicitações de operadores/tratoristas daquela equipe; pedidos de supervisor e de outros turnos devem ficar indisponíveis.
3. No operador aprovado, crie uma pendência e uma ocorrência com foto. Com internet, confira os textos no supervisor e no administrador na mesma equipe. A foto deve continuar no aparelho do autor, com compartilhamento manual disponível. Não deve haver tentativa de ativar Storage ou cobrança.
4. Teste um supervisor da mesma frente com outro turno. Ele não deve acessar os registros anteriores.
5. Com o aplicativo já aberto, desligue a internet, crie um registro, religue a internet e aguarde o envio. A fila é preservada no aparelho. A abertura de uma nova sessão exige conexão para validar o acesso.
6. Teste alterações simultâneas: conflitos devem preservar uma cópia local, em vez de sobrescrever outro aparelho. A opção **Sincronização** mostra as pendências e permite recarregar a versão compartilhada. As cópias conflitantes ficam na pasta `conflitos` da equipe, incluída no backup.
7. Verifique mapas, pinturas, GPS, talhões, O.S., estoque, relatórios, caminhões, compartilhamento e backup dentro da equipe selecionada.
8. Teste **Voltar**: navega sem encerrar a sessão; na tela inicial, recolhe o aplicativo. Somente **Sair da conta** encerra a sessão e pede as credenciais novamente.

## Dados antigos do aparelho

Os dados das versões anteriores continuam preservados no local original. Eles não identificam equipe. Só o principal vê **Dados antigos deste aparelho** e pode copiar os dados para a equipe escolhida após conferir a origem. Não importe um conjunto que misture várias equipes. A importação não apaga os originais nem substitui IDs existentes; registros novos da equipe são mantidos. Supervisores não podem executar essa atribuição.

A troca de equipe de um usuário não move registros antigos para a nova equipe. Os registros continuam vinculados à frente e ao turno onde foram criados.

## Verificações realizadas e limites

Os testes de regras são executados com dados fictícios no projeto local `demo-dm-agro`; não usam nem modificam usuários ou registros reais. Os testes Android verificam políticas, isolamento local, referências de fotos e migração preservando originais. A compilação gera o APK de teste; o teste final com o projeto real depende da publicação das regras, do índice e dos dois celulares. O Storage não é usado pelo APK gratuito.

A credencial antiga do João (80911) continua sendo um caso separado de Firebase Authentication: a aprovação no Firestore não cria nem redefine uma senha.
