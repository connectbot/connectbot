#!/usr/bin/env bash
# Copyright 2026 Kenny Root
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

case "${1:-}" in
  Oss) flavor=oss ;;
  Google) flavor=google ;;
  *) echo "Usage: $0 Oss|Google" >&2; exit 2 ;;
esac
task=$1
diagnostics="build/instrumentation-diagnostics/$flavor"
reports="app/build/outputs/androidTest-results/connected/debug/flavors/$flavor"
mkdir -p "$diagnostics"
logcat_pid=
memory_pid=

# Per-test logs only exist after discovery succeeds. Dump the device buffers
# before the emulator stops, including when Gradle reports an empty suite.
finish() {
  local result=$?
  trap - EXIT
  if [[ -n "$memory_pid" ]]; then
    kill "$memory_pid" 2>/dev/null || true
    wait "$memory_pid" 2>/dev/null || true
  fi
  if [[ -n "$logcat_pid" ]]; then
    # Reap the streaming reader even if the device vanished or adb hung.
    kill -KILL "$logcat_pid" 2>/dev/null || true
    wait "$logcat_pid" 2>/dev/null || true
  fi
  # adb can wait indefinitely after the emulator disappears. Diagnostics must
  # not turn a completed test failure into a hung job, even if adb ignores TERM.
  timeout --kill-after=5s 30s adb logcat -d -v threadtime -b all > "$diagnostics/logcat.txt" 2>&1 || true
  if (( result != 0 )); then
    timeout --kill-after=5s 30s adb shell dumpsys activity instrumentation > "$diagnostics/instrumentation.txt" 2>&1 || true
    timeout --kill-after=5s 30s adb shell df -h /data > "$diagnostics/device-storage.txt" 2>&1 || true
  fi
  exit "$result"
}
trap finish EXIT

# Capture while the device is alive; an exit-time dump cannot recover logs
# after the emulator process has crashed or been killed by the host.
python3 .github/scripts/collect_instrumentation_memory.py \
  > "$diagnostics/host-memory-timeline.jsonl" 2> "$diagnostics/host-memory-monitor.txt" &
memory_pid=$!
adb logcat -v threadtime -b all > "$diagnostics/live-logcat.txt" 2>&1 &
logcat_pid=$!

# All instrumentation sources live in :app; :mosh has no tests of its own.
# Run only one test task against the emulator, and verify execution before
# requesting coverage so an empty suite is reported as the primary failure.
./gradlew --profile --info ":app:connected${task}DebugAndroidTest" --rerun \
  2>&1 | tee "$diagnostics/gradle-tests.log"
# The test engine can return success and write passing, partial XML results
# when its adb stream closes mid-suite. Do not mistake those for a full run.
if ! timeout --kill-after=5s 30s adb get-state > "$diagnostics/device-state.txt" 2>&1 ||
    [[ "$(cat "$diagnostics/device-state.txt")" != device ]]; then
  echo "Emulator disconnected during instrumentation; test results may be incomplete. See $diagnostics." >&2
  exit 1
fi
python3 .github/scripts/verify_uuid_migration_results.py "$reports"
# The report depends on the connected test task. Use the already verified run,
# rather than executing the device suite again in this Gradle invocation.
./gradlew --profile ":app:create${task}DebugAndroidTestCoverageReport" \
  -x ":app:connected${task}DebugAndroidTest" \
  2>&1 | tee "$diagnostics/gradle-coverage.log"
