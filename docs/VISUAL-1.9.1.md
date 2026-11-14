# DM AGRO 1.9.1 — Telas e menu simplificados

A revisão visual abrange os controles compartilhados de todas as telas: início, ferramentas, biblioteca e visualizador de mapas, formulários, acesso/cadastro, administração, notificações, previsão, atualização e preparação de envio de estoque. O layout dos atalhos segue a opção B aprovada; a paleta noturna segue a opção C. A preferência de tema continua salva no aparelho, com claro como padrão.

- Estoque: quatro blocos com ícones para Novo estoque, Vários lotes, Compartilhar e Calendário, com espaço entre eles. Os cartões separam os talhões da área/produtividade.
- Pendências: blocos Nova pendência e Cadastrar equipamento. Cartões menores, com quantidade aberta/urgência em vermelho e quantidade concluída em outra linha, sem repetir os totais. Históricos permanecem acessíveis abaixo da lista.
- Botões secundários usam contorno e superfície clara/escura; ações principais dos demais formulários/ferramentas usam verde. As ações não ficam encostadas. Campos seguem a mesma borda, raio e espaçamento.
- Telas estreitas e fontes maiores empilham os blocos para manter a leitura. Formulários longos têm rolagem, incluindo o de pendências.
- Biblioteca de mapas com cabeçalho de voltar compacto, busca, favoritos e atalho da pasta pública do Drive. Pinturas, PDF de entrada, coordenadas e GPS mantêm seu funcionamento.
- Cadastros, administração, previsão, atualização e notificações usam cartões de margens menores e os mesmos botões.

## Menu dos três pontinhos

O menu do início contém somente **Tema do aplicativo**, **Configurações** e **Sair da conta**. Nas ferramentas com compartilhamento por imagem, acrescenta apenas **Compartilhar imagem**. No estoque, o envio de mensagem com as fotos/mapas é feito pelo bloco Compartilhar; não foi substituído por imagem de tabela.

As ferramentas são abertas pelos cartões do início; o botão voltar retorna à tela anterior. A troca de equipe do administrador continua pelo botão Trocar no início. Configurações reúne atualização, notificações, registros excluídos e sincronização, conforme as permissões. Exportar/restaurar backup ficam dentro de Cópia de segurança. Isso remove a longa lista de navegação repetida sem apagar registros ou perder a restauração.

## Instalação e verificação

Instale o APK 1.9.1 (código 26) sobre o aplicativo atual, sem desinstalar nem limpar dados. A assinatura permanece a mesma. Use ⋮ → Tema do aplicativo para alternar claro/noturno. Confira os blocos de estoque, o cadastro de pendências e um envio pelo WhatsApp no aparelho.

Não há alteração de permissões, regras Firebase, índices, Cloudflare, plano ou formato dos registros. A exclusão/restauração e o histórico mantêm as restrições existentes. Supervisores e operadores mantêm o fluxo atual de cadastro e aprovação. Relatórios de turno continua removido. O estoque mantém mensagem e foto original do mapa, sem o rodapé de fotos/mapas.

As capturas em `visual-1.9.1/` são geradas a partir das telas Android implementadas, usando dados de teste locais. Os testes incluem os dois temas, ações clicáveis, menu reduzido, botões separados, fonte maior, largura reduzida, preservação de registros e as regressões anteriores. A conferência física da instalação e do recebimento no WhatsApp permanece a ser feita no celular.

## Disponibilizar para os demais usuários

Extraia PUBLICAR-DM-AGRO-1.9.1.zip e publique uma release estável mais recente no GitHub, tag v1.9.1, destino dm-agro-login, com os cinco arquivos na área Attach binaries abaixo da descrição. Não marque Pre-release. O aplicativo poderá detectar a atualização ao abrir ou em ⋮ → Configurações → Verificar atualização. Publicar os anexos não atualiza automaticamente o código da branch. A main deve permanecer preservada.

Validação concluída nesta entrega: **176 testes Android, zero falhas**, com testes nativos de layout nas APIs 28 e 35; `testDebugUnitTest assembleDebug` concluído. Não houve mudança nas regras ou no backend nesta versão. O APK foi validado com a assinatura permanente, pacote com.colheitagps.app, versão 1.9.1, código 26 e mínimo API 26.
