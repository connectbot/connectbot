#!/usr/bin/env python3
# Copyright 2026 Kenny Root
# SPDX-License-Identifier: Apache-2.0
"""Reject successful Gradle exits that produced no complete migration test results."""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

TEST_CLASS = "org.connectbot.data.migration.UuidMigrationDeviceTest"
EXPECTED = {
    "populatedProductionUpgradePreservesDataAndSurvivesReopen",
    "allDaosCanReadMigratedEntities",
    "everyHistoricalUpgradePreservesData",
    "cascadeDeletesStillWork",
    "failureDuringCopyRollsBackAndRetries",
    "failureAfterDropRollsBackAndRetries",
    "failureDuringRenameRollsBackAndRetries",
    "encryptedPasswordRemainsDecryptableAfterProductionStartup",
}


def main(root=None):
    root = Path(sys.argv[1]) if root is None else Path(root)
    cases = [
        case
        for report in root.rglob("TEST-*.xml")
        for case in ET.parse(report).getroot().iter("testcase")
        if case.get("classname") == TEST_CLASS
    ]
    missing = EXPECTED - {case.get("name") for case in cases}
    unsuccessful = [
        case.get("name")
        for case in cases
        if any(case.find(tag) is not None for tag in ("failure", "error", "skipped"))
    ]
    if missing or unsuccessful:
        raise SystemExit(
            f"UUID migration verification failed: missing={sorted(missing)}, "
            f"unsuccessful={unsuccessful}. Reports: {root}"
        )
    print(f"Verified {len(cases)} passing UUID migration device tests in {root}")


if __name__ == "__main__":
    main()
