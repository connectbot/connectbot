#!/usr/bin/env python3
# Copyright 2026 Kenny Root
# SPDX-License-Identifier: Apache-2.0
"""Validate Gradle's content-addressed download store before fresh resolution.

Only files-2.1 is restored, never Gradle's binary resolution metadata. Rehashing
its SHA-1 addresses prevents altered files from masquerading as verified cached
downloads. Gradle then resolves against repository checksums with
--refresh-dependencies; artifacts without published checksums are downloaded.
"""

import hashlib
import os
import re
import sys
from pathlib import Path


def verify_cache(root):
    root = Path(root)
    verified = 0
    discarded = 0
    if root.is_symlink():
        root.unlink()
        return verified, 1
    for directory, directories, files in os.walk(root, followlinks=False):
        for name in list(directories):
            path = Path(directory) / name
            if path.is_symlink():
                path.unlink()
                directories.remove(name)
                discarded += 1
        for name in files:
            path = Path(directory) / name
            parts = path.relative_to(root).parts
            valid = path.is_file() and not path.is_symlink() and len(parts) == 5 and re.fullmatch(r"[0-9a-f]{40}", parts[3])
            if valid:
                checksum = hashlib.sha1()
                with path.open("rb") as artifact:
                    for chunk in iter(lambda: artifact.read(1024 * 1024), b""):
                        checksum.update(chunk)
                valid = checksum.hexdigest() == parts[3]
            if valid:
                verified += 1
            else:
                path.unlink()
                discarded += 1
                print(f"Discarded invalid cached download: {path.relative_to(root)}")
    return verified, discarded


if __name__ == "__main__":
    verified, discarded = verify_cache(sys.argv[1])
    print(f"Verified {verified} cached downloads; discarded {discarded}. Repository checks run during Gradle resolution.")
