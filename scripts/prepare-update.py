#!/usr/bin/env python3
"""Prepare an immutable GitHub release from the verified APK. No Firebase deployment or billing."""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
from pathlib import Path

REPO = 'Diegomachadosl10/colheita-gps-oficial'
PACKAGE = 'com.colheitagps.app'

def prepare(apk, output, build_tools, notes):
    apk, output, build_tools = Path(apk), Path(output), Path(build_tools)
    badging = subprocess.check_output([str(build_tools/'aapt'), 'dump', 'badging', str(apk)], text=True)
    package = re.search(r"package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", badging)
    if not package or package[1] != PACKAGE:
        raise ValueError('APK de outro aplicativo')
    version = package[3]
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', version):
        raise ValueError('Use uma versão numérica, por exemplo 1.2.0')
    signatures = subprocess.check_output([str(build_tools/'apksigner'), 'verify', '--print-certs', str(apk)], text=True)
    certs = re.findall(r'Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]+)', signatures)
    expected = (Path(__file__).parent/'android-signing.sha256').read_text().strip().lower()
    if len(certs) != 1 or certs[0].lower() != expected:
        raise ValueError('Assinatura diferente dos APKs já instalados. A publicação foi bloqueada.')
    min_sdk = int(re.search(r"sdkVersion:'([0-9]+)'", badging)[1])
    tag = 'v' + version
    name = f'DM-AGRO-{version}.apk'
    with apk.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    info = {'schemaVersion':1, 'packageName':PACKAGE, 'versionCode':int(package[2]), 'versionName':version,
        'minSdk':min_sdk, 'apkUrl':f'https://github.com/{REPO}/releases/download/{tag}/{name}',
        'sha256':digest, 'sizeBytes':apk.stat().st_size, 'changes':notes.strip()[:4000]}
    if not 0 < info['sizeBytes'] <= 250*1024*1024:
        raise ValueError('APK acima do limite de atualização')
    output.mkdir(parents=True, exist_ok=True)
    shutil.copy2(apk, output/name)
    (output/'dm-agro-update.json').write_text(json.dumps(info, ensure_ascii=False, indent=2)+'\n')
    (output/'SHA256.txt').write_text(f'{digest}  {name}\n')
    (output/'release-notes.md').write_text(notes.strip()+'\n')
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'],'a') as f:
            f.write(f'tag={tag}\nversion={version}\n')
    return info

if __name__ == '__main__':
    p=argparse.ArgumentParser()
    p.add_argument('--apk', required=True)
    p.add_argument('--output', required=True)
    p.add_argument('--build-tools', required=True)
    p.add_argument('--notes-file')
    args=p.parse_args()
    notes=Path(args.notes_file).read_text() if args.notes_file else os.environ.get('UPDATE_NOTES','Melhorias e correções do DM AGRO.')
    info=prepare(args.apk,args.output,args.build_tools,notes)
    print('Atualização preparada:', info['versionName'])
