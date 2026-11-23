#!/usr/bin/env python3
"""FieldKit APK contract check.

Reads a signed APK, parses its binary AndroidManifest.xml with the standard
library only, and asserts the compatibility contract this repository depends
on. No Android SDK, no third-party packages, no device.

The contract exists because the 2.6 build shipped two permissions bugs that no
document caught: no BLUETOOTH_SCAN/BLUETOOTH_CONNECT (so every radio screen
fails on Android 12+) and READ/WRITE_EXTERNAL_STORAGE (a no-op under scoped
storage, which is what broke the MBTiles import).

Exit codes: 0 contract holds, 1 a check failed, 2 the APK cannot be read.
"""

from __future__ import annotations

import argparse
import json
import struct
import sys
import textwrap
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

# ---------------------------------------------------------------- binary XML

CHUNK_STRING_POOL = 0x0001
CHUNK_START_ELEMENT = 0x0102
CHUNK_END_ELEMENT = 0x0103

TYPE_STRING = 0x03
TYPE_INT_DEC = 0x10
TYPE_INT_HEX = 0x11
TYPE_BOOL = 0x12

NS_ANDROID = "http://schemas.android.com/apk/res/android"


class AxmlError(Exception):
    """The file is not a binary XML document this parser understands."""


def _u16(data: bytes, off: int) -> int:
    return struct.unpack_from("<H", data, off)[0]


def _u32(data: bytes, off: int) -> int:
    return struct.unpack_from("<I", data, off)[0]


def _string_pool(data: bytes, off: int) -> list[str]:
    header_size = _u16(data, off + 2)
    count = _u32(data, off + 8)
    flags = _u32(data, off + 16)
    strings_start = _u32(data, off + 20)
    utf8 = bool(flags & 0x100)
    pool: list[str] = []
    for index in range(count):
        rel = _u32(data, off + header_size + 4 * index)
        pos = off + strings_start + rel
        if utf8:
            # UTF-16 length first, then UTF-8 byte length; each 1 or 2 bytes.
            pos += 2 if data[pos] & 0x80 else 1
            length = data[pos]
            pos += 1
            if length & 0x80:
                length = ((length & 0x7F) << 8) | data[pos]
                pos += 1
            pool.append(data[pos:pos + length].decode("utf-8", "replace"))
        else:
            length = _u16(data, pos)
            pos += 2
            if length & 0x8000:
                length = ((length & 0x7FFF) << 16) | _u16(data, pos)
                pos += 2
            pool.append(data[pos:pos + 2 * length].decode("utf-16-le", "replace"))
    return pool


def parse_manifest(data: bytes) -> dict:
    """Parse a binary AndroidManifest.xml into a {"name", "attrs", "children"} tree."""
    if len(data) < 8 or _u16(data, 0) != 0x0003:
        raise AxmlError("not a binary XML document")
    strings: list[str] = []
    total = _u32(data, 4)
    pos = _u16(data, 2)
    root: dict = {"name": "#document", "attrs": {}, "children": []}
    stack = [root]
    limit = min(total, len(data))
    while pos + 8 <= limit:
        chunk_type = _u16(data, pos)
        header_size = _u16(data, pos + 2)
        size = _u32(data, pos + 4)
        if size <= 0:
            break
        if chunk_type == CHUNK_STRING_POOL:
            strings = _string_pool(data, pos)
        elif chunk_type == CHUNK_START_ELEMENT:
            body = pos + header_size
            name = strings[_u32(data, body + 4)] if strings else "?"
            attr_start = _u16(data, body + 8)
            attr_size = _u16(data, body + 10)
            attr_count = _u16(data, body + 12)
            attrs: dict = {}
            for index in range(attr_count):
                at = body + attr_start + index * attr_size
                ns = _u32(data, at)
                name_index = _u32(data, at + 4)
                value_type = data[at + 15]
                raw = _u32(data, at + 16)
                attr_name = strings[name_index] if name_index < len(strings) else "?"
                # ns is 0xFFFFFFFF when an attribute has no namespace.
                if 0 < ns < len(strings) and strings[ns] != NS_ANDROID:
                    attr_name = f"{strings[ns]}:{attr_name}"
                if value_type == TYPE_STRING:
                    value = strings[raw] if raw < len(strings) else None
                elif value_type == TYPE_BOOL:
                    value = bool(raw)
                elif value_type in (TYPE_INT_DEC, TYPE_INT_HEX):
                    value = raw if raw < 0x80000000 else raw - 0x100000000
                else:
                    value = raw
                attrs[attr_name] = value
            node = {"name": name, "attrs": attrs, "children": []}
            stack[-1]["children"].append(node)
            stack.append(node)
        elif chunk_type == CHUNK_END_ELEMENT and len(stack) > 1:
            stack.pop()
        pos += size
    return root


