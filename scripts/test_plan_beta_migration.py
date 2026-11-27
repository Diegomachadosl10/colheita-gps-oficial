import copy
import hashlib
import importlib.util
import json
import unittest
import subprocess
import sys
import tempfile
from pathlib import Path

spec = importlib.util.spec_from_file_location('migration', Path(__file__).with_name('plan-beta-migration.py'))
migration = importlib.util.module_from_spec(spec)
spec.loader.exec_module(migration)


class MigrationPlanTest(unittest.TestCase):
    def fixture(self, kind='item', group='pendencias', key='p-1'):
        record_id = hashlib.sha256(f'{kind}|{group}|{key}'.encode()).hexdigest()
        item = {'id': key, 'equipamento': '22071', 'descricao': 'Rolamento', 'fotos': ['@arquivo/fotos/a.jpg'],
                'concluida': True, 'dataConclusao': '26/11/2026', '__arquivado': True,
                'historicoExclusoes': [{'acao': 'excluido', 'porUid': 'sup'}]}
        fields = {k: {'stringValue': v} for k, v in {'equipeId': 'santo_inacio_C', 'frenteId': 'santo_inacio', 'turno': 'C',
                  'tipo': kind, 'grupo': group, 'chave': key, 'conteudo': json.dumps(item), 'autorUid': 'autor', 'editadoPor': 'sup'}.items()}
        fields.update({'versao': {'integerValue': '7'}, 'excluido': {'booleanValue': True},
                       'atualizadoEm': {'timestampValue': '2026-11-26T14:00:00Z'}})
        return {'schema': 1, 'project': migration.PROJECT, 'documents': [{'name': migration.PREFIX+'equipes/santo_inacio_C/registros/'+record_id,
                'updateTime': '2026-11-26T14:00:00Z', 'fields': fields}]}

    def test_plan_preserves_identity_revision_timestamps_history_and_attachment_references_without_touching_source(self):
        snapshot = self.fixture()
        original = copy.deepcopy(snapshot)
        result = migration.plan(snapshot)
        entry = result['entries'][0]
        self.assertEqual(snapshot, original)
        self.assertTrue(entry['targetMustNotExist'])
        self.assertIn('/ambientes/beta/equipes/', entry['target'])
        fields = entry['fields']
        self.assertEqual(fields['versao'], {'integerValue': '7'})
        self.assertEqual(fields['autorUid'], {'stringValue': 'autor'})
        self.assertEqual(fields['atualizadoEm'], snapshot['documents'][0]['fields']['atualizadoEm'])
        self.assertNotIn('conteudo', fields)
        data = fields['dados']['mapValue']['fields']
        self.assertEqual(data['id'], {'stringValue': 'p-1'})
        self.assertEqual(data['fotos'], {'arrayValue': {'values': [{'stringValue': '@arquivo/fotos/a.jpg'}]}})
        self.assertTrue(data['__arquivado']['booleanValue'])
        self.assertIn('historicoExclusoes', data)
        self.assertEqual(result, migration.plan(snapshot))

    def test_preference_content_is_kept_byte_for_byte_and_server_files_are_retained_without_enabling_storage(self):
        snapshot = self.fixture('preferencia', 'pinturas', 'mapa-1')
        source = snapshot['documents'][0]['fields']['conteudo']
        self.assertEqual(source, migration.plan(snapshot)['entries'][0]['fields']['conteudo'])
        result = migration.plan(self.fixture('arquivo', 'arquivos', 'foto.jpg'))
        self.assertEqual([], result['entries'])
        self.assertEqual(1, len(result['retainedSourceAttachments']))

    def test_unsupported_groups_duplicates_changed_archive_state_and_forged_ids_fail_closed(self):
        values = []
        duplicate = self.fixture(); duplicate['documents'] *= 2; values.append(duplicate)
        foreign = self.fixture(); foreign['project'] = 'another-project'; values.append(foreign)
        archive = self.fixture(); archive['documents'][0]['fields']['excluido'] = {'booleanValue': False}; values.append(archive)
        identity = self.fixture(); identity['documents'][0]['name'] += 'bad'; values.append(identity)
        values.append(self.fixture(group='unknown'))
        private = self.fixture(); private['documents'][0]['name'] = migration.PREFIX+'configuracao/servico'; values.append(private)
        for snapshot in values:
            with self.subTest(snapshot=snapshot['documents'][0]['name']):
                with self.assertRaises(ValueError): migration.plan(snapshot)

    def test_existing_profiles_are_not_autoapproved_or_renamed(self):
        snapshot = self.fixture()
        fields = {'papel': {'stringValue': 'supervisor'}, 'status': {'stringValue': 'pendente'}}
        snapshot['documents'] = [{'name': migration.PREFIX+'usuarios/fake-uid', 'updateTime': '2026-11-26T14:00:00Z', 'fields': fields}]
        entry = migration.plan(snapshot)['entries'][0]
        self.assertEqual(fields, entry['fields'])
        self.assertTrue(entry['target'].endswith('/usuarios/fake-uid'))
        self.assertIn('review-existing-supervisor-approvals', migration.plan(snapshot)['requiresBeforeApply'])
        snapshot['documents'][0]['fields']['senha'] = {'stringValue': 'fictional-credential'}
        with self.assertRaises(ValueError): migration.plan(snapshot)

    def test_cli_creates_a_private_plan_once_and_prints_only_counts_and_hashes(self):
        with tempfile.TemporaryDirectory() as folder:
            snapshot, output = Path(folder)/'source.json', Path(folder)/'plan.json'
            snapshot.write_text(json.dumps(self.fixture()))
            command = [sys.executable, str(Path(migration.__file__)), '--snapshot', str(snapshot), '--plan', str(output)]
            first = subprocess.run(command, text=True, capture_output=True, check=True)
            self.assertEqual(0o600, output.stat().st_mode & 0o777)
            self.assertNotIn('22071', first.stdout)
            self.assertEqual('plan-only', json.loads(first.stdout)['mode'])
            before = output.read_bytes()
            repeated = subprocess.run(command, text=True, capture_output=True)
            self.assertNotEqual(0, repeated.returncode)
            self.assertEqual(before, output.read_bytes())


if __name__ == '__main__': unittest.main()
