# DM AGRO 1.8.3 — Mensagem limpa, mapas atuais e remoção de Relatórios

O estoque continua enviando os dados como mensagem com a foto original do mapa pintado anexada. Foi removido o rodapé “Fotos/mapas anexados dos lotes…”. A seleção de mapa mostra somente imagens vinculadas a mapas atualmente cadastrados na biblioteca, com o PDF presente no aparelho. A ferramenta Relatórios de turno foi retirada do aplicativo.

## Alterações

- Mensagens de estoque ativo e de vários lotes não recebem mais o rodapé de fotos/mapas. Os dados e os anexos originais continuam sendo enviados como na versão 1.8.2.
- Os seletores de mapa do estoque ativo, do formulário de estoque e do envio de vários lotes consultam o mesmo catálogo da biblioteca Mapas. Um PNG antigo sem cadastro atual, de um mapa excluído, de outra equipe ou cujo PDF foi removido não aparece como opção genérica “Mapa PDF”.
- Nomes e renomeações vêm do catálogo atual. Mapas com nomes iguais continuam distinguíveis pelo seletor. A imagem pintada precisa estar salva no aparelho para ser oferecida; abrir o mapa e concluir a pintura gera essa imagem.
- Removidos o cartão Relatórios, o atalho Relatórios de turno no menu, as telas de criação/edição dos relatórios e a seção correspondente no calendário. Relatórios antigos também não aparecem no compartilhamento do dia nem em Registros excluídos.
- O editor de pendências foi separado do antigo editor de relatórios, mantendo equipamento, descrição, O.S., fotos, validações e permissões.

A remoção da ferramenta não executa descarte de dados no banco. O armazenamento e a sincronização reconhecem os registros antigos para compatibilidade com cópias locais e aparelhos ainda desatualizados, sem oferecer telas ou novos cadastros de relatórios. As imagens antigas também são preservadas como possíveis anexos de estoque/histórico; apenas deixam de aparecer na seleção de mapas quando não pertencem à biblioteca ativa.

## Instalar e conferir no celular

1. Instale **DM-AGRO-1.8.3.apk** sobre a versão atual, sem desinstalar nem limpar os dados.
2. Envie um estoque com mapa para o WhatsApp. Confira que a mensagem não tem mais “Fotos/mapas anexados…”.
3. Abra a seleção de mapa do estoque. Ela deve listar os mapas atuais da biblioteca que têm imagem pintada salva, sem as sobras antigas.
4. Confira o início, o menu e o calendário: a ferramenta Relatórios de turno foi removida. Estoque, pendências, ocorrências, peças, chuva, mapas, O.S. e caminhões continuam disponíveis conforme o perfil.

Esta versão não exige novas regras Firebase, índices, plano ou configuração do Cloudflare. O controle de equipes e usuários, a autenticação por HTTPS, a correção do fechamento ao compartilhar e o envio de estoque por mensagem e foto foram preservados.

## Validação

- Compilação Android concluída; **157 testes aprovados**.
- Testes confirmam catálogo ativo com PDF existente, exclusão das sobras sem apagar anexos, nomes atualizados e uso do mesmo filtro no envio de um lote e de vários lotes.
- Testes com o FileProvider real, em Android API 28 e 35, conferem mensagens sem rodapé e os bytes dos mapas originais compartilhados.
- Testes de interface confirmam a retirada de Relatórios do início, do menu e do calendário, e a preservação de O.S., fotos, data e ID ao editar uma pendência.
- Regras Firebase e backend permanecem idênticos aos da versão anterior. Não houve operação de exclusão no banco de produção.
- APK **1.8.3**, código **24**, Android mínimo **8.0 / API 26**, mesma assinatura de atualização.
- O envio final pelo WhatsApp e a lista de mapas do aparelho devem ser conferidos no celular.

## Ideias para uma próxima etapa

Sem criar novas ferramentas no início, as melhorias mais úteis seriam busca por lote/equipamento nas telas existentes, mapas favoritos no topo da biblioteca e uma marcação de urgência nas pendências. São sugestões; não foram adicionadas nesta versão.

## Publicar para os outros celulares

Extraia **PUBLICAR-DM-AGRO-1.8.3.zip**. Em [Nova release](https://github.com/Diegomachadosl10/colheita-gps-oficial/releases/new), use tag **v1.8.3**, destino **dm-agro-login** e título **DM AGRO 1.8.3**. Anexe os cinco arquivos extraídos na área **Attach binaries…**, abaixo da descrição: APK, `dm-agro-update.json`, `SHA256.txt`, ZIP do código e bundle. Publique como versão estável mais recente, sem **Pre-release**. Os aplicativos podem encontrar a atualização ao abrir ou em Verificar atualização.

A `main` deve permanecer preservada. Se o GitHub recusar a gravação, o pacote contém o APK e o código atualizados, sem publicação automática. Anexar arquivos à release não atualiza o código da branch remota; executar Actions antes de atualizar a branch compilará a versão antiga.
