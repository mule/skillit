#!/usr/bin/env bash
# Tests scripts/check-no-scans.sh and scripts/hooks/* against throwaway git repos.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
check="$here/check-no-scans.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
fails=0
export GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@example.invalid
export GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@example.invalid
# Minimal valid PNG/PDF headers so `file --mime-type` recognises them.
png() { printf '\x89PNG\r\n\x1a\n\0\0\0\rIHDR\0\0\0\1\0\0\0\1\x08\x02\0\0\0\x90wS\xde' >"$1"; }
pdf() { printf '%%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%%%EOF\n' >"$1"; }

new_repo() {
  local dir
  dir="$(mktemp -d "$tmp/r.XXXXXX")"
  mkdir -p "$dir/scripts" "$dir/docs" "$dir/app/src/main/res/drawable-nodpi" "$dir/app/src/main/assets"
  git -C "$dir" init -q
  : >"$dir/scripts/scan-allowlist.txt"
  echo "# notes" >"$dir/docs/notes.md"
  echo "$dir"
}
add() { git -C "$1" add -A; }
commit() { git -C "$1" add -A && git -C "$1" commit -qm "${2:-c}" >/dev/null 2>&1; }

expect() { # expect <0|1> <name> <cmd...>
  local want="$1" name="$2" got=0
  shift 2
  "$@" >/dev/null 2>&1 || got=$?
  [[ "$got" -ne 0 ]] && got=1
  if [[ "$got" == "$want" ]]; then echo "PASS $name"; else echo "FAIL $name (exit $got, want $want)"; fails=1; fi
}

# --- baseline
r="$(new_repo)"
echo 'class A' >"$r/app/A.kt"; echo x >"$r/gradlew"; add "$r"
expect 0 "plain source files pass" "$check" -C "$r"

# --- F1: whole tree, not just docs/
r="$(new_repo)"; mkdir -p "$r/docs/math"; echo x >"$r/docs/math/p14.JPG"; add "$r"
expect 1 "F1 docs/ scan (mixed case) fails" "$check" -C "$r"
r="$(new_repo)"; echo x >"$r/app/src/main/assets/page14.jpg"; add "$r"
expect 1 "F1 assets/ image fails" "$check" -C "$r"
r="$(new_repo)"; echo x >"$r/app/src/main/res/drawable-nodpi/crop.jpg"; add "$r"
expect 1 "F1 res/ image fails" "$check" -C "$r"
r="$(new_repo)"; pdf "$r/page14.scan.pdf"; git -C "$r" add -f page14.scan.pdf
expect 1 "F1 root-level *.scan.pdf fails" "$check" -C "$r"
r="$(new_repo)"; pdf "$r/docs/p.scan.pdf"; echo docs/p.scan.pdf >"$r/scripts/scan-allowlist.txt"; add "$r"
expect 1 "F1 *.scan.pdf fails even when allowlisted" "$check" -C "$r"

# --- allowlist
r="$(new_repo)"; echo x >"$r/app/src/main/res/drawable-nodpi/nature_a.jpg"
printf '# pending\napp/src/main/res/drawable-nodpi/nature_a.jpg  # trailing comment\n' >"$r/scripts/scan-allowlist.txt"; add "$r"
expect 0 "allowlisted image passes" "$check" -C "$r"
echo x >"$r/app/src/main/res/drawable-nodpi/nature_b.jpg"; add "$r"
expect 1 "unlisted image next to allowlisted fails" "$check" -C "$r"

# --- F3: wider extensions + extensionless by content
for ext in pdf jpg jpeg png heic heif tif tiff webp gif bmp avif dng zip 7z rar; do
  r="$(new_repo)"; echo x >"$r/docs/file.$ext"; add "$r"
  expect 1 "F3 *.$ext fails" "$check" -C "$r"
done
r="$(new_repo)"; png "$r/docs/page14"; add "$r"
expect 1 "F3 extensionless PNG content fails" "$check" -C "$r"
r="$(new_repo)"; pdf "$r/page15"; add "$r"
expect 1 "F3 extensionless PDF content fails" "$check" -C "$r"
r="$(new_repo)"; printf '#!/bin/sh\necho hi\n' >"$r/tool"; add "$r"
expect 0 "F3 extensionless script passes" "$check" -C "$r"

r="$(new_repo)"; png "$r/docs/page14.txt"; add "$r"
expect 1 "F3 image renamed to .txt fails (content sniff)" "$check" -C "$r"
r="$(new_repo)"; (cd "$r" && echo x >inner.txt && git hash-object -w inner.txt >/dev/null && git archive --format=zip -o w.jar "$(printf '100644 blob %s\tinner.txt\n' "$(git hash-object inner.txt)" | git mktree)" && rm inner.txt); add "$r"
expect 1 "F3 zip with other extension fails" "$check" -C "$r"
r="$(new_repo)"; echo 'x' >"$r/docs/a.md"; add "$r"
expect 1 "missing 'file' command fails closed" env PATH="$tmp/nofile" /bin/bash "$check" -C "$r"

