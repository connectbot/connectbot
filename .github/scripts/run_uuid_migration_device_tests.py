#!/usr/bin/env python3
# Copyright 2026 Kenny Root
# SPDX-License-Identifier: Apache-2.0
"""Run the isolated migration suite without API 24's broken split-install response."""

import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

from verify_uuid_migration_results import TEST_CLASS
from verify_uuid_migration_results import main as verify_results


def parse_results(output):
    suite = ET.Element("testsuite", name=TEST_CLASS)
    status = {}
    completed = False
    for line in output.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            key, separator, value = line.removeprefix("INSTRUMENTATION_STATUS: ").partition("=")
            if separator:
                status[key] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            code = int(line.split(": ", 1)[1])
            if code <= 0 and "test" in status:
                case = ET.SubElement(
                    suite, "testcase", classname=status.get("class", ""), name=status["test"]
                )
                if code != 0:
                    ET.SubElement(case, "failure", message=f"Instrumentation status {code}").text = status.get("stack", "See instrumentation.log")
            status = {}
        elif line == "INSTRUMENTATION_CODE: -1":
            completed = True
    suite.set("tests", str(len(suite)))
    suite.set("failures", str(sum(case.find("failure") is not None for case in suite)))
    suite.set("errors", "0")
    suite.set("skipped", "0")
    return suite, completed


def main():
    # These tests own isolated database/preferences names and need no UI or Hilt injection.
    # A standalone APK plus non-streaming install avoids the API 24 split-commit failure.
    for apk in (
        "app/build/outputs/apk/oss/debug/app-oss-debug.apk",
        "app/build/outputs/apk/androidTest/oss/debug/app-oss-debug-androidTest.apk",
    ):
        subprocess.run(["adb", "install", "--no-streaming", "-r", "-t", apk], check=True)
    result = subprocess.run(
        ["adb", "shell", "am", "instrument", "-w", "-r", "-e", "class", TEST_CLASS,
         "org.connectbot.tests/org.connectbot.HiltTestRunner"],
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
    )
    directory = Path("app/build/outputs/androidTest-results/connected/debug/flavors/oss/uuid-direct")
    directory.mkdir(parents=True, exist_ok=True)
    (directory / "instrumentation.log").write_text(result.stdout)
    print(result.stdout, end="", flush=True)
    suite, completed = parse_results(result.stdout)
    ET.ElementTree(suite).write(directory / "TEST-uuid-migration.xml", encoding="utf-8", xml_declaration=True)
    if result.returncode != 0 or not completed:
        raise SystemExit("Migration instrumentation did not finish successfully; see instrumentation.log")
    verify_results(directory)


if __name__ == "__main__":
    main()
