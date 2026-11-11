# DM AGRO 1.6.0 — Girar mapa e avisar equipamento encalhado

## Rotação livre

No mapa, coloque dois dedos e gire. O mesmo gesto permite aproximar, afastar e mover. O botão 0° do cabeçalho repõe a orientação original do PDF mantendo o ponto que estava no centro e o zoom. O menu + também oferece essa opção e as instruções de uso.

Mapa, GPS, pinturas, linhas, vértices e guias usam a mesma transformação de visualização. A seleção, a borracha, o desenho e a inclusão de pontos centrais usam a transformação inversa. Centralizar GPS funciona com o mapa girado e conserva a rotação. Trocar ou retirar dedos durante o gesto não deve criar saltos nem apagar ou pintar só pela chegada do primeiro dedo.

As coordenadas salvas, a georreferência, os cálculos de área/distância e o PDF original não são girados ou reescritos. A rotação muda somente a visualização e não aumenta a precisão do GPS físico nem corrige a georreferência de um PDF. O mapa reabre enquadrado na orientação original; a rotação não é compartilhada como edição da equipe. A imagem de mapa pintado continua sendo salva na orientação original do mapa, como nas versões anteriores. 0° significa orientação do PDF, não uma promessa de norte geográfico para cima.

## Avisar equipamento encalhado

Todos os perfis aprovados têm o botão Avisar equipamento encalhado no início, nas ocorrências e no menu + do mapa. Informe o equipamento, escolha um equipamento salvo se preferir, acrescente local/talhão/ponto de referência e uma observação opcional. Confira e toque em Registrar aviso.

A localização é opcional. O aplicativo permite buscar o GPS e anexar uma posição recente, com precisão informada pelo aparelho. Posições com mais de dois minutos, datas futuras ou coordenadas inválidas não são anexadas como localização atual. Sem GPS, o equipamento e o ponto de referência permitem registrar o aviso. Ao corrigir um aviso, a localização já registrada é mantida quando não se anexa uma nova posição válida.

O aviso é salvo como ocorrência de campo da equipe atual, com identificador próprio. A sincronização e a fila de notificações existentes encaminham o evento. O serviço atual pode exibir o título Nova ocorrência de campo, com EQUIPAMENTO ENCALHADO, equipamento e informações no texto. Não foi criado um serviço externo novo nem alterado o Worker. Supervisores permanecem limitados à própria frente e turno. O administrador recebe os avisos conforme a frente escolhida nas configurações; quem criou o registro não recebe uma notificação do próprio evento.

Ver avisos de encalhado lista as solicitações abertas e permite consultar o histórico das concluídas. Edição, conclusão, exclusão e restauração seguem as permissões das ocorrências. O autor edita seu registro; só supervisor/admin excluem e restauram registros de trabalho. O aviso pode ser compartilhado em PDF ou imagem com os outros relatórios de campo.

O envio ao supervisor exige internet e notificações ativadas no aparelho de destino. Sem conexão, o registro fica salvo no aparelho do operador; abra o aplicativo conectado para concluir a sincronização. Registrar ou sincronizar não confirma leitura, recebimento pelo supervisor ou prestação de apoio. O botão não inicia uma chamada de rádio ou telefônica.

## Instalar e testar no celular

Instale DM-AGRO-1.6.0.apk sobre a versão existente, sem desinstalar ou limpar dados. A assinatura é a mesma das versões anteriores. Esta versão é código 17 e exige Android 8.0/API 26 ou mais recente.

1. Abra um mapa já utilizado e confira uma pintura conhecida. Gire com dois dedos, faça zoom e mova. Confira o GPS sobre o mesmo ponto do mapa. Centralize no GPS com o mapa girado e depois toque em 0°.
2. Com o mapa girado, selecione um talhão pintado, teste a edição dos vértices e a inclusão de pontos para medir área/distância. Confira que os pontos seguem o local tocado e que a área não muda só por girar.
3. Na conta de um operador de teste, use Avisar equipamento encalhado. Informe um equipamento fictício e escreva TESTE na observação e no local para distinguir de uma solicitação real. Registre e confira Ver avisos de encalhado.
4. No segundo celular, use o supervisor da mesma frente e turno, com internet e avisos ativados. Deixe o celular na tela inicial. Confira a notificação, abra o aplicativo e localize o aviso da equipe. Para testar com o administrador, a frente escolhida nas notificações deve corresponder à frente do operador. Testar na mesma conta que criou o registro não gera autoaviso.
5. Sem internet no operador, salve um novo aviso de teste; depois volte à internet e abra o aplicativo para sincronizar. Confira que ele continua salvo e não é duplicado.
6. Corrija o equipamento de um aviso próprio e confira o histórico. Como supervisor, conclua um teste e consulte o histórico. Teste PDF/imagem no seu leitor e no WhatsApp.

O APK não foi executado em um aparelho físico nesta sessão. Os gestos, a posição real no talhão e a entrega pelo Android precisam da conferência nos celulares.

## Publicar para atualização pelo próprio aplicativo

A conexão GitHub pode recusar o envio com erro 403. Se isso acontecer, a versão permanece no código local de dm-agro-login e nos anexos; ela não foi publicada automaticamente. A main é preservada.

1. Baixe PUBLICAR-DM-AGRO-1.6.0.zip e extraia em uma pasta nova.
2. Abra https://github.com/Diegomachadosl10/colheita-gps-oficial/releases/new . Crie a tag v1.6.0, destino dm-agro-login e título DM AGRO 1.6.0.
3. Em Attach binaries… abaixo da descrição, anexe os cinco arquivos extraídos: DM-AGRO-1.6.0.apk, dm-agro-update.json, SHA256.txt, DM-AGRO-1.6.0-codigo.zip e DM-AGRO-1.6.0-dm-agro-login.bundle. Não use Add files dentro da descrição.
4. Publique como versão estável mais recente (Latest), sem Pre-release. Depois disso, os aplicativos podem encontrar a atualização ao abrir ou em Verificar atualização.

Os mesmos cinco arquivos estão separados na pasta reenvio-1.6.0 se o ZIP não abrir no celular. Publicar os anexos não atualiza o código da ramificação remota; o Source code automático do GitHub pode representar o commit remoto anterior. O ZIP de código e o bundle contêm o código desta entrega.

Para gerar pelo Actions, a ramificação remota precisa conter estas alterações e a assinatura permanente precisa estar configurada: Actions → Gerar APK Colheita GPS → Run workflow → dm-agro-login. O pacote desta entrega já contém o APK compilado e assinado com a mesma chave dos APKs instalados.

## Configuração e verificação

Não há novas regras Firebase, configuração Cloudflare, plano pago ou upload automático de fotos para habilitar estas duas funções. O aviso usa o grupo de ocorrências já existente e o serviço de notificações já configurado. As funções e as regras de restauração da versão 1.5.0 continuam incluídas; quem ainda não aplicou as regras daquela versão deve seguir as instruções de 1.5.0 para essas funções anteriores.

Compilação testDebugUnitTest + assembleDebug aprovada, com 113 testes Android sem falhas. Incluem transformação real do Canvas e sua inversa em ângulos arbitrários, rotação com zoom/movimento, mudança de IDs dos dedos, virada de ângulo, GPS e pintura desenhados no mesmo referencial, área e coordenadas salvas preservadas, borracha e pincel após rotação, validação de localização recente, aviso offline e correção sem duplicação. Serviço de notificações: 18 testes aprovados, incluindo o aviso de encalhado no pipeline já implantado, destinatários da equipe e deduplicação. O teste do serviço usa respostas simuladas; não equivale a comprovar entrega FCM ao aparelho físico.
