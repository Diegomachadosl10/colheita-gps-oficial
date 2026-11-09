#!/usr/bin/env python3
"""Restore only from a protected repository secret, never from the public APK artifact."""
import base64
import os
from pathlib import Path

value = os.environ.get('ANDROID_UPDATE_KEYSTORE_BASE64', '')
if not value:
    raise SystemExit('Configure o segredo ANDROID_UPDATE_KEYSTORE_BASE64 com a chave permanente já usada nesta conversa. Não gere outra chave: ela impediria atualizar os aplicativos instalados.')
raw = base64.b64decode(value, validate=True)
if not 1000 < len(raw) < 100000:
    raise SystemExit('Arquivo de assinatura inválido')
target = Path.home()/'.android'/'debug.keystore'
target.parent.mkdir(parents=True, exist_ok=True)
target.write_bytes(raw)
target.chmod(0o600)
print('Chave de assinatura restaurada do segredo protegido.')
