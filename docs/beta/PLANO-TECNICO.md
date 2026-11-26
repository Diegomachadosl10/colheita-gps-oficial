# DM AGRO Professional — plano técnico do Beta

Este é o plano para revisão depois da [auditoria inicial](AUDITORIA-2026-11-26.md). Os itens futuros abaixo **não estão implementados**. A produção permanece 2.2.7. Não há autorização nesta etapa para migrar dados, implantar regras ou publicar atualização para os usuários.

## Princípios de entrega

1. Evoluir a base funcional, sem reescrever tudo de uma vez nem voltar à `main` antiga.
2. Mesma identidade Android e certificado; atualização sobre o aplicativo existente, preservando mapas, fotos, pinturas, IDs e históricos.
3. Separar decisões de interface, domínio e persistência. Cada migração deve ter simulação, backup e verificação de restauração antes de tocar em dados reais.
4. Nenhuma empresa nova usa dados operacionais antes de passar pelos testes de isolamento e permissões. Uma cor diferente na tela não oferece isolamento.
5. Beta sem anúncios, cartão, cobrança ou prazo de encerramento. Arquitetura para licenças futuras não pode criar bloqueios de pagamento na Beta.
6. Somente recursos gratuitos autorizados, dentro das respectivas cotas. Não existe promessa de infraestrutura ilimitada e gratuita. Não ativar fallback pago automaticamente.
7. Mostrar claramente fonte, data e abrangência dos indicadores; nunca preencher números fictícios para deixar o painel bonito.

## Etapas e critérios de aceite

| Etapa | Entrega | Condição para avançar |
| --- | --- | --- |
| 0 — Auditoria e compilação | Branch isolada, baseline, testes e CI independente da assinatura | Auditoria revisada; CI Beta aprovado; produção inalterada |
| 1 — Contratos e segurança | Modelos de empresa, vínculo, frente, turno e operações autorizadas; regras/Worker inicialmente em ambiente de teste | Matriz de permissões e testes negativos entre empresas/frentes aprovados; nenhuma autoaprovação de supervisor |
| 2 — Persistência e migração | Adaptadores legados, armazenamento transacional local, fila e simulador de migração | Dados fictícios convertidos/restaurados sem perda de IDs, anexos, pinturas e revisões; prova de idempotência |
| 3 — Experiência e indicadores | Identidade Beta, navegação por módulos, acessibilidade e painel com dados reais | Celular pequeno, fonte grande e temas claro/escuro sem cortes; valores conferidos com a base |
| 4 — Ferramentas e exportação | Estoque livre, relatórios de turno, novas permissões de manutenção, ocorrências/caminhões e PDF/WhatsApp | Fluxos completos de criar/editar/concluir/excluir/restaurar e exportar aprovados por papel |
| 5 — DM AGRO IA inicial | Ditado acionado por botão, interpretação local assistida, consulta e prévia de confirmação | Nenhuma gravação sem confirmação; ambiguidades e baixa confiança não causam ações; limite do recurso offline explícito |
| 6 — Homologação e entrega Beta | Feedback, identificação de versão, backup, documentação de privacidade e canal de atualização isolado | Testes no celular com dados autorizados, assinatura preservada e autorização específica para disponibilizar a versão |
| 7 — Preparação comercial | Painel web, licenças inativas, requisitos Play/LGPD e integrações | Escopo, custos, direitos de uso e obrigações aprovados antes de ativar qualquer serviço comercial |

A etapa 0 é a entrega atual. As etapas seguintes começam somente depois da apresentação deste diagnóstico/plano e da definição da próxima etapa com Diego. Não se trata de uma estimativa de conclusão em uma única atualização.

## Arquitetura proposta

Extrair gradualmente os fluxos da Activity para componentes de domínio e persistência, começando por testes de caracterização. Manter as telas atuais funcionando enquanto cada fluxo recebe uma implementação nova.

