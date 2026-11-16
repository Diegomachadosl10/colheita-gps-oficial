# DM AGRO 2.2.2 — metade como faixa de colheita

A metade passa a acompanhar a curva inteira, começando pela borda superior ou pela inferior. Foram retiradas esquerda/direita da escolha de pintura parcial. O usuário confirmou que deseja uma faixa ao longo de todo o arco, em vez de um corte reto atravessando a geometria.

## Comandos

- “DM AGRO, pinte metade da curva de 2,52 do talhão 15 do K39 de cima para baixo.”
- “DM AGRO, pinte metade da curva de 2,52 do talhão 15 do K39 de baixo para cima.”
- Se faltar o sentido, aparecem somente **De cima para baixo** e **De baixo para cima**.

A expressão completa é interpretada pelo ponto de partida: “de cima para baixo” não é confundido com começar por baixo. A direção de colheita fica separada da localização usada para escolher entre curvas de mesma área. Um carreador apontado ou uma curva à esquerda não definem o sentido da pintura parcial. Cima e baixo seguem a orientação original do PDF; girar a visualização continua preservando GPS e coordenadas.

## Geometria

A faixa é calculada como a metade de cada seção vertical interna do contorno. Entre vértices consecutivos, as duas bordas são interpoladas e o limite intermediário acompanha a curvatura. A união dessas pequenas faixas cobre metade da área geométrica ao longo de toda a extensão, preservando o limite externo. As faixas superior e inferior são complementares e não se sobrepõem em área.

A curva marcada 2,52 do talhão 15 no K-39 Parte 01, mostrada pelo usuário, foi usada na validação local. O resultado é uma faixa ao longo do arco completo. Também são preservadas a verificação dos contornos, as diferenças entre hectares impressos e medidos e a confirmação antes de gravar. Não são alteradas pinturas já salvas.

## Confirmação e tempo de resposta

- A prévia reutiliza o bitmap do mapa que já está aberto, sem abrir/renderizar novamente o PDF para cada confirmação.
- Com a voz ativada, a leitura dos contornos começa em segundo plano ao abrir o mapa. Pedidos simultâneos compartilham a mesma leitura; o resultado fica em memória e no cache local existente.
- Ao usar o lote com vários arquivos, o mapa atualmente aberto é priorizado quando contém o contorno solicitado.
- O botão Confirmar fica habilitado assim que a prévia estiver pronta, independentemente do término da fala. A abertura assíncrona da janela não desativa uma confirmação já pronta.
- A resposta falada de confirmação é curta; o aviso de diferença de área permanece visível e é mencionado quando existe.

A primeira leitura de um mapa ainda depende de seu tamanho e da capacidade do celular. Não há promessa de tempo fixo em aparelhos reais. O microfone, o TTS e a resposta ao ruído continuam sujeitos ao serviço Android e devem ser conferidos em campo.

Nenhum serviço pago novo, mudança de Firebase/Cloudflare ou alteração da assinatura permanente. A atualização preserva os dados instalados e as permissões por conta/equipe.

Validação local: 303 testes Android aprovados e 7 testes do publicador aprovados. Inclui a curva 2,52 do talhão 15 do PDF original, faixas complementares ao longo do arco, gravação georreferenciada, cache de leitura compartilhado, prévia com bitmap e confirmação antes do término da fala.