def walk(node: dict):
    yield node
    for child in node["children"]:
        yield from walk(child)


def _first(node: dict, name: str):
    return next((n for n in walk(node) if n["name"] == name), None)


# ---------------------------------------------------------------- facts

COMPONENT_KINDS = ("activity", "activity-alias", "service", "receiver", "provider")


def collect(apk_path: str | Path) -> dict:
    apk = Path(apk_path)
    try:
        with zipfile.ZipFile(apk) as zf:
            infos = [i for i in zf.infolist() if not i.is_dir()]
            if "AndroidManifest.xml" not in zf.namelist():
                raise AxmlError("no AndroidManifest.xml")
            manifest = parse_manifest(zf.read("AndroidManifest.xml"))
            dex_bytes = sum(i.file_size for i in infos if i.filename.endswith(".dex"))
            native = [
                i.filename for i in infos
                if i.filename.endswith(".so") or i.filename.startswith("lib/")
            ]
            signers = [
                i.filename for i in infos
                if i.filename.upper().startswith("META-INF/")
                and i.filename.upper().endswith((".RSA", ".DSA", ".EC"))
            ]
            debug_cert = False
            if signers:
                blob = zf.read(signers[0])
                debug_cert = b"Android Debug" in blob
            entries = len(infos)
    except zipfile.BadZipFile as exc:
        raise AxmlError(f"not an APK: {exc}") from exc

    manifest_node = _first(manifest, "manifest")
    if manifest_node is None:
        raise AxmlError("no <manifest> element")
    uses_sdk = _first(manifest_node, "uses-sdk")
    application = _first(manifest_node, "application")

    permissions = [
        {
            "name": node["attrs"].get("name"),
            "maxSdkVersion": node["attrs"].get("maxSdkVersion"),
        }
        for node in manifest_node["children"]
        if node["name"] == "uses-permission"
    ]

    components = []
    if application is not None:
        for node in application["children"]:
            if node["name"] in COMPONENT_KINDS:
                components.append(
                    {
                        "kind": node["name"],
                        "name": node["attrs"].get("name"),
                        "exported": node["attrs"].get("exported"),
                    }
                )

    app_attrs = application["attrs"] if application else {}
    sdk_attrs = uses_sdk["attrs"] if uses_sdk else {}

    return {
        "apk": str(apk),
        "size_bytes": apk.stat().st_size,
        "entries": entries,
        "package": manifest_node["attrs"].get("package"),
        "versionCode": manifest_node["attrs"].get("versionCode"),
        "versionName": manifest_node["attrs"].get("versionName"),
        "minSdkVersion": sdk_attrs.get("minSdkVersion"),
        "targetSdkVersion": sdk_attrs.get("targetSdkVersion"),
        "compileSdkVersion": manifest_node["attrs"].get("compileSdkVersion"),
        "permissions": permissions,
        "components": components,
        "allowBackup": app_attrs.get("allowBackup"),
        "usesCleartextTraffic": app_attrs.get("usesCleartextTraffic"),
        "dex_bytes": dex_bytes,
        "native_libs": native,
        "signers": signers,
        "debug_certificate": debug_cert,
    }


