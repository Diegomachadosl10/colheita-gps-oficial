# Firebase — conferência manual pelo celular em 28/11/2026

## Evidência recebida e alcance

Diego abriu o console do projeto `dm-agro-c0e04`, mostrou o cadastro Android `com.colheitagps.app`, enviou o texto das regras até `/configuracao/servico` e três capturas que cobrem o editor até a linha final 121.

A comparação manual é compatível com `firebase/firestore.rules` da branch `dm-agro-login`, blob `5b29f8d5ee1189c9acf20945bb8d0129e243050a`. O mesmo arquivo-base existe na branch Beta. O texto inicial e as estruturas/condições visíveis na continuação correspondem ao conjunto estável. As capturas não constituem comparação byte a byte do ruleset publicado, prova de revisão/data de publicação ou execução de requisições reais.

Não foi recebida sessão administrativa, senha, token ou chave privada. A integração Firebase no ChatGPT apresentou “Indisponível”; isso é distinto da configuração Firebase já existente no Android e no serviço Cloudflare, que Diego realizou manualmente. Não exigir reinstalações da integração para continuar desenvolvimento e testes isolados. A conferência manual não dá acesso automático ao servidor por esta conversa.

## Resultado da leitura

- O administrador principal é identificado por UID fixo; a matrícula é vinculada ao e-mail técnico do próprio usuário autenticado.
- Supervisores podem ativar o próprio pedido nas condições legadas e novos pedidos de supervisor podem entrar aprovados. Isso é a política estável anterior, não a nova aprovação prevista no Beta.
- Gestão de registros/aprovação de operadores fica limitada à frente **e turno** do supervisor no conjunto estável. O Beta preparado usa os três turnos da própria frente e inclui ajudantes.
- Em `equipes/<equipe>/registros`, exclusão física é recusada. As revisões são incrementais, autor/tipo/grupo/chave permanecem fixos e o arquivamento lógico sincronizado fica restrito a gestor para itens.
- A coleção legada `/pendencias` tem regra separada que ainda permite gravação/exclusão física pelo administrador. Não generalizar a proteção contra exclusão física dos registros da equipe a todas as coleções.
- Os registros estáveis guardam o conteúdo operacional como uma string `conteudo`. `valido` confere metadados e limites, mas não inspeciona `concluida`, categoria de equipamento ou campos de auditoria dentro desse JSON. Portanto, “somente gestores concluem pendências” não é garantido por essas regras legadas, mesmo que a interface esconda a ação. Esta é uma constatação da análise do código, sem tentativa contra dados reais ou evidência de abuso.
- As capturas mostram o fechamento do conjunto estável e o bloqueio genérico ao final. Não aparece a autorização de `ambientes/beta`. Esse conjunto, sozinho, recusaria as consultas do candidato Beta ao novo espaço.

## Comparação com a preparação Beta

`firebase/firestore.beta.rules` preserva o conjunto estável e acrescenta o espaço Beta. Os itens Beta têm `dados` estruturados, coerência de ID/arquivamento e restrições explícitas à conclusão, categoria de equipamento e histórico por operadores. Supervisores aprovam somente os papéis de folha da própria frente; não aprovam outros supervisores. Essas regras continuam candidatas, sem implantação confirmada.

A execução [46141372481](https://github.com/Diegomachadosl10/colheita-gps-oficial/actions/runs/46141372481), código `c122c6c79de9439a2f03b2214efaaaa38f6fb05f`, está concluída com sucesso na tentativa 2. A evidência dos testes e seu alcance estão na [continuidade local](CONTINUIDADE-LOCAL-2026-11-28.md): 375 testes Android locais, regras em emuladores fictícios, serviços e três testes Android nativos. Esses resultados não provam implantação nem autorização em produção.

## Continuidade necessária

A leitura manual das regras avançou; índices, perfis, documentos reais, configuração do serviço e restauração ainda não foram homologados. Permanecem pendentes a continuidade das operações administrativas de credenciais, cópia local/outbox, migração verificada e corte dos clientes antigos. O planejador e o inventário existentes não aplicam essas etapas.

A ausência do plugin não impede trabalho no repositório. Operações necessárias no console podem ser executadas pelo titular no celular, com arquivos/roteiro concretos, testados e revisados antes da execução. Isso não autoriza improvisar uma cópia, descartar filas, enviar chaves pelo chat ou distribuir um Beta vazio.

Esta etapa acrescenta somente documentação na branch de desenvolvimento. Não altera regras publicadas, documentos, Worker, assinatura, APK ou canal estável de atualização.
