# Serviço de avisos do DM AGRO Beta

**Código preparado para revisão; não implantado.** O aplicativo continua com `BetaWorkspace.notificationsReady = false`. Este serviço não substitui `backend/worker.js`, não modifica contas e não ativa avisos sozinho.

O arquivo [beta-worker.js](beta-worker.js) é um módulo único, sem dependências npm de produção, para copiar no editor móvel da Cloudflare. A [configuração candidata](wrangler.beta.jsonc) define o nome `dm-agro-beta-servico`; nenhum workflow faz implantação. A conta e as cotas gratuitas do projeto continuam compartilhadas, sem nova empresa ou cobrança.

## Contrato e isolamento

- Projeto fixado no código: `dm-agro-c0e04`. Namespace Firestore: `ambientes/beta`. Somente os testes usam o projeto fictício `demo-dm-agro`, com todas as respostas Google simuladas.
- Configuração explícita `DM_AGRO_ENVIRONMENT=beta` e segredo **FIREBASE_BETA_SERVICE_ACCOUNT**. Não há fallback para `FIREBASE_SERVICE_ACCOUNT` da produção. O conteúdo da credencial não entra no repositório, APK, documento Firestore, relatório de teste ou chat.
- `GET /health` informa ambiente, protocolo 1, projeto e namespace. `configured: true` confirma a presença e a forma básica da configuração, **não** a autorização real no Google nem a entrega de uma notificação.
- Rotas POST exclusivas `/beta/v1/devices/register`, `/beta/v1/devices/unregister`, `/beta/v1/devices/test` e `/beta/v1/events/notify`. Exigem token Firebase assinado e envelope `environment: beta`, `protocol: 1`. As rotas antigas e todas as rotas de correção/exclusão de contas respondem 404, inclusive para Diego.
- Assinatura RS256, emissor, audiência, validade, UID, credencial desativada e revogação são verificados antes da autorização operacional. A identidade vem do token; uma matrícula ou UID enviado no corpo não concede autoridade.
- O servidor consulta somente o cadastro Beta. Aprovação na produção não autoriza o Beta. Somente gestores aprovados cadastram aparelhos; a frente do supervisor vem do cadastro. Diego escolhe a frente dos avisos do próprio aparelho.
- O Android exige um endereço Workers HTTPS com `beta` como segmento do nome do Worker, confirma `/health` antes de enviar o token do usuário e utiliza apenas as rotas com prefixo `/beta/v1`. Não sugere um URL de produção nem um endereço ainda inexistente.

## Avisos e entrega

O evento nasce de um item já confirmado em `ambientes/beta/equipes/{frente_turno}/registros/{id}`. O Worker verifica formato tipado 2, equipe, ID canônico, autor/editor, revisão e arquivamento. Não interpreta JSON opaco nem aceita título, destinatários ou descrição escolhidos pelo chamador. Mapas pessoais e pinturas não geram avisos.

Supervisores recebem registros dos turnos A, B e C da própria frente. Não recebem de outra frente. Diego recebe somente a frente escolhida em cada aparelho. O autor não recebe seu próprio aviso normal. Solicitação de supervisor vai apenas a Diego; solicitações de operadores, tratoristas e ajudantes vão também aos supervisores da frente, ainda respeitando a frente escolhida nos aparelhos de Diego.

FCM usa mensagens de dados de alta prioridade com marca explícita de Beta. O aplicativo rejeita mensagens da produção/versão antiga, valida a conta e a frente novamente e evita exibir o mesmo evento repetido. Um teste de envio é restrito ao aparelho do gestor autenticado e tem intervalo de 30 segundos. Aceitação pelo FCM não confirma exibição pelo Android.

Consultas são paginadas: não há o limite silencioso anterior de três aparelhos. Cada chamada envia no máximo seis avisos e percorre um número limitado de destinatários/aparelhos; o progresso fica salvo e a resposta `pending` pede continuação pela fila Android. Há um teto de 45 subrequisições por chamada, abaixo do limite usual de 50 do Workers Free. O limite de destinatários de um evento nesta Beta é 2.000; ele é uma proteção, não promessa de capacidade ou de cota gratuita disponível.

Uma lease com precondição de revisão impede processamento simultâneo comum. Cada aparelho aceito tem seu cursor confirmado antes de seguir. Falhas transitórias preservam o progresso. Uma queda entre aceitação pelo FCM e gravação do cursor ainda pode repetir o transporte; a deduplicação no aparelho continua necessária. Não é uma garantia de entrega exatamente uma vez.

