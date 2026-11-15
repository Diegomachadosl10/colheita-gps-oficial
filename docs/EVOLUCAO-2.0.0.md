# DM AGRO 2.0.0 — Organização do trabalho no campo

Versão 2.0.0, código 28, na branch dm-agro-login. Esta entrega implementa o conjunto de dez melhorias aprovado em 15/11/2026. A main e o acesso por matrícula, a divisão por frente/turno, o fluxo de aprovação e as permissões de exclusão/restauração permanecem preservados.

## O que mudou

1. **Serviços para parada de chuva.** No cadastro/edição da pendência, marque Pode ser feito na parada de chuva. A lista de pendências pode ser filtrada por essa marcação. O filtro vale também para os detalhes do equipamento e para o compartilhamento dos serviços da parada. É possível limpar o filtro para consultar todas as pendências novamente.
2. **Peças ligadas a pendências.** No formulário da peça, Vincular a uma pendência seleciona uma aberta da equipe e preenche o equipamento de referência. Os dados precisam corresponder ao equipamento do vínculo. A pendência mostra a quantidade de peças a pedir, pedidas ou recebidas; Peças vinculadas permite consultar os pedidos. A peça recebida não conclui automaticamente a manutenção.
3. **Rascunhos automáticos.** Pendências, estoque e caminhões guardam o preenchimento local durante a digitação e ao pausar/fechar o formulário. Ao abrir o mesmo formulário novamente, os dados são recuperados para conferência. Campos inválidos não viram registros; o cadastro operacional só é gravado ao salvar. Fotos já anexadas são mantidas no rascunho da pendência/estoque. Rascunhos e a organização do início são separados por conta e equipe neste aparelho. Pedidos de peças também preservam o preenchimento automaticamente.
4. **Prévia antes de enviar.** O envio de texto/imagens das ferramentas, incluindo estoque e pendências, mostra Conferir envio antes do seletor de aplicativos. O texto pode ser lido e selecionado. Nas imagens, Anterior/Próxima permite percorrer todos os anexos. Escolher aplicativo só abre o seletor após confirmação; a pessoa ainda escolhe o WhatsApp e o destinatário. Conta/equipe são verificadas novamente antes da entrega.
5. **Estoque por lote.** Escolher lote já cadastrado preenche um registro para atualização. Ao digitar um lote existente e salvar, o aplicativo oferece atualizar um dos registros correspondentes ou salvar separadamente. O lote atualizado preserva seu ID e seus anexos existentes. O cadastro de vários lotes também oferece atualização de correspondências únicas e rejeita lote repetido no próprio lote de cadastro. As listas mostram data e nome de quem fez a última gravação a partir desta versão.
6. **Espera de caminhões.** Caminhões sem saída mostram o tempo desde a entrada até o momento/horário de corte consultado. O maior tempo recebe indicação textual e borda de destaque. A tela do turno atual atualiza os tempos periodicamente, sem interromper um formulário em preenchimento. Os limites dos turnos e o histórico continuam iguais; nenhuma entrada é apagada ao trocar o turno.
7. **Mapas identificáveis.** A biblioteca usa miniaturas do mapa pintado ou da primeira página do PDF, com ícone de mapa quando a miniatura não está disponível. A data de uma pintura alterada e salva passa a aparecer no aparelho. Abrir e salvar um desenho sem alterações não muda essa data. Ao sair do visualizador, o aplicativo confirma a pintura salva; falha de exportação mantém o visualizador aberto. O zoom, a rotação, a georreferência e as coordenadas não foram alterados.
8. **Campos e unidades.** Área restante e produtividade exibem ha e t/ha e aceitam números com vírgula ou ponto decimal. Texto inválido, negativos, separadores misturados e cartões incompletos impedem a gravação, preservando o rascunho. Lote, talhão, matrícula e códigos não são transformados em números para não perder letras/zeros. A seleção rápida de talhões foi mantida. As validações de milímetros e de horários/datas dos caminhões continuam ativas.
9. **Histórico de correções.** Registros operacionais gravados a partir desta versão guardam data/nome de criação/última gravação e as últimas 100 correções de campos de negócio. O histórico informa campo, valor anterior e valor novo, sem copiar caminhos locais de fotos para os eventos. Pendências, peças, estoque, caminhões, chuva e ocorrências têm acesso ao histórico nas suas opções/detalhes. Exclusões/restaurações e O.S. mantêm seu histórico próprio anterior. Dados antigos não recebem uma autoria de criação inventada.
10. **Início personalizado.** Em ⋮ → Configurações → Organizar início, marque ferramentas favoritas e use as setas para ordenar cada grupo. Favoritas aparecem primeiro. A preferência é local por usuário/equipe e só inclui ferramentas permitidas para o perfil atual; personalizar não concede acesso adicional. Os temas B/C e a escolha claro/noturno continuam disponíveis.

