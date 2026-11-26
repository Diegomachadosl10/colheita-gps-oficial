# DM AGRO Professional — auditoria inicial do Beta

Data: 26/11/2026. Responsável pelo produto: Diego Machado.

## Resultado desta etapa

A base estável compila. A auditoria não reproduziu erro de compilação Kotlin/Android. Os workflows recentes paravam **antes de compilar**, na restauração da assinatura: o segredo `ANDROID_UPDATE_KEYSTORE_BASE64` estava ausente. Essa proteção não deve ser removida para distribuir APK com outra chave.

Foi criada a branch **`dm-agro-professional-beta`** e um workflow independente que compila, testa e analisa o código sem depender desse segredo. Ele guarda somente relatórios, não distribui APK e não publica releases. O código funcional do aplicativo, regras Firebase e Worker permanecem iguais aos da produção nesta etapa.

Não foi lançada atualização. Nenhum usuário precisa instalar, desinstalar ou limpar dados para esta auditoria.

## Base preservada e recuperação

| Item | Evidência |
| --- | --- |
| Última release estável | [DM AGRO 2.2.7](https://github.com/Diegomachadosl10/colheita-gps-oficial/releases/tag/v2.2.7), código 38, publicada em 25/11/2026 |
| Commit do código dessa release | `e2e13bcff1aa855bd5f7b5771619387e6684fa41` |
| Linha de produção | `dm-agro-login`, `e07aee322d9cbb9f74129bebc171ceb5cca27b7f` |
| `main` preservada | `9aa61a3383f098d1517f6871c722077dba76969b`; não é a base funcional mais recente |
| Desenvolvimento isolado | `dm-agro-professional-beta`, criada a partir da linha estável, sem reverter funcionalidades |
| Aplicativo instalado | `com.colheitagps.app`, Android mínimo 26, alvo/compilação 35 |
| Certificado permanente, SHA-256 | `d6bff2432575da5928e07127ef565b19c8148f53c2c0047de3f2bb7ce003b3a6` |
| APK estável, SHA-256 | `c01f7fdcdbd4aa74382b5755972413b3f5a801ae9010a197b122c99337399177` |
| Tamanho do APK estável | 13.120.586 bytes |

O manifesto público consultado pelo atualizador e o APK foram baixados novamente e conferidos. A assinatura corresponde ao certificado existente. O APK atual é **debuggable**: funciona na distribuição atual, mas isso não equivale a um pacote comercial homologado para a Google Play. A elegibilidade do certificado existente e o processo de Play App Signing precisam ser verificados antes de propor qualquer mudança de assinatura.

O histórico contém 138 commits alcançáveis e 30 tags. A base inventariada tem 198 arquivos, 66 arquivos Kotlin ativos e 59 arquivos de testes Android. As alterações posteriores ao commit da release na linha de produção são de publicação/documentação; a auditoria parte da mesma aplicação estável. [Inventário com tamanhos e hashes](inventario-base.json).

## Compilação e GitHub Actions

Stack reproduzida: AGP 8.7.3, Kotlin 2.0.21, Gradle 8.9 e SDK 35. A compilação local usou Java 21; o workflow Android conserva Java 17, compatível com a configuração do projeto. As regras usam Java 21 e Node 22 no GitHub. Não existe Gradle Wrapper versionado; a versão do Gradle é provisionada pelo Actions.

O [workflow de produção que falhou](https://github.com/Diegomachadosl10/colheita-gps-oficial/actions/runs/45549261737) passou no Worker e nas regras; falhou em **Restaurar a assinatura permanente**, e o passo de compilação foi ignorado. O [publicador da 2.2.7](https://github.com/Diegomachadosl10/colheita-gps-oficial/actions/runs/45549098065) passou e publicou o APK previamente assinado e validado.

O novo [workflow Beta](../../.github/workflows/beta-audit.yml) tem somente permissão de leitura, checkout sem persistir credenciais e três jobs: serviço/publicador, regras em emuladores e Android/testes/lint. Ele não lê a chave privada, não faz deploy e não inclui APK nos artefatos. O APK intermediário do runner pode usar sua chave descartável de debug; **não é uma atualização distribuível**. A compilação assinada para distribuição continua dependendo da chave permanente existente, configurada de maneira protegida em uma etapa futura.

O workflow Beta concluiu **com sucesso nos três jobs**. Validações podem ser acompanhadas pelo celular em [Actions — execução da auditoria](https://github.com/Diegomachadosl10/colheita-gps-oficial/actions/runs/45796151156). Commits de código na branch Beta iniciam os testes automaticamente. O botão manual do GitHub pode depender de o workflow também existir na branch padrão; não foi alterada a `main` para isso.

## Testes executados

| Verificação local | Resultado |
| --- | --- |
| Compilação e empacotamento `assembleDebug` | Aprovados |
| Testes Android, 59 classes | **283 aprovados**, zero falhas/erros/ignorados |
| Regras Firebase em Firestore/Storage emulados | **28 aprovados** |
| Worker Cloudflare, com serviços externos simulados | **18 aprovados** |
| Publicador e validações de pacote | **7 aprovados** |
| Lint Android completo | **Zero erros, 128 avisos** |
| Provas adicionais das permissões atuais | Duas divergências confirmadas em `demo-dm-agro`, sem dados reais |

No primeiro comando offline, os testes e o APK passaram, mas o lint precisou de três dependências ausentes do cache. Elas foram resolvidas pelos repositórios declarados, sem alterar as dependências do aplicativo, e o lint completo passou. A porta padrão do emulador local estava ocupada; a repetição usou uma configuração temporária com portas livres. O `firebase.json` de produção não foi modificado.

Os avisos incluem persistência síncrona em preferências (34), alocações ao desenhar (23), textos/interface sem recursos de internacionalização (56), dependências com versões posteriores (5), EXIF (2) e pontos de acessibilidade/ícones. Há um aviso de referência estática a `Context` no atualizador, que usa `applicationContext`; precisa de análise, não deve ser declarado vazamento confirmado apenas pelo aviso. Não foram ocultados erros nem criada baseline de lint para mascará-los.

Testes automatizados não substituem testes de campo de GPS, celulares de baixa memória ou compartilhamento real por WhatsApp. Não foram criadas contas reais para esta auditoria.

## Arquitetura e funcionalidades atuais

O aplicativo é Android nativo, com interfaces construídas em Kotlin. `MainActivity.kt` reúne aproximadamente 5.028 linhas, incluindo telas, mapas e georreferência. Existem duas cópias antigas de `MainActivity.kt` fora do diretório Kotlin compilado; editar essas cópias não altera o aplicativo instalado. Elas não foram apagadas na auditoria.

| Área solicitada | Base encontrada | Trabalho necessário no Beta |
| --- | --- | --- |
| Identidade visual | Marca DM AGRO, temas claro/escuro, ícones, abertura e autoria | Subtítulo novo, identificação Beta e sistema visual consistente |
| Painel | Ferramentas, contagem de pendências, clima, versículo e informações da equipe | Indicadores consolidados com origem dos dados e período explícitos |
| Mapas/GPS | GeoPDF, GPS, cache local, zoom, rotação, polígonos manuais, edição/exclusão, medidas e pinturas salvas | Homologar variedade de PDFs, vínculos com lotes/O.S./talhões e exportação PDF acessível |
| Cálculo de área | Área georreferenciada e união rasterizada das pinturas já existem | Validar aproximações, múltiplas zonas, falhas e consolidação entre mapas sem duplicar áreas |
| Estoque | Lote, talhões, área, produtividade, histórico, calendário, lotes em conjunto e mensagem com mapa/foto | Observações/vínculo explícito, texto realmente livre, interpretação numérica opcional e PDF |
| Relatórios de turno | A tela foi retirada por solicitação anterior; passagem de turno e renderizador de relatório permanecem | Reintroduzir o módulo pedido agora, com relato livre, fotos, responsável e PDF |
| Equipamentos/pendências | Cadastro persistente, agrupamento, fotos, urgência, histórico e restauração | Nova regra para conclusão, datas/responsáveis verificáveis e separação visual sem duplicar cadastro |
| Ocorrências | Texto livre, fotos, coordenadas opcionais, edição, histórico e imagem | Melhorar obtenção da posição sem depender de uma leitura anterior, acompanhamento e PDF |
| Caminhões | Entrada/saída, datas, tempo de espera, histórico e seleção por turno/dia | Voz confirmada, consolidação de permanência e turnos configuráveis |
| Empresa/equipe | Uma configuração global e partição por frente/turno | **Multiempresa ainda não existe**; faltam propriedades, administradores de empresa e catálogo dinâmico |
| Autenticação | Firebase Auth por matrícula, senha não persistida pelo código, papéis e aprovações | Empresa no vínculo/login, papel ajudante e nova política de aprovação |
| Online/offline | Preferências locais, fila durável, revisões, tombstones e cópias de conflitos | Retomada offline após reiniciar, fila resistente ao ciclo do processo e modelo transacional para expansão |
| Anexos | Fotos/PDFs locais, preservados no compartilhamento e no backup | Não afirmar sincronização automática entre aparelhos: ela está desativada para manter o plano gratuito |
| PDF e WhatsApp | WhatsApp por mensagens/imagens funciona; renderizador PDF existe internamente, sem oferta geral no fluxo atual | Reintroduzir PDF no Beta, preservando mensagem+mapa do estoque e os modos sem foto |
| DM AGRO IA | Assistente de voz foi retirado na 2.2.3; não há IA ativa ou permissão de áudio | Implementação nova, gradual, acionada pelo usuário e sempre com confirmação |
| Outros recursos existentes | Chuva, peças a pedir, preventiva, passagem de turno, clima, versículo e favoritos | Preservar e integrar na organização nova, sem exclusões silenciosas |
| Comercialização | Sem mensalidade, anúncio ou SDK de cobrança no código ativo | Feedback, licenças futuras inativas, privacidade, LGPD, painel web e preparação Play |

## Permissões: diferenças prioritárias

As provas adicionais usaram apenas usuários fictícios no emulador:

1. **Um novo cadastro pode escolher supervisor e entrar aprovado.** Isso foi uma decisão explícita da versão antiga, implementada no cadastro e nas regras; o Worker aceita o perfil aprovado resultante. A nova especificação exige aprovação: a mudança deve ocorrer de forma coordenada no Beta, sem confiar apenas no botão da interface.
2. **O operador autor consegue concluir a própria pendência.** A interface permite essa ação, e o estado está dentro do campo `conteudo`, que é uma string JSON. As regras verificam autoria/equipe/revisão, mas não conseguem validar a transição de `concluida` dentro dessa string. A restrição nova precisa de campos de estado tipados e verificação no servidor, além do bloqueio na interface.

Os supervisores hoje são limitados à **mesma frente e turno**. O pedido novo descreve acesso à própria frente e turnos personalizados; essa diferença precisa ser modelada, testada e migrada. O papel `ajudante` e o administrador de empresa também não existem na base.

As frentes e os turnos estão fixos no Android (`TeamPolicy`, `TeamStore`), nas regras e no Worker. Alterar somente a lista da tela não resolve: o servidor recusaria os novos valores. A matrícula também é global no endereço técnico do Authentication, portanto números iguais de empresas diferentes precisam de uma estratégia compatível com as contas antigas.

Não foi alterada nenhuma dessas permissões nesta etapa. Elas são critérios obrigatórios antes de liberar o Beta multiempresa.

## Dados, sincronização, privacidade e limites

- Os registros ficam em `equipes/{frente_turno}/registros`; arquivos/preferências locais usam a mesma separação. Não há `empresaId` nem vínculo de empresa que impeça colisões futuras.
- A fila usa IDs estáveis, revisão otimista, tombstones e preservação de conflitos. Há polling de 12 segundos e um executor ligado à Activity; recuperação após encerrar o processo e consumo de bateria precisam de testes dedicados.
- Mapas pessoais são privados por usuário; não devem ser convertidos em mapas compartilhados pela migração sem autorização.
- O histórico de alterações existe no conteúdo editável do registro; não é um livro de auditoria imutável com garantia de servidor. O envelope possui timestamp e revisão verificados, mas isso não protege cada campo do histórico interno.
- Backup/restauração ZIP existem; não há política comercial de retenção, criptografia de exportações e recuperação homologada entre aparelhos. `allowBackup` está habilitado e deve entrar na revisão de privacidade.
- A abertura fria ainda precisa conferir acesso online; a sessão reaproveitada na memória por até cinco minutos não garante operação offline depois de reiniciar o aplicativo/celular.
- `AUTOMATIC_ATTACHMENTS` está desativado. Não ativar Storage/Blaze para prometer sincronização de fotos: isso contrariaria a exigência de custo zero e precisa de avaliação separada.
- O Worker está acessível e informou `configured: true`, projeto esperado e versão de serviço 1.4.0 no `/health`. Isso **não comprova** IAM, configuração FCM, quotas, faturamento ou entrega real de uma notificação.
- Não foi disponibilizada credencial administrativa Firebase nesta sessão. As regras **implantadas** e os dados reais não foram lidos nem modificados. Aprovar testes das regras do Git não prova que sejam as regras em produção.
- A varredura limitada de 614 blobs Git alcançáveis não identificou PEM privado ou token GitHub nos padrões pesquisados, nem arquivos de keystore versionados por nome. Não equivale a uma certificação de ausência de todos os tipos de segredo. A configuração pública do cliente Firebase não é uma chave administrativa.
- Não há política de privacidade/LGPD do aplicativo nem licença geral identificada. Existe atribuição da base de municípios e do MET Norway. A origem e o direito de uso de cada recurso precisam ser confirmados antes da comercialização; nenhum mapa de terceiro foi importado para a auditoria.

## Próxima etapa e limites

O [plano técnico](PLANO-TECNICO.md) estabelece a sequência para segurança/multiempresa, migração, experiência visual, módulos, IA e homologação. Nenhuma dessas transformações estruturais está concluída por existir este documento.

**Nesta etapa não houve** nova versão instalada, migração, alteração de regras implantadas, mudança de assinatura/applicationId, API paga, cobrança ou criação de infraestrutura. A linha estável e o feed de atualização permanecem 2.2.7. As evidências resumidas estão em [evidencias-auditoria.json](evidencias-auditoria.json).
