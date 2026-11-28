# Diagnóstico da assinatura permanente

O ambiente de execução de 28/11 retomou sem o checkout e sem o arquivo privado que existia no workspace anterior. Os commits foram recuperados do GitHub. A ausência do arquivo neste ambiente não prova perda de todas as cópias da chave; a disponibilidade no segredo protegido precisa ser verificada separadamente.

O workflow `signing-readiness.yml` é independente da auditoria Beta e não gera APK nem faz publicação. Executa somente na branch de desenvolvimento do repositório autorizado, com permissão `contents: read`, sem artefatos. Testa o diagnóstico com dados fictícios antes de consultar o segredo `ANDROID_UPDATE_KEYSTORE_BASE64`.

O helper `.github/scripts/check-signing.py` devolve somente um estado conhecido: ausente, inválido, não legível, certificado diferente ou certificado conferido. Quando há segredo, decodifica em pasta temporária privada, aplica modo 0600 e consulta somente o certificado da entrada usada pelo build atual. A saída do subprocesso e eventuais exceções não são publicadas. O segredo original não é repassado ao subprocesso e o diretório temporário é removido ao sair.

Os quatro testes do helper verificam rejeição de entradas inválidas/certificados diferentes, exigência de uma entrada com chave privada, permissão/limpeza do temporário, ausência do segredo no subprocesso e supressão de saídas/exceções sensíveis. Foram aprovados localmente; a disponibilidade real será registrada após execução do workflow.

Uma conferência positiva do certificado não substitui assinar e verificar o APK final. Uma conferência negativa mantém a distribuição bloqueada: recuperar a chave **já existente**, sem gerar outra assinatura e sem solicitar seu envio pelo chat. Não tratar APK assinado com chave descartável do runner como atualização compatível.