- **Identidade e sessão:** Firebase Authentication e uma autorização de empresa/frente validada no servidor. O papel recebido de um Intent ou escolhido no cadastro nunca concede acesso.
- **Domínio:** modelos/serviços para empresas, catálogo, mapas, estoque, manutenção, ocorrências, caminhões, relatórios e indicadores, sem depender de Activity.
- **Persistência:** Room para registros, vínculos, histórico e outbox; arquivos em pastas privadas por empresa/usuário. SharedPreferences continuam disponíveis pelo adaptador legado durante a migração.
- **Sincronização:** operações idempotentes, fila durável, comparação de revisão, tombstones e execução com WorkManager. Conflitos não podem ser resolvidos por sobrescrita silenciosa.
- **Interface:** telas por módulo, estado explícito de carregamento/salvamento/sincronização e componentes comuns de formulário, lista, cartão e compartilhamento.
- **Integrações:** Worker e Firestore com autorização por empresa; fornecedores e custos separados da lógica operacional. Chave administrativa somente em segredo do serviço.

Não é necessário criar vários módulos Gradle imediatamente. Primeiro separar responsabilidades dentro do projeto e medir o resultado; modularização física só quando trouxer benefício real.

## Multiempresa e cadastro dinâmico

Contrato de dados proposto, a validar no emulador antes de uso real:

```text
usuarios/{uid}                              identidade e preferências pessoais
empresas/{empresaId}                        cadastro e estado da empresa
empresas/{empresaId}/membros/{uid}          papel, aprovação e frentes permitidas
empresas/{empresaId}/propriedades/{id}     propriedades da empresa
empresas/{empresaId}/frentes/{frenteId}    catálogo de frentes
empresas/{empresaId}/turnos/{turnoId}      horários, vigência e fuso
empresas/{empresaId}/equipamentos/{id}    identificação e vínculo de frente
empresas/{empresaId}/registros/{id}       módulo, frente, turno e estado tipados
empresas/{empresaId}/auditoria/{id}       eventos imutáveis autorizados
```

O contrato final pode separar coleções por módulo. Em qualquer formato, a empresa da URL deve coincidir com a dos dados, e toda leitura/lista/escrita precisa validar o vínculo do usuário. As chaves locais, sessões e filas também precisam incluir empresa, não apenas UID e `frente_turno`.

Os campos controlados de um registro incluem `id`, `tipo`, `empresaId`, `frenteId`, `turnoId`, `autorUid`, `estado`, `revisao`, timestamps de servidor e metadados de exclusão. As transições de estado e autoria deixam de depender de JSON opaco. Campos de negócio continuam preservando o texto original do usuário.

As nove frentes solicitadas serão **dados iniciais de uma empresa de teste**, não uma lista permanente dentro do Android, das regras e do Worker. Cadastro, edição, arquivamento e consulta respeitam as permissões. Arquivar frente/equipamento não apaga seus registros históricos.

A, B e C serão configuração inicial: A 07:00–15:20, B 15:20–23:20 e C 23:20–07:00. Outros turnos terão IDs estáveis, horários e fuso da empresa. A data operacional do turno que cruza a meia-noite deve ser explícita e testada; não depender apenas do relógio/fuso do celular.

A matrícula é identificador textual e preserva zeros. A estratégia de login deve acomodar números iguais em empresas diferentes e manter as contas antigas: vínculo de identidade/matrícula por empresa, com resolução controlada no servidor e compatibilidade com o endereço técnico legado. Não renomear contas em lote nem armazenar a senha.

## Matriz de permissões pretendida

| Papel | Abrangência | Aprovações | Pendências/ocorrências |
| --- | --- | --- | --- |
| Administrador geral — Diego | Plataforma, com acesso administrativo explícito | Administradores de empresa e suporte autorizado | Administração e recuperação conforme política auditada |
| Administrador de empresa | Somente a própria empresa | Supervisores e demais membros da própria empresa | Gerencia operações da empresa, sem alcançar outra |
| Supervisor | Própria frente, incluindo os turnos dessa frente | Operadores/tratoristas/ajudantes da própria frente; nunca outros supervisores | Cria/edita, conclui com confirmação, exclui/restaura conforme autorização |
| Operador/tratorista/ajudante | Mapas e ferramentas liberadas da própria frente | Nenhuma | Registra e edita os próprios registros; não conclui, exclui ou restaura |

