#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

TOML_FILE="gradle/libs.versions.toml"

read_version() {
  local key="$1"
  local value
  value=$(sed -nE "s/^${key}[[:space:]]*=[[:space:]]*\"([^\"]+)\"/\1/p" "$TOML_FILE" | head -n 1)
  if [[ -z "$value" ]]; then
    echo "Missing version key: $key" >&2
    exit 1
  fi
  printf '%s' "$value"
}

badge_escape() {
  local raw="$1"
  raw="${raw//-/--}"
  raw="${raw// /_}"
  printf '%s' "$raw"
}

KOTLIN_VERSION=$(read_version "kotlin")
COMPOSE_BOM_VERSION=$(read_version "compose-bom")
AGP_VERSION=$(read_version "agp")
MIN_SDK_VERSION=$(read_version "minSdk")
TARGET_SDK_VERSION=$(read_version "targetSdk")
GRADLE_VERSION=$(sed -nE 's/^distributionUrl=.*gradle-([0-9A-Za-z.-]+)-(bin|all)\.zip/\1/p' gradle/wrapper/gradle-wrapper.properties)
if [[ -z "$GRADLE_VERSION" ]]; then
  echo "Missing Gradle version in gradle-wrapper.properties" >&2
  exit 1
fi

KOTLIN_BADGE=$(badge_escape "$KOTLIN_VERSION")
COMPOSE_BADGE=$(badge_escape "$COMPOSE_BOM_VERSION")
AGP_BADGE=$(badge_escape "$AGP_VERSION")
GRADLE_BADGE=$(badge_escape "$GRADLE_VERSION")

SECOND_BADGE_LINE="[![Kotlin](https://img.shields.io/badge/Kotlin-${KOTLIN_BADGE}-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org) [![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-BOM_${COMPOSE_BADGE}-4285F4?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/jetpack/compose) [![Gradle](https://img.shields.io/badge/Gradle-${GRADLE_BADGE}-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org) [![AGP](https://img.shields.io/badge/AGP-${AGP_BADGE}-3DDC84?style=flat-square&logo=gradle&logoColor=white)](https://developer.android.com/studio/releases/gradle-plugin) [![Min SDK](https://img.shields.io/badge/Min_SDK-${MIN_SDK_VERSION}-brightgreen?style=flat-square&logo=android)](https://developer.android.com/about/versions) [![Target SDK](https://img.shields.io/badge/Target_SDK-${TARGET_SDK_VERSION}-blue?style=flat-square&logo=android)](https://developer.android.com/about/versions) [![Telegram](https://img.shields.io/badge/Telegram-Group-2CA5E0?style=flat-square&logo=telegram&logoColor=white)](https://t.me/+NR2QaQ4dlEgxYmNl)"

for readme in README.md README-EN.md; do
  tmp_file="$(mktemp)"
  awk -v replacement="$SECOND_BADGE_LINE" '
    /^\[!\[Kotlin\]/ { print replacement; next }
    { print }
  ' "$readme" > "$tmp_file"
  mv "$tmp_file" "$readme"
done

echo "README badges synced from $TOML_FILE and gradle-wrapper.properties"
