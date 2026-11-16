import hashlib
import base64
import importlib.util
import json
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

spec = importlib.util.spec_from_file_location('publish_prebuilt', Path(__file__).with_name('publish-prebuilt.py'))
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


class PrebuiltPackageTests(unittest.TestCase):
    def package(self, folder):
        names = ['DM-AGRO-2.0.0.apk', 'dm-agro-update.json', 'SHA256.txt',
                 'DM-AGRO-2.0.0-codigo.zip', 'DM-AGRO-2.0.0-dm-agro-login.bundle']
        meta = {'version': '2.0.0', 'sourceCommit': 'a' * 40, 'files': {}}
        for name in names:
            (folder / name).write_bytes(name.encode())
            meta['files'][name] = {'size': len(name.encode()), 'sha256': hashlib.sha256(name.encode()).hexdigest()}
        (folder / 'publication.json').write_text(json.dumps(meta))
        return meta

    def test_rejects_changed_asset_before_any_upload(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            self.package(folder)
            publisher.checked_files(folder)
            (folder / 'DM-AGRO-2.0.0.apk').write_bytes(b'changed')
            with self.assertRaises(ValueError):
                publisher.checked_files(folder)

    def test_only_the_explicit_release_assets_are_accepted(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            meta = self.package(folder)
            meta['files']['private.keystore'] = {'size': 1, 'sha256': '0' * 64}
            (folder / 'publication.json').write_text(json.dumps(meta))
            with self.assertRaises(ValueError):
                publisher.checked_files(folder)

    def test_public_assets_can_be_transferred_by_git_object_without_committing_binaries(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            meta = self.package(folder)
            objects = {}
            for name, info in meta['files'].items():
                path = folder / name
                data = path.read_bytes()
                blob = hashlib.sha1(f'blob {len(data)}\0'.encode() + data).hexdigest()
                info['gitBlob'] = blob
                objects[f'repos/{publisher.REPO}/git/blobs/{blob}'] = {'encoding': 'base64', 'size': len(data), 'content': base64.b64encode(data).decode()}
                path.unlink()
            (folder / 'publication.json').write_text(json.dumps(meta))
            with patch.object(publisher, 'api', side_effect=lambda path: objects[path]):
                publisher.checked_files(folder, download_blobs=True)
            self.assertEqual(b'DM-AGRO-2.0.0.apk', (folder / 'DM-AGRO-2.0.0.apk').read_bytes())

    def test_a_corrupted_git_object_is_rejected_before_writing_or_uploading_the_apk(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            meta = self.package(folder)
            name = 'DM-AGRO-2.0.0.apk'
            meta['files'][name]['gitBlob'] = 'a' * 40
            (folder / name).unlink()
            (folder / 'publication.json').write_text(json.dumps(meta))
            payload = {'encoding': 'base64', 'size': meta['files'][name]['size'], 'content': base64.b64encode(b'corrupt').decode()}
            with patch.object(publisher, 'api', return_value=payload):
                with self.assertRaises(ValueError):
                    publisher.checked_files(folder, download_blobs=True)
            self.assertFalse((folder / name).exists())
