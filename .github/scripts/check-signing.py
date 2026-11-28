#!/usr/bin/env python3
"""Inspect the protected signing secret without producing an APK or exposing credentials."""
import base64
import os
from pathlib import Path
import re
import subprocess
import tempfile

MESSAGES = {
    'missing': 'A assinatura permanente ainda não está disponível no segredo protegido do repositório.',
    'invalid': 'O segredo protegido não contém um arquivo de assinatura válido.',
    'unreadable': 'Não foi possível conferir o arquivo de assinatura com a configuração atual.',
    'different_certificate': 'O certificado não corresponde aos aplicativos instalados. A publicação permanece bloqueada.',
    'certificate_verified': 'O segredo está disponível e o certificado corresponde ao certificado permanente fixado.',
}


def inspect_signing(value, expected, runner=None):
    if not value:
        return 'missing'
    if not re.fullmatch(r'[0-9a-f]{64}', expected) or len(value) > 150000:
        return 'invalid'
    try:
        raw = base64.b64decode(value, validate=True)
        if not 1000 < len(raw) < 100000:
            return 'invalid'
    except (ValueError, TypeError):
        return 'invalid'
    try:
        with tempfile.TemporaryDirectory(prefix='dm-agro-signing-check-') as directory:
            path = Path(directory)/'signing.keystore'
            path.write_bytes(raw)
            path.chmod(0o600)
            child = os.environ.copy()
            child.pop('ANDROID_UPDATE_KEYSTORE_BASE64', None)
            # The current Android debug signing configuration uses the SDK's standard credentials.
            child['DM_AGRO_KEYSTORE_CHECK_PASSWORD'] = 'android'
            result = (runner or subprocess.run)([
                'keytool', '-J-Duser.language=en', '-J-Duser.country=US', '-list', '-v',
                '-keystore', str(path), '-alias', 'androiddebugkey',
                '-storepass:env', 'DM_AGRO_KEYSTORE_CHECK_PASSWORD',
            ], env=child, input='', text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30)
            if result.returncode != 0:
                return 'unreadable'
            certs = re.findall(r'SHA256:\s*([0-9A-Fa-f:]+)', result.stdout)
            if 'Entry type: PrivateKeyEntry' not in result.stdout or len(certs) != 1:
                return 'unreadable'
            return 'certificate_verified' if certs[0].replace(':', '').lower() == expected else 'different_certificate'
    except Exception:
        # Never include subprocess output, input credentials or exceptions in diagnostics.
        return 'unreadable'


def main():
    expected = (Path(__file__).resolve().parents[2]/'scripts/android-signing.sha256').read_text().strip()
    status = inspect_signing(os.environ.get('ANDROID_UPDATE_KEYSTORE_BASE64', ''), expected)
    message = MESSAGES[status]
    print(message)
    summary = os.environ.get('GITHUB_STEP_SUMMARY')
    if summary:
        with open(summary, 'a') as output:
            output.write(f'## Assinatura permanente do DM AGRO\n\n{message}\n\n')
            output.write('Este diagnóstico não gera nem publica APK, não imprime a chave e não substitui a validação da assinatura do APK final.\n')
    return 0 if status == 'certificate_verified' else 1


if __name__ == '__main__':
    raise SystemExit(main())
