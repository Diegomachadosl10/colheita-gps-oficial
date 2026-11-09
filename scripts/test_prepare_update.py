import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('prepare_update',Path(__file__).with_name('prepare-update.py'))
module=importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class PublicationTests(unittest.TestCase):
    def fake_tools(self, cert=None, package=None, version='1.2.0'):
        expected=Path(__file__).with_name('android-signing.sha256').read_text().strip()
        def run(args,**kwargs):
            if 'aapt' in args[0]:
                return f"package: name='{package or module.PACKAGE}' versionCode='6' versionName='{version}'\nsdkVersion:'26'\n"
            return 'Signer #1 certificate SHA-256 digest: '+(cert or expected)+'\n'
        return run
    def test_manifest_and_apk_are_prepared_without_any_private_key(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); apk=root/'test.apk'; apk.write_bytes(b'test APK')
            with patch.object(module.subprocess,'check_output',side_effect=self.fake_tools()):
                info=module.prepare(apk,root/'release',root,'Texto com acentos e $() literais')
            self.assertEqual(info['versionCode'],6)
            self.assertEqual(info['apkUrl'],'https://github.com/'+module.REPO+'/releases/download/v1.2.0/DM-AGRO-1.2.0.apk')
            self.assertEqual(json.loads((root/'release/dm-agro-update.json').read_text())['changes'],'Texto com acentos e $() literais')
            self.assertEqual({p.suffix for p in (root/'release').iterdir()},{'.apk','.json','.txt','.md'})
    def test_other_signing_keys_cannot_be_published(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); apk=root/'test.apk'; apk.write_bytes(b'test')
            with patch.object(module.subprocess,'check_output',side_effect=self.fake_tools(cert='0'*64)):
                with self.assertRaises(ValueError): module.prepare(apk,root/'release',root,'')
            self.assertFalse((root/'release').exists())
    def test_other_packages_and_invalid_version_names_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); apk=root/'test.apk'; apk.write_bytes(b'test')
            for data in [self.fake_tools(package='outro.app'),self.fake_tools(version='../../outro')]:
                with patch.object(module.subprocess,'check_output',side_effect=data):
                    with self.assertRaises(ValueError): module.prepare(apk,root/'release',root,'')

if __name__=='__main__': unittest.main()
