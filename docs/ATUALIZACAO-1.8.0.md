# DM AGRO 1.8.0 — Compartilhamento simples e cadastro de supervisores

As ferramentas agora compartilham somente imagens. O cadastro de supervisor entra sem aprovação do administrador principal, com acesso limitado à frente e ao turno escolhidos. Operadores e tratoristas continuam aguardando aprovação de um gestor da própria equipe. O administrador principal continua com acesso geral e pode bloquear, ajustar ou excluir os demais cadastros.

## Instalação e configuração pelo celular

1. Primeiro, abra o [Firestore do DM AGRO](https://console.firebase.google.com/project/dm-agro-c0e04/firestore/rules) e entre na aba **Regras**.
2. Guarde uma cópia das regras atualmente publicadas. Copie todo o conteúdo de [firebase/firestore.rules](../firebase/firestore.rules), substitua o texto do editor e toque em **Publicar**. Na entrega do chat, o mesmo conteúdo está em **REGRAS-FIRESTORE-1.8.0.txt**, para facilitar a cópia pelo celular.
3. Instale **DM-AGRO-1.8.0.apk** sobre o aplicativo atual. Não desinstale e não limpe os dados. O pacote mantém a assinatura das versões anteriores.
4. Cadastre um supervisor com matrícula própria e selecione frente e turno. Ele deve entrar ao concluir o cadastro. Um pedido antigo de supervisor ainda pendente será ativado quando essa pessoa entrar usando a versão nova.
5. Cadastre um operador. Ele deve aguardar aprovação. No supervisor, confira que só é possível aprovar operadores/tratoristas da mesma frente e turno. O cadastro de outra equipe deve ficar inacessível.
6. Compartilhe dois lotes do estoque, escolhendo foto ou mapa pintado em um e deixando o outro sem foto. Os dados dos dois lotes e a foto escolhida devem sair como imagens, sem opção de PDF. A seleção de não enviar uma foto não apaga o anexo do registro.
7. Confira as opções de Mapas, pendências, peças, ocorrências, chuva, caminhões e relatórios. Verifique também a conexão pelo Wi-Fi, cuja correção por HTTPS foi preservada.

Não é necessário alterar Cloudflare, índices, Authentication ou Storage para esta versão. O plano permanece gratuito. A publicação das regras no projeto real depende do acesso do proprietário ao console; os testes aqui usam somente um emulador de demonstração.

## Por que as regras mudaram

As regras anteriores exigiam que todo novo cadastro começasse como operador pendente. Só alterar a tela do Android faria o servidor recusar um supervisor aprovado automaticamente. A mudança foi limitada ao cadastro e à ativação do próprio pedido de supervisor:

- Um novo cadastro pode nascer como `supervisor / supervisor / aprovado` somente quando solicita supervisor. Seu e-mail interno deve corresponder à matrícula, o UID deve ser o da própria sessão e frente/turno devem ser válidos.
- Um pedido antigo com `status: pendente`, papel e perfil operador e `solicitacaoPerfil: supervisor` pode mudar somente `papel`, `perfil` e `status` para supervisor aprovado. A ativação por HTTPS usa o ID token da própria pessoa, aplica as mesmas regras e exige que o documento já exista.
- Um operador já cadastrado não pode trocar a solicitação para supervisor, aprovar a si mesmo nem mudar equipe. Supervisores não podem aprovar outros supervisores, ajustar a própria frente/turno ou obter acesso geral.
- Cadastros bloqueados, excluídos ou marcados em `contasEncerradas` não podem usar essa ativação. Continua sendo possível ao administrador bloquear um supervisor.
- Só o UID principal mantém acesso global. As regras de registros, histórico, exclusão/restauração, notificações e isolamento por equipe não foram abertas nem substituídas.

A escolha de Supervisor no cadastro agora concede esse acesso após o servidor aceitar o cadastro; não haverá confirmação manual do administrador. Essa é a mudança de funcionamento solicitada pelo proprietário. Aplicativos antigos ainda podem criar solicitações pendentes; a nova versão trata os pedidos de supervisor pendentes sem sobrescrever cadastros já aprovados, bloqueados ou excluídos.

## Compartilhamento e Mapas

PDF e escolha de formato foram retirados das ações de compartilhamento das ferramentas. Listas, relatórios, caminhões, chuva, pedidos de peças, pendências, ocorrências, O.S. e estoque usam imagens PNG organizadas. Registros extensos continuam em várias páginas de imagem, enviadas juntas com permissão de leitura de cada arquivo. Fotos disponíveis no aparelho entram como imagens adicionais identificadas. Nenhum PDF é gerado ou oferecido por essas ações.

No estoque, os lotes selecionados continuam juntos, com foto ou mapa pintado opcional em cada lote. Os dados dos lotes sem foto também vão em imagem. Os nomes legíveis da seleção de mapas foram preservados.

A biblioteca e o menu do mapa contêm somente ações de mapa: abrir, importar, renomear, excluir, compartilhar imagem, GPS, pintura, medições, rotação e salvar mapa pintado. O.S. continua na ferramenta **Ordens de serviço**, incluindo encerramento e histórico. Os arquivos originais PDF e a georreferência permanecem locais para a importação e o funcionamento dos mapas. O link público do Drive continua disponível.

A navegação Voltar, o botão Sair, a atualização pelo aplicativo, a sincronização dos registros e as permissões anteriores foram preservados. Não houve migração destrutiva, exclusão de registros ou envio automático de fotos ao serviço pago de Storage.

## Verificação

- Compilação de `testDebugUnitTest assembleDebug` concluída.
- **133 testes Android aprovados**, incluindo envio de uma ou várias imagens com autorização de leitura em Android API 26 e 35, estoque misto com fotos opcionais, menus de mapa, GPS/rotação, histórico de O.S. e permissões.
- **27 testes Firebase aprovados** no projeto isolado `demo-dm-agro`: novo supervisor aprovado, ativação de pedido antigo por SDK e REST, aprovação apenas da equipe, bloqueios, isolamento entre equipes e proteção do administrador.
- O teste de renderização confirmou imagens paginadas para lotes selecionados com foto omitida, preservando os anexos armazenados. A instalação, o cadastro no projeto real após publicar as regras e o recebimento pelo aplicativo escolhido para compartilhar ainda devem ser conferidos no celular.
- APK versão **1.8.0**, código **21**, Android mínimo **8.0 / API 26**. Assinatura conferida pelo preparador de atualização.

## Publicar atualização para os outros celulares

Depois de publicar as regras e testar o APK:

1. Extraia **PUBLICAR-DM-AGRO-1.8.0.zip**.
2. Abra [Nova release no GitHub](https://github.com/Diegomachadosl10/colheita-gps-oficial/releases/new).
3. Use tag **v1.8.0**, destino **dm-agro-login** e título **DM AGRO 1.8.0**.
4. Na área **Attach binaries…**, abaixo da descrição, anexe os cinco arquivos extraídos: APK, `dm-agro-update.json`, `SHA256.txt`, ZIP de código e bundle da ramificação. Não use **Add files** dentro da descrição.
5. Publique como versão estável mais recente, sem marcar **Pre-release**. Os aparelhos poderão encontrar a atualização pela consulta automática ao abrir o aplicativo ou por **Verificar atualização**.

A branch `main` não deve ser substituída. Anexar um ZIP de código a uma release não atualiza os arquivos da branch remota. Quando o envio do código ao GitHub estiver disponível, o workflow **Gerar APK Colheita GPS** poderá ser executado na branch `dm-agro-login`, com a assinatura permanente configurada. A publicação de regras Firebase continua sendo um passo separado do APK e do workflow.