Preservar a exceção já solicitada para o dono excluir seus mapas pessoais; isso não concede exclusão de mapas compartilhados nem de registros da equipe. Uma pintura excluída não deve apagar o PDF original.

As ações de conclusão, reabertura, exclusão e restauração precisam ser definidas como comandos autorizados, com confirmação e auditoria. Esconder o botão é insuficiente: as regras/serviço devem rejeitar a tentativa pelo mesmo usuário.

As aprovações mudam em relação à base antiga. Convites e empresas/frentes válidas limitam o cadastro; escolher Supervisor não dá acesso automático. Perfis aprovados legados só entram na empresa correta após revisão da atribuição, sem desativar usuários em produção por surpresa.

## Migração, offline e recuperação

1. Catalogar schemas e backups legados; verificar contagens, IDs, revisões, referências e hashes dos anexos, usando somente dados fictícios ou expressamente autorizados.
2. Criar um adaptador de leitura da base atual, sem alterar os arquivos antigos. Os leitores devem reconhecer a versão do schema e recusar migração incompatível sem apagar dados.
3. Simular a atribuição da base antiga a uma empresa/frente, apresentada ao administrador. Nunca adivinhar a empresa por nome parecido de frente nem compartilhar mapas pessoais automaticamente.
4. Drenar/contabilizar filas pendentes e gerar um backup validado. Copiar com IDs preservados, tabela de correspondência e checkpoints; repetir a operação não pode duplicar dados.
5. Homologar restauração em outra instalação de teste, incluindo fotos, mapas, pinturas, históricos e registros excluídos. Exportação e retenção de backups precisam de proteção de dados definida.
6. Planejar corte por empresa/frente e compatibilidade com aplicativos antigos. Evitar escrita dupla cega; definir qual schema é autoritativo e como o legado fica somente para leitura no corte aprovado.
7. Prever recuperação por versão de correção com código maior e mesma assinatura. Instalar um APK antigo com código menor não é uma estratégia normal de rollback Android. Não descartar gravações feitas depois do backup ao reverter uma configuração.

Offline deve funcionar também após encerrar o processo, para usuários previamente aprovados e conforme uma política de autorização local protegida. Contas nunca aprovadas não ganham acesso por falta de rede. A janela de revalidação e o tratamento de bloqueio/revogação precisam ser definidos; revogação instantânea não pode ser garantida enquanto o aparelho está desconectado. Isso é uma política de segurança, não um prazo de encerramento da Beta.

As operações terão ID único, revisão-base, horário informado e recebimento no servidor. Repetir um envio, trocar de rede ou reiniciar durante a sincronização não pode duplicar caminhões, pendências ou notificações. Campos não conflitantes podem ser mesclados somente por regra explícita; os demais preservam as duas versões para decisão.

**Anexos:** a Beta gratuita inicialmente preserva arquivos locais e compartilhamento manual. A sincronização automática de fotos exige avaliar uma alternativa realmente gratuita, sem cartão, com cotas e comportamento de falha definidos. Não habilitar Storage/Blaze, R2, S3 ou outro serviço faturável para cumprir essa promessa sem autorização. A documentação deve distinguir registros sincronizados de anexos apenas locais.

## Experiência e painel profissional

- Nome DM AGRO; subtítulo Gestão Operacional Agrícola; autoria Desenvolvido por Diego Machado; versão instalada e identificação Beta gratuita.
- Verde-escuro, branco e detalhes verde-claro, com temas claro/escuro. Aplicar o mesmo padrão às ferramentas, diálogos, formulários e exportações.
- Alvos de toque de pelo menos 48 dp, textos grandes, contraste, TalkBack e uso com fontes ampliadas. Evitar menus longos, ações repetidas e campos técnicos na navegação.
- Módulos solicitados: Mapas, Estoque, Relatórios, Equipamentos, Pendências, Ocorrências, Caminhões, Equipes, Calendário, Indicadores, DM AGRO IA e Configurações. Equipamentos e Pendências reutilizam o mesmo cadastro, sem duplicação de dados.
- Preservar chuva, peças, preventiva, passagem de turno, previsão, versículo e favoritos como recursos acessíveis, sem criar botões redundantes no painel.

