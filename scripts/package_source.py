#!/usr/bin/env python3
"""Create a reproducible source handoff ZIP; excludes local SDK paths, outputs and secrets."""
from pathlib import Path
import hashlib
import sys
import zipfile

root = Path(__file__).resolve().parents[1]
out = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else root.parent / "kai-android-project.zip"
excluded = {"build", ".gradle", ".kotlin", ".git", ".idea", "captures"}
files = [p for p in root.rglob("*") if p.is_file()
         and not (set(p.relative_to(root).parts) & excluded)
         and p.name != "local.properties"
         and p.suffix not in {".apk", ".aab", ".jks", ".keystore", ".hprof", ".zip"}
         and not p.name.endswith(".env")]
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for path in sorted(files):
        info = zipfile.ZipInfo("kai/" + path.relative_to(root).as_posix(), (2026, 11, 15, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = (0o100755 if path.name == "gradlew" or path.suffix == ".py" else 0o100644) << 16
        archive.writestr(info, path.read_bytes())
print(f"{out}: {len(files)} files, {out.stat().st_size} bytes")
print("SHA-256 " + hashlib.sha256(out.read_bytes()).hexdigest())
