# DM AGRO 1.9.2 — Pendências por mensagem e imagem

O compartilhamento individual permite escolher **Mensagem para WhatsApp** ou **Imagem**, mantendo as duas formas. Na tela de pendências, o botão **Compartilhar todas as abertas** prepara todas as pendências abertas da equipe atual, mesmo que a busca esteja filtrando um equipamento.

## Usar pelo celular

1. Para uma pendência, abra o equipamento e a pendência → Compartilhar. Para todas, abra Pendências → Compartilhar todas as abertas.
2. Escolha Mensagem para WhatsApp ou Imagem.
3. Marque/desmarque Incluir fotos disponíveis neste celular. Sem fotos locais, é possível enviar normalmente as informações.
4. Toque Compartilhar e escolha o WhatsApp e o grupo desejado no seletor do Android.

A mensagem reúne as pendências por equipamento, com descrição, prioridade urgente quando informada, O.S., data e situação. Concluídas, excluídas, cadastros de equipamento e pedidos de peças não entram no envio de todas as abertas. O conteúdo é consultado novamente ao confirmar, para retirar registros concluídos/excluídos enquanto a janela estava aberta.

No formato Imagem, cada pendência produz um PNG com descrição e fotos na mesma página. Quando houver várias fotos locais, elas ficam em uma grade no mesmo PNG. No envio de várias pendências, os PNGs são anexados juntos, um para cada pendência. Sem fotos, o PNG contém os dados. O tamanho da imagem acompanha o conteúdo em vez de desperdiçar uma página A4 só para texto.

Na mensagem com fotos, o texto contém todas as pendências e os anexos ilustrados trazem cada pendência com suas fotos na mesma página; itens sem foto continuam presentes no texto. Na mensagem sem fotos, é enviado somente texto editável ao WhatsApp, sem anexos de imagem. O aplicativo não envia automaticamente: o usuário escolhe o destino e confirma no WhatsApp.

## Preservação e limites

- O compartilhamento não conclui, altera ou apaga registros ou fotos. A escolha de omitir fotos vale somente para o envio.
- Somente a equipe já aberta na sessão é lida; não há consulta a outras frentes/turnos. O administrador pode trocar a equipe pelo fluxo existente antes de preparar o envio.
- Fotos continuam locais no plano gratuito. O exportador usa arquivos dentro da pasta da equipe atual, resolve caminhos canônicos e ignora anexos de fora dela. Nomes de usuário internos, IDs, caminhos e campos de sincronização não são publicados como metadados de texto.
- Imagens são preparadas uma a uma em uma fila, com decodificação reduzida e liberação de memória. Descrições extraordinariamente grandes que excederem o limite de imagem podem ser enviadas por mensagem sem fotos; não há corte silencioso do conteúdo.
- O seletor e as permissões de leitura usam o FileProvider com o contexto da aplicação e as raízes restritas existentes. Falhas de preparação/destino e falta de memória são tratadas sem fechar o aplicativo. A entrega é cancelada quando conta/equipe mudam antes de abrir o compartilhamento.
- Não há PDF novo, alteração de regras, cobrança, Storage ou modificação de Cloudflare. Estoque continua com seu envio de mensagem e mapa original. A revisão visual B/C e o menu simplificado da 1.9.1 estão incluídos.

## Instalação e publicação

APK 1.9.2, código 27, mínimo Android 8/API 26, assinatura permanente. Instale sobre a versão atual sem desinstalar nem limpar dados. Confira no aparelho uma pendência com foto e o envio de todas as abertas, com e sem foto.

Para os demais celulares, publique os cinco arquivos de PUBLICAR-DM-AGRO-1.9.2.zip como release estável mais recente, tag v1.9.2, destino dm-agro-login, na área Attach binaries abaixo da descrição da release. A detecção ao abrir e a tela Verificar atualização usam a release publicada. Anexar o código não atualiza a branch remota; a main deve permanecer preservada.

## Verificação concluída

`testDebugUnitTest assembleDebug` concluído; **190 testes Android, zero falhas**. Os novos testes nas APIs 28 e 35 verificam seleção das abertas, exclusão de peças/cadastros/histórico, texto sem anexos, composição das fotos e texto em uma única imagem, omissão de fotos, permissões de todas as URIs, proteção contra anexos de outra equipe, envio em lote, consulta novamente ao confirmar e cancelamento após troca da sessão. O APK usa a mesma assinatura das versões instaladas. A instalação e a recepção das mensagens/imagens no WhatsApp ainda devem ser conferidas no celular.
