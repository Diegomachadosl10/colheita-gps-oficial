# DM AGRO 1.3.1 — Peças a pedir

Android `versionCode 10`, pacote `com.colheitagps.app`, ramificação `dm-agro-login`.

## Mudanças

- O cartão duplicado **Equipamentos** da tela inicial foi substituído por **Peças a pedir**. Equipamentos continuam na tela **Pendências**, com as mesmas opções e permissões da 1.3.0.
- Peças permitem código/número do item, nome/descrição, quantidade inteira positiva e equipamento de referência opcional. O código é texto: zeros à esquerda são preservados.
- Até seis fotos podem ser escolhidas no celular ou capturadas pela câmera. As cópias ficam na pasta da equipe no aparelho; a foto original da galeria é preservada. O formulário mantém um rascunho antes de abrir a galeria/câmera e permite retomá-lo. Uma foto em tamanho completo salva pela câmera pode ser recuperada nesse rascunho caso a tela seja recriada.
- A lista diferencia **A pedir**, **Pedido** e **Recebido**. Recebidos e excluídos aparecem no histórico. O autor pode editar seu pedido ativo; gestores podem editar pedidos da equipe, mudar a situação e excluir com preservação no histórico. A exclusão continua protegida pelas permissões de manutenção existentes.
- **Compartilhar pedido e fotos** usa o compartilhamento do Android, incluindo código, descrição, quantidade, referência e as fotos disponíveis nesse aparelho.
- Somente o texto sincroniza automaticamente entre os celulares da mesma frente/turno. Fotos são compartilhadas manualmente. Os pedidos usam o grupo de manutenção já existente, identificado por `categoriaRegistro=peca`, sem aparecer como equipamentos ou pendências de máquinas. O sincronizador e os avisos de manutenção existentes continuam atendendo os novos registros; o resumo do pedido inclui código e quantidade.
- Em Notificações, o endereço público correto da Cloudflare aparece preenchido: `https://dm-agro-servico.dieguiin-msl.workers.dev`. Uma configuração válida já salva é preservada ao carregar a tela. Para o principal, **Ativar avisos** salva o endereço e a frente escolhida antes de registrar o aparelho; mostra o andamento e só confirma autorização após o servidor aceitar o registro. Os demais gestores continuam usando o serviço definido pelo principal e sua equipe cadastrada.

As ações e telas de edição/exclusão de equipamentos permanecem como na 1.3.0. Mapas, GPS, talhões, O.S., estoque, ocorrências, chuva, relatórios, permissões, entrada por matrícula e atualização pelo próprio aplicativo são preservados.

## Firebase e Cloudflare

**Não há alteração em `firebase/firestore.rules`, `firebase/storage.rules` ou `backend/worker.js`.** Esta versão usa as regras 1.3.0 já publicadas e o serviço já configurado. Não é necessário gerar outra chave, alterar o Worker ou publicar novas regras para adicionar Peças a pedir.

Os registros de peças são um subtipo de manutenção, com o mesmo envelope de autorização por UID e equipe. Saves de pendências preservam os registros de peças; saves de peças preservam equipamentos e pendências. Fotos continuam fora do conteúdo enviado ao Firestore e são mantidas nas cópias locais e no backup operacional existente.

O plano continua **Firebase Spark / Cloudflare Workers Free**, com suas cotas gratuitas. Não ativar Storage nem faturamento. O código não contém chave administrativa privada.

## Publicação pelo celular

1. Extrair `PUBLICAR-DM-AGRO-1.3.1.zip` em uma pasta nova.
2. GitHub → Releases → New release: tag **v1.3.1**, ramificação **dm-agro-login**, título **DM AGRO 1.3.1**.
3. Na caixa **Attach binaries…**, abaixo da descrição, anexar somente os cinco arquivos dessa pasta: APK, `dm-agro-update.json`, `SHA256.txt`, código ZIP e bundle.
4. Publicar como versão estável e mais recente (**Latest**), sem Pre-release.
5. No DM AGRO já instalado: menu ⋮ → **Verificar atualização → Baixar → Instalar**. Não desinstalar nem limpar dados. Atualizar os dois celulares antes de testar a nova tela.

**O JSON da chave privada do Firebase não é um arquivo dessa publicação e deve permanecer privado.** O manifesto público correto chama-se `dm-agro-update.json` e vem dentro do pacote.

O envio de commits desta sessão vinha sendo recusado pelo GitHub com HTTP 403. Os anexos de código e bundle guardam o código atual enquanto essa gravação continuar bloqueada. Os Source code automáticos do GitHub dependem do commit remoto da tag e não substituem os anexos.

## Teste nos celulares

1. Conferir que o início mostra somente **Pendências** para acessar equipamentos e o novo **Peças a pedir** no lugar do cartão duplicado.
2. Cadastrar uma peça fictícia com código `00081-AB`, quantidade 2, descrição e referência. Conferir que os zeros permanecem e que a peça não cria um equipamento na manutenção.
3. Escolher uma foto da galeria e tirar outra pela câmera. Salvar e abrir as fotos. Compartilhar o pedido e conferir código, quantidade e anexos no seletor do Android.
4. Abrir uma pendência anterior, editar/salvar e retornar às peças. Os dois conjuntos devem continuar preservados.
5. No outro celular da mesma frente e turno, conferir o texto da peça. As fotos serão exibidas somente se também estiverem disponíveis nesse aparelho; usar compartilhamento manual para enviá-las.
6. Como gestor, marcar uma peça como recebida e conferir o histórico. Excluir outra peça fictícia e conferir que as informações e fotos locais continuam no histórico. Operador não deve ter a ação de excluir.
7. Em Notificações, manter Santo Inácio para o administrador e tocar em **Ativar avisos**. Autorizar no Android e aguardar **Avisos autorizados neste aparelho**. O servidor precisa estar configurado e o aparelho conectado; chave reconhecida em `/health` não equivale a teste de entrega FCM.

Validação automatizada local inclui domínio, formulários, cópia de anexo da galeria, preservação dos dados, renderização nativa, regras em emuladores `demo-dm-agro`, serviço simulado e preparo de atualização. Câmera real, compartilhamento em outro aplicativo e entrega FCM continuam sendo verificações dos celulares do proprietário.

Resultado: compilação concluída; **57 testes Android**, **18 testes Firebase emulados**, **12 testes do serviço** e **3 testes Python** aprovados (**90 no total**). A prévia `PAINEL-1.3.1.png` foi renderizada pelos componentes Android em teste nativo.
