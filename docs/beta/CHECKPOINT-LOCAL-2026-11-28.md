# Cópia privada e recuperação local antes da migração

`StableLocalBundle` acrescenta a etapa de recuperação local ao inventário somente para leitura. É um componente interno da branch Beta: não está ligado à inicialização, não importa dados automaticamente e não oferece outro APK. A versão estável continua separada.

## Comportamento implementado

- Captura o inventário autorizado de UID/equipe e recusa origem inconsistente, fotos ausentes ou referências inválidas. Preserva as strings originais, IDs, arquivos, PDFs, pinturas, rascunhos, baselines, alterações pendentes e conflitos já selecionados pelo inventário.
- Cria ZIP e comprovante em `noBackupFilesDir/beta_transition`, fora de exportações públicas e do backup automático do Android. Pastas e arquivos são restritos ao proprietário. O caminho usa hash do UID; o conteúdo continua privado e não é enviado ao servidor.
- Confere tamanho e SHA-256 de cada arquivo durante a cópia e na leitura do ZIP. Recaptura a origem antes de concluir e recusa alterações detectadas. Só finaliza o arquivo temporário depois de conferir o inventário e sincronizar a gravação no disco.
- O comprovante privado vincula usuário, papel, equipe, fingerprint da origem e checksum do ZIP. Uma nova instância consegue reabrir a cópia mesmo que os arquivos originais tenham desaparecido. Os checksums detectam corrupção; não são assinatura/autenticidade para importação de arquivos externos.
- Repetir a cópia de uma origem idêntica reutiliza o checkpoint verificado. Uma origem alterada gera outro checkpoint e mantém o anterior. Arquivos existentes divergentes são recusados, não substituídos.
- A recuperação exige novamente acesso aprovado à equipe e o mesmo UID/papel. Recusa arquivo externo à pasta esperada, link simbólico, comprovante divergente, schema desconhecido, nomes repetidos/inesperados, travessia de diretórios e divergência de tamanho/hash.
- Restaura em uma pasta nova de quarentena, com preferências e fila em estruturas imutáveis para inspeção. Mantém tipos distintos de preferências, incluindo `Int`, `Long`, conjuntos e representação exata de `Float`. Falhas removem apenas a restauração parcial.

O limite por checkpoint é 1 GiB de anexos, 10 mil arquivos e 32 MiB de índice. Exceder limite interrompe a criação; não trunca dados. Não há limpeza automática dos checkpoints anteriores.

## Alcance e pendências

Isso é recuperação local **em quarentena**, não ativação de uma base restaurada. Não altera preferências estáveis ou operacionais Beta, não reescreve caminhos, não executa filas nem reproduz notificações. O formato é próprio para a transição; o importador do ZIP antigo da interface não passa a aceitá-lo automaticamente.

A pasta privada não é uma cópia externa contra perda do aparelho/desinstalação. Exportação protegida, recuperação em outro aparelho, política de retenção e ativação transacional ainda exigem implementação/homologação. As referências originais dos arquivos permanecem no snapshot para conferência; a futura ativação precisa convertê-las com o destino correto e validar o servidor/corte antes de instalar qualquer preferência ou baseline.

A recaptura ajuda a detectar mudanças, mas não substitui a suspensão coordenada dos escritores, o corte dos clientes antigos e a conferência do estado do servidor. Não considerar o checkpoint como autorização para migrar ou publicar o Beta.

## Validação preparada

Os testes adicionados cobrem cópia/recuperação com mapa e foto marcada/original, arquivo pendente e conflito, reabertura sem a origem, repetição, alteração durante cópia, origem inválida, usuário/papel/equipe sem acesso, mapas pessoais, tipos/imutabilidade, corrupção e ZIP malformado/link simbólico.

Um teste instrumentado usa pastas e preferências com prefixo exclusivo e dados sintéticos para exercitar gravação/sincronização no disco, reabertura e restauração em Android nativo, sem tocar no cadastro, dados operacionais ou autenticação do aplicativo. Resultados serão registrados após execução no Actions; preparar o teste não prova sua aprovação.

## Resultado executado

[Actions 46172120333](https://github.com/Diegomachadosl10/colheita-gps-oficial/actions/runs/46172120333), código `e768a1ba7dec14522bfa681529702360b1b67a61`: quatro jobs aprovados. O XML do artefato de auditoria confirma **393 testes Android**, zero falhas/erros/ignorados, e lint sem erros, com 136 avisos. Os arquivos novos não apresentam ocorrências nesse relatório de lint. São 18 casos novos, com os nove cenários executados nas APIs 28 e 35.

O log nativo confirma início e fim de **quatro testes em Android API 35**, com build aprovado. O checkpoint foi reaberto e restaurado nesse emulador, junto com as verificações anteriores de PDF/compartilhamento, Keystore e GPS sem redes. Serviços/publicador e regras também passaram. Nenhum APK, regra, dado real ou serviço foi publicado por essa validação.
