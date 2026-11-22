#!/usr/bin/env bash
# Scan guard: keeps scans/photos of book pages (copyrighted, with a child's
# handwriting) out of this public repo. See README "Source material".
#
# A path is rejected when it, anywhere in the tree:
#   - ends in .scan.pdf (always, even if allowlisted),
#   - contains a newline (always),
#   - has an image/PDF/archive extension (see EXT_RE) and is not listed exactly
#     in scripts/scan-allowlist.txt,
#   - has any other name but `file --mime-type` says image/*, PDF or an archive,
#     and it is not allowlisted (so renaming page.jpg to page.txt doesn't help).
# It fails closed: a git error or missing `file` command is a failure.
#
# Usage:
#   scripts/check-no-scans.sh [-C dir]                 every file in the index (tracked + staged)
#   scripts/check-no-scans.sh [-C dir] --range A..B    every file added/changed in each commit of A..B
#   scripts/check-no-scans.sh [-C dir] --range B --not --remotes   (any rev-list arguments)
# The allowlist is always read from the working tree (or index) of the checked repo.
set -euo pipefail

EXT_RE='\.(pdf|jpe?g|png|hei[cf]|tiff?|webp|gif|bmp|avif|dng|zip|7z|rar)$'
MIME_RE='^(image/|application/(pdf|zip|x-7z-compressed|x-rar|vnd\.rar))'
ALLOWLIST=scripts/scan-allowlist.txt

command -v file >/dev/null || { echo "check-no-scans: 'file' command not found" >&2; exit 2; }
if [[ "${1:-}" == -C ]]; then cd "$2"; shift 2; fi
cd "$(git rev-parse --show-toplevel)"

declare -A allowed=()
if [[ -f "$ALLOWLIST" ]]; then
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%%#*}"                      # strip comments
    line="${line%"${line##*[![:space:]]}"}" # strip trailing whitespace
    [[ -n "$line" ]] && allowed["$line"]=1
  done <"$ALLOWLIST"
fi

bad=()

# check_path <path> <blob-spec>   blob-spec is what `git cat-file blob` takes (":path" or "commit:path")
check_path() {
  local path="$1" blob="$2" lower
  if [[ "$path" == *$'\n'* ]]; then bad+=("$path (newline in name)"); return; fi
  lower="${path,,}"
  if [[ "$lower" == *.scan.pdf ]]; then bad+=("$path"); return; fi
  [[ -n "${allowed[$path]-}" ]] && return
  if [[ "$lower" =~ $EXT_RE ]]; then bad+=("$path"); return; fi
  local mime
  if ! mime="$(git cat-file blob "$blob" | file --mime-type -b -)" || [[ -z "$mime" ]]; then
    bad+=("$path (could not read content)"); return
  fi
  if [[ "$mime" =~ $MIME_RE ]]; then bad+=("$path ($mime)"); fi
}

# list_or_die <file> <cmd...>  writes a git listing to <file>, exiting on failure
# (a process substitution would silently swallow the error and pass).
list_or_die() {
  local out="$1"; shift
  if ! "$@" >"$out"; then echo "check-no-scans: '$*' failed" >&2; exit 2; fi
}
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

if [[ "${1:-}" == --range ]]; then
  shift
  list_or_die "$work/commits" git rev-list "$@"
  while IFS= read -r commit; do
    # -m: diff merge commits against every parent, so merges can't hide files.
    list_or_die "$work/paths" git diff-tree -r -m --root --no-commit-id --name-only -z --diff-filter=AMRC "$commit"
    while IFS= read -r -d '' path; do
      check_path "$path" "$commit:$path"
    done <"$work/paths"
  done <"$work/commits"
else
  list_or_die "$work/paths" git ls-files --cached -z
  while IFS= read -r -d '' path; do
    check_path "$path" ":$path"
  done <"$work/paths"
fi

if ((${#bad[@]})); then
  echo "ERROR: these files look like scans/photos of source material and must not be committed:" >&2
  printf '  %q\n' "${bad[@]}" >&2
  echo "Remove them (git rm --cached <file>; rewrite the commit if it was already made)." >&2
  echo "A genuine non-scan image needs an exact entry in $ALLOWLIST (code-owner review)." >&2
  exit 1
fi
echo "check-no-scans: OK"
