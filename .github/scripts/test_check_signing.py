import base64
import importlib.util
import io
from pathlib import Path
import subprocess
import tempfile
import unittest
from contextlib import redirect_stdout, redirect_stderr
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('check_signing', Path(__file__).with_name('check-signing.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class SigningReadinessTests(unittest.TestCase):
    expected = (Path(__file__).resolve().parents[2]/'scripts/android-signing.sha256').read_text().strip()
    fixture = base64.b64encode(b'SYNTHETIC-TEST-ONLY-'*100).decode()

    def result(self, certificate=None, entry='PrivateKeyEntry', returncode=0):
        cert = certificate or self.expected
        encoded = ':'.join(cert[i:i+2].upper() for i in range(0, len(cert), 2))
        return subprocess.CompletedProcess([], returncode, f'Entry type: {entry}\nSHA256: {encoded}\n', 'SYNTHETIC-PRIVATE-OUTPUT')

    def test_missing_and_malformed_secrets_never_invoke_a_signing_tool(self):
        def unexpected(*args, **kwargs):
            self.fail('Invalid input must not reach the signing tool')
        self.assertEqual(module.inspect_signing('', self.expected, unexpected), 'missing')
        for value in ['NOT BASE64!', base64.b64encode(b'too short').decode(), 'x'*150001]:
            self.assertEqual(module.inspect_signing(value, self.expected, unexpected), 'invalid')

    def test_only_the_pinned_certificate_with_a_private_key_entry_is_accepted(self):
        self.assertEqual(module.inspect_signing(self.fixture, self.expected, lambda *a, **k: self.result()), 'certificate_verified')
        self.assertEqual(module.inspect_signing(self.fixture, self.expected, lambda *a, **k: self.result('0'*64)), 'different_certificate')
        self.assertEqual(module.inspect_signing(self.fixture, self.expected, lambda *a, **k: self.result(entry='trustedCertEntry')), 'unreadable')
        self.assertEqual(module.inspect_signing(self.fixture, self.expected, lambda *a, **k: self.result(returncode=1)), 'unreadable')

    def test_key_is_private_temporary_and_not_passed_to_child_or_logs(self):
        paths = []
        def run(args, **kwargs):
            path = Path(args[args.index('-keystore')+1]);paths.append(path)
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            self.assertEqual(path.read_bytes(), base64.b64decode(self.fixture))
            self.assertNotIn('ANDROID_UPDATE_KEYSTORE_BASE64', kwargs['env'])
            self.assertNotIn(self.fixture, str(args));self.assertFalse(kwargs.get('shell', False))
            return self.result()
        output = io.StringIO()
        with patch.dict(module.os.environ, {'ANDROID_UPDATE_KEYSTORE_BASE64': self.fixture}), redirect_stdout(output), redirect_stderr(output):
            self.assertEqual(module.inspect_signing(self.fixture, self.expected, run), 'certificate_verified')
        self.assertEqual(output.getvalue(), '');self.assertTrue(all(not path.exists() for path in paths))

    def test_diagnostics_scrub_sensitive_exceptions_and_stop_before_publication(self):
        def fail(*args, **kwargs):
            raise RuntimeError('SYNTHETIC-PRIVATE-OUTPUT')
        with tempfile.TemporaryDirectory() as directory:
            summary = Path(directory)/'summary.md';output = io.StringIO()
            with patch.dict(module.os.environ, {'ANDROID_UPDATE_KEYSTORE_BASE64': self.fixture, 'GITHUB_STEP_SUMMARY': str(summary)}), \
                    patch.object(module.subprocess, 'run', side_effect=fail), redirect_stdout(output), redirect_stderr(output):
                self.assertEqual(module.main(), 1)
            text = output.getvalue()+summary.read_text()
            self.assertNotIn(self.fixture, text);self.assertNotIn('SYNTHETIC-PRIVATE-OUTPUT', text)
            self.assertIn(module.MESSAGES['unreadable'], text)


if __name__ == '__main__':
    unittest.main()
