import hashlib
import importlib.util
import json
import tempfile
import unittest
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
