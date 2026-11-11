# DM AGRO 1.5.0 — Restauração, caminhões e compartilhamento

## O que muda

Equipamentos excluídos voltam pelo caminho Pendências → Equipamentos excluídos → tocar no equipamento → Restaurar equipamento. Supervisor e administrador podem restaurar só o equipamento ou também as pendências excluídas junto dele. Pendências já concluídas continuam concluídas; fotos locais e identificadores são preservados, sem recriar cópias. Em exclusões antigas, que não identificavam quais pendências foram excluídas em conjunto, a tela explica a diferença e pede a escolha explícita de incluir todas as pendências excluídas daquele equipamento.

O menu ⋮ e o início dos gestores têm Registros excluídos, com consulta e restauração de pendências, peças, ocorrências, chuva, estoques, relatórios, caminhões e movimentações. Exclusões passam a preservar o registro no histórico. Registros excluídos por versões antigas só podem ser recuperados quando seu conteúdo ainda existe no aparelho ou no documento de sincronização; não se promete recuperar dados apagados definitivamente antes desta versão. O histórico de exclusão é metadado do aplicativo, não um livro de auditoria imutável.

Ordens de serviço excluídas nesta versão guardam número, mapa e talhões para restauração. Uma restauração não sobrescreve outra O.S. com o mesmo número e talhões diferentes: é preciso escolher outro número. A edição normal também impede renomear para um número já ocupado. O arquivo do mapa não é recriado pela restauração da O.S.

Operadores e tratoristas mantêm a edição dos próprios registros ativos. Só gestores excluem ou restauram registros operacionais. Supervisores continuam limitados à própria frente e turno; o administrador principal mantém o acesso geral. A exclusão dos mapas pessoais pelo próprio dono continua como exceção. Administração de contas e aprovação de supervisores continuam exclusivas do administrador principal.

Equipamentos com pendências abertas mostram um círculo vermelho e a quantidade de pendências. Equipamentos sem pendências abertas mostram um visto verde e texto. O estado também é descrito para leitores de tela; não depende apenas da cor.

## Caminhões por dia e turno

O controle de caminhões mantém o escopo da equipe atual. A tela mostra a frente e o turno, permite escolher uma data ou Todos os dias e registra número, data e horário de entrada e de saída. Cada entrada é uma viagem independente, inclusive para um mesmo número de caminhão. Pode-se registrar só a entrada e preencher a saída depois, editando o mesmo registro.

Sem data de saída explícita, um horário de saída menor que o de entrada é registrado no dia seguinte. A data pode ser escolhida para viagens maiores. Horários impossíveis e saídas anteriores à entrada são rejeitados sem fechar o formulário. Os botões de horário atual e a digitação por voz auxiliam o preenchimento; os dados devem ser conferidos antes de salvar.

Até que horas define o corte do dia escolhido. Entradas posteriores ficam fora e saídas posteriores são mostradas como ainda não ocorridas naquele horário. Essa consulta usa uma cópia; o horário real salvo não é alterado. Caminhões de dias anteriores que ainda aguardavam saída no corte também aparecem. Entradas e saídas são contadas nas respectivas datas, inclusive na virada do dia; o total aguardando saída considera a equipe até o corte. Registros antigos sem data ou horário ficam disponíveis para correção e não entram no recorte temporal até serem corrigidos. O calendário inclui caminhões na data da entrada e na data da saída.

## Compartilhar PDF ou imagem

No menu ⋮ de cada ferramenta de campo aparece Compartilhar PDF / imagem. Há cabeçalho DM AGRO, equipe, período quando aplicável, tabelas, data de geração e numeração de páginas. Tabelas e textos longos continuam em outras páginas. Imagens são PNG em páginas A4; havendo várias páginas, o Android compartilha vários arquivos. Os aplicativos de destino podem tratar a ordem e os anexos de formas diferentes.

Disponível para pendências e equipamentos, histórico por equipamento, peças, chuva, ocorrências, estoques, relatórios de turno, ordens de serviço, calendário e registros excluídos. O controle de caminhões tem botão direto para compartilhar o período exibido, inclusive o horário de corte. A lista de usuários também pode ser exportada pelos gestores com seus filtros e o escopo de acesso existente; não inclui credenciais, UID ou e-mail de autenticação.

Na biblioteca de mapas, há exportação da lista em PDF/imagem. As opções do mapa preservam o compartilhamento do PDF original e acrescentam Compartilhar imagem do mapa: usa a prévia pintada existente ou renderiza as páginas do PDF quando não há prévia. A imagem exportada é uma representação visual; o PDF original continua sendo usado para georreferenciamento e GPS. O atalho para a pasta pública do Google Drive permanece inalterado.

