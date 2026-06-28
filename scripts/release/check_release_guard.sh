#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TOOLKIT_DIR="${ROOT_DIR}/scripts/_toolkit"

# Source the toolkit's modular release guard
source "${TOOLKIT_DIR}/release/check_release_guard.sh"

# XposedSmsCode specific configuration
MAX_LEN="${PLAY_WHATSNEW_MAX:-500}"
if [[ -n "${1:-}" ]] && [[ -d "$1" ]]; then
  TAG_NAME="${2:-}"
else
  TAG_NAME="${1:-}"
fi
REQUIRED_LOCALES=(${PLAY_WHATSNEW_REQUIRED_LOCALES:-en-US zh-CN})
ALLOW_NON_ASCII_COMMIT_SUBJECT="${ALLOW_NON_ASCII_COMMIT_SUBJECT:-false}"

# Validate numeric inputs
if [[ ! "$MAX_LEN" =~ ^[0-9]+$ ]]; then
  echo "ERROR: PLAY_WHATSNEW_MAX must be an integer, got '$MAX_LEN'." >&2
  exit 2
fi

# Run the base release guard
echo "Release guard (XposedSmsCode)"
check_version_extraction "$ROOT_DIR"
check_commit_subjects "$ROOT_DIR" "$ALLOW_NON_ASCII_COMMIT_SUBJECT"
check_changelog_section "$ROOT_DIR" "$VERSION_NAME"
check_tag_matches_version "$TAG_NAME" "$VERSION_NAME"

# XposedSmsCode specific: check whatsnew locales
whatsnew_dir="${ROOT_DIR}/distribution/whatsnew"
fail=0
for locale in "${REQUIRED_LOCALES[@]}"; do
  file="$whatsnew_dir/whatsnew-$locale"
  if [[ ! -f "$file" ]]; then
    echo "FAIL: required whatsnew locale missing: $locale ($file)"
    fail=1
    continue
  fi
  count="$(wc -m < "$file" | tr -d '[:space:]')"
  if (( count == 0 )); then
    echo "FAIL: $locale is empty ($file)"
    fail=1
  elif (( count > MAX_LEN )); then
    echo "FAIL: $locale length=$count exceeds max=$MAX_LEN ($file)"
    fail=1
  else
    echo "PASS: $locale length=$count/$MAX_LEN"
  fi
done
if (( fail != 0 )); then
  echo "Release guard failed." >&2
  exit 1
fi
if (( $? != 0 )); then
  echo "Release guard failed." >&2
  exit 1
fi

echo "Release guard passed."
