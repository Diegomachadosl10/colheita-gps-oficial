#!/usr/bin/env python3
"""Upload all validated files to a draft, then publish it. Only the working branch is accepted."""
import json
import os
import subprocess
from pathlib import Path

repo='Diegomachadosl10/colheita-gps-oficial'
if os.environ.get('GITHUB_REF') != 'refs/heads/dm-agro-login' or os.environ.get('GITHUB_EVENT_NAME') != 'workflow_dispatch':
    raise SystemExit('A publicação exige execução manual na ramificação dm-agro-login.')
folder=Path('release')
info=json.loads((folder/'dm-agro-update.json').read_text())
tag='v'+info['versionName']
apk=folder/f"DM-AGRO-{info['versionName']}.apk"
assert apk.is_file() and (folder/'SHA256.txt').is_file()
query=subprocess.run(['gh','api',f'repos/{repo}/releases/tags/{tag}'],capture_output=True,text=True)
if query.returncode == 0:
    release=json.loads(query.stdout)
    if not release['draft'] or release['target_commitish'] != os.environ['GITHUB_SHA']:
        raise SystemExit('Essa versão já existe. Aumente versionCode e versionName antes de publicar outra atualização.')
else:
    if '404' not in query.stderr:
        raise SystemExit(query.stderr)
    subprocess.run(['gh','release','create',tag,'--repo',repo,'--draft','--target',os.environ['GITHUB_SHA'],
        '--title',f"DM AGRO {info['versionName']}",'--notes-file',str(folder/'release-notes.md')],check=True)
subprocess.run(['gh','release','upload',tag,str(apk),str(folder/'dm-agro-update.json'),str(folder/'SHA256.txt'),
    '--repo',repo,'--clobber'],check=True)
subprocess.run(['gh','release','edit',tag,'--repo',repo,'--draft=false','--latest'],check=True)
