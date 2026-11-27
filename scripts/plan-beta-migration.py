#!/usr/bin/env python3
"""Offline, read-only conversion plan for a Firestore REST document snapshot.

Never contacts Firebase or writes a document. Snapshot/plan files contain private
operational data: keep them outside Git, with restricted permissions.
"""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path

PROJECT = 'dm-agro-c0e04'
PREFIX = f'projects/{PROJECT}/databases/(default)/documents/'
FRONTS = {'santo_inacio', 'santa_ines', 'itororo', 'cafeara', 'lupionopolis', 'colorado', 'sandovalina', 'jardim_olinda', 'itaguaje'}
GROUPS = {'pendencias', 'ocorrencias_campo', 'estoques_salvos', 'historico_estoque', 'relatorios_turno', 'caminhoes', 'movimentos', 'historico_os'}
PREFERENCES = {'mapas', 'pinturas', 'ordens_servico', 'talhoes_por_os'}


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(',', ':'), allow_nan=False).encode()).hexdigest()


def string(fields, key):
    value = fields.get(key, {}).get('stringValue')
    if not isinstance(value, str) or not value:
        raise ValueError(f'Missing string field: {key}')
    return value


def typed(value):
    if value is None:
        return {'nullValue': None}
    if isinstance(value, bool):
        return {'booleanValue': value}
    if isinstance(value, str):
        return {'stringValue': value}
    if isinstance(value, int):
        if not -(2**63) <= value < 2**63:
            raise ValueError('Integer outside Firestore range')
        return {'integerValue': str(value)}
    if isinstance(value, float):
        # Reject NaN/Infinity (JSON permits them by default in Python).
        json.dumps(value, allow_nan=False)
        return {'doubleValue': value}
    if isinstance(value, list):
        return {'arrayValue': {'values': [typed(v) for v in value]}}
    if isinstance(value, dict):
        return {'mapValue': {'fields': {k: typed(v) for k, v in value.items()}}}
    raise ValueError('Unsupported JSON value')


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate JSON property')
        result[key] = value
    return result


def plan(snapshot):
    if snapshot.get('schema') != 1 or snapshot.get('project') != PROJECT:
        raise ValueError('Unexpected snapshot/project')
    documents = snapshot.get('documents')
    if not isinstance(documents, list) or not documents:
        raise ValueError('Snapshot has no documents')
    seen, entries, attachments, profiles = set(), [], [], []
    for original in documents:
        source = original.get('name', '')
        if not source.startswith(PREFIX) or source in seen or not original.get('updateTime'):
            raise ValueError('Duplicate, unversioned or foreign document')
        seen.add(source)
        path = source[len(PREFIX):].split('/')
        if any(not p or p in ('.', '..') for p in path):
            raise ValueError('Invalid path')
        fields = copy.deepcopy(original.get('fields', {}))
        if len(path) == 2 and path[0] == 'usuarios':
            # No renaming identities, autoapprovals or credential mutations.
            if set(fields) & {'senha', 'password', 'private_key', 'client_secret', 'access_token', 'refresh_token', 'id_token'}:
                raise ValueError('Credential-like fields require separate secure remediation')
            profiles.append(path[1])
        elif len(path) == 4 and path[0] == 'equipes' and path[2] == 'registros':
            front, separator, shift = path[1].rpartition('_')
            if not separator or front not in FRONTS or shift not in ('A', 'B', 'C'):
                raise ValueError('Unknown team requires explicit catalogue mapping')
            if string(fields, 'equipeId') != path[1] or string(fields, 'frenteId') != front or string(fields, 'turno') != shift:
                raise ValueError('Team/path mismatch')
            kind, group, key = (string(fields, k) for k in ('tipo', 'grupo', 'chave'))
            for field in ('autorUid', 'editadoPor'):
                string(fields, field)
            revision = fields.get('versao', {}).get('integerValue', '')
            if not isinstance(revision, str) or not revision.isdecimal() or int(revision) >= 2**63:
                raise ValueError('Invalid source revision')
            expected = hashlib.sha256(f'{kind}|{group}|{key}'.encode()).hexdigest()
            if path[3] != expected or not key or len(key) >= 250:
                raise ValueError('Record identity mismatch')
            if 'formato' in fields or 'dados' in fields:
                raise ValueError('Source already contains a different schema')
            if kind == 'arquivo' and group == 'arquivos':
                # Storage is preserved in the source; do not enable paid upload/download.
                attachments.append({'source': source, 'sourceSha256': digest(original)})
                continue
            if kind == 'item' and group in GROUPS:
                payload = json.loads(string(fields, 'conteudo'), object_pairs_hook=unique_object)
                if not isinstance(payload, dict) or payload.get('id') != key:
                    raise ValueError('Payload identity mismatch')
                archived = payload.get('__arquivado', False)
                if not isinstance(archived, bool) or fields.get('excluido') != {'booleanValue': archived}:
                    raise ValueError('Inconsistent archive state needs review')
                if not isinstance(payload.get('concluida', False), bool):
                    raise ValueError('Inconsistent completion state needs review')
                fields['dados'] = typed(payload)
                del fields['conteudo']
            elif not (kind == 'preferencia' and group in PREFERENCES):
                raise ValueError('Unsupported record group/type')
            elif len(string(fields, 'conteudo').encode()) >= 750000:
                raise ValueError('Preference exceeds current transport limit')
            fields['formato'] = {'integerValue': '2'}
        else:
            raise ValueError('Snapshot includes unsupported/private service documents')
        target = PREFIX + 'ambientes/beta/' + '/'.join(path)
        entries.append({'source': source, 'sourceUpdateTime': original['updateTime'], 'sourceSha256': digest(original),
                        'target': target, 'targetMustNotExist': True, 'fields': fields, 'targetFieldsSha256': digest(fields)})
    entries.sort(key=lambda e: e['source'])
    return {'schema': 1, 'project': PROJECT, 'environment': 'beta', 'mode': 'plan-only',
            'snapshotSha256': digest(snapshot), 'entries': entries, 'retainedSourceAttachments': attachments,
            'requiresBeforeApply': ['server-rules-and-worker-homologation', 'review-existing-supervisor-approvals',
                                   'local-files-and-outbox-migration', 'legacy-client-cutover-and-final-source-recheck',
                                   'private-backup-restore-verification'],
            'counts': {'profiles': len(profiles), 'copiedDocuments': len(entries), 'retainedAttachmentDocuments': len(attachments)}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--snapshot', type=Path, required=True)
    parser.add_argument('--plan', type=Path, required=True)
    args = parser.parse_args()
    source = args.snapshot.read_text()
    result = plan(json.loads(source, object_pairs_hook=unique_object))
    # Atomic exclusive creation: never overwrite a prior backup/plan.
    descriptor = os.open(args.plan, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, 'w') as output:
        json.dump(result, output, ensure_ascii=False, indent=2, allow_nan=False)
        output.write('\n')
    print(json.dumps({'mode': result['mode'], 'counts': result['counts'], 'planSha256': digest(result)}))


if __name__ == '__main__':
    main()
