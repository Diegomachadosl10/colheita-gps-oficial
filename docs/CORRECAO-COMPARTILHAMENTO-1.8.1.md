# DM AGRO 1.8.1 — Correção do fechamento ao compartilhar

O compartilhamento por imagem agora prepara os arquivos usando o contexto da aplicação e trata erros dentro do retorno para a tela. Os caminhos de pastas permitidos no FileProvider foram preservados. Mapas pintados podem ser compartilhados pelo menu do mapa aberto e continuam disponíveis como foto opcional de cada lote no estoque.

## O erro corrigido

Foi reproduzido em teste Android com o FileProvider real e o XML de caminhos usado pelo aplicativo: `IllegalArgumentException: Failed to find configured root that contains …/cache/equipes/…/relatorios/…png`. O MainActivity redefine suas pastas para a equipe atual. Quando inicializado com esse Activity, o FileProvider aplicava novamente o trecho `equipes/` dos caminhos configurados, deixando a imagem fora da raiz calculada. A exceção ocorria no retorno à tela, fora do bloco de tratamento da tarefa em segundo plano, e encerrava o aplicativo.

A correção usa `applicationContext` para obter as URIs, sem ampliar as raízes do provedor. A preparação das URIs e a abertura do seletor de compartilhamento ficam dentro do tratamento de erro da própria tela. Falhas de arquivo, de permissão ou ao abrir o seletor apresentam uma mensagem e mantêm o aplicativo aberto. A preparação dos relatórios e mapas usa uma única fila para limitar o uso simultâneo de memória.

## Mapas pintados e estoque

- No mapa aberto, conclua a pintura, toque em **+ → Compartilhar mapa pintado** e escolha o aplicativo para enviar.
- Na biblioteca, toque no mapa e em **Compartilhar imagem do mapa**. Havendo uma imagem pintada salva, ela será enviada; quando não houver pintura salva, o mapa original é convertido para imagem.
- Antes de compartilhar o mapa aberto, a imagem é atualizada com as pinturas atuais. A gravação da prévia usa um arquivo temporário e substituição atômica, para não entregar um PNG parcialmente gravado. A imagem não é compartilhada se a atualização falhar.
- Compartilhar durante a edição de uma área pede para concluir ou cancelar a edição; isso evita gravar a remoção temporária de um polígono em edição.
- A opção de salvar a imagem do mapa usa o mapa aberto, em vez de uma foto anteriormente selecionada no estoque.
- No estoque, selecione os lotes e toque em **Adicionar/Trocar foto deste lote → Usar mapa pintado salvo**. Confira a prévia e toque em **Usar este mapa**. Depois, **Compartilhar estoques por imagem** envia os dados dos lotes e as imagens escolhidas juntos. Lotes sem foto continuam incluídos.
- O compartilhamento permanece somente por imagem. Não foram recolocadas opções de PDF. A leitura de mapas PDF, georreferência, GPS, rotação, coordenadas e dados existentes foram mantidos.

## Instalar e testar no celular

1. Instale **DM-AGRO-1.8.1.apk** sobre a versão atual, sem desinstalar nem limpar os dados. A assinatura é a mesma.
2. Compartilhe uma pendência ou um relatório e confirme que o seletor abre sem fechar o aplicativo.
3. Conclua uma pintura e compartilhe o mapa pelo botão **+ → Compartilhar mapa pintado**. Confira a imagem recebida.
4. Envie dois lotes do estoque: em um, use um mapa pintado; deixe o outro sem foto. Confira os dados dos dois lotes e o mapa enviado.

Esta correção não altera regras Firebase, Authentication, Cloudflare, índices, plano ou aprovação de usuários. Não é necessário publicar regras novas para corrigir o compartilhamento. O cadastro de supervisores continua usando as regras da versão 1.8.0 já entregues.

## Verificação

- O teste de regressão falhou no código anterior com a mesma classe de erro de FileProvider e passou após a correção.
- Compilação Android e **145 testes aprovados**, incluindo testes com recursos Android, FileProvider real, XML de caminhos de produção e envio de uma ou várias imagens em Android API 28 e 35.
- Os testes abrem o seletor Android simulado, leem os bytes PNG por `content://`, conferem permissão de leitura de cada imagem e verificam as cores da pintura enviada em Mapas e no estoque. Também conferem preservação das coordenadas, dos registros, da pintura em edição e bloqueio do envio quando a sessão muda.
- Os testes de regras da versão 1.8.0 não foram repetidos: as regras permanecem idênticas e tinham 27 testes aprovados.
- A seleção do aplicativo destinatário e o recebimento final no WhatsApp ou outro aplicativo precisam ser conferidos no celular. O teste local não enviou mensagens a ninguém.
- Versão **1.8.1**, código **22**, Android mínimo **8.0 / API 26**, assinatura verificada pelo preparador de atualização.

## Atualizar os demais celulares

Extraia **PUBLICAR-DM-AGRO-1.8.1.zip**. Em [Nova release](https://github.com/Diegomachadosl10/colheita-gps-oficial/releases/new), use tag **v1.8.1**, destino **dm-agro-login** e título **DM AGRO 1.8.1**. Anexe os cinco arquivos na área **Attach binaries…**, abaixo da descrição: APK, `dm-agro-update.json`, `SHA256.txt`, ZIP do código e bundle da branch. Publique como versão estável mais recente, sem **Pre-release**. O aplicativo poderá encontrar a atualização ao abrir ou por **Verificar atualização**.

A branch `main` permanece preservada. Se a conexão GitHub recusar gravação, o APK e o código continuam disponíveis no pacote desta entrega. Anexar o ZIP do código à release não atualiza os arquivos da branch remota; nesse caso, rodar Actions ainda usa o código antigo.
