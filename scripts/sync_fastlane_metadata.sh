#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_FILE="$ROOT_DIR/gradle/libs.versions.toml"
FASTLANE_ROOT="$ROOT_DIR/fastlane/metadata/android"
MODE="write"
STATUS=0

usage() {
  cat <<'EOF'
Usage:
  scripts/sync_fastlane_metadata.sh [--check]

Options:
  --check   Validate metadata is synchronized, do not modify files.
EOF
}

extract_toml_value() {
  local key="$1"
  local file="$2"
  sed -nE "s/^${key}[[:space:]]*=[[:space:]]*\"([^\"]+)\"/\1/p" "$file" | head -n1
}

fail() {
  local message="$1"
  echo "ERROR: $message" >&2
  STATUS=1
}

if [[ $# -gt 1 ]]; then
  usage
  exit 2
fi

if [[ $# -eq 1 ]]; then
  case "$1" in
    --check)
      MODE="check"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "ERROR: unknown option '$1'" >&2
      usage
      exit 2
      ;;
  esac
fi

if [[ ! -f "$VERSION_FILE" ]]; then
  echo "ERROR: missing version file: $VERSION_FILE" >&2
  exit 2
fi

VERSION_CODE="$(extract_toml_value "versionCode" "$VERSION_FILE")"
if [[ -z "$VERSION_CODE" ]]; then
  echo "ERROR: failed to parse versionCode from $VERSION_FILE" >&2
  exit 2
fi

sync_locale() {
  local locale="$1"
  local whatsnew_source="$2"
  local art_source="$3"
  local locale_root="$FASTLANE_ROOT/$locale"
  local changelog_target="$locale_root/changelogs/$VERSION_CODE.txt"
  local screenshots_dir="$locale_root/images/phoneScreenshots"

  if [[ ! -f "$whatsnew_source" ]]; then
    fail "missing source file: $whatsnew_source"
    return
  fi
  if [[ ! -d "$art_source" ]]; then
    fail "missing screenshot source directory: $art_source"
    return
  fi

  local files=()
  mapfile -t files < <(find "$art_source" -maxdepth 1 -type f -name '*.png' | sort)
  if [[ ${#files[@]} -eq 0 ]]; then
    fail "no PNG screenshots found in $art_source"
    return
  fi

  if [[ "$MODE" == "write" ]]; then
    mkdir -p "$locale_root/changelogs" "$screenshots_dir"
    cp "$whatsnew_source" "$changelog_target"
    find "$screenshots_dir" -maxdepth 1 -type f -name '*.png' -delete
    local i=1
    local src=""
    for src in "${files[@]}"; do
      cp "$src" "$screenshots_dir/${i}.png"
      i=$((i + 1))
    done
    echo "SYNC: $locale changelog and ${#files[@]} screenshots"
    return
  fi

  if [[ ! -f "$changelog_target" ]]; then
    fail "missing target changelog: $changelog_target"
  elif ! cmp -s "$whatsnew_source" "$changelog_target"; then
    fail "changelog out of sync for $locale: $changelog_target"
  fi

  if [[ ! -d "$screenshots_dir" ]]; then
    fail "missing target screenshot directory: $screenshots_dir"
    return
  fi

  local j=1
  local src_file=""
  for src_file in "${files[@]}"; do
    local target_file="$screenshots_dir/${j}.png"
    if [[ ! -f "$target_file" ]]; then
      fail "missing screenshot: $target_file"
    elif ! cmp -s "$src_file" "$target_file"; then
      fail "screenshot out of sync for $locale: $target_file"
    fi
    j=$((j + 1))
  done

  local expected_count="${#files[@]}"
  local actual_count
  actual_count="$(find "$screenshots_dir" -maxdepth 1 -type f -name '*.png' | wc -l | tr -d '[:space:]')"
  if [[ "$actual_count" != "$expected_count" ]]; then
    fail "unexpected screenshot count for $locale: expected=$expected_count actual=$actual_count"
  else
    echo "CHECK: $locale changelog and screenshots are synchronized"
  fi
}

sync_locale \
  "en-US" \
  "$ROOT_DIR/distribution/whatsnew/whatsnew-en-US" \
  "$ROOT_DIR/art/en"

sync_locale \
  "zh-CN" \
  "$ROOT_DIR/distribution/whatsnew/whatsnew-zh-CN" \
  "$ROOT_DIR/art/cn"

if (( STATUS != 0 )); then
  exit 1
fi

if [[ "$MODE" == "write" ]]; then
  echo "Fastlane metadata synchronized for versionCode=$VERSION_CODE"
else
  echo "Fastlane metadata check passed for versionCode=$VERSION_CODE"
fi
