# DM AGRO 1.4.0 — Atualização pelo celular

## O que está pronto

- Tela verde de boas-vindas (opção A), com Entrar, Solicitar acesso e versículo diário. Contas já conectadas mantêm sua sessão; o versículo também aparece na tela principal. A seleção de 31 versículos fica no aplicativo, muda pela data local do celular e se repete em ciclo, sem internet ou serviço pago.
- Lista de usuários com busca por nome, matrícula e equipe, filtros de situação/frente e páginas de até dez pessoas. As ações ficam no botão Gerenciar de cada cadastro. O painel mantém a consulta autorizada existente e pagina a exibição localmente, sem novos índices Firebase. Mais opções reúne cadastros excluídos e notificações.
- Em Estoque, Cadastrar vários lotes permite preencher até vinte cartões por cadastro, com um lote por cartão. Salvar todos preserva os estoques antigos e não substitui o lote que estava aberto. Compartilhar vários estoques permite escolher lotes existentes, gerar uma mensagem única com as fotos locais disponíveis ou salvar um PDF conjunto. Após salvar o PDF, abrir o arquivo no celular para compartilhar.
- Consulta discreta de atualização ao abrir o app, antes ou depois do login. Uma consulta pode ser reaproveitada por até cinco minutos para evitar repetições durante a navegação. O aviso aparece uma vez por versão na sessão do aplicativo; Agora não permite continuar trabalhando. Sem internet, a consulta não interrompe a tela. O instalador existente continua verificando origem, tamanho, hash, pacote e assinatura, e depende da confirmação do Android.
- Notificações de registros novos e alterados com fila durável e deduplicação por revisão, teste de envio ao próprio celular e diagnóstico de último aviso exibido. Avisos de mapas pessoais continuam desativados. A implantação do Worker 1.4.0 é necessária para o teste e para reconhecer corretamente as revisões.

## Primeiro: atualizar o serviço de notificações

Seguir CONFIGURAR-AVISOS-1.4.0.md e usar o arquivo CLOUDFLARE-DM-AGRO-1.4.0.txt no Worker que já existe. Manter o segredo já cadastrado; não gerar outra chave. A URL permanece https://dm-agro-servico.dieguiin-msl.workers.dev .

Nenhuma regra Firebase mudou. Manter Firebase Spark e Cloudflare Workers Free. A entrega está sujeita à internet, aos controles do Android e às cotas gratuitas. O teste real de recebimento ainda precisa ser feito nos celulares.

## Publicar a atualização do aplicativo

1. Baixar e extrair PUBLICAR-DM-AGRO-1.4.0.zip em uma pasta nova. Existem cinco arquivos diretamente na raiz do ZIP.
2. No GitHub, abrir https://github.com/Diegomachadosl10/colheita-gps-oficial/releases/new .
3. Criar a tag v1.4.0, escolher a ramificação dm-agro-login e dar o título DM AGRO 1.4.0.
4. Na área “Attach binaries…” abaixo da descrição, anexar somente os cinco arquivos extraídos: DM-AGRO-1.4.0.apk, dm-agro-update.json, SHA256.txt, DM-AGRO-1.4.0-codigo.zip e DM-AGRO-1.4.0-dm-agro-login.bundle. Não usar o botão de anexar arquivos dentro da descrição. Não anexar a chave privada JSON do Firebase que possa estar na pasta Downloads.
5. Publicar como versão estável mais recente (Latest), sem Pre-release.
6. No DM AGRO já instalado, usar Verificar atualização, baixar e instalar. Não desinstalar nem limpar os dados. A consulta automática passa a existir depois desta instalação e servirá às próximas versões.
7. Atualizar também os outros celulares envolvidos no teste dos avisos.

Os arquivos individuais também estão disponíveis na pasta reenvio-1.4.0, caso o download/extrator do celular tenha problemas com o ZIP.

## Testes rápidos depois de instalar

1. Como administrador, abrir Gerenciar usuários, buscar um nome ou matrícula, filtrar a frente e abrir Gerenciar. As permissões habituais permanecem: somente o principal aprova supervisores ou exclui contas.
2. Como supervisor, conferir somente os operadores elegíveis da própria frente e turno. Não devem aparecer ações administrativas exclusivas do principal.
3. Em Estoque, cadastrar dois lotes fictícios juntos, conferir os estoques antigos e compartilhar apenas os lotes selecionados. Conferir mensagem, fotos locais e PDF.
4. Em Notificações, ativar avisos e usar Testar notificação neste celular; após receber, Atualizar diagnóstico deve mostrar o horário. Depois criar e alterar uma pendência usando outra conta da mesma equipe, deixando o celular do destinatário em segundo plano. O próprio autor não recebe um aviso normal do que acabou de fazer.
5. Conferir que Santo Inácio é a frente de avisos do administrador. Supervisores continuam limitados à própria frente e turno. Não compartilhar mapas pessoais automaticamente.
6. Conferir o versículo na tela principal. A tela de boas-vindas aparece quando não existe sessão; usar outro aparelho de teste ou sair da conta para conferi-la. O botão Voltar não encerra uma conta conectada.

## Validação realizada

Compilação Android concluída e 108 testes aprovados: 70 Android, 18 de regras Firebase em emuladores demo-dm-agro, 17 do serviço com Google/FCM simulados e 3 de preparação da atualização. A prévia BOAS-VINDAS-1.4.0.png foi renderizada por componentes Android em teste, sem celular físico. Os testes não enviaram notificações nem alteraram usuários reais.

A publicação no GitHub depende da permissão de escrita da conexão. Se houver recusa HTTP 403, a ramificação remota permanece como estava: usar os anexos de código e bundle desta versão. O código-fonte automático “Source code” do GitHub pode apontar para o código remoto antigo; os anexos DM-AGRO-1.4.0-codigo.zip e DM-AGRO-1.4.0-dm-agro-login.bundle contêm as alterações atuais. A ramificação main permanece preservada.
