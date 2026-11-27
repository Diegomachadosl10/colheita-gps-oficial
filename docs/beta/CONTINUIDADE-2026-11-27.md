# Continuidade do Beta — 27/11/2026

Diego confirmou que deseja a **atualização disponível no próprio aplicativo**. A publicação final está autorizada, após concluir a versão e conferir a transição dos dados. Não se trata de nova autorização para cadastro de empresas, APIs pagas ou perda de registros. O arquivo estável de atualização e a produção 2.2.7 continuam preservados enquanto a versão não atende a esses critérios.

## Funcionalidades acrescentadas nesta continuação

- Relatórios de turno em módulo próprio para supervisor/administrador: relato livre, data operacional, lote/O.S. opcional, recado para o próximo turno e até 12 fotos. Criação/edição preservam ID, autor, data inicial, anexos e histórico de correções. Campos incompletos não fecham o formulário; rascunhos ficam por usuário/equipe. Exclusão usa o arquivamento restaurável já existente.
- Envio do relatório como mensagem, imagem ou PDF, sempre com prévia e botão Enviar. Responsável, data, frente e turno acompanham o relatório. O compartilhamento fica a cargo da pessoa; não há envio automático a contatos/grupos.
- Indicadores da equipe selecionada: equipamentos ativos efetivamente cadastrados, pendências abertas/urgentes, lotes, área restante numérica, produtividade ponderada por área numérica e ocorrências abertas. Cada cálculo explicita sua abrangência e os lotes aproveitados. Texto livre não vira zero nem número adivinhado; dados ausentes aparecem como indisponíveis.
- A área colhida **ainda não é calculada no painel**: faltam classificação explícita de colheita, período/safra e prevenção de dupla contagem de pinturas em mapas distintos. Mostrar essa limitação é parte do contrato de dados, não uma estimativa concluída.
- Feedback em Configurações: texto revisável com versão/Android; nenhum nome, matrícula, mapa, foto, áudio ou banco é anexado automaticamente. A pessoa escolhe o destinatário.

## Acesso offline após reiniciar

Uma entrada aprovada, conferida online tanto no perfil quanto na leitura de acesso da equipe, pode gerar uma autorização local de **24 horas**. Ela é restrita ao mesmo UID, instalação e ambiente. Não dá acesso a uma conta que nunca obteve uma aprovação válida no servidor.

A autorização contém somente UID, perfil mínimo, nome, frente/turno e relógios. Não guarda senha, token Firebase, e-mail nem segredo do serviço. O arquivo usa AES-GCM autenticado com chave do Android Keystore e vínculo ao UID/ambiente. Fica em `noBackupFilesDir`, fora do backup comum. Falha de chave/arquivo permite continuar a entrada online válida, mas desabilita o reaproveitamento offline.

O prazo não se renova por abrir o aplicativo, ler o arquivo ou observar um snapshot aprovado. Recuos relevantes do relógio ou inconsistência do relógio monotônico invalidam a autorização; após reiniciar o aparelho, mantém-se o prazo de parede original. O arquivo não equivale a uma fonte de horário confiável contra um aparelho comprometido. Expiração é conferida ao entrar/retomar e aproximadamente a cada minuto em primeiro plano; salva rascunho/mapa antes de voltar à entrada.

Sem rede ativa, uma autorização ainda válida evita uma consulta desnecessária. Com rede presente, só falhas de DNS/tempo/indisponibilidade específicas permitem o fallback. Negação de permissão, credencial inválida, perfil inválido, resposta malformada ou erro TLS não autorizam fallback. Bloqueio, exclusão e mudança de frente/papel observados no servidor invalidam o arquivo. Sair da conta remove as autorizações Beta locais.

Aprovar usuários e outras rotinas administrativas continuam exigindo a consulta online do perfil e as regras do servidor. As gravações operacionais offline permanecem na fila local, com as permissões conferidas novamente pelo servidor ao sincronizar. Revogação instantânea sem internet é impossível; esta janela é a política inicial do piloto, não um prazo de validade comercial da Beta.

## Preparação da migração

`scripts/plan-beta-migration.py` produz somente um **plano offline** a partir de um snapshot de documentos no formato REST do Firestore. Não contém cliente HTTP, token, comando de implantação nem escrita no Firebase. O resultado não é uma migração executada.

