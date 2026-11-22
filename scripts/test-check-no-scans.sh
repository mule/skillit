#!/usr/bin/env bash
# Tests scripts/check-no-scans.sh against throwaway git repo fixtures.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
check="$here/check-no-scans.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
fails=0

new_repo() {
  local dir="$tmp/$1"
  mkdir -p "$dir/scripts" "$dir/docs"
  git -C "$dir" init -q
  : > "$dir/scripts/scan-allowlist.txt"
  echo "$dir"
}

expect() { # expect <0|1> <name> <dir>
  local want="$1" name="$2" dir="$3" got=0
  "$check" "$dir" >/dev/null 2>&1 || got=$?
  [[ "$got" -ne 0 ]] && got=1
  if [[ "$got" == "$want" ]]; then echo "PASS $name"; else echo "FAIL $name (exit $got, want $want)"; fails=1; fi
}

# Good file only: markdown under docs, image outside docs.
r="$(new_repo good)"
echo "# notes" > "$r/docs/notes.md"
mkdir -p "$r/app"; echo x > "$r/app/icon.png"
git -C "$r" add -A
expect 0 "good files pass" "$r"

# Bad file staged under docs/ (mixed-case extension, nested dir).
r="$(new_repo bad)"
echo "# notes" > "$r/docs/notes.md"
mkdir -p "$r/docs/math"; echo x > "$r/docs/math/page-14.JPG"
git -C "$r" add -A
expect 1 "staged scan under docs fails" "$r"

# Each extension is caught.
for ext in pdf jpg jpeg png heic; do
  r="$(new_repo "ext-$ext")"
  echo x > "$r/docs/file.$ext"; git -C "$r" add -A
  expect 1 "docs/*.$ext fails" "$r"
done

# Allowlisted path passes; a second, unlisted one still fails.
r="$(new_repo allow)"
echo x > "$r/docs/diagram.png"
printf '# ok: hand-drawn diagram\ndocs/diagram.png\n' > "$r/scripts/scan-allowlist.txt"
git -C "$r" add -A
expect 0 "allowlisted file passes" "$r"
echo x > "$r/docs/other.png"; git -C "$r" add -A
expect 1 "unlisted file next to allowlisted fails" "$r"

exit "$fails"
