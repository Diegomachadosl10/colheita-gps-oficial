# DM AGRO 2.2.1 — pintura por voz com os exemplos do K/39

A versão anterior pedia hectares mesmo ao solicitar um talhão inteiro e perdia partes de talhões separados por carreadores. Agora diferencia três pedidos:

- “DM AGRO, quero que pinte o talhão 11 do K39 inteiro.” Usa todas as partes do contorno cadastrado no PDF, preservando o carreador entre elas. Não pede hectares de uma curva.
- “DM AGRO, quero que pinte a curva de 2,99 do talhão 11 do K39.” Localiza a região que contém esse rótulo dentro do talhão e prepara a pintura inteira.
- “DM AGRO, quero que pinte metade da curva de 2,99 do talhão 11 do K39.” Pergunta de qual lado começar e divide a área geométrica ao meio.

As transcrições “Deneago” e “Dê-me água”, observadas nos exemplos enviados, também ativam a chamada na tela inicial quando a voz estiver ligada. Isso não torna o reconhecimento universal: o serviço de fala do Android ainda pode interpretar outras frases incorretamente; o aplicativo continua mostrando o que entendeu e pedindo confirmação.

Quando o lote tem mais de uma parte salva, procura o contorno solicitado nos mapas desse lote. Uma correspondência única evita a pergunta sobre qual arquivo usar; múltiplas correspondências continuam exigindo escolha. Um mapa com nome genérico pode ser escolhido na biblioteca existente, sem exigir nova importação.

## Contornos e área

O leitor mantém o agrupamento original dos desenhos do PDF: duas partes de um mesmo talhão são identificadas pelo mesmo número e conferidas pela área total, sem preencher a estrada entre elas. A gravação do conjunto é única; um contorno inválido impede toda a operação.

A união das linhas usa encaixe geométrico com precisão de 0,001 ponto de PDF para não deixar falhas artificiais nas interseções. Interrupções de até 3 pontos em uma linha vermelha só são ligadas quando as duas extremidades apontam uma para a outra, são escolhas próximas e únicas entre si e a ligação não cruza outra linha. O contorno pintado não altera a borda do talhão ou do carreador.

Os hectares escritos no PDF identificam a curva solicitada; o aplicativo não estica um contorno para forçar coincidência com o número impresso. Na amostra K-39 Parte 01, a região delimitada que contém o rótulo 2,99 tem aproximadamente 2,49 ha pela georreferência original. Essa diferença é informada na prévia antes da confirmação. Diferenças superiores a 35%, contornos com vários rótulos de curva ou identificação insuficiente continuam exigindo conferência manual. A metade sempre usa a área geométrica, preservando a transformação do GPS.

O desenho é mostrado antes de pintar; talhão e curva mantêm as coordenadas originais e o viewport. O microfone continua restrito ao primeiro plano. A conversa esconde os campos de digitação quando a prévia está pronta: **Confirmar**, **Cancelar**, **Corrigir** e **Ouvir** ficam acessíveis. Para escrever, use **Escrever** ou **Corrigir**. A desativação da voz permanece nas Configurações.

Os PDFs originais são usados apenas em testes locais e não acompanham o código ou o APK. Não há mudança nas regras Firebase, no serviço Cloudflare, na assinatura permanente ou nos planos gratuitos.

Validação: 295 testes Android aprovados, incluindo os três comandos reais, talhão com duas partes, preservação do carreador, gravação única, meia área e leitura do cache; 7 testes do publicador aprovados. O reconhecimento de fala e a prévia nativa ainda precisam ser conferidos no aparelho em campo.

[Versão estável 2.2.1 publicada para todos](https://github.com/Diegomachadosl10/colheita-gps-oficial/releases/tag/v2.2.1), código 32. Manifesto e APK de 22.319.398 bytes baixados publicamente e conferidos; SHA-256 `8555ce157d65202203d93dcb886aebe7f935d0d5effa4b12778df96647ceac92`. Assinatura permanente preservada e [publicador aprovado](https://github.com/Diegomachadosl10/colheita-gps-oficial/actions/runs/43829190117). Main preservada.
