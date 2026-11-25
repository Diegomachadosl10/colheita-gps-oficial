# DM AGRO 2.2.7 — entrada sem verificação repetida

A tela “Verificando acesso” foi retirada da entrada. A abertura inicial ou a renovação de uma sessão expirada mostra somente a marca e um pequeno indicador de carregamento, sem perguntar novamente ao usuário se deve verificar o acesso. O administrador abre diretamente o aplicativo, sem o painel intermediário que exigia tocar em Abrir aplicativo; Gerenciar usuários continua disponível no início.

A mesma consulta de entrada é compartilhada enquanto estiver em andamento. Depois de confirmar no servidor o perfil aprovado e a disponibilidade das equipes, a entrada e o início reaproveitam esse resultado durante até cinco minutos. Essa memória pertence somente ao processo e ao UID autenticado: não persiste permissões em arquivo nem guarda senhas ou tokens. A expiração, a saída e a troca de conta exigem nova consulta automática. Respostas atrasadas de uma sessão anterior são descartadas.

O acompanhamento do perfil no servidor permanece ativo e inclui mudanças de metadados. Bloqueio, exclusão ou alteração de perfil, frente ou turno invalida o reaproveitamento e redireciona a entrada, preservando pinturas e rascunhos. Contas pendentes ou inválidas nunca entram por meio do reaproveitamento. As consultas de telas administrativas e de compartilhamento continuam consultando o servidor; não foram removidas regras ou aprovações da equipe. Falhas reais de conexão e acesso continuam tendo orientação e opção de tentar novamente.

A navegação entre a abertura discreta e uma tela de cadastro, bloqueio ou erro reconecta os controles ao conteúdo visível. Não há criação de usuário real para teste nem mudança no Firebase, Cloudflare ou faturamento. GPS e marcador maior da versão 2.2.6 permanecem. Os comandos de voz continuam removidos.

Atualize sobre a versão instalada, sem desinstalar nem limpar dados. As ideias de responsável/prazo, fotos antes/depois, aviso de estoque antigo e checklist foram apenas propostas; não fazem parte desta correção.

Validação: 43 testes Android aprovados, incluindo reaproveitamento e descarte de sessão nas APIs 28 e 35, isolamento de contas, expiração e recusa do servidor, interface de abertura/erro, políticas de acesso, GPS e consulta discreta de atualizações. Compilação do APK concluída; 7 testes do publicador aprovados. Não foi realizado teste com uma conta de produção nem em celular físico nesta sessão.
