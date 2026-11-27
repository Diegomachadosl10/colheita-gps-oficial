# Entrega 3 — serviço de notificações do Beta

Continuação do desenvolvimento em 26–27/11/2026, somente para nossa equipe/empresa, na branch `dm-agro-professional-beta`. O serviço e o protocolo estão implementados e testados em ambiente isolado. **Nenhum Worker ou regra foi implantado, e os avisos continuam desabilitados no aplicativo Beta.**

## Implementado

- Worker novo, independente do estável, em [backend/beta-worker.js](../../backend/beta-worker.js), pronto como arquivo único para o editor da Cloudflare no celular. Não há pacotes npm de produção, novos recursos pagos ou alteração do plano Firebase.
- Namespace fixo `ambientes/beta`, credencial de configuração própria e rotas `/beta/v1`. O Worker recusa as rotas estáveis e não contém operações de correção/exclusão de credenciais. Uma aprovação em produção não concede acesso ao serviço Beta.
- Supervisores recebem avisos dos turnos da própria frente. Diego recebe somente a frente escolhida no aparelho. O autor não recebe o próprio aviso. Solicitação de supervisor vai somente a Diego; as demais solicitações chegam também aos supervisores da frente.
- Título, descrição, revisão e escopo vêm do registro tipado confirmado no servidor. Mapas pessoais não geram avisos. O chamador não escolhe destinatários nem substitui o conteúdo da mensagem.
- Paginação de supervisores e aparelhos, progresso persistido, limite por chamada e controle de concorrência. Um aparelho já confirmado não é reenviado apenas porque outro falhou depois. Falhas ambíguas entre FCM e checkpoint ainda podem repetir o transporte; o aparelho mantém deduplicação do evento.
- Tokens, vínculos e checkpoints ficam privados ao serviço. Clientes não podem ler nem forjar esses documentos, inclusive o UID administrador do aplicativo. Uma configuração que contenha campos de credencial por engano também fica ilegível aos clientes até ser corrigida.
- O Android confirma ambiente/projeto/protocolo em `/health` antes de enviar o token do usuário ao Worker. Não preenche um URL estável nem um endereço fictício. Avisos e toques sem marca Beta são rejeitados.
- Classe de trabalho, nomes de fila, tags de cancelamento, canal de notificação, preferências e ação do PendingIntent são próprios do Beta. A desativação não remove o token FCM compartilhado da instalação. A fila estável não é cancelada nem enviada ao serviço Beta.
- Configurações candidatas `firebase.beta.json` e `backend/wrangler.beta.jsonc`, com regras e índices preparados. O Actions usa somente emuladores `demo-dm-agro` para as regras e não tem etapa de implantação.

## Testes e evidências

Os resultados finais estão em [evidencias-entrega-3.json](evidencias-entrega-3.json). Os testes do servidor geram chaves efêmeras em memória e simulam integralmente Google/Firebase/FCM. Nenhum aparelho real recebeu um aviso desses testes.

Código desta entrega: `daab9e6` (serviço/regras), `2ba04fe` (Android) e `56c4ff69a495d66f3a3b5d80a7572d27f516d551` (proteção da configuração). [Execução no GitHub Actions](https://github.com/Diegomachadosl10/colheita-gps-oficial/actions/runs/46011957590).

| Verificação local | Resultado |
| --- | --- |
| Compilação Android | `assembleDebug` aprovado |
| Testes Android/Robolectric | 319 aprovados, sem falhas/erros/ignorados |
| Regras Firestore/Storage | 28 estáveis + 28 de compatibilidade + 13 Beta; todos aprovados |
| Worker Beta | 21 aprovados com rede totalmente simulada |
| Worker estável e publicador | 18 e 7 aprovados |
| Lint | Zero erros; 123 avisos, ante 128 na base auditada |
| Identidade do APK local | Mesmo applicationId e certificado; sem distribuição |

Os casos cobrem assinaturas inválidas, tokens de outro projeto, contas bloqueadas/revogadas, perfis só da produção, outra frente, turnos da mesma frente, solicitações de supervisor/ajudante, payload opaco, revisão antiga/nova, ID de registro forjado, mais supervisores/aparelhos do que uma página, lease, falha parcial e dispositivo `UNREGISTERED`.

O contrato fictício [beta-notification.json](../../app/src/test/resources/beta-notification.json) é conferido pelos testes Node e Android. Os testes Android também verificam a rejeição de avisos antigos antes de acessar Firebase, o namespace da fila persistida e o endereço/protocolo exigidos pelo cliente.

As regras estáveis, o Worker estável, `firebase.json`, o `applicationId`, o certificado e os dados existentes permanecem preservados. A versão interna continua 2.3.0/código 39. O Actions guarda somente relatórios, sem APK nem chave.

## O que falta para os avisos funcionarem no celular

O [roteiro pelo celular](../../backend/BETA.md) explica a preparação reservada para depois da autorização: criar outro Worker Free, configurar a credencial privadamente, conferir permissões e saúde do serviço, conciliar as regras reais antes da adição Beta, conferir índices e configurar o URL real. Nenhuma dessas ações externas foi feita nesta entrega.

Depois disso, será necessário preparar uma compilação de homologação autorizada, mantendo a assinatura, o backup e a compatibilidade de instalação. A flag só poderá ser habilitada nessa etapa. Confirmar no Android físico a entrega, abertura do registro, segundo plano, falta/retorno de internet e troca de conta/frente.

O corte de filas e o retorno entre canais precisam de homologação própria: trabalhos antigos ficam pendentes, e a classe Beta distinta impede que um cliente estável antigo os interprete como eventos de produção. Isso não é uma implementação completa de migração de filas nem de rollback. O retorno Android exige código de versão crescente e nunca desinstalação ou limpeza de dados.

Limpeza automática dos documentos antigos, monitoramento das cotas e recuperação após falhas prolongadas ainda precisam ser concluídos antes de ampliar os testes. O projeto/quotas são compartilhados; a conta de serviço ignora regras de cliente e o isolamento do servidor é imposto pelo código. Não há promessa de infraestrutura gratuita ilimitada nem de entrega instantânea quando o Android/internet impedirem o recebimento.

Servidor preparado não significa Beta pronto para distribuir. Canal opt-in, assinatura no CI, homologação, autorização offline persistente, catálogo editável de frentes/turnos, relatórios de turno, novos indicadores, IA e revisão visual completa continuam no plano. Produção permanece 2.2.7, sem atualização nova publicada.
