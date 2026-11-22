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
#   scripts/check-no-scans.sh [-C dir]
#       Every file in the index (tracked + staged). The allowlist is read from
#       the index too, so an unstaged allowlist edit has no effect.
#   scripts/check-no-scans.sh [-C dir] [--policy REV] --range <rev-list args>
#       Every file in each commit of the range except deletions (--diff-filter=d),
#       e.g. --range A..B, or --range B for B's whole history. The allowlist is
#       the one committed at REV (default HEAD); pass the tip being pushed.
set -euo pipefail

EXT_RE='\.(pdf|jpe?g|png|hei[cf]|tiff?|webp|gif|bmp|avif|dng|zip|7z|rar|docx|pptx|xlsx|odt|odp|ods|epub|xps|oxps|ps|eps|cbz|cbr|djvu|zst|iso)$'
# Images, PDFs, archives, and documents that embed images (Office, EPUB, XPS...).
MIME_RE='^(image/|application/(pdf|x-pdf|(x-)?zip(-compressed)?|java-archive|x-7z-compressed|x-rar(-compressed)?|vnd\.rar|x-tar|g?zip|x-gzip|x-bzip2|x-xz|x-archive|vnd\.openxmlformats-officedocument\.|vnd\.oasis\.opendocument\.|epub\+zip|vnd\.ms-xpsdocument|oxps|postscript|zstd|x-lzma|x-lzip|x-iso9660-image|vnd\.ms-cab-compressed|x-cpio|vnd\.djvu|x-djvu|msword|vnd\.ms-(powerpoint|excel)))'
ALLOWLIST=scripts/scan-allowlist.txt

command -v file >/dev/null || { echo "check-no-scans: 'file' command not found" >&2; exit 2; }
policy=HEAD
while [[ $# -gt 0 ]]; do
  case "$1" in
    -C) cd "$2"; shift 2 ;;
    --policy) policy="$2"; shift 2 ;;
    *) break ;;
  esac
done
cd "$(git rev-parse --show-toplevel)"

# The allowlist comes from git, never from the working tree.
if [[ "${1:-}" == --range ]]; then
  if ! commit_id="$(git rev-parse --verify -q "$policy^{commit}")"; then
    echo "check-no-scans: --policy '$policy' is not a commit" >&2; exit 2
  fi
  allow_spec="$commit_id:$ALLOWLIST"
else
  allow_spec=":$ALLOWLIST"
fi
declare -A allowed=()
if git cat-file -e "$allow_spec" 2>/dev/null; then
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%%#*}"                      # strip comments
    line="${line%"${line##*[![:space:]]}"}" # strip trailing whitespace
    [[ -n "$line" ]] && allowed["$line"]=1
  done < <(git cat-file blob "$allow_spec")
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
  # Sniff a real file, not a pipe: libmagic's results on stdin vary by platform.
  if ! git cat-file blob "$blob" >"$work/blob" || ! mime="$(file --mime-type -b "$work/blob")" || [[ -z "$mime" ]]; then
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
    # d (all but deletions): also covers type changes, e.g. symlink -> file.
    list_or_die "$work/paths" git diff-tree -r -m --root --no-commit-id --name-only -z --diff-filter=d "$commit"
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
