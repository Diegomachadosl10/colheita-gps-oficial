# Serviço gratuito DM AGRO

Um módulo único para **Cloudflare Workers Free**, sem pacotes npm de produção, chave no APK, Firebase Functions ou alteração do plano Spark. A conta gratuita tem cotas de requisições e CPU; não selecionar Workers Paid nem ativar faturamento. O serviço usa recursos gratuitos do Firebase Authentication, Firestore e Cloud Messaging dentro das cotas do projeto.

## Implantação pelo celular

1. Cloudflare → Workers & Pages → Create application → Start with Hello World → Deploy.
2. Edit code → substituir o exemplo pelo conteúdo completo de `worker.js` → Deploy. Não conectar ao ZIP automático Source code do GitHub; ele pode estar desatualizado enquanto o envio de commits estiver bloqueado.
3. No projeto Firebase **dm-agro-c0e04**, Configurações do projeto → Contas de serviço → Firebase Admin SDK → Gerar nova chave privada. Guardar o JSON em local privado do celular. **Não enviar pelo chat, não anexar ao GitHub e não colocar no código do aplicativo.**
4. No Worker, Settings → Variables and Secrets → Add → tipo **Secret**, nome **FIREBASE_SERVICE_ACCOUNT**. Colar o conteúdo completo do JSON, incluindo as chaves externas `{ ... }`, e salvar/implantar. O segredo não entra em `worker.js`.
5. Verificar `https://SEU-WORKER.SEU-SUBDOMINIO.workers.dev/health`. Deve retornar o projeto correto e `configured: true`; esse campo confirma a presença do segredo, e não é um teste de envio real.
6. Publicar `firebase/firestore.rules` da mesma versão. No DM AGRO, menu ⋮ → Notificações → salvar o endereço HTTPS do Worker (sem `/health`). Só o UID principal pode salvar esse endereço.
7. Na mesma tela, escolher **Santo Inácio** como frente de notificações do administrador e tocar em **Ativar avisos**. Autorizar a permissão do Android. No supervisor, ativar os avisos com a própria conta: o servidor fixa frente e turno pelo cadastro aprovado.
8. Fazer um registro de teste por outra conta da mesma equipe. Confirmar a notificação no celular destinatário com o aplicativo em segundo plano. Outro município/frente não deve avisar o administrador inscrito em Santo Inácio. Mapas pessoais não geram avisos de equipe.

Se a configuração Firebase não permitir a geração de chave ou a permissão de Cloud Messaging estiver desativada, não substituir isso por credencial dentro do APK. Corrigir a configuração da conta de serviço no console. O administrador pode conferir Firebase → Configurações do projeto → Cloud Messaging → API Firebase Cloud Messaging HTTP v1. A conta de serviço precisa das permissões de Authentication Admin, acesso Firestore e envio FCM; recomenda-se uma conta dedicada com esses acessos ao invés de compartilhar outras credenciais.

## Segurança e operações

### Atualização do serviço 1.4.0

O Worker 1.4.0 adiciona `/devices/test`, restrito ao próprio aparelho previamente registrado de um gestor autenticado, com intervalo de 30 segundos. Esse teste verifica a aceitação real pelo FCM e informa recusas de permissão/API. A resposta de sucesso confirma o envio aceito pelo Firebase, não garante que o Android exibiu o aviso. O aplicativo mostra separadamente a data do último aviso efetivamente exibido.

No painel Cloudflare, abrir o Worker existente, **Editar código**, substituir todo `worker.js` pelo arquivo desta versão e **Implantar**. Manter o segredo `FIREBASE_SERVICE_ACCOUNT` já cadastrado. A rota pública `/health` passa a informar `serviceVersion: "1.4.0"`; ela continua sem expor credenciais e não equivale a um teste de entrega. Não é necessário mudar as regras Firebase nem o plano.

O aplicativo mantém uma caixa de saída durável para avisos de registros sincronizados e prioriza seu agendamento nas versões do Android compatíveis. Cada revisão é deduplicada no servidor. Alterações de um registro também geram aviso; a identidade deve coincidir com o editor que consta no registro confirmado. Revisões superadas são descartadas. Um erro FCM 404 genérico não remove mais o registro do aparelho; só o erro específico `UNREGISTERED` permite essa remoção.

Teste pelo celular: ativar os avisos, tocar em **Testar notificação neste celular**, conferir a notificação e tocar em **Atualizar diagnóstico**. Depois testar um registro feito por outra conta da mesma equipe, com o aplicativo destinatário em segundo plano. Não é enviada uma notificação normal ao próprio autor da alteração. Mapas pessoais continuam privados e não disparam avisos. O administrador recebe somente a frente escolhida no aparelho; cada supervisor recebe somente sua frente e turno.

### Controles preservados

- `PROJECT` e `OWNER` fixam o projeto e o UID principal, evitando que parâmetros do cliente selecionem outra conta/projeto.
- O token de acesso Firebase é validado por assinatura RS256, emissor, audiência, validade e situação atual da credencial. A identidade vem do token, nunca de um UID enviado pelo aplicativo.
- Correção e exclusão de contas só aceitam o UID principal, com autenticação recente, bloqueio contra operações concorrentes e proteção da própria conta administradora.
- Correção altera o e-mail interno do Authentication e a matrícula no cadastro, preservando senha e UID. Em falha de atualização do perfil, o e-mail original é restaurado. Uma conta excluída corrigida volta pendente, exigindo aprovação.
- Exclusão definitiva primeiro revoga acesso no cadastro, depois remove a credencial e, por último, o documento do usuário. Os registros operacionais por UID são preservados. Ações ficam em `auditoriaContas`, sem senhas ou chaves.
- Antes de remover o perfil, o servidor grava somente um marcador privado do UID em `contasEncerradas`. Ele impede recriação do cadastro por um token antigo ainda válido. O novo cadastro da matrícula liberada usa outro UID e continua dependendo de aprovação.
- Somente o aplicativo com usuário gestor pode registrar seu próprio token FCM. O supervisor não pode escolher outra frente. Cada aparelho administrador escolhe uma frente de notificações, inicialmente **Santo Inácio**. O acesso administrativo às outras frentes continua disponível.
- Títulos, destinatários e escopo de notificações vêm dos registros efetivamente salvos e dos cadastros verificados no servidor. O aplicativo 1.8.0 cadastra supervisores já aprovados e ativa pedidos antigos pendentes pelas regras Firestore; não envia pedido de aprovação ao administrador para novos supervisores. Clientes antigos ainda podem gerar um aviso de pedido de supervisor, destinado somente ao administrador na frente selecionada.
- Envio usa FCM de alta prioridade com mensagem de dados. O aplicativo valida novamente conta e equipe antes de exibir, evitando exibição de notificações de uma conta encerrada ou de outra equipe. O envio é imediato após a sincronização do registro; internet, permissões, serviços Google e gerenciamento de energia do Android controlam a entrega.
- Eventos repetidos e notificações recebidas têm deduplicação. A fila do aplicativo repete falhas transitórias; há controle de versões e leases no servidor. A disponibilidade não é garantida durante indisponibilidade de provedores ou esgotamento das cotas gratuitas.

Não fazer requisições manuais de teste a endpoints de exclusão em produção. Os testes automatizados usam chaves geradas em memória, identidades fictícias e respostas Google simuladas, sem tocar em usuários reais:

```sh
node backend/worker.test.mjs
```