Permissões e vínculo do token são consultados novamente durante a continuação. Trocar de conta não permite receber os dados da conta anterior. Só o erro FCM específico `UNREGISTERED` remove um registro; erros genéricos não apagam o aparelho. Essa remoção é limitada ao namespace Beta. O aplicativo não exclui o token FCM compartilhado da instalação ao sair do Beta.

Dispositivos sem atividade há 30 dias são ignorados e registros confirmados há mais de 24 horas não disparam um aviso antigo. Limpeza automática dos documentos de eventos/aparelhos ainda é pendente. O Firestore permanece sendo o histórico operacional; uma notificação não o substitui.

## Preparação pelo celular, somente após autorização

1. Manter o Worker estável e a versão 2.2.7 funcionando. Criar **outro Worker**, chamado `dm-agro-beta-servico`, em Workers Free. Copiar o arquivo completo `beta-worker.js` da branch Beta; não usar o código da `main` antiga.
2. Criar uma conta de serviço dedicada ao Beta no projeto correto. Avaliar os papéis mínimos `roles/firebaseauth.viewer`, `roles/datastore.user` e `roles/firebasecloudmessaging.admin`, com API Cloud Messaging HTTP v1 disponível. Guardar a chave privadamente; não enviá-la pelo chat nem anexá-la ao GitHub. Essa conta tem permissões de projeto e ignora regras de cliente: o namespace é imposto pelo código do Worker, não por uma restrição IAM a documentos.
3. No novo Worker, configurar `DM_AGRO_ENVIRONMENT` como texto `beta` e `FIREBASE_BETA_SERVICE_ACCOUNT` como **Secret**. Não editar os segredos do Worker estável. Não ativar Workers Paid, Blaze, Functions, KV, R2 ou Storage pago.
4. Conferir `/health` no URL real copiado do painel. Ele deve informar `environment: beta`, `workspace: ambientes/beta`, protocolo 1 e o projeto do aplicativo. Não há um URL Beta ativo informado nesta entrega.
5. Antes de publicar regras, comparar as regras reais do console com a base auditada, manter uma cópia e revisar a adição dos blocos Beta. A configuração candidata `firebase.beta.json` usa `firebase/firestore.beta.rules` e os índices Beta; `firebase.json` e as regras estáveis não foram alterados. Não substituir uma revisão real mais recente sem conciliação. Publicar regras/índices é uma ação separada que exige autorização.
6. Preparar `ambientes/beta/configuracao/servico` com **somente** o campo `url` do novo Worker. As regras candidatas permitem essa configuração apenas a Diego, recusando endereços estáveis e campos de credencial. A tela do aplicativo só será liberada depois da preparação/homologação autorizada; não alterar a flag para contornar esse processo.
7. Coordenar assinatura permanente, versão crescente e canal Beta opt-in antes de instalar sobre o aplicativo. Validar backup, arquivos estáveis preservados, cadastro/aprovação Beta e filas pendentes de ambas as versões. Não desinstalar nem limpar dados. Beta e estável têm a mesma identidade Android e não são instalados lado a lado.
8. Em uma compilação de homologação autorizada com avisos habilitados: registrar os aparelhos, testar a entrega, criar um registro por outra conta e conferir fundo/fechamento normal, toque no aviso, outra frente, outro turno, sem internet, retomada, bloqueio de usuário e troca de conta. Android, internet e serviços Google podem atrasar ou impedir avisos, principalmente com o aplicativo forçado a parar. Não há garantia de instantaneidade absoluta.

## Validação sem tocar na produção

O Actions da branch Beta executa o Worker estável, o Worker Beta, o publicador, as regras em emuladores e o aplicativo Android. Os testes Beta geram chaves temporárias apenas em memória e interceptam todas as chamadas externas. Nenhuma notificação é enviada de verdade por esses testes.

`app/src/test/resources/beta-notification.json` é um contrato fictício compartilhado pelos testes Node e Android. Ele demonstra que o envelope e o ID do evento emitidos pelo servidor são aceitos pelo aplicativo.

As regras bloqueiam leitura/gravação de tokens, vínculos e checkpoints por todos os clientes, inclusive o UID administrador do aplicativo. A operação privada fica no servidor. Antes de qualquer uso real, ainda faltam credenciais/configuração autorizadas, permissões e índices conferidos no servidor, teste FCM/Android físico, corte de filas e canal de atualização. As cotas Spark/Workers Free precisam ser acompanhadas; não há fallback pago automático.
