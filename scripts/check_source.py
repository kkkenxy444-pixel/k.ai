#!/usr/bin/env python3
"""Static source invariants; this does not replace compilation or device testing."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
android = "{http://schemas.android.com/apk/res/android}"
permissions = {node.attrib[android + "name"] for node in manifest.findall("uses-permission")}
assert permissions == {"android.permission.INTERNET"}, permissions
application = manifest.find("application")
assert application is not None
assert application.attrib[android + "allowBackup"] == "false"
assert application.attrib[android + "usesCleartextTraffic"] == "false"

key_pattern = re.compile(r"AIza[0-9A-Za-z_-]{35}")
bad = []
for path in ROOT.rglob("*"):
    if not path.is_file() or any(part in {"build", ".gradle", ".kotlin", ".git", ".idea"} for part in path.relative_to(ROOT).parts):
        continue
    if path.name == "local.properties" or path.suffix in {".jar", ".png", ".zip", ".apk"}:
        continue
    text = path.read_text(errors="ignore")
    if key_pattern.search(text):
        bad.append(str(path.relative_to(ROOT)))
    if "/src/main/" in str(path) and path.suffix == ".kt":
        for forbidden in ["android.webkit.WebView", "android.media.MediaRecorder", "android.speech.SpeechRecognizer", "fallbackToDestructiveMigration(", "HttpLoggingInterceptor"]:
            assert forbidden not in text, (path, forbidden)
assert not bad, "Possible embedded credentials in: " + ", ".join(bad)
assert (ROOT / "gradle/wrapper/gradle-wrapper.jar").is_file()
properties = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text()
assert "distributionSha256Sum=" in properties
print("PASS: internet-only manifest, disabled cleartext/backup, no runtime microphone/WebView/destructive migration/logging, no embedded Google-key pattern, pinned wrapper present.")
