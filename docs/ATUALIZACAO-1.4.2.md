# DM AGRO 1.4.2 — Nomes claros na escolha do mapa pintado

## Correção

A seleção de mapa pintado em Preparar envio de estoques agora consulta o nome do mapa no catálogo da equipe, o mesmo usado pela biblioteca. A extensão PDF/PNG é omitida somente da apresentação. O código interno do arquivo não é mostrado.

A lista mostra nome e data de atualização. Mapas com nomes repetidos recebem uma numeração para distingui-los. Se não existir nome no catálogo, o mapa antigo continua selecionável como Mapa pintado 1, Mapa pintado 2 etc., com data e prévia para identificação. A numeração dos mapas sem nome reflete a lista atual.

Tocar no mapa abre a prévia antes de anexá-lo. Usar este mapa confirma a escolha e atualiza somente a foto do lote selecionado. Escolher outro volta à lista. A prévia não altera o arquivo nem a pintura. Nomes de outras equipes não são usados.

Todos os recursos, dados, permissões, fotos e arquivos existentes são preservados. Nenhuma regra do Firebase ou código do Cloudflare mudou. O serviço de notificações já configurado continua válido.

## Publicar pelo celular

1. Baixar e extrair PUBLICAR-DM-AGRO-1.4.2.zip em uma pasta nova. Existem cinco arquivos na raiz.
2. Abrir https://github.com/Diegomachadosl10/colheita-gps-oficial/releases/new . Criar tag v1.4.2, destino dm-agro-login e título DM AGRO 1.4.2.
3. Na área Attach binaries… abaixo da descrição, anexar somente DM-AGRO-1.4.2.apk, dm-agro-update.json, SHA256.txt, DM-AGRO-1.4.2-codigo.zip e DM-AGRO-1.4.2-dm-agro-login.bundle. Não anexar a chave privada JSON do Firebase da pasta Downloads.
4. Publicar como versão estável mais recente (Latest), sem Pre-release.
5. No DM AGRO instalado, aceitar o aviso de atualização ao abrir ou usar Verificar atualização. Baixar e instalar sem desinstalar nem limpar dados.

Os arquivos também ficam separados em reenvio-1.4.2 se houver problema ao extrair o ZIP no celular.

A publicação automática depende da permissão de escrita da conexão GitHub. Se ocorrer erro 403, a ramificação remota continua como estava. Os anexos de código e bundle desta versão contêm as alterações atuais de dm-agro-login. Os arquivos Source code automáticos do GitHub podem apontar para o commit remoto antigo. A ramificação main permanece preservada.

## Testar no celular

1. Abrir Estoque → Compartilhar vários estoques e selecionar os lotes desejados.
2. Em um lote, tocar em Adicionar foto a este lote ou Trocar foto deste lote → Usar mapa pintado salvo.
3. Conferir se a lista mostra o nome que aparece em Mapas, sem o código longo mapa_relatorio_… .
4. Tocar em um nome e conferir a prévia. Usar Escolher outro e verificar que o lote mantém sua foto anterior. Voltar ao mapa desejado e tocar em Usar este mapa.
5. Conferir que somente o lote escolhido foi atualizado e enviar mensagem/fotos ou PDF como antes.

Compilação e 84 testes Android aprovados, incluindo a escolha pelo nome cadastrado, confirmação obrigatória na prévia, anexo somente ao lote correto e mapas antigos sem nome sem consultar o catálogo de outra equipe. A imagem PREVIA-MAPA-1.4.2.png é uma renderização Android de teste com dados e imagem fictícios; conferir os mapas reais depois de instalar.