O envio de vários estoques mantém mensagem e fotos opcionais por lote e acrescenta imagem organizada, além de PDF. As fotos selecionadas acompanham o relatório; lotes sem foto continuam incluídos. Relatórios de outras ferramentas podem incluir fotos disponíveis neste aparelho. Não há upload automático de fotos ou PDFs para Storage. A previsão também pode ser compartilhada com fonte MET Norway e licença CC BY 4.0.

## Firestore: ajuste necessário

É necessário publicar o arquivo completo firebase/firestore.rules no Cloud Firestore → Regras. O arquivo REGRAS-FIRESTORE-1.5.0.txt da entrega contém exatamente essas regras, sem instruções misturadas no código. Não publicar em Storage.

Justificativa: as regras anteriores já protegiam a exclusão de pendências, mas permitiam que o autor excluísse outros registros, como ocorrências e chuva. A nova condição exige gestor da equipe para criar um registro operacional já excluído e para excluir, alterar ou restaurar um registro operacional arquivado. A edição de registros ativos pelo autor continua permitida. O grupo historico_os é adicionado como grupo exclusivo dos gestores para preservar/restaurar ordens de serviço. A exceção de mapas pessoais e a edição normal das pinturas legadas são preservadas.

O Firebase publicado não foi alterado por esta sessão. Não é necessário trocar de plano, habilitar Storage, cadastrar cartão, mudar o Worker ou alterar as configurações de notificações. As regras são verificadas somente em emuladores locais de demonstração.

## Publicar e instalar pelo celular

1. Copiar todo o REGRAS-FIRESTORE-1.5.0.txt para Cloud Firestore → Regras e tocar Publicar. Isso preserva os cadastros e os dados existentes.
2. Baixar e extrair PUBLICAR-DM-AGRO-1.5.0.zip em uma pasta nova. Há cinco arquivos no nível inicial.
3. Abrir https://github.com/Diegomachadosl10/colheita-gps-oficial/releases/new e criar a tag v1.5.0, com destino dm-agro-login e título DM AGRO 1.5.0.
4. Em Attach binaries… abaixo da descrição, anexar os cinco arquivos: DM-AGRO-1.5.0.apk, dm-agro-update.json, SHA256.txt, DM-AGRO-1.5.0-codigo.zip e DM-AGRO-1.5.0-dm-agro-login.bundle. Não usar Add files dentro do campo de descrição. Não anexar arquivos de credenciais do Firebase.
5. Publicar como versão estável mais recente (Latest), sem marcar Pre-release.
6. No aplicativo, aceitar o aviso ao abrir ou usar Verificar atualização. Instalar sobre a versão existente, sem desinstalar nem limpar dados. A instalação exige confirmação do Android.

A versão é 1.5.0, código 16, Android mínimo 8.0/API 26. A assinatura é a mesma dos APKs anteriores e é conferida antes de preparar a atualização. Os arquivos também são fornecidos separadamente caso o ZIP apresente problema no celular.

A conexão GitHub pode continuar recusando gravação com HTTP 403. Se isso acontecer, o código desta entrega está no ZIP de código e no bundle da ramificação dm-agro-login; publicar os anexos não atualiza o código da ramificação remota. O Source code automático da release pode representar o commit remoto antigo. A main é preservada.

Para gerar pelo GitHub Actions quando a ramificação remota contiver estas alterações: Actions → Gerar APK Colheita GPS → Run workflow → escolher dm-agro-login. Para também disponibilizar a atualização interna, marcar Publicar atualização para os aplicativos instalados. O fluxo exige a assinatura permanente já usada pelo aplicativo, configurada no secret ANDROID_UPDATE_KEYSTORE_BASE64; o script bloqueia assinatura diferente. Sem a gravação no repositório, o Actions usa o código remoto anterior; o APK pronto desta entrega já foi compilado com o código atual.

## Verificação

Compilação testDebugUnitTest + assembleDebug aprovada: 103 testes Android, incluindo recuperação e permissões, edição de caminhões, virada do dia, corte temporal, indicadores, conteúdo de relatórios e paginação. Emuladores Firebase: 22 testes. Serviço Cloudflare: 17 testes. Scripts de atualização: 3 testes.

As imagens usam o renderizador Android em Robolectric. Para PDF, o ambiente verifica as chamadas de composição e a paginação com Canvas real, pois Robolectric não implementa o gravador PDF nativo; isso não equivale a testar o arquivo PDF final em um celular. A abertura dos arquivos no leitor de PDF e o compartilhamento por WhatsApp precisam ser confirmados no Android. O aplicativo não foi instalado em aparelho físico nesta sessão.
