#!/usr/bin/env bash
# Fails if any tracked or staged PDF/JPG/JPEG/PNG/HEIC file exists under docs/,
# unless its exact repo-relative path is listed in scripts/scan-allowlist.txt.
# Scans of book pages (with a child's handwriting) must never be committed.
# Usage: scripts/check-no-scans.sh [repo-dir]   (default: current repo)
set -euo pipefail

repo="${1:-$(git rev-parse --show-toplevel)}"
cd "$repo"
allowlist="scripts/scan-allowlist.txt"

allowed=""
if [[ -f "$allowlist" ]]; then
  # Strip comments and blank lines.
  allowed="$(sed -e 's/#.*//' -e 's/[[:space:]]*$//' "$allowlist" | grep -v '^$' || true)"
fi

# --cached covers both committed and staged files.
bad=()
while IFS= read -r -d '' path; do
  if [[ -n "$allowed" ]] && grep -qxF -- "$path" <<<"$allowed"; then
    continue
  fi
  bad+=("$path")
done < <(git ls-files --cached -z -- docs \
  | grep -zEi '\.(pdf|jpe?g|png|heic)$' || true)

if ((${#bad[@]})); then
  echo "ERROR: image/PDF files under docs/ look like source scans and must not be committed:" >&2
  printf '  %s\n' "${bad[@]}" >&2
  echo "Remove them (git rm --cached <file>) or, if not a scan, add the path to $allowlist." >&2
  exit 1
fi
echo "check-no-scans: OK (no scans under docs/)"
