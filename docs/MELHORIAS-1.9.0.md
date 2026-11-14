# DM AGRO 1.9.0 — Busca, favoritos, urgência e temas claro/noturno

A versão acrescenta as três melhorias autorizadas nas telas existentes e aplica o visual **Claro e limpo** escolhido pelo proprietário. O tema **Noturno** pode ser escolhido a qualquer momento pelo menu. A troca mantém a conta conectada, a equipe e a tela de trabalho; a preferência é lembrada no aparelho.

## Instalar e usar pelo celular

1. Instale **DM-AGRO-1.9.0.apk** sobre a versão atual. Não desinstale nem limpe os dados.
2. Para trocar o visual, abra **⋮ → Tema do aplicativo → Claro e limpo / Noturno**. O padrão é Claro e limpo. A configuração é local ao aparelho e continua após fechar ou sair da conta.
3. No **Estoque**, use **Buscar lote ou talhão**. Em **Pendências**, use **Buscar equipamento, pendência ou O.S.**. Na biblioteca **Mapas**, use **Buscar mapa ou lote**. A busca aceita letras sem acentos e diferenças como `K39` / `K/39`; apagar o texto volta a mostrar a lista.
4. Na biblioteca Mapas, toque na estrela **☆** ao lado do nome. A estrela **★** indica favorito e coloca esse mapa no topo. Toque novamente para desfavoritar. Os favoritos são pessoais, separados por conta e equipe neste aparelho.
5. Ao criar ou editar uma pendência, marque **Marcar como urgente** e salve. Equipamentos com pendências urgentes aparecem primeiro, com indicação visível em vermelho. As pendências urgentes abertas também aparecem primeiro dentro do equipamento. Concluir ou excluir uma pendência retira sua urgência da lista ativa, preservando a informação no registro.
6. Confira que a atualização da lista pela sincronização mantém a busca digitada. A escolha de tema mantém a tela em uso e o filtro.

As imagens de referência mostradas na conversa são propostas de visual. As prévias geradas pelo teste Android em `visual-1.9.0/DM-AGRO-claro.png` e `visual-1.9.0/DM-AGRO-noturno.png` mostram a implementação real, com rolagem para alcançar todas as ferramentas. Fontes ampliadas e telas estreitas continuam usando uma coluna quando necessário.

## Funcionamento e limites

- A busca filtra somente dados já disponíveis para a conta e equipe atual. Não consulta equipes adicionais nem cria novos índices no Firebase. O filtro é preservado ao voltar da tela de um item e limpo quando muda a equipe ou conta.
- Os favoritos não modificam os nomes, os cadastros, as pinturas ou as permissões dos mapas. Um mapa favorito fora do catálogo ativo não passa a aparecer por esse motivo. A preferência fica no aparelho e não é compartilhada automaticamente entre telefones.
- A urgência é um campo dentro do conteúdo já sincronizado da pendência. Operadores editam os próprios registros conforme as permissões existentes; supervisores e administrador continuam com seu escopo anterior. A marcação não permite editar pendências alheias ou de outra equipe. O compartilhamento da pendência inclui sua prioridade.
- Os textos e cartões usam cores de contraste verificadas em ambos os temas. Campos, botões, perfil de cadastro, previsão do tempo, administração e preparação de estoque usam a mesma paleta. Mapas/PDFs, imagens pintadas e fotos mantêm suas cores originais; o tema não altera coordenadas, georreferência ou GPS.
- O estoque mantém envio como mensagem com foto original do mapa, sem o rodapé de fotos/mapas. O seletor usa somente mapas ativos da biblioteca. A ferramenta Relatórios de turno continua removida.
- Preservados a autenticação por HTTPS, o administrador principal, a aprovação de operadores pela equipe, o encerramento e histórico de O.S., os períodos de caminhões e a correção do fechamento ao compartilhar.
- Não é necessário alterar regras, índices, Authentication, Storage ou Cloudflare. O plano continua gratuito. Não houve envio de mensagens a pessoas durante os testes.

## Validação

- Compilação `testDebugUnitTest assembleDebug` concluída e **170 testes Android aprovados**.
- **28 testes Firebase aprovados** no emulador `demo-dm-agro`, incluindo sincronização da urgência sem ampliar autoria, acesso ou equipe. As regras de produção não foram modificadas nem implantadas.
- Testes cobrem busca sem acentos/separadores, filtros com foco preservado, favoritos persistentes e isolados por UID/equipe, ordenação e edição de urgência, prioridade no compartilhamento e preservação dos dados anteriores.
- Testes de tema em Android API 28 e 35 conferem contraste de texto/urgência, preferência persistente, mudança na própria tela de estoque sem logout e renderização das telas nos dois modos. Os testes de grade/rolagem continuam conferindo que todas as ferramentas são alcançáveis, inclusive com fonte ampliada.
- APK **1.9.0**, código **25**, Android mínimo **8.0 / API 26**, mesma assinatura das versões instaladas.
- A instalação, a leitura ao ar livre, o recebimento no WhatsApp e a sincronização entre os celulares ainda devem ser conferidos nos aparelhos.

## Publicar para os outros celulares

Extraia **PUBLICAR-DM-AGRO-1.9.0.zip**. Em [Nova release](https://github.com/Diegomachadosl10/colheita-gps-oficial/releases/new), use tag **v1.9.0**, destino **dm-agro-login**, título **DM AGRO 1.9.0**. Anexe os cinco arquivos extraídos na área **Attach binaries…**, abaixo da descrição: APK, `dm-agro-update.json`, `SHA256.txt`, ZIP de código e bundle. Publique como versão estável mais recente, sem marcar **Pre-release**. Os aplicativos poderão encontrar a atualização ao abrir ou em Verificar atualização.

A `main` permanece preservada. Se o GitHub recusar gravação, esta entrega contém o APK e o código atualizados, sem publicação automática. Anexar os arquivos à release não atualiza a branch remota; Actions só usará o código novo depois que ele estiver nessa branch.
