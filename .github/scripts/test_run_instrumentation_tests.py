#!/usr/bin/env python3
# Copyright 2026 Kenny Root
# SPDX-License-Identifier: Apache-2.0
"""Exercise the CI runner's failure and coverage gates without an emulator."""

import os
import shutil
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

from verify_uuid_migration_results import EXPECTED, TEST_CLASS


class InstrumentationRunnerTest(unittest.TestCase):
    def run_script(self, flavor="Oss", *, tests_exit=0, cases=None, adb_exit=0):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            scripts = root / ".github/scripts"
            scripts.mkdir(parents=True)
            for name in ("run_instrumentation_tests.sh", "verify_uuid_migration_results.py"):
                shutil.copy(Path(__file__).parent / name, scripts)
            adb = root / "adb"
            adb.write_text('#!/bin/bash\necho "adb $*" >> calls\necho "device diagnostic"\nexit "$ADB_EXIT"\n')
            adb.chmod(0o755)
            gradle = root / "gradlew"
            gradle.write_text(
                '#!/bin/bash\necho "gradle $*" >> calls\necho "gradle output"\n'
                'if [[ "$*" != *CoverageReport* ]]; then exit "${TESTS_EXIT}"; fi\n'
            )
            gradle.chmod(0o755)
            reports = root / f"app/build/outputs/androidTest-results/connected/debug/flavors/{flavor.lower()}"
            if cases is not None:
                reports.mkdir(parents=True)
                suite = ET.Element("testsuite", tests=str(len(cases)))
                for name, outcome in cases:
                    case = ET.SubElement(suite, "testcase", classname=TEST_CLASS, name=name)
                    if outcome:
                        ET.SubElement(case, outcome)
                ET.ElementTree(suite).write(reports / "TEST-device.xml")
            result = subprocess.run(
                ["bash", str(scripts / "run_instrumentation_tests.sh"), flavor],
                cwd=root,
                env={
                    **os.environ,
                    "PATH": f"{root}:{os.environ['PATH']}",
                    "TESTS_EXIT": str(tests_exit),
                    "ADB_EXIT": str(adb_exit),
                },
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                timeout=10,
            )
            calls = (root / "calls").read_text() if (root / "calls").exists() else ""
            diagnostics = {
                path.name: path.read_text()
                for path in (root / "build/instrumentation-diagnostics").rglob("*")
                if path.is_file()
            }
            return result, calls, diagnostics

    def test_gradle_failure_is_preserved_and_coverage_does_not_run(self):
        result, calls, logs = self.run_script(tests_exit=23)
        self.assertEqual(23, result.returncode)
        self.assertNotIn("CoverageReport", calls)
        self.assertIn("dumpsys activity instrumentation", calls)
        self.assertIn("gradle output", logs["gradle-tests.log"])
        self.assertIn("device diagnostic", logs["logcat.txt"])

    def test_failed_diagnostic_capture_preserves_success(self):
        result, _, _ = self.run_script(cases=[(name, None) for name in EXPECTED], adb_exit=143)
        self.assertEqual(0, result.returncode, result.stdout)

    def test_empty_suite_fails_before_coverage(self):
        result, calls, _ = self.run_script(cases=[])
        self.assertNotEqual(0, result.returncode)
        self.assertIn("UUID migration verification failed", result.stdout)
        self.assertNotIn("CoverageReport", calls)

    def test_skipped_migration_fails_before_coverage(self):
        cases = [(name, "skipped") for name in EXPECTED]
        result, calls, _ = self.run_script(cases=cases)
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("CoverageReport", calls)
        self.assertEqual(1, calls.count("gradle --profile --info"))

    def test_both_flavors_generate_coverage_after_verification(self):
        for flavor in ("Oss", "Google"):
            with self.subTest(flavor=flavor):
                result, calls, _ = self.run_script(flavor, cases=[(name, None) for name in EXPECTED])
                self.assertEqual(0, result.returncode, result.stdout)
                self.assertIn(f":app:create{flavor}DebugAndroidTestCoverageReport", calls)
                self.assertNotIn(":mosh:", calls)
                self.assertEqual(1, calls.count("gradle --profile --info"))
                self.assertIn(f"-x :app:connected{flavor}DebugAndroidTest", calls)

    def test_invalid_flavor_runs_nothing(self):
        result, calls, _ = self.run_script("invalid")
        self.assertEqual(2, result.returncode)
        self.assertEqual("", calls)


if __name__ == "__main__":
    unittest.main()
