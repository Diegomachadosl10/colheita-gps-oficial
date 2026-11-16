# DM AGRO 2.2.0 — comandos de voz e curvas do mapa

O microfone do cabeçalho abre a conversa em qualquer ferramenta. No início, **Ativar comandos de voz** permite ouvir a chamada “DM AGRO” enquanto essa tela estiver aberta e em primeiro plano. A autorização é individual por conta/equipe, pode ser desligada e não cria gravação de áudio no aplicativo. O serviço de reconhecimento do Android pode depender de internet e do idioma instalado. Também é possível escrever o comando.

Ouvir apenas prepara uma ação. O aplicativo mostra os dados entendidos, pergunta o que falta e só grava depois de **Confirmar**. “Não, é 22283”, “coloca como urgente” e “cancelar” permitem corrigir ou desistir. Ruído e números precisam ser conferidos na prévia; não há garantia de reconhecimento perfeito em campo.

## Exemplos

- “DM AGRO, colhedora 22282 deu problema no rolamento.” Cria pendência normal; “urgente” altera a prioridade. Não inventa diagnóstico ou serviço.
- “Colhedora 22282 parou para abastecer” e “giro de facas” entram como rotina nas Ocorrências, sem criar outra ferramenta de paradas. Troca de facas continua sendo pendência.
- “Caminhão 10880 chegou agora” ou “10880 está saindo agora.” A hora fica fixada quando o comando é capturado. Entradas duplicadas são bloqueadas e saídas com mais de uma entrada exigem escolha. Datas e turnos mantêm as validações existentes.
- “Estoque do K/39, área 40 hectares, produtividade 80 e coloque o mapa pintado.” Atualiza o estoque existente; pergunta talhões ou qual registro/mapa quando necessário. O mapa anexado vem das pinturas salvas na biblioteca. O envio continua por mensagem e imagem.
- “Choveu vinte milímetros.” Se já houver leitura, pergunta se é acumulado ou adicional.
- “Pedir duas peças rolamento, item P001, para equipamento 22282.” Mantém código e quantidade.
- “Registrar ocorrência de erosão perto do carreador.”
- “Mande todas as pendências urgentes sem foto.” Prepara o compartilhamento existente, com prévia e botão **Enviar**. Também aceita equipamento específico e serviços para parada de chuva.
- “Abra mapas”, “abra peças”, “abra previsão do tempo” e os nomes das demais ferramentas abrem as telas existentes, respeitando o papel do usuário.
- “Encerrar ordem de serviço 123.” Pede o mapa e abre o encerramento existente, preservando o histórico.
- Horímetro e próxima revisão usam o cadastro de preventiva existente; somente gestores podem alterar.

“Voltou a trabalhar” pede qual pendência foi resolvida; não conclui todas as pendências de um equipamento.

## Pintura de curvas

“DM AGRO, pinte a curva de 3,15 do talhão 9 inteira” trata 3,15 como **hectares impressos**, não como código. “Pela metade” divide a área da curva e pede o lado de início. A orientação esquerda/direita/cima/baixo é a do PDF original, explicitada na conversa.

A análise é local, com PDFBox Android e JTS. Lê a primeira página de PDFs vetoriais com talhões identificáveis, limites vermelhos das curvas, números de área e georreferência válida. Confere a área geométrica contra a área impressa antes de oferecer um contorno. PDFs digitalizados, sem informações suficientes ou com orientação não suportada continuam na pintura manual; o aplicativo não inventa limites.

Curvas de mesma área no mesmo talhão mantêm identificadores distintos. A prévia numera as alternativas. É possível escolher primeira/segunda, indicar um lado ou **tocar no carreador de referência** para escolher a curva mais próxima. O carreador ainda não é identificado semanticamente de forma automática: se a fala não determinar um trecho único, o usuário precisa apontar ou escolher. Empates não são decididos ao acaso.

Uma prévia com o contorno azul aparece antes de pintar. A pintura usa as coordenadas originais, a mesma georreferência e o mesmo mecanismo de gravação/exportação da pintura manual; preserva o GPS, o zoom e a rotação. Uma edição ou medição em andamento precisa ser concluída ou cancelada antes.

O mapa K-39 Parte 01 original foi usado localmente para validar a curva **3,15 ha do talhão 8**. O exemplo verbal “talhão 9” não altera a identificação real do documento. Os PDFs do usuário não são incluídos no repositório nem no pacote da atualização.

## Permissões, custo e validação

As ações reutilizam as permissões e os registros das ferramentas atuais. Operadores não ganham acesso às ferramentas exclusivas de gestores. Ao confirmar uma alteração, a conta, a equipe e o registro original são conferidos novamente para impedir gravação em outra equipe ou sobre uma edição mais recente.

Sem novo serviço pago, mudança de plano Firebase, Storage ou faturamento. Não exige mudar o Worker de notificações. As licenças das bibliotecas de mapa acompanham o APK em `assets/map-components`.

Testes cobrem interpretação, correções, confirmação, permissões, conflito de registros, compartilhamento, chuva acumulada, caminhões, curvas repetidas, leitura do PDF original, meia área e preservação de GPS/viewport. O serviço de microfone do aparelho e o comportamento com ruído ainda exigem teste em campo.

Atualize sobre a instalação atual, sem desinstalar nem limpar dados.

Validação local: 290 testes completos aprovados e 49 verificações específicas aprovadas após o ajuste final das distâncias nas proporções originais do PDF; 7 testes do publicador aprovados.
