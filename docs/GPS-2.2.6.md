# DM AGRO 2.2.6 — localização no mapa

O mapa passa a solicitar localização de alta precisão a cada segundo, inclusive quando o aparelho está parado. O GPS por satélite funciona sem internet; o provedor combinado do próprio Android e uma estimativa de rede de até 20 m podem ajudar na primeira posição e na retomada, quando disponíveis. Uma estimativa de rede não substitui uma posição recente de GPS/provedor combinado.

Posições antigas (mais de 10 segundos), inválidas, sem precisão ou com erro superior a 50 m são descartadas. Uma leitura muito menos precisa não substitui uma posição recente de boa qualidade. Um salto incompatível com o tempo decorrido e com a margem de erro precisa de outra leitura coerente antes de alterar o marcador. O movimento normal mantém as coordenadas recebidas, sem atraso causado por média móvel ou alteração na georreferência.

O mapa retoma a assinatura ao voltar ao primeiro plano. Mudanças nos provedores são acompanhadas e uma consulta pontual ajuda a recuperação a cada 15 segundos sem posição recente, mantendo a assinatura contínua de GPS. Não há reinício periódico do receptor, que poderia atrasar a aquisição dos satélites.

A última posição continua visível em cinza quando fica antiga ou o GPS está desativado; a faixa de estado explica que o aplicativo está buscando sinal. Ao receber uma nova posição válida, o marcador volta a azul. A bolinha tem centro azul maior e borda branca, adaptados à densidade da tela e com tamanho constante ao aproximar, afastar e girar. A tela permanece acesa enquanto o mapa está aberto.

As solicitações e consultas são canceladas ao pausar o aplicativo ou sair do mapa. A permissão continua sendo apenas de localização em uso, sem localização em segundo plano, áudio ou serviço pago novo. Android recente recebe a solicitação conjunta das permissões precisa e aproximada para permitir a escolha de localização precisa.

A precisão física depende do receptor, da visibilidade dos satélites e do aparelho; não foi realizado teste de campo com um celular físico nesta sessão. A transformação GPS/GeoPDF, as pinturas, os mapas salvos, a rotação e os dados de trabalho não são alterados. Os comandos de voz continuam removidos.

Instale a atualização sobre o aplicativo atual, sem desinstalar nem limpar os dados.

Validação: 44 testes Android aprovados, incluindo filtragem e recuperação do GPS nas APIs 28 e 35, posições em cache, aparelho parado, cancelamento ao pausar, perda/retorno do provedor, zoom/rotação, pinturas preservadas e compartilhamento de pendências. Compilação do APK concluída. Os 7 testes do publicador também passaram.

[Versão estável 2.2.6 publicada para todos](https://github.com/Diegomachadosl10/colheita-gps-oficial/releases/tag/v2.2.6), código 37. Manifesto e APK público de 13110606 bytes baixados e conferidos; SHA-256 `b2586f4021cd22e8963ed030b23970b09581d58ab847de4b8caa914a24cd5cbd`. Assinatura permanente e ausência de permissão de áudio verificadas. [Publicador aprovado](https://github.com/Diegomachadosl10/colheita-gps-oficial/actions/runs/45529802841). Main preservada.
