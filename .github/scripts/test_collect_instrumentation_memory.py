#!/usr/bin/env python3
# Copyright 2026 Kenny Root
# SPDX-License-Identifier: Apache-2.0
"""Verify memory evidence and monitor shutdown without inducing a real OOM."""

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from collect_instrumentation_memory import snapshot


class InstrumentationMemoryTest(unittest.TestCase):
    def test_retains_oom_counters_after_emulator_disappears(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            proc = root / "proc"
            (proc / "self").mkdir(parents=True)
            (proc / "self/cgroup").write_text("0::/runner/tests\n")
            (proc / "meminfo").write_text("MemAvailable: 1234 kB\n")
            (proc / "vmstat").write_text("pgfault 100\noom_kill 2\n")
            emulator = proc / "123"
            emulator.mkdir()
            (emulator / "comm").write_text("qemu-system-x86\n")
            (emulator / "status").write_text("Name:\tqemu-system-x86\nVmRSS:\t1024 kB\nVmHWM:\t2048 kB\n")
            cgroups = root / "cgroups"
            (cgroups / "runner/tests").mkdir(parents=True)
            (cgroups / "runner/memory.max").write_text("4096\n")
            events = cgroups / "runner/tests/memory.events"
            events.write_text("oom 0\noom_kill 0\n")

            before = snapshot(proc, cgroups)
            self.assertEqual("1024 kB", before["processes"]["123"]["VmRSS"])
            self.assertEqual("2048 kB", before["processes"]["123"]["VmHWM"])
            self.assertEqual("4096", before["cgroups"]["runner"]["memory.max"])
            self.assertEqual("2", before["oom_kill"])

            (emulator / "comm").unlink()
            (proc / "vmstat").write_text("oom_kill 3\n")
            events.write_text("oom 1\noom_kill 1\n")
            after = snapshot(proc, cgroups)
            self.assertEqual({}, after["processes"])
            self.assertEqual("3", after["oom_kill"])
            self.assertIn("oom_kill 1", after["cgroups"]["runner/tests"]["memory.events"])

    def test_missing_cgroup_controller_is_supported(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            result = snapshot(root, root / "missing")
            self.assertEqual({}, result["cgroups"])
            self.assertIsNone(result["oom_kill"])

    def test_termination_flushes_final_sample_and_exits(self):
        monitor = subprocess.Popen(
            [sys.executable, str(Path(__file__).with_name("collect_instrumentation_memory.py"))],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        try:
            first = json.loads(monitor.stdout.readline())
            monitor.terminate()
            output, errors = monitor.communicate(timeout=5)
            final = json.loads(output.strip())
            self.assertEqual(0, monitor.returncode, errors)
            self.assertIn("MemAvailable:", first["meminfo"])
            self.assertGreaterEqual(final["timestamp"], first["timestamp"])
        finally:
            if monitor.poll() is None:
                monitor.kill()
                monitor.communicate()


if __name__ == "__main__":
    unittest.main()