- Restringe projeto e caminhos à base estável de usuários/registros da equipe, gera destinos em `ambientes/beta` e exige destino inexistente para uma futura aplicação.
- Preserva UID, IDs, revisões, autores, timestamps, campos livres, históricos, estado de arquivamento e referências de anexos. Converte `conteudo` dos itens para `dados` tipados, `formato: 2`, sem alterar a origem.
- Rejeita inconsistência de ID/equipe/arquivamento, grupos desconhecidos, duplicação de documentos/propriedades JSON e campos parecidos com credenciais no perfil. Não corrige inconsistências apagando dados.
- Perfis mantêm o estado original; não há aprovação automática. O plano exige revisar as aprovações antigas de supervisores antes do corte.
- Referências de arquivos do servidor ficam inventariadas na origem, sem ativar Storage/upload/download pago. A cópia dos anexos locais, mapas pessoais e filas antigas continua sendo uma etapa separada a concluir.
- Inclui hash/updateTime de cada origem e hash do destino planejado para uma futura revalidação imediatamente antes de copiar. O snapshot deve ser privado, integral e versionado. Uma captura antiga não autoriza ignorar gravações feitas pelos aplicativos antigos depois dela.
- O arquivo de saída é criado com permissão 0600 e nunca sobrescreve um plano anterior. A saída do comando mostra apenas contagens/hashes. Não adicionar snapshots ou planos com dados reais ao Git.

Exemplo exclusivamente com arquivos privados fora do repositório:

```sh
python3 scripts/plan-beta-migration.py --snapshot /tmp/dm-agro-privado/origem.json --plan /tmp/dm-agro-privado/plano.json
```

## Verificação e limites de liberação

Há testes Android/Robolectric para persistência/edição do relatório, fotos preservadas mesmo quando ausentes neste aparelho, período noturno, permissões de operador, indicadores e autorização offline, incluindo adulteração, troca de conta/chave, expiração, reinício e bloqueio. A compilação também inclui testes instrumentados novos para PDF nativo, URI real do FileProvider e chave real do Android Keystore. Estes últimos exigem execução no emulador Android do Actions; compilar o teste não prova sua execução.

**Validação concluída no código `39e2651153ffb8bc36556b05c969d42cf5d3afb4`:** [Actions 46025766436](https://github.com/Diegomachadosl10/colheita-gps-oficial/actions/runs/46025766436), quatro jobs aprovados. Foram executados 341 testes Android/Robolectric, 2 testes instrumentados em Android API 35, 69 testes de regras em emuladores fictícios, 18 testes do Worker estável, 21 do Worker Beta e 12 testes Python do publicador/planejador. Compilação local de aplicativo e APK de testes aprovada; lint sem erros, com 128 avisos. O certificado do APK local coincide com a assinatura permanente fixada; nenhum APK foi anexado ao workflow ou distribuído. [Evidências legíveis por máquina](evidencias-continuidade-2026-11-27.json).

O primeiro ensaio do job nativo falhou antes de executar os testes porque o instalador tentou obter o pacote SDK obsoleto `tools`. O workflow agora seleciona explicitamente os pacotes suportados; o novo ensaio executou e aprovou os dois testes. Android nativo no emulador não equivale a homologação de WhatsApp, GPS no campo ou entrada no Firebase real.

Não há neste ambiente uma sessão administrativa autenticada de Firebase nem credenciais de administração Cloudflare. O GitHub está conectado para código/Actions, mas a consulta aos segredos retorna HTTP 403. Nenhuma chave foi solicitada pelo chat. A integração oficial Firebase foi sugerida e ainda depende de instalação/conexão pelo titular; não há ferramenta Firebase ativa neste momento.

Antes da atualização final ainda é necessário:

1. Obter acesso administrativo seguro, ler as regras/índices reais e homologar o servidor Beta/avisos sem sobrescrever mudanças de produção. A correção/exclusão de credenciais globais continua bloqueada no Beta; precisa recuperar essa continuidade com autorização no servidor antes da atualização final.
2. Concluir cópia local, adaptação das bases de sincronização e corte dos clientes legados, com backup/recuperação e dados reais expressamente autorizados. O planejador offline sozinho não resolve essa transição.
3. Completar o catálogo editável e os demais pontos do plano ainda não implementados: área colhida rastreável, assistente local com confirmação, exportação pintada com georreferência preservada e revisão visual/homologação completa no aparelho.
4. Conferir assinatura permanente e instalação por cima, testar os fluxos reais e publicar somente a entrega final validada, com continuidade dos usuários. Não há obrigação de pedir outra confirmação genérica de publicação: Diego já a autorizou; faltam execução e evidências.

Este documento não afirma que o aplicativo completo está pronto ou que a atualização está disponível.
