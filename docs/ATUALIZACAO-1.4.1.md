# DM AGRO 1.4.1 — Ocorrências e fotos nos estoques

## Mudanças

A grade da página inicial podia reduzir sua última linha a um pixel quando tinha número ímpar de ferramentas. O espaço vazio da segunda coluna estava com altura fixa, enquanto o cartão vizinho preenchia a altura da linha. A correção mantém as duas células com a altura da linha, preservando o cartão inteiro. Ocorrências continua disponível para todos os perfis aprovados, dentro da própria equipe.

O envio de vários estoques agora abre Preparar envio de estoques. Cada lote tem seu próprio cartão, uma foto opcional e o controle Enviar foto deste lote. É possível misturar lotes com e sem foto. Adicionar ou trocar a foto guarda uma cópia local associada somente ao lote escolhido. Desmarcar a foto não a apaga do cadastro. Fotos da galeria e mapas pintados já salvos podem ser usados.

Enviar mensagem e fotos prepara texto e anexos no compartilhamento do Android; uma foto usa ACTION_SEND e várias usam ACTION_SEND_MULTIPLE, com ClipData e permissão de leitura para todos os anexos. O PDF reúne o resumo de todos os lotes e páginas identificadas por lote para as fotos incluídas. O envio é feito em uma operação pelo seletor do Android. O aplicativo destinatário controla como apresenta texto, legendas e anexos: conferir o recebimento no WhatsApp ao testar.

Também foi corrigido o caminho permitido no FileProvider para a pasta cache/equipes. Antes, os fluxos de estoque criavam anexos nessa pasta, mas o Android não conseguia gerar as URIs de compartilhamento. O acesso continua restrito aos arquivos concedidos pelo aplicativo; o provider permanece não exportado.

As fotos ficam no celular, com compartilhamento manual. Não é feita sincronização de imagens pelo Storage pago. Se uma foto cadastrada em outro aparelho não estiver no celular atual, a tela informa e permite escolher uma foto local ou seguir sem ela.

A preparação verifica novamente o acesso no servidor ao entrar e voltar à tela. Somente gestores autorizados da equipe e o administrador principal usam o envio de estoques. O novo editor modifica apenas o anexo local do lote escolhido, preserva os dados atuais e usa a mesma trava de armazenamento das outras atividades da equipe. A seleção de fotos omitidas e o lote da galeria são preservados na recriação da tela.

## Publicar pelo celular

1. Baixar e extrair PUBLICAR-DM-AGRO-1.4.1.zip em uma pasta nova. Ele contém cinco arquivos na raiz.
2. Abrir https://github.com/Diegomachadosl10/colheita-gps-oficial/releases/new .
3. Criar a tag v1.4.1, destino dm-agro-login, título DM AGRO 1.4.1.
4. Em Attach binaries… abaixo da descrição, anexar somente DM-AGRO-1.4.1.apk, dm-agro-update.json, SHA256.txt, DM-AGRO-1.4.1-codigo.zip e DM-AGRO-1.4.1-dm-agro-login.bundle. Não anexar chaves privadas JSON do Firebase da pasta Downloads.
5. Publicar como versão estável mais recente (Latest), sem Pre-release.
6. No DM AGRO, usar o aviso de atualização ao abrir o app ou o menu Verificar atualização, baixar e instalar. Não desinstalar nem limpar os dados. Instalar também no celular do operador que mostrou o cartão cortado.

Se o ZIP der erro no celular, os mesmos cinco arquivos estão separados em reenvio-1.4.1.

A gravação automática no GitHub depende da permissão da conexão. Se houver erro HTTP 403, o código remoto continua como estava. Os anexos de código e bundle contêm as alterações atuais da ramificação dm-agro-login; o Source code automático da release pode continuar apontando para o commit remoto antigo. A ramificação main é preservada.

## O que testar no celular

1. Na conta de operador/tratorista, rolar a página inicial até Ocorrências, tocar no cartão e conferir + Nova ocorrência. O atalho ⋮ → Ocorrências de campo continua funcionando.
2. No gestor, abrir Estoque → Compartilhar vários estoques e selecionar dois ou três lotes.
3. Em Preparar envio de estoques, adicionar uma foto ao primeiro lote, outra ao terceiro e deixar o segundo sem foto. Confirmar que a foto de um lote não troca a dos outros.
4. Tocar em Enviar mensagem e fotos, escolher WhatsApp e conferir todos os lotes e as duas imagens no destinatário.
5. Voltar ao DM AGRO, desmarcar uma foto e repetir: a outra deve seguir com todos os dados dos lotes. Desmarcar todas e conferir o envio só de texto. A foto desmarcada deve continuar salva para o próximo envio.
6. Usar Enviar tudo em PDF e conferir resumo dos lotes e páginas de fotos identificadas por lote. Abrir o PDF recebido no outro celular.
7. Opcionalmente testar um mapa pintado salvo no lugar da foto. Conferir que os registros de estoque antigos, suas fotos e os demais módulos permanecem preservados.

## Validação

Compilação Android e 82 testes Android aprovados, incluindo grade com 1, 3, 5 e 9 cartões, telas estreitas, letras ampliadas, alcance do cartão Ocorrências, fotos por lote, omissão sem apagar, recriação da seleção, cópia de imagem e URIs geradas pelo FileProvider com os caminhos de produção.

O teste de composição do PDF usa Canvas/Bitmap nativos e captura as páginas porque o Robolectric 4.14 não implementa o gravador PDF nativo do Android. A abertura e o recebimento do PDF final, assim como o comportamento do WhatsApp, precisam ser confirmados nos celulares. As prévias OCORRENCIAS-1.4.1.png e FOTOS-ESTOQUES-1.4.1.png são renderizações Android de teste, com lotes e fotos fictícios.

Não há alterações em firebase/firestore.rules, firebase/storage.rules ou backend/worker.js. Não é necessário republicar regras ou mexer no Cloudflare. O serviço 1.4.0 e a configuração de notificações já testada continuam válidos. Os planos gratuitos permanecem iguais.