# --- F4: newline in name, exact match only
r="$(new_repo)"; echo x >"$r/docs/diagram.png"; echo docs/diagram.png >"$r/scripts/scan-allowlist.txt"
echo x >"$r/docs/diagram.png"$'\n'".png"; add "$r"
expect 1 "F4 newline in name bypassing allowlist fails" "$check" -C "$r"
r="$(new_repo)"; echo x >"$r/docs/diagram.png.png"; echo docs/diagram.png >"$r/scripts/scan-allowlist.txt"; add "$r"
expect 1 "F4 allowlist prefix is not a match" "$check" -C "$r"
r="$(new_repo)"; echo x >"$r/docs/d.png"; echo 'docs/*.png' >"$r/scripts/scan-allowlist.txt"; add "$r"
expect 1 "F4 allowlist glob is not a match" "$check" -C "$r"

# --- F2: every commit in a range, not just the tip
r="$(new_repo)"; commit "$r" base; base="$(git -C "$r" rev-parse HEAD)"
echo x >"$r/docs/p.jpg"; commit "$r" add-scan
git -C "$r" rm -q docs/p.jpg; commit "$r" remove-scan
expect 0 "F2 tip tree is clean" "$check" -C "$r"
expect 1 "F2 range catches scan added then removed" "$check" -C "$r" --range "$base..HEAD"
expect 1 "F2 range catches it in root-inclusive history" "$check" -C "$r" --range HEAD
r="$(new_repo)"; commit "$r" base; base="$(git -C "$r" rev-parse HEAD)"; echo 'x' >"$r/docs/more.md"; commit "$r"
expect 0 "F2 clean range passes" "$check" -C "$r" --range "$base..HEAD"
r="$(new_repo)"; commit "$r" base; base="$(git -C "$r" rev-parse HEAD)"
echo x >"$r/docs/a.md"; commit "$r"; git -C "$r" mv docs/a.md docs/a.jpg; commit "$r" rename
expect 1 "F2 range catches rename to image" "$check" -C "$r" --range "$base..HEAD"

expect 1 "F2 bad range fails closed" "$check" -C "$r" --range no-such-ref..HEAD
r="$(new_repo)"; commit "$r" base
git -C "$r" checkout -qb side; echo x >"$r/docs/s.md"; commit "$r" side
git -C "$r" checkout -q -; echo x >"$r/docs/m.md"; commit "$r" main2; base="$(git -C "$r" rev-parse HEAD)"
git -C "$r" merge -q --no-commit side >/dev/null 2>&1; echo x >"$r/docs/evil.jpg"; git -C "$r" add -A
git -C "$r" commit -qm merge --no-verify >/dev/null
expect 1 "F2 scan hidden in a merge commit fails" "$check" -C "$r" --range "$base..HEAD"

# --- F2: hooks
hooks="$here/hooks"
r="$(new_repo)"; mkdir -p "$r/scripts/hooks"; cp "$check" "$r/scripts/"; cp "$hooks"/* "$r/scripts/hooks/"
git -C "$r" config core.hooksPath scripts/hooks; commit "$r" base
echo x >"$r/docs/p.jpg"; git -C "$r" add docs/p.jpg
expect 1 "F2 pre-commit hook blocks scan" git -C "$r" commit -qm scan
git -C "$r" rm -q --cached docs/p.jpg; rm "$r/docs/p.jpg"
echo ok >"$r/docs/ok.md"; git -C "$r" add docs/ok.md
expect 0 "F2 pre-commit hook allows clean commit" git -C "$r" commit -qm ok

remote="$tmp/remote.git"; git init -q --bare "$remote"
git -C "$r" remote add origin "$remote"
expect 0 "F2 pre-push allows clean push" git -C "$r" push -q origin HEAD:refs/heads/main
echo x >"$r/docs/p.jpg"; git -C "$r" add docs/p.jpg; git -C "$r" commit -qm scan --no-verify
git -C "$r" rm -q docs/p.jpg; git -C "$r" commit -qm unscan >/dev/null 2>&1
expect 1 "F2 pre-push blocks scan in pushed history" git -C "$r" push -q origin HEAD:refs/heads/main
expect 1 "F2 pre-push blocks scan on a new branch" git -C "$r" push -q origin HEAD:refs/heads/topic

exit "$fails"
