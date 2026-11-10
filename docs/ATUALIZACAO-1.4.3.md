# DM AGRO 1.4.3 — Atalho para a pasta pública de mapas

## O que muda

No rodapé da biblioteca de Mapas, aparece Buscar mapas no Google Drive, com o texto Não tem o PDF neste celular?. O botão abre somente a pasta pública fornecida pelo administrador:

https://drive.google.com/drive/folders/1goZtocxdT1tgmjcNzpy3CoQHkeN4WLX4

O link usa o navegador ou aplicativo que o Android escolher. O DM AGRO não pede e-mail nem faz autenticação no Google. Não foi adicionada importação automática por link. O PDF continua sendo escolhido pelo botão + e importado pelo fluxo existente. GPS e georreferenciamento dependem do PDF, como antes. O botão + fica preservado e tem seu espaço reservado à direita do novo atalho.

Todos os perfis aprovados podem usar o atalho. Ele não altera os mapas locais, o catálogo pessoal dos operadores, as equipes ou as permissões. Nenhuma regra Firebase ou código Cloudflare mudou; não é necessário refazer configuração ou mudar de plano.

## Publicar pelo celular

1. Baixar e extrair PUBLICAR-DM-AGRO-1.4.3.zip em uma pasta nova.
2. Abrir https://github.com/Diegomachadosl10/colheita-gps-oficial/releases/new . Criar tag v1.4.3, destino dm-agro-login e título DM AGRO 1.4.3.
3. Na área Attach binaries… abaixo da descrição, anexar os cinco arquivos extraídos: DM-AGRO-1.4.3.apk, dm-agro-update.json, SHA256.txt, DM-AGRO-1.4.3-codigo.zip e DM-AGRO-1.4.3-dm-agro-login.bundle. Não anexar a chave privada JSON do Firebase da pasta Downloads.
4. Publicar como versão estável mais recente (Latest), sem Pre-release.
5. No DM AGRO, aceitar o aviso de atualização ao abrir ou usar Verificar atualização. Baixar e instalar sem desinstalar nem limpar os dados.

Os mesmos arquivos também estão separados em reenvio-1.4.3 caso o ZIP dê problema no celular.

A conexão GitHub ainda pode recusar gravação com HTTP 403. Nesse caso, a ramificação remota permanece como estava e os anexos de código e bundle contêm as alterações atuais de dm-agro-login. O Source code automático da release pode apontar para o commit remoto antigo. A ramificação main é preservada.

## Testar no celular

1. Abrir Mapas e conferir o botão Buscar mapas no Google Drive no rodapé, inclusive quando já existem mapas na lista.
2. Tocar no botão e conferir se abre a pasta pública correta.
3. Buscar o PDF desejado, voltar ao DM AGRO e importar pelo + como antes.
4. Abrir o mapa e conferir as funções habituais e as pinturas anteriores. O atalho não transforma um PDF comum em um mapa georreferenciado.

Compilação Android e 84 testes Android existentes aprovados. A abertura da pasta pública e a seleção do PDF pelo Drive/navegador precisam ser conferidas no celular. A mudança não solicita novas permissões ou credenciais Google.