Indicadores precisam de contrato de cálculo e rastreabilidade:

| Indicador | Regra inicial proposta |
| --- | --- |
| Área colhida | Pinturas classificadas explicitamente como colhidas, vinculadas ao mapa/lote/período, com união de sobreposições e indicação de aproximação; não inferir apenas de cor em foto |
| Estoque restante | Área restante informada nos lotes ativos; vínculo e período explícitos, sem subtração automática não autorizada |
| Equipamentos | Cadastros ativos da frente/empresa selecionada, sem contar pendências como equipamentos |
| Pendências abertas | Registros ativos pendentes, sem peças, equipamentos ou itens arquivados misturados |
| Produtividade | Valor informado; média ponderada por área somente quando os dados numéricos forem válidos e comparáveis |
| Frente/turno | Catálogo da empresa e período operacional, incluindo virada de dia |
| Notificações | Avisos autorizados da frente do usuário, com estados de envio/recebimento sem confundir permissão Android com entrega |

Definir período/safra de referência para não somar a mesma área de ciclos diferentes; não inventar um vínculo quando o dado legado não o informa. Valores ausentes ou não numéricos mostram essa condição, em vez de zero inventado. Preservar texto livre e, quando possível, extrair um valor normalizado separado para cálculo; não forçar o operador a seguir uma sintaxe para satisfazer o gráfico.

## Ferramentas e compartilhamento

- **Mapas:** manter o PDF original e a georreferência; pinturas/metadados em camada separada. Preservar zoom/rotação/GPS, pontos manuais, edição e exclusão. Mostrar ha/m² e total. PDF e imagem compartilhados devem conter a pintura aplicada, sem modificar as coordenadas do original. Validar múltiplos mapas e zonas com PDFs autorizados e fixtures sintéticas.
A exportação do mapa pintado precisa de testes específicos dos metadados GeoPDF (`MediaBox`, `BBox`, `Measure`, `GPTS`, `LPTS` e projeção) e pontos de controle antes/depois. Gerar um PDF novo com uma imagem não preserva automaticamente a georreferência. A biblioteca/estratégia de escrita deve ter licença compatível com uso comercial; não apresentar uma exportação apenas visual como GeoPDF equivalente.

- **Estoque:** lote, talhões, área, produtividade, observações, data/turno e mapa associado; entrada livre e edição preservada no histórico. Nenhum campo obrigatório de estimativa de toneladas. No WhatsApp, manter mensagem + foto/mapa original ou pintado, inclusive vários lotes com/sem foto. PDF será uma opção separada.
- **Relatórios:** restaurar o módulo por turno com relato livre, fotos, pendências e ocorrências. PDF e mensagem/imagens com empresa, frente, turno, período e responsável, usando dados selecionados pelo usuário.
- **Manutenção:** várias pendências por equipamento, histórico e imagens com foto e descrição juntas. Somente gestor autorizado conclui/exclui; confirmação antes de concluir; autor mantém edição dos próprios registros. Exclusão é arquivamento recuperável, não perda de histórico.
- **Ocorrências:** sem O.S. obrigatória; texto e fotos, posição com precisão/data da leitura, acompanhamento e exportação.
- **Caminhões:** número e horários preservados, datas explícitas, permanência e filtros por período. A virada do turno renova a lista ativa e mantém o histórico. Voz passa pela mesma confirmação do registro digitado.
- **WhatsApp:** usar o compartilhamento Android escolhido pela pessoa; não enviar mensagem a grupos/contatos automaticamente. Manter prévia e botão Enviar.

## DM AGRO IA: escopo seguro e gratuito inicial

Primeiro entregar um assistente de preenchimento e consulta, sem prometer compreensão perfeita de fala, interpretação agronômica ou reconhecimento automático de curvas do mapa.