def _int_or_none(value):
    if value is None:
        return None
    try:
        return int(value, 0)
    except (TypeError, ValueError):
        return None


def _bool_or_none(value):
    if value is None:
        return None
    return str(value).lower() == "true"


def collect_manifest_xml(path: str | Path, min_sdk: int = 21, target_sdk: int = 36) -> dict:
    """Facts from a source AndroidManifest.xml, before there is an APK to read.

    min_sdk and target_sdk are not in the file: app/build.sh passes them to
    aapt2, so they are arguments here with the same defaults.
    """
    ns = "{http://schemas.android.com/apk/res/android}"
    manifest = ET.parse(path).getroot()
    application = manifest.find("application")
    if application is None:
        raise AxmlError("no <application> element")

    permissions = [
        {
            "name": node.get(f"{ns}name"),
            "maxSdkVersion": _int_or_none(node.get(f"{ns}maxSdkVersion")),
        }
        for node in manifest.findall("uses-permission")
    ]
    components = [
        {
            "kind": kind,
            "name": node.get(f"{ns}name"),
            "exported": _bool_or_none(node.get(f"{ns}exported")),
        }
        for kind in COMPONENT_KINDS
        for node in application.findall(kind)
    ]
    return {
        "apk": str(path),
        "source_manifest": True,
        "size_bytes": Path(path).stat().st_size,
        "entries": 1,
        "package": manifest.get("package"),
        "versionCode": None,
        "versionName": None,
        "minSdkVersion": min_sdk,
        "targetSdkVersion": target_sdk,
        "compileSdkVersion": None,
        "permissions": permissions,
        "components": components,
        "allowBackup": _bool_or_none(application.get(f"{ns}allowBackup")),
        "usesCleartextTraffic": _bool_or_none(application.get(f"{ns}usesCleartextTraffic")),
        "dex_bytes": 0,
        "native_libs": [],
        "signers": [],
        "debug_certificate": False,
    }


# ---------------------------------------------------------------- contract

LEGACY_BLUETOOTH = ("android.permission.BLUETOOTH", "android.permission.BLUETOOTH_ADMIN")
STORAGE = (
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
)
EXPORTED_ALLOWED = {
    "se.blimark.fieldkit.MainActivity",
    "se.blimark.fieldkit.FieldKitWidget",
    # The V2 package has one component and it is the launcher.
    "se.blimark.fieldkit.v2.V2Activity",
    ".V2Activity",
}


