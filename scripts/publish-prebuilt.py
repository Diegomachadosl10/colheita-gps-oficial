#!/usr/bin/env python3
"""Publish a pre-signed, hash-checked package on the authorized working branch.

The runner's release upload uses its own short-lived GitHub token. No signing key
is copied to the repository or required to publish an APK that is already signed.
"""
import hashlib
import base64
import json
import os
import re
import subprocess
from pathlib import Path

REPO = 'Diegomachadosl10/colheita-gps-oficial'


def checked_files(folder, download_blobs=False):
    meta = json.loads((folder / 'publication.json').read_text())
    version = meta['version']
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', version):
        raise ValueError('Versão inválida')
    if not re.fullmatch(r'[0-9a-f]{40}', meta['sourceCommit']):
        raise ValueError('Commit inválido')
    expected = {f'DM-AGRO-{version}.apk', 'dm-agro-update.json', 'SHA256.txt',
                f'DM-AGRO-{version}-codigo.zip', f'DM-AGRO-{version}-dm-agro-login.bundle'}
    if set(meta['files']) != expected:
        raise ValueError('O pacote contém arquivos não autorizados')
    for name, info in meta['files'].items():
        path = folder / name
        if not path.exists() and download_blobs:
            blob = info.get('gitBlob', '')
            if not re.fullmatch(r'[0-9a-f]{40}', blob) or not 0 < info['size'] <= 100 * 1024 * 1024:
                raise ValueError(f'Objeto Git ou tamanho inválido: {name}')
            payload = api(f'repos/{REPO}/git/blobs/{blob}')
            if payload.get('encoding') != 'base64' or payload.get('size') != info['size']:
                raise ValueError(f'Objeto Git incompatível: {name}')
            data = base64.b64decode(''.join(payload['content'].split()), validate=True)
            git_hash = hashlib.sha1(f'blob {len(data)}\0'.encode() + data).hexdigest()
            if git_hash != blob or len(data) != info['size'] or hashlib.sha256(data).hexdigest() != info['sha256']:
                raise ValueError(f'Conteúdo do objeto Git diferente: {name}')
            path.write_bytes(data)
        if not path.is_file() or path.stat().st_size != info['size']:
            raise ValueError(f'Tamanho diferente: {name}')
        if hashlib.sha256(path.read_bytes()).hexdigest() != info['sha256']:
            raise ValueError(f'SHA-256 diferente: {name}')
    return meta


def api(endpoint):
    return json.loads(subprocess.check_output(['gh', 'api', endpoint], text=True))


def main():
    if os.environ.get('GITHUB_REPOSITORY') != REPO or os.environ.get('GITHUB_REF') != 'refs/heads/dm-agro-login' or os.environ.get('GITHUB_EVENT_NAME') != 'push':
        raise SystemExit('Publicação permitida somente por push na branch dm-agro-login do projeto.')
    folder = Path('release-prebuilt')
    if not (folder / 'publication.json').is_file():
        print('Nenhum pacote pré-assinado para publicar.')
        return
    meta = checked_files(folder, download_blobs=True)
    source = meta['sourceCommit']
    subprocess.run(['git', 'merge-base', '--is-ancestor', source, 'HEAD'], check=True)
    # Publishing helpers may change, but the app must match the tested source commit.
    subprocess.run(['git', 'diff', '--exit-code', source, 'HEAD', '--',
                    'app', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties',
                    'backend', 'firebase', 'scripts/prepare-update.py', 'scripts/android-signing.sha256'], check=True)
    version = meta['version']
    subprocess.run(['python3', 'scripts/prepare-update.py', '--apk', str(folder / f'DM-AGRO-{version}.apk'),
                    '--output', '.verified-release', '--build-tools', os.environ['ANDROID_HOME'] + '/build-tools/35.0.0',
                    '--notes-file', str(folder / 'release-notes.md')], check=True)
    verified = json.loads(Path('.verified-release/dm-agro-update.json').read_text())
    expected = json.loads((folder / 'dm-agro-update.json').read_text())
    if verified != expected:
        raise SystemExit('O manifesto não corresponde ao APK/assinatura/notas conferidos.')
    release_id = int(meta['releaseId'])
    release = api(f'repos/{REPO}/releases/{release_id}')
    if release['tag_name'] != 'v' + version or release['target_commitish'] != source or release['prerelease']:
        raise SystemExit('Rascunho pertence a outra versão ou commit.')
    if not release['draft']:
        raise SystemExit('Esta versão já está publicada. Não sobrescrever uma atualização estável.')
    existing = {a['name']: a for a in release.get('assets', [])}
    missing = []
    for name, info in meta['files'].items():
        if name not in existing:
            missing.append(str(folder / name))
        elif existing[name]['size'] != info['size'] or existing[name].get('digest') != 'sha256:' + info['sha256']:
            raise SystemExit('Anexo existente diferente; nada será sobrescrito: ' + name)
    if set(existing) - set(meta['files']):
        raise SystemExit('Há anexos inesperados no rascunho.')
    if missing:
        subprocess.run(['gh', 'release', 'upload', 'v' + version, *missing, '--repo', REPO], check=True)
    release = api(f'repos/{REPO}/releases/{release_id}')
    assets = {a['name']: a for a in release['assets']}
    if set(assets) != set(meta['files']):
        raise SystemExit('Pacote incompleto; o rascunho não será publicado.')
    for name, info in meta['files'].items():
        if assets[name]['size'] != info['size'] or assets[name].get('digest') != 'sha256:' + info['sha256']:
            raise SystemExit('Falha ao conferir o anexo enviado: ' + name)
    subprocess.run(['gh', 'api', '--method', 'PATCH', f'repos/{REPO}/releases/{release_id}',
                    '-F', 'draft=false', '-F', 'prerelease=false', '-f', 'make_latest=true'],
                   check=True, stdout=subprocess.DEVNULL)
    latest = api(f'repos/{REPO}/releases/latest')
    if latest['id'] != release_id:
        raise SystemExit('Confira a versão mais recente antes de anunciar a atualização.')
    print('Atualização estável publicada e marcada como mais recente: ' + latest['html_url'])


if __name__ == '__main__':
    main()