## Preservação e uso sem cobrança nova

- Sem novas regras/índices, Firebase Storage, servidor pago ou alteração de plano/Cloudflare. Vínculos, marcação de chuva e histórico usam o conteúdo dos registros já sincronizados; rascunhos, miniaturas e preferências são locais.
- Fotos continuam no aparelho em que foram anexadas. A sincronização do texto não baixa fotos de outro celular automaticamente.
- Estoque continua sendo mensagem e mapa/foto original no WhatsApp. Pendências continuam oferecendo mensagem ou PNG com descrição e fotos na mesma página, incluindo o envio de todas as abertas e a opção sem fotos. A prévia não modifica os anexos armazenados.
- Relatórios de turno e o botão Encalhado permanecem removidos. Todas as exclusões/restaurações mantêm as permissões anteriores, incluindo a exceção dos mapas pessoais.
- Revisões originais de sincronização são preservadas nos rascunhos de edição para que a proteção de conflitos continue funcionando. Um formulário de uma sessão/equipe anterior não pode gravar em outra equipe.
- A indicação Salvo neste celular não é uma confirmação de recebimento no servidor. O estado de sincronização continua informando espera, erro ou confirmação efetiva, com a quantidade aguardando envio quando disponível.

## Instalar e conferir

Instale o APK sobre a versão atual, sem desinstalar nem limpar dados. Confira no aparelho: fechar/reabrir os três formulários; selecionar um serviço para chuva; vincular uma peça e mudar sua situação; atualizar lote existente; visualizar os anexos antes de enviar; observar um caminhão em espera; salvar uma pintura e organizar o início. As telas são verificadas em testes automatizados; recebimento real no WhatsApp, GPS no campo e instalação precisam de conferência física.

## Publicação

A [release estável 2.0.0](https://github.com/Diegomachadosl10/colheita-gps-oficial/releases/tag/v2.0.0) foi publicada em 15/11/2026 e marcada como mais recente. Os cinco anexos foram conferidos por tamanho e SHA-256 antes da liberação. O manifesto público usado pelo atualizador entrega a versão 2.0.0, código 28; o APK público foi baixado e teve hash e assinatura permanente confirmados. A tag aponta para 1499e97a33ddaf82b8bde341a7a3c7b8ffecc4f0. A main foi preservada.

A publicação usou o APK previamente assinado pelo workflow publish-prebuilt.yml, sem enviar chave privada. Usuários de versões anteriores podem atualizar ao abrir o aplicativo ou em ⋮ → Configurações → Verificar atualização, confirmando a instalação no Android. Quem já instalou 2.0.0 não recebe nova atualização. A publicação na Play Store não faz parte desta entrega.

## Validação concluída

- `testDebugUnitTest assembleDebug` concluído na revisão final, com **214 testes Android, zero falhas**, incluindo cobertura nas APIs 28 e 35.
- Os testes verificam persistência e isolamento dos rascunhos/preferências, não criação de registros ao fechar formulários, validação numérica, atualização de lote sem duplicar ID, vínculo de peça, correções sem caminhos privados no histórico, espera com virada de data, filtro de chuva nos detalhes, data de pintura sem alterações, confirmação de prévia e proteção após troca de equipe.
- As regressões de autenticação, permissões locais, navegação, compartilhamento com FileProvider real, estoque com mapas originais, pendências com fotos na mesma página, rotação/GPS, arquivamento/restauração e limites dos turnos continuam aprovadas.
- APK verificado: pacote com.colheitagps.app, versão 2.0.0, código 28, mínimo API 26, mesma assinatura permanente. Não houve implantação ou mudança de regras/índices/backend.
- Instalação física, envio/recepção no WhatsApp e GPS no campo ainda precisam de conferência no aparelho.

A primeira execução no GitHub aprovou os testes do serviço e do Firebase, mas o setup Android tentou instalar o pacote obsoleto tools. O workflow agora declara explicitamente platform-tools, platforms;android-35 e build-tools;35.0.0, sem essa dependência obsoleta. A assinatura ainda deve ser restaurada exclusivamente de segredo protegido; não se deve publicar a chave nem gerar outra para substituir a existente.
