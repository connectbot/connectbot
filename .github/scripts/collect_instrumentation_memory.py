#!/usr/bin/env python3
# Copyright 2026 Kenny Root
# SPDX-License-Identifier: Apache-2.0
"""Keep host memory evidence even when the emulator disappears mid-suite."""

import json
import signal
import threading
from datetime import datetime, timezone
from pathlib import Path


def read_text(path):
    try:
        return path.read_text().strip()
    except OSError:
        # Processes can exit between discovery and reading /proc; some hosts
        # also do not expose the cgroup v2 memory controller.
        return ""


def snapshot(proc=Path("/proc"), cgroup_root=Path("/sys/fs/cgroup")):
    processes = {}
    for directory in proc.iterdir():
        if not directory.name.isdigit():
            continue
        name = read_text(directory / "comm")
        if name != "java" and not name.startswith(("qemu", "emulator")):
            continue
        status = {}
        for line in read_text(directory / "status").splitlines():
            key, _, value = line.partition(":")
            if key in ("Name", "PPid", "VmRSS", "VmHWM", "VmSwap"):
                status[key] = value.strip()
        processes[directory.name] = status

    cgroups = {}
    for line in read_text(proc / "self/cgroup").splitlines():
        if not line.startswith("0::"):
            continue
        relative = Path(line[3:].lstrip("/"))
        if ".." in relative.parts:
            continue
        directory = cgroup_root / relative
        while True:
            cgroups[str(directory.relative_to(cgroup_root))] = {
                name: read_text(directory / name)
                for name in ("memory.current", "memory.max", "memory.peak", "memory.events")
            }
            if directory == cgroup_root:
                break
            directory = directory.parent

    return {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "meminfo": read_text(proc / "meminfo"),
        "oom_kill": next(
            (line.split()[1] for line in read_text(proc / "vmstat").splitlines()
             if line.startswith("oom_kill ")),
            None,
        ),
        "processes": processes,
        "cgroups": cgroups,
    }


def main():
    stopped = threading.Event()
    signal.signal(signal.SIGTERM, lambda *_: stopped.set())
    while True:
        print(json.dumps(snapshot()), flush=True)
        if stopped.wait(5):
            # Include final OOM counters, even if the emulator exited between samples.
            print(json.dumps(snapshot()), flush=True)
            return


if __name__ == "__main__":
    main()
