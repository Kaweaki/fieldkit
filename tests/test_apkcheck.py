"""Tests for tools/apkcheck.py.

They run against the shipped 2.6 APK, which is the point: the contract checker
must be able to say what that build gets wrong. Nothing here needs a device,
an SDK or a network.

    py -m unittest discover -s tests -t .
"""

import json
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

import apkcheck  # noqa: E402

APK = ROOT / "dist" / "FieldKit-2.6.apk"


class ManifestFacts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.facts = apkcheck.collect(APK)
        cls.results = apkcheck.check(cls.facts)
        cls.by_id = {r["id"]: r for r in cls.results}

    def test_package_and_version(self):
        self.assertEqual(self.facts["package"], "se.blimark.fieldkit")
        self.assertEqual(self.facts["versionCode"], 17)
        self.assertEqual(self.facts["versionName"], "2.6")

    def test_sdk_levels(self):
        self.assertEqual(self.facts["minSdkVersion"], 21)
        self.assertEqual(self.facts["targetSdkVersion"], 34)

    def test_activity_count(self):
        activities = [c for c in self.facts["components"] if c["kind"] == "activity"]
        self.assertEqual(len(activities), 24)

    def test_only_launcher_and_widget_exported(self):
        exported = sorted(
            c["name"] for c in self.facts["components"] if c["exported"] is True
        )
        self.assertEqual(
            exported,
            ["se.blimark.fieldkit.FieldKitWidget", "se.blimark.fieldkit.MainActivity"],
        )

    def test_no_native_code(self):
        self.assertEqual(self.facts["native_libs"], [])

    def test_permission_set(self):
        names = {p["name"] for p in self.facts["permissions"]}
        self.assertEqual(len(names), 13)
        self.assertIn("android.permission.INTERNET", names)
        self.assertNotIn("android.permission.BLUETOOTH_SCAN", names)

    def test_debug_certificate_detected(self):
        self.assertTrue(self.facts["debug_certificate"])


class ContractCatchesTheBugs(unittest.TestCase):
    def setUp(self):
        self.facts = apkcheck.collect(APK)
        self.by_id = {r["id"]: r for r in apkcheck.check(self.facts)}

    def test_target_sdk_fails(self):
        self.assertEqual(self.by_id["C1"]["status"], "FAIL")
        self.assertIn("34", self.by_id["C1"]["detail"])

    def test_bluetooth_permissions_fail(self):
        self.assertEqual(self.by_id["C3"]["status"], "FAIL")
        self.assertIn("BLUETOOTH_SCAN", self.by_id["C3"]["detail"])

    def test_storage_permissions_fail(self):
        self.assertEqual(self.by_id["C4"]["status"], "FAIL")

    def test_exported_components_pass(self):
        self.assertEqual(self.by_id["C5"]["status"], "PASS")

    def test_policy_and_native_pass(self):
        self.assertEqual(self.by_id["C6"]["status"], "PASS")
        self.assertEqual(self.by_id["C7"]["status"], "PASS")

    def test_debug_signing_warns(self):
        self.assertEqual(self.by_id["C8"]["status"], "WARN")

    def test_cli_exits_one_on_failure(self):
        completed = subprocess.run(
            [sys.executable, str(ROOT / "tools" / "apkcheck.py"), str(APK), "--json"],
            capture_output=True, text=True, check=False,
        )
        self.assertEqual(completed.returncode, 1, completed.stderr)
        payload = json.loads(completed.stdout)
        self.assertEqual(payload["facts"]["targetSdkVersion"], 34)
        self.assertTrue(any(c["status"] == "FAIL" for c in payload["checks"]))

    def test_report_mode_exits_zero(self):
        completed = subprocess.run(
            [sys.executable, str(ROOT / "tools" / "apkcheck.py"), str(APK), "--report"],
            capture_output=True, text=True, check=False,
        )
        self.assertEqual(completed.returncode, 0, completed.stderr)
        self.assertIn("FAIL C3", completed.stdout)


class CorrectedManifestSatisfiesTheContract(unittest.TestCase):
    """app/AndroidManifest.xml must pass the same checks the 2.6 APK fails."""

    def setUp(self):
        self.facts = apkcheck.collect_manifest_xml(
            ROOT / "app" / "AndroidManifest.xml", min_sdk=21, target_sdk=36
        )
        self.results = apkcheck.check(self.facts)
        self.by_id = {r["id"]: r for r in self.results}

    def test_no_failures(self):
        failures = [r for r in self.results if r["status"] == "FAIL"]
        self.assertEqual(failures, [])

    def test_bluetooth_split_is_complete(self):
        self.assertEqual(self.by_id["C3"]["status"], "PASS")
        names = {p["name"]: p["maxSdkVersion"] for p in self.facts["permissions"]}
        self.assertEqual(names["android.permission.BLUETOOTH"], 30)
        self.assertEqual(names["android.permission.BLUETOOTH_ADMIN"], 30)

    def test_no_storage_permissions(self):
        self.assertEqual(self.by_id["C4"]["status"], "PASS")
        names = {p["name"] for p in self.facts["permissions"]}
        self.assertNotIn("android.permission.WRITE_EXTERNAL_STORAGE", names)

    def test_same_24_activities_and_exports(self):
        activities = [c for c in self.facts["components"] if c["kind"] == "activity"]
        self.assertEqual(len(activities), 24)
        exported = sorted(c["name"] for c in self.facts["components"] if c["exported"] is True)
        self.assertEqual(
            exported,
            ["se.blimark.fieldkit.FieldKitWidget", "se.blimark.fieldkit.MainActivity"],
        )

    def test_signing_is_skipped_for_a_source_manifest(self):
        self.assertEqual(self.by_id["C8"]["status"], "SKIP")


class ParserRejectsNonsense(unittest.TestCase):
    def test_plain_bytes_are_not_binary_xml(self):
        with self.assertRaises(apkcheck.AxmlError):
            apkcheck.parse_manifest(b"not xml at all")

    def test_missing_file_is_an_error_code(self):
        completed = subprocess.run(
            [sys.executable, str(ROOT / "tools" / "apkcheck.py"),
             str(ROOT / "dist" / "does-not-exist.apk")],
            capture_output=True, text=True, check=False,
        )
        self.assertEqual(completed.returncode, 2)
        self.assertIn("apkcheck:", completed.stderr)


if __name__ == "__main__":
    unittest.main()