def check(facts: dict) -> list[dict]:
    results: list[dict] = []
    perms = {p["name"]: p["maxSdkVersion"] for p in facts["permissions"]}
    target = facts["targetSdkVersion"]
    minimum = facts["minSdkVersion"]

    def add(check_id: str, status: str, title: str, detail: str) -> None:
        results.append({"id": check_id, "status": status, "title": title, "detail": detail})

    # C1 target API level
    if target is None:
        add("C1", "FAIL", "Target API level", "no targetSdkVersion; the platform assumes 1")
    elif target < 36:
        add(
            "C1", "FAIL", "Target API level",
            f"targetSdkVersion {target}; Play needs 36 for new apps and updates since "
            f"2026-08-31, and 35 for an existing app to stay visible to new users. "
            f"An app targeting {target} is only offered to devices at or below API {target}.",
        )
    else:
        add("C1", "PASS", "Target API level", f"targetSdkVersion {target} meets the Play floor (36)")

    # C2 minimum API level
    if minimum is None:
        add("C2", "FAIL", "Minimum API level", "no minSdkVersion")
    elif minimum < 23:
        add(
            "C2", "WARN", "Minimum API level",
            f"minSdkVersion {minimum} keeps the pre-23 crypto fallback (PBKDF2 from the "
            f"passcode, no keystore key). API 24 reaches 99.5% of active devices, so the "
            f"fallback costs about 0.3% of reach and one whole code path.",
        )
    else:
        add("C2", "PASS", "Minimum API level",
            f"minSdkVersion {minimum}; API 21 reaches 99.8% of active devices, API 24 reaches 99.5%")

    # C3 Bluetooth permissions
    if target is None or target < 31:
        add("C3", "SKIP", "Bluetooth permissions",
            f"targetSdk {target} < 31, legacy Bluetooth permissions still apply")
    else:
        missing = [p for p in ("android.permission.BLUETOOTH_SCAN",
                               "android.permission.BLUETOOTH_CONNECT") if p not in perms]
        if missing:
            add(
                "C3", "FAIL", "Bluetooth permissions",
                f"missing {', '.join(p.rsplit('.', 1)[-1] for p in missing)} with "
                f"targetSdk {target}: scanning, BLE, SPP and the PC link fail with a "
                f"SecurityException on Android 12+ (77.8% of active devices)",
            )
        else:
            uncapped = [p for p in LEGACY_BLUETOOTH if p in perms and perms[p] != 30]
            if "android.permission.BLUETOOTH_ADVERTISE" not in perms:
                add("C3", "WARN", "Bluetooth permissions",
                    "BLUETOOTH_SCAN and BLUETOOTH_CONNECT present, BLUETOOTH_ADVERTISE absent: "
                    "making the phone discoverable for PC link needs it on Android 12+")
            elif uncapped:
                add("C3", "WARN", "Bluetooth permissions",
                    f"{len(uncapped)} legacy permission(s) not capped at maxSdkVersion=30")
            else:
                add("C3", "PASS", "Bluetooth permissions",
                    "BLUETOOTH_SCAN, BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE declared; "
                    "legacy pair capped at 30")

    # C4 storage permissions
    if target is not None and target >= 30:
        declared = [p for p in STORAGE if p in perms]
        # Capped at API 28 they still grant access on Android 9 and below, which
        # is where the path-based import lives; uncapped they are a lie above 28.
        dead = [p for p in declared if perms[p] is None or perms[p] >= 29]
        if dead:
            add(
                "C4", "FAIL", "Storage permissions",
                f"{len(dead)} storage permission(s) still live above API 28 under "
                f"scoped storage (targetSdk {target}); they grant nothing there. "
                f"Cap them at maxSdkVersion=28 and import with ACTION_OPEN_DOCUMENT.",
            )
        elif declared:
            add(
                "C4", "WARN", "Storage permissions",
                "capped at API 28: they work on Android 9 and below, where the MBTiles "
                "file is opened by path. Replace the path with ACTION_OPEN_DOCUMENT and "
                "delete them.",
            )
        else:
            add("C4", "PASS", "Storage permissions", "no storage permissions; document picker only")
    else:
        add("C4", "SKIP", "Storage permissions", "targetSdk < 30, storage permissions still grant access")

    # C5 exported components
    extra = [
        c for c in facts["components"]
        if c["exported"] is True and c["name"] not in EXPORTED_ALLOWED
    ]
    if extra:
        add("C5", "FAIL", "Exported components",
            "exported beyond the launcher and the widget: "
            + ", ".join(f"{c['name']} ({c['kind']})" for c in extra))
    else:
        exported = [c["name"] for c in facts["components"] if c["exported"] is True]
        add("C5", "PASS", "Exported components",
            f"{len(exported)} exported ({', '.join(n.rsplit('.', 1)[-1] for n in exported)})")

    # C6 backup and cleartext
    backup = facts["allowBackup"]
    cleartext = facts["usesCleartextTraffic"]
    if backup is True:
        add("C6", "FAIL", "Backup and cleartext",
            "allowBackup is not false: profiles, statuses and the passcode record are "
            "copied off the device by auto-backup")
    elif cleartext is True:
        add("C6", "WARN", "Backup and cleartext",
            "usesCleartextTraffic is not false")
    else:
        add("C6", "PASS", "Backup and cleartext",
            f"allowBackup={backup}, usesCleartextTraffic={cleartext}")

    # C7 native code
    if facts["native_libs"]:
        add("C7", "WARN", "Native code",
            f"{len(facts['native_libs'])} native library file(s): check 16 KB page support "
            f"and per-ABI coverage before shipping")
    else:
        add("C7", "PASS", "Native code",
            "no native libraries: one APK covers ARM, ARM64, x86, x86_64 and 16 KB page devices")

    # C8 signing certificate
    if facts.get("source_manifest"):
        add("C8", "SKIP", "Signing", "source manifest: signing is checked on the built APK")
    elif not facts["signers"]:
        add("C8", "FAIL", "Signing", "not signed")
    elif facts["debug_certificate"]:
        add("C8", "WARN", "Signing",
            f"signed with the Android debug certificate ({facts['signers'][0]}): "
            f"not acceptable for Play, not rotatable, and any other debug-signed package "
            f"can update it")
    else:
        add("C8", "PASS", "Signing", f"release signature in {facts['signers'][0]}")

    return results