1. Acionamento explícito por botão; sem escuta contínua ao abrir o aplicativo.
2. Reconhecimento no dispositivo quando o Android e os pacotes de idioma suportarem. Identificar disponibilidade real; não afirmar que qualquer celular terá voz offline. Digitação continua plenamente funcional.
3. Interpretador local de intenções e entidades: equipamento, lote, área, descrição, caminhão, chegada/saída e período. Consultas usam apenas os dados autorizados daquela empresa/frente.
4. Mostrar o que foi entendido, campos faltantes, ambiguidade e confiança. Exigir escolha quando dois registros forem possíveis.
5. Confirmar, corrigir ou cancelar antes de salvar; executar o mesmo comando de domínio e a mesma autorização usados pela interface manual. A IA não tem permissões adicionais.
6. Avaliar linguagem do campo e ruído com exemplos de teste e colegas autorizados. Áudio não precisa ser guardado nem enviado para treinamento. Nenhuma API paga ou chave de provedor entra no APK.

Gerar resumo ou identificar recorrência exige citar os registros usados e separar constatação de sugestão. Uma recomendação não conclui manutenção, não autoriza gasto e não altera o estoque automaticamente.

## Distribuição, comercial e uso somente pelo celular

- Continuar com commits e Actions. Consultar resultados e baixar relatórios pelo navegador do Android; não exigir Android Studio local.
- Recuperar a capacidade de assinatura do CI configurando **a chave já existente** em segredo/ambiente protegido. Não colocar arquivo de chave em artefatos públicos nem trocar certificado para fazer o build ficar verde.
- Antes de distribuir Beta: canal opt-in, feed separado do estável e release prerelease que não substitua `latest`. A lógica atual de preparação/publicação precisa de adaptação e testes para esse canal.
- `applicationId` e certificado permanecem os mesmos. Isso permite atualizar por cima, mas **não permite instalar Beta e estável lado a lado**. A troca de canal requer planejamento de versão e proteção da base local; não criar sufixo `.beta` por conta própria.
- Coordenar `versionCode` crescente entre canais. VersionName/identificação Beta, retorno ao canal estável e disponibilidade para cada usuário precisam de testes; não publicar um APK de código menor como recuperação.
- Adicionar botão de sugestão/erro com versão e diagnóstico mínimo revisável. Não anexar mapa, áudio, matrícula, nome ou banco automaticamente.
- Preparar termos da Beta, política de privacidade, direitos dos titulares, retenção/exclusão, contatos, atribuições/licenças e responsabilidade pelos dados de cada empresa. Não declarar conformidade LGPD somente por criar um documento.
- Para Google Play, validar requisitos vigentes de target SDK, assinatura, AAB/release, privacidade e conta do desenvolvedor. Não pagar cadastro nem publicar sem autorização.
- Painel web e licenças futuras usam o mesmo isolamento e autorização de empresa. Cobranças, anúncios, cartão e encerramento por prazo permanecem desativados na Beta.

## Testes obrigatórios por entrega

1. Compatibilidade: instalar sobre a base com a mesma assinatura, sem desinstalação, e conferir dados/pinturas/backups.
2. Segurança: outra empresa/frente, perfil pendente, operador tentando concluir/excluir, supervisor tentando aprovar supervisor, alteração de UID e requisições diretas fora da interface.
3. Dados: edição persistente, criação repetida, conflito, exclusão/restauração, queda de rede/processo e filas antigas.
4. Operação: limites e virada dos turnos, entrada/saída de caminhão, estoque livre e datas/fusos.
5. Mapa: rotação/zoom, coordenadas, precisão/perda/retorno do GPS, áreas sobrepostas, PDF sem georreferência reconhecida e exportação pintada.
6. Compartilhamento: texto, uma/várias fotos, sem foto, PDF e WhatsApp no Android, sem fechar o aplicativo.
7. Interface: claro/escuro, tela pequena, fonte grande, TalkBack e feedback de salvamento.
8. IA: exemplos ambíguos, negação/cancelamento, números parecidos, confirmação, ausência de voz offline e tentativa de ultrapassar permissões.

Cada commit deve declarar o que mudou, o que foi testado e os limites. Cada entrega para os colegas requer autorização de distribuição e evidências, não apenas uma lista de funcionalidades planejadas.
