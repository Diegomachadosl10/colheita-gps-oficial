# DM AGRO 2.2.3 — retirada dos comandos de voz

Os comandos de voz foram retirados a pedido do proprietário. A tela inicial volta a mostrar a equipe e as ferramentas sem o botão de ativação nem o texto do microfone. O cabeçalho, os menus, o mapa e as configurações deixam de oferecer o assistente.

Foram removidos a escuta automática, a resposta falada, a conversa de confirmação e a pintura por comando de voz, incluindo a análise de curvas em segundo plano. A permissão Android de gravação de áudio e as consultas a serviços de reconhecimento/TTS também foram removidas. Preferências antigas de voz ativada não inicializam nenhum serviço. O atalho antigo de ditado no formulário de caminhões foi retirado; o preenchimento manual e os botões de horário atual continuam disponíveis.

As bibliotecas extras usadas pelo reconhecimento de curvas e suas dependências deixam de fazer parte do APK. O leitor PDF nativo, o GPS, a rotação e a pintura manual permanecem disponíveis.

A remoção não executa migração ou limpeza dos registros, mapas ou pinturas salvas. Pinturas anteriores usam o mesmo formato de áreas, linhas e cores e continuam sendo abertas pelo editor manual. O tratamento dos resultados de fotos e do compartilhamento foi preservado; somente o retorno de reconhecimento de fala foi retirado.

A implementação ativa da tela principal foi comparada com a versão anterior ao assistente: além da retirada do recurso, apenas o ditado antigo de caminhões e seu retorno foram removidos. Nenhuma mudança em Firebase, Cloudflare, plano gratuito, aprovação de usuários ou permissões por equipe.

Instale sobre a versão atual, sem desinstalar nem limpar dados.

Validação local: 250 testes Android aprovados, incluindo início/configurações sem controles de voz com a preferência antiga ativada e leitura de pinturas existentes. O APK foi inspecionado: sem RECORD_AUDIO e sem a classe do assistente no DEX.
