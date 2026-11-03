#!/usr/bin/env python3
# Copyright 2026 Kenny Root
# SPDX-License-Identifier: Apache-2.0
"""Ensure restored bytes cannot bypass Gradle's repository checksum lookup."""

import hashlib
import tempfile
import unittest
from pathlib import Path

from verify_gradle_download_cache import verify_cache


class DownloadCacheTest(unittest.TestCase):
    def artifact(self, root, content=b"published artifact"):
        checksum = hashlib.sha1(content).hexdigest()
        path = root / "example/library/1.0" / checksum / "library-1.0.jar"
        path.parent.mkdir(parents=True)
        path.write_bytes(content)
        return path

    def test_valid_download_is_preserved(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            path = self.artifact(root)
            self.assertEqual((1, 0), verify_cache(root))
            self.assertEqual(b"published artifact", path.read_bytes())

    def test_tampered_download_is_discarded(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            path = self.artifact(root)
            path.write_bytes(b"tampered cache entry")
            self.assertEqual((0, 1), verify_cache(root))
            self.assertFalse(path.exists())

    def test_unrecognized_cache_entries_are_discarded(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            path = root / "unexpected-metadata.bin"
            path.write_bytes(b"untrusted resolution metadata")
            self.assertEqual((0, 1), verify_cache(root))
            self.assertFalse(path.exists())

    def test_symlinks_do_not_read_or_modify_external_files(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "cache"
            root.mkdir()
            external = Path(temporary) / "external"
            external.mkdir()
            target = external / "keep.txt"
            target.write_text("keep")
            (root / "linked-directory").symlink_to(external, target_is_directory=True)
            (root / "linked-file").symlink_to(target)
            self.assertEqual((0, 2), verify_cache(root))
            self.assertEqual("keep", target.read_text())

    def test_missing_cache_is_a_normal_cache_miss(self):
        with tempfile.TemporaryDirectory() as temporary:
            self.assertEqual((0, 0), verify_cache(Path(temporary) / "missing"))


if __name__ == "__main__":
    unittest.main()