# ---------------------------------------------------------------- cli

def render(facts: dict, results: list[dict]) -> str:
    lines = [f"FieldKit APK contract  {facts['apk']}", ""]
    if facts.get("versionCode") is None:
        lines.append(f"  {facts['package']}  source manifest")
    else:
        lines.append(f"  {facts['package']}  {facts['versionName']} ({facts['versionCode']})")
    lines.append(
        f"  minSdk {facts['minSdkVersion']}  targetSdk {facts['targetSdkVersion']}  "
        f"entries {facts['entries']}  dex {facts['dex_bytes']} B  "
        f"native {len(facts['native_libs'])}"
    )
    lines.append(
        f"  permissions {len(facts['permissions'])}  components {len(facts['components'])}  "
        f"size {facts['size_bytes']} B"
    )
    lines.append("")
    for result in results:
        lines.append(f"  {result['status']:<4} {result['id']} {result['title']}")
        for row in textwrap.wrap(result["detail"], 86) or [""]:
            lines.append(f"       {row}")
    counts = {s: sum(1 for r in results if r["status"] == s) for s in ("PASS", "WARN", "FAIL", "SKIP")}
    lines.append("")
    lines.append(
        f"  {len(results)} checks: {counts['PASS']} passed, {counts['WARN']} warned, "
        f"{counts['FAIL']} failed, {counts['SKIP']} skipped"
    )
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Check a FieldKit APK against the compatibility contract.")
    parser.add_argument("apk", help="path to the APK, or a source AndroidManifest.xml")
    parser.add_argument("--json", action="store_true", help="machine-readable output")
    parser.add_argument("--report", action="store_true",
                        help="always exit 0, for reporting on a known non-conforming build")
    parser.add_argument("--min-sdk", type=int, default=21,
                        help="minSdkVersion a source manifest is built with (default 21)")
    parser.add_argument("--target-sdk", type=int, default=36,
                        help="targetSdkVersion a source manifest is built with (default 36)")
    args = parser.parse_args(argv)

    try:
        if args.apk.lower().endswith(".xml"):
            facts = collect_manifest_xml(args.apk, args.min_sdk, args.target_sdk)
        else:
            facts = collect(args.apk)
    except (AxmlError, OSError, ET.ParseError) as exc:
        print(f"apkcheck: {exc}", file=sys.stderr)
        return 2

    results = check(facts)
    if args.json:
        print(json.dumps({"facts": facts, "checks": results}, indent=2))
    else:
        print(render(facts, results))

    failed = any(r["status"] == "FAIL" for r in results)
    return 0 if args.report or not failed else 1


if __name__ == "__main__":
    raise SystemExit(main())
