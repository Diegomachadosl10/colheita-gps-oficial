# Diagnóstico da assinatura permanente

O ambiente de execução de 28/11 retomou sem o checkout e sem o arquivo privado que existia no workspace anterior. Os commits foram recuperados do GitHub. A ausência do arquivo neste ambiente não prova perda de todas as cópias da chave; a disponibilidade no segredo protegido precisa ser verificada separadamente.

O workflow `signing-readiness.yml` é independente da auditoria Beta e não gera APK nem faz publicação. Executa somente na branch de desenvolvimento do repositório autorizado, com permissão `contents: read`, sem artefatos. Testa o diagnóstico com dados fictícios antes de consultar o segredo `ANDROID_UPDATE_KEYSTORE_BASE64`.

O helper `.github/scripts/check-signing.py` devolve somente um estado conhecido: ausente, inválido, não legível, certificado diferente ou certificado conferido. Quando há segredo, decodifica em pasta temporária privada, aplica modo 0600 e consulta somente o certificado da entrada usada pelo build atual. A saída do subprocesso e eventuais exceções não são publicadas. O segredo original não é repassado ao subprocesso e o diretório temporário é removido ao sair.

Os quatro testes do helper verificam rejeição de entradas inválidas/certificados diferentes, exigência de uma entrada com chave privada, permissão/limpeza do temporário, ausência do segredo no subprocesso e supressão de saídas/exceções sensíveis. Foram aprovados localmente; a disponibilidade real será registrada após execução do workflow.

Uma conferência positiva do certificado não substitui assinar e verificar o APK final. Uma conferência negativa mantém a distribuição bloqueada: recuperar a chave **já existente**, sem gerar outra assinatura e sem solicitar seu envio pelo chat. Não tratar APK assinado com chave descartável do runner como atualização compatível.

## Resultado confirmado em 28/11

[Actions 46172841688](https://github.com/Diegomachadosl10/colheita-gps-oficial/actions/runs/46172841688), código `d92b7da692faa0c2c1379f8a7fb7d8e2bd093051`: os quatro testes do diagnóstico passaram. O passo de disponibilidade devolveu **ausente**, interrompendo o job com código 1. Portanto, a assinatura não está disponível ao workflow pelo segredo esperado; isso não é falha de compilação Android. A auditoria de aplicativo/checkpoint no [run 46172120333](https://github.com/Diegomachadosl10/colheita-gps-oficial/actions/runs/46172120333) passou separadamente, nos quatro jobs.

Diego informou não saber onde guardou uma cópia. Foram consultadas todas as branches/tags remotas disponíveis sem trocar o checkout Beta. A busca por nomes de arquivos de keystore no histórico alcançável não encontrou cópia. Uma inspeção de 786 blobs Git de até 300 KiB também não identificou os cabeçalhos de keystore pesquisados, binários ou codificados em blocos Base64 contíguos. Isso não certifica ausência de qualquer formato/representação de segredo.

Também foram conferidos 30 ZIPs de código das releases e o APK atual 2.2.8: nenhum candidato foi identificado pelos nomes/extensões e formatos pesquisados. Os arquivos foram lidos em pasta privada fora do repositório, sem publicar conteúdo. Os nomes dos artefatos e os metadados dos caches do Actions não indicaram backup específico da assinatura; não se concluiu que todo conteúdo desses caches foi inspecionado.

O arquivo privado que existia em `/workspace/dm-agro-build-tools/assinatura-permanente/dm-agro.keystore` e o keystore de debug padrão não estão presentes neste ambiente. A API do ambiente o informa conectado/em execução; isso não prova retenção dos arquivos do runtime anterior. Não afirmar perda definitiva de todas as cópias, nem tentar recriar a chave a partir do certificado público do APK.

O próximo passo de recuperação é localizar uma cópia original em arquivos privados do titular ou recuperar o workspace anterior pelo provedor. Se for encontrado um arquivo com nome compatível, conferir seu certificado contra `scripts/android-signing.sha256` antes de considerá-lo a assinatura correta. Manter a chave fora do chat, Git, logs e artefatos públicos. A versão estável permanece disponível e nenhuma atualização substituta foi distribuída.
