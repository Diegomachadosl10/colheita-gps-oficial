# DM AGRO 1.8.2 — Estoque por mensagem e foto do mapa

O envio normal de estoque volta ao formato anterior: os dados vão como texto na mensagem e a foto ou mapa pintado vai como anexo original. Não é gerada uma imagem de relatório com os dados do estoque. O envio de vários lotes juntos continua disponível, com foto opcional por lote.

## Usar pelo celular

1. Instale **DM-AGRO-1.8.2.apk** sobre a versão atual, sem desinstalar nem limpar os dados.
2. Em **Estoque de cana**, escolha o estoque ou use **Compartilhar vários estoques**.
3. Para anexar o mapa pintado, toque em **Adicionar/Trocar foto deste lote → Usar mapa pintado salvo**, confira a prévia e confirme. Mantenha marcada a foto dos lotes que deseja enviar.
4. Toque em **Enviar mensagem e fotos** e escolha **WhatsApp** no seletor do Android.
5. Confira o destinatário, o texto e os mapas antes de enviar. Os dados dos lotes sem foto seguem na mesma mensagem. Se nenhum lote tiver foto, o envio será somente texto.

Para o estoque ativo, o atalho **Compartilhar estoque** também envia mensagem e foto do mapa. O texto do estoque ativo mantém a formatação anterior. Na tela do estoque ativo foi retirado o atalho genérico que gerava imagem de relatório, evitando escolher o formato errado.

As fotos e mapas são copiados para o compartilhamento sem transformar seu conteúdo: o PNG do mapa pintado não recebe tabela ou cabeçalho de relatório. O mesmo mapa usado em vários lotes pode ir uma única vez, acompanhado do texto com todos os lotes. O usuário também pode escolher outro aplicativo no seletor do Android, incluindo a versão do WhatsApp instalada no aparelho.

## Preservado

- Correção do FileProvider que impedia o aplicativo de fechar ao preparar imagens, usando o contexto da aplicação e os caminhos restritos já configurados.
- Tratamento de erro no retorno à tela e conferência de sessão/equipe antes de abrir o seletor.
- Mapas pintados, GPS, rotação, O.S. e histórico, pendências e demais ferramentas. Os compartilhamentos das demais ferramentas continuam como antes desta correção.
- Seleção de fotos por lote, envio de vários lotes, campos dos registros e anexos originais. Desmarcar uma foto para um envio não a apaga do cadastro.
- Controle do administrador e dos supervisores por equipe. Esta versão não altera regras Firebase, Authentication, Cloudflare, índices ou plano.
- Não foram recolocadas opções de PDF.

## Verificação

- Compilação Android e **153 testes aprovados**.
- Testes com FileProvider real e os caminhos XML de produção, em Android API 28 e 35, conferem o texto junto dos anexos, um estoque ativo, vários lotes com dois mapas, lote sem foto e bloqueio de envio após mudança da sessão.
- Os bytes das imagens compartilhadas são comparados aos mapas originais. Isso confirma que o estoque não está sendo convertido em páginas de imagem de relatório e que as cores pintadas não foram substituídas.
- Mantidos os testes de compartilhamento de mapas, preservação de coordenadas, edição de pinturas e erro de pasta sem fechamento.
- Não foram enviados dados ao WhatsApp real durante os testes. A prévia, o destinatário e o recebimento no aplicativo escolhido devem ser conferidos no celular.
- APK versão **1.8.2**, código **23**, Android mínimo **8.0 / API 26**, mesma assinatura das versões anteriores.

## Publicar para os outros celulares

Extraia **PUBLICAR-DM-AGRO-1.8.2.zip** e abra [Nova release](https://github.com/Diegomachadosl10/colheita-gps-oficial/releases/new). Use tag **v1.8.2**, destino **dm-agro-login**, título **DM AGRO 1.8.2**. Anexe os cinco arquivos extraídos na área **Attach binaries…**, abaixo da descrição: APK, `dm-agro-update.json`, `SHA256.txt`, ZIP de código e bundle. Publique como versão estável mais recente, sem marcar **Pre-release**. Os aplicativos poderão encontrar a atualização ao abrir ou em **Verificar atualização**.

A `main` deve permanecer preservada. Se o GitHub recusar gravação, o APK e o código estão disponíveis no pacote desta entrega, sem publicação automática. Anexar os arquivos à release não modifica o código da branch remota; executar Actions antes de atualizar a branch usará o código antigo.
