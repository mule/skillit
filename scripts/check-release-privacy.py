#!/usr/bin/env python3
"""Release A privacy gate (#26 security review F4).

1. The merged *release* manifest may only contain the permissions and components listed in
   app/release-manifest-allowlist.txt, and must not contain ML Kit's telemetry upload backend.
2. No network client imports in app/engine/mathgen production sources until M4b (sync) is designed.

Run after `./gradlew :app:processReleaseManifest`. Exit code 1 on any violation.
Update the allowlist only together with a privacy review (issue #30).
"""
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml"
ALLOWLIST = ROOT / "app/release-manifest-allowlist.txt"
SOURCE_ROOTS = tuple(ROOT / module / "src/main" for module in ("app", "engine", "mathgen"))
A = "{http://schemas.android.com/apk/res/android}"

FORBIDDEN_BACKEND = "backend:com.google.android.datatransport.cct.CctBackendFactory"
NETWORK_IMPORTS = re.compile(
    r"^\s*import\s+(java\.net\.(URL|URI|HttpURLConnection|Socket|ServerSocket|DatagramSocket)\b"
    r"|javax\.net\.|okhttp3\.|okio\.|retrofit2\.|io\.ktor\.client\.|com\.android\.volley\.|android\.net\.http\.)",
    re.M,
)


def manifest_entries(path):
    root = ET.parse(path).getroot()
    entries = {f"permission {e.get(A + 'name')}" for e in root.iter("uses-permission")}
    app = root.find("application")
    for kind in ("activity", "activity-alias", "service", "receiver", "provider"):
        for e in app.iter(kind):
            entries.add(f"{kind} {e.get(A + 'name')}")
    backends = {m.get(A + "name") for m in app.iter("meta-data") if (m.get(A + "name") or "").startswith("backend:")}
    return entries, backends


def main():
    problems = []
    if not MANIFEST.exists():
        sys.exit(f"Missing {MANIFEST}. Run ./gradlew :app:processReleaseManifest first.")
    entries, backends = manifest_entries(MANIFEST)
    allowed = {l.strip() for l in ALLOWLIST.read_text().splitlines() if l.strip() and not l.startswith("#")}
    for e in sorted(entries - allowed):
        problems.append(f"manifest: not in allowlist: {e}")
    for e in sorted(allowed - entries):
        problems.append(f"manifest: allowlisted but gone (tidy the allowlist): {e}")
    if FORBIDDEN_BACKEND in backends:
        problems.append("manifest: ML Kit telemetry upload backend (CCT) is back; see AndroidManifest.xml")
    sources = sorted(f for source in SOURCE_ROOTS for pattern in ("*.kt", "*.java") for f in source.rglob(pattern))
    for f in sources:
        text = f.read_text(encoding="utf-8")
        for m in NETWORK_IMPORTS.finditer(text):
            line = text[: m.start()].count("\n") + 1
            problems.append(f"source: network client import in {f.relative_to(ROOT)}:{line}: {m.group(0).strip()}")
    if problems:
        print("Release privacy check FAILED:\n  " + "\n  ".join(problems))
        sys.exit(1)
    print(f"Release privacy check OK: {len(entries)} manifest entries allowlisted, no telemetry backend, no network imports.")


if __name__ == "__main__":
    main()
