import hashlib
import importlib.util
import io
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('prepare_audio', Path(__file__).with_name('prepare-software-audio.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class SoftwareAudioSourcesTest(unittest.TestCase):
    def test_cached_source_requires_the_exact_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / 'source'
            target.write_bytes(b'verified')
            with patch.object(module.urllib.request, 'urlopen') as request:
                result = module.verified_download('https://example.test/source', hashlib.sha256(b'verified').hexdigest(), target)
            self.assertEqual(target, result)
            request.assert_not_called()

    def test_hash_mismatch_never_replaces_existing_file(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / 'source'
            target.write_bytes(b'old')
            with patch.object(module.urllib.request, 'urlopen', return_value=io.BytesIO(b'corrupted')):
                with self.assertRaises(ValueError):
                    module.verified_download('https://example.test/source', '0' * 64, target)
            self.assertEqual(b'old', target.read_bytes())
            self.assertFalse(target.with_suffix('.part').exists())

    def test_download_size_bound(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / 'source'
            with patch.object(module.urllib.request, 'urlopen', return_value=io.BytesIO(b'too large')):
                with self.assertRaises(ValueError):
                    module.verified_download('https://example.test/source', '0' * 64, target, maximum=2)
            self.assertFalse(target.exists())

    def test_source_archive_rejects_traversal_and_links(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, kind in [('ffmpeg/../../escape', tarfile.REGTYPE), ('ffmpeg/link', tarfile.SYMTYPE)]:
                archive = root / 'source.tar'
                with tarfile.open(archive, 'w') as output:
                    member = tarfile.TarInfo(name)
                    member.type = kind
                    member.linkname = '/outside'
                    output.addfile(member)
                with self.assertRaises(ValueError):
                    module.extract_sources(archive, root / 'output', 'ffmpeg')


if __name__ == '__main__':
    unittest.main()
