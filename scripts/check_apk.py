#!/usr/bin/env python3
"""Inspect built bytes; does not replace installation or live API testing."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("apk", type=Path)
parser.add_argument("--aapt2", required=True)
parser.add_argument("--release", action="store_true")
args = parser.parse_args()
badging = subprocess.check_output([args.aapt2, "dump", "badging", str(args.apk)], text=True)
manifest = subprocess.check_output([args.aapt2, "dump", "xmltree", str(args.apk), "--file", "AndroidManifest.xml"], text=True)
permissions = re.findall(r"uses-permission: name='([^']+)'", badging)
forbidden = {"android.permission.RECORD_AUDIO", "android.permission.CAMERA", "android.permission.CALL_PHONE", "android.permission.READ_CONTACTS", "android.permission.MANAGE_EXTERNAL_STORAGE"}
assert not forbidden.intersection(permissions), permissions
assert re.search(r"allowBackup\([^\n]+\)=false", manifest)
assert re.search(r"usesCleartextTraffic\([^\n]+\)=false", manifest)
if args.release:
    assert not re.search(r"debuggable\([^\n]+\)=true", manifest)
    assert "androidx.compose.ui.tooling.PreviewActivity" not in manifest
with zipfile.ZipFile(args.apk) as archive:
    assert "classes.dex" in archive.namelist()
    for name in archive.namelist():
        assert not re.search(rb"AIza[0-9A-Za-z_-]{35}", archive.read(name)), f"Possible credential in {name}"
print(json.dumps({"apk": args.apk.name, "bytes": args.apk.stat().st_size,
    "sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(), "permissions": permissions,
    "credentials_pattern_found": False, "backup": False, "cleartext": False,
    "release_checks": args.release, "result": "PASS"}, indent=2))
