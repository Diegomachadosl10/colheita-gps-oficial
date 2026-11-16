# DM AGRO 2.1.0 — Passagem de turno e cuidados com os equipamentos

Versão 2.1.0, código 29, na branch `dm-agro-login`. Mantida a assinatura dos aparelhos instalados. A main, os dados existentes e os serviços gratuitos permanecem preservados.

## Cinco melhorias

1. **Passagem de turno.** No início, gestores encontram Passagem de turno. A data escolhida é a data de início do turno da equipe, inclusive no turno C, das 23:20 às 07:00 do dia seguinte. Pendências abertas, peças aguardadas e estoque mostram a situação atual disponível na equipe; os caminhões respeitam o intervalo escolhido e o horário atual ou fim do período. Um recado opcional fica em rascunho local por conta, equipe e período. Compartilhar mensagem ou imagem abre a prévia antes do seletor de aplicativos. O envio não encerra nem altera registros.
2. **Manutenção preventiva.** Em Pendências, abra as opções de um equipamento → Manutenção preventiva / horímetro. O supervisor ou administrador descreve o próximo serviço e informa data, horímetro ou ambos. Horímetro de revisão exige uma leitura atual. A leitura é manual; não há estimativa automática de horas trabalhadas. O cartão avisa a 7 dias ou 50 horas do prazo e destaca quando a data chega ou o horímetro é atingido. Abra o equipamento para consultar os detalhes ou atualizar a leitura. Programação, correções, exclusão/restauração e correção do número preservam o histórico e os vínculos.
3. **Notificação abre o registro.** O toque carrega equipe, conta destinatária e ID do evento. Após validar o acesso, o aplicativo resolve o hash do evento contra os metadados da sincronização e abre a pendência, peça, chuva, ocorrência, estoque, caminhão ou equipamento correspondente. Não usa o texto do aviso como endereço. Continua compatível com o Worker já instalado, sem nova implantação. Eventos de solicitação abrem a gestão de acessos; o teste não abre um registro fictício. Avisos antigos ou registros ainda indisponíveis têm retorno informativo, sem mudar permissões ou criar registros. A resolução ocorre fora da interface e consulta somente os metadados já disponíveis da própria equipe. A entrega continua dependendo da conexão, Android e serviços Google.
4. **Fotos com marcações.** Salve o registro, abra Ver fotos e escolha Marcar foto. Em estoque, use Ver / marcar foto nas opções do lote. Arraste para desenhar uma seta ou círculo; para texto, escreva e toque na posição desejada. Desfazer ajusta as marcações. Salvar foto cria uma cópia JPEG local e um arquivo de edição; a original permanece intacta, acessível por Ver original. Reabrir Marcar foto recupera os elementos; remover todas as marcações de uma foto já marcada permite voltar à original. A orientação EXIF é aplicada antes das coordenadas. O compartilhamento usa a cópia marcada na posição da foto anterior, preservando as demais fotos e o envio da pendência com descrição em uma única imagem. Fotos continuam locais no plano gratuito.
5. **Ajuda por tela.** O `?` do cabeçalho explica a ferramenta atual, inclusive acesso, notificações, previsão, atualização e envio de estoque. No visualizador, menu `+` → Ajuda do mapa. Os menus de três pontos continuam curtos.

## Dados, permissões e atualização

Não há nova coleção, índice, regra ou implantação Firebase/Cloudflare. A revisão usa o registro de equipamento existente e suas revisões de sincronização. Rascunhos e marcações são locais; dados de texto continuam sincronizando com a equipe. Operadores continuam editando os próprios registros; somente gestores excluem ou restauram registros da equipe, com a exceção já existente dos mapas pessoais. Mapas PDF, pinturas e GPS não são alterados pelo editor de fotos.

Mantidos os temas claro/noturno, login por matrícula, versão 2.0.0, compartilhamento de estoque por mensagem e mapa/foto, pendências com ou sem fotos e históricos. Relatórios de turno e compartilhamento em PDF continuam removidos.

Instale a atualização sobre o aplicativo existente, sem desinstalar nem limpar dados. A distribuição usa a release estável `v2.1.0`, com APK, manifesto, SHA-256, ZIP do código e bundle. O Android confirma a instalação; não existe instalação silenciosa.

## Verificação

`testDebugUnitTest assembleDebug` foi concluído com **246 testes, zero falhas**, incluindo as APIs 28 e 35; os cinco testes dos scripts de publicação também passaram. A assinatura permanente do APK e as prévias claro/noturno foram verificadas.

Os testes cobrem passagem do turno C à meia-noite, cortes de caminhões, exclusão de registros arquivados/concluídos, prévia e confirmação do envio, limites da preventiva, decimais brasileiros, preservação ao corrigir/restaurar equipamento e bloqueio de gravação após troca de equipe. Notificações são verificadas com metadados fictícios do protocolo do Worker existente, incluindo abertura real das opções da pendência e recusa de outra conta. Fotos são verificadas com gráficos nativos: posicionamento com margens, desfazer, orientação de câmera, hash da original, reedição e uma única imagem por pendência. O recebimento real no WhatsApp e a entrega de um aviso em aparelhos físicos continuam sujeitos à conferência de uso.

## Publicação confirmada

A [release estável v2.1.0](https://github.com/Diegomachadosl10/colheita-gps-oficial/releases/tag/v2.1.0) foi publicada em 16/11/2026, marcada como mais recente, com os cinco anexos conferidos por tamanho e SHA-256. A tag aponta para `01045a17107710495beeea47453632bc799525af`. O manifesto público usado pelos aplicativos já entrega o código 29, versão 2.1.0; o APK público foi baixado e teve seu hash e certificado permanente confirmados.

A publicação foi concluída pelo [workflow de pacote previamente assinado](https://github.com/Diegomachadosl10/colheita-gps-oficial/actions/runs/43778025811). Os sete testes dos scripts passaram. A transferência dos anexos usa objetos Git conferidos, sem adicionar binários ao histórico da branch e sem enviar a chave privada de assinatura. Não houve implantação Firebase/Cloudflare nem alteração na main.
