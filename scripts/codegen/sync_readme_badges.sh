#!/usr/bin/env bash
set -euo pipefail

# Sync README badges from libs.versions.toml + gradle-wrapper.properties.
# Badge helpers are single-sourced from magisk-ci-toolkit/codegen/badges.sh;
# this wrapper only declares the XposedSmsCode-specific badge subset + values.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

TOOLKIT_DIR="$("$ROOT_DIR/scripts/resolve_ci_toolkit.sh")"
# shellcheck source=/dev/null
source "$TOOLKIT_DIR/codegen/badges.sh"

TOML="gradle/libs.versions.toml"
WRAPPER_PROPS="gradle/wrapper/gradle-wrapper.properties"
REPO="magisk317/XposedSmsCode"

kotlin="$(read_toml_value kotlin "$TOML")"
java="$(read_toml_value java "$TOML")"
compose="$(read_toml_value compose-bom-alpha "$TOML")"
agp="$(read_toml_value agp "$TOML")"
min_sdk="$(read_toml_value minSdk "$TOML")"
target_sdk="$(read_toml_value targetSdk "$TOML")"
xposed="$(read_toml_value libxposed-api "$TOML")"
gradle_ver="$(read_gradle_version "$WRAPPER_PROPS")"

# Xposed API badge shows the major API level (e.g. 102.0.0 -> 102).
xposed_level="${xposed%%.*}"

# --- platform segment (GitHub; keeps XposedSmsCode's richer badge set) ------
plat="[![Commits](https://img.shields.io/github/commit-activity/y/${REPO}?style=flat-square)](https://github.com/${REPO}/graphs/commit-activity)"
plat="$plat [![Last Commit](https://img.shields.io/github/last-commit/${REPO}?style=flat-square)](https://github.com/${REPO}/commits)"
plat="$plat [![Contributors](https://img.shields.io/github/contributors/${REPO}?style=flat-square)](https://github.com/${REPO}/graphs/contributors)"
plat="$plat [![CI](https://img.shields.io/github/actions/workflow/status/${REPO}/ci.yml?style=flat-square&label=Build&logo=github-actions&logoColor=white)](https://github.com/${REPO}/actions/workflows/ci.yml)"
plat="$plat [![Latest Release](https://img.shields.io/github/v/release/${REPO}?include_prereleases&style=flat-square&logo=github)](https://github.com/${REPO}/releases)"
plat="$plat [![Release Date](https://img.shields.io/github/release-date/${REPO}?style=flat-square)](https://github.com/${REPO}/releases)"
plat="$plat [![Downloads](https://img.shields.io/github/downloads/${REPO}/total?style=flat-square&color=blue)](https://github.com/${REPO}/releases)"
plat="$plat [![License](https://img.shields.io/github/license/${REPO}?style=flat-square)](LICENSE)"

# --- tech segment (shared shape across all three repos) ---------------------
tech="$(tech_badge Kotlin "$kotlin" 7F52FF kotlin https://kotlinlang.org)"
tech="$tech $(tech_badge Java "${java}+" E76F00 openjdk https://openjdk.org)"
tech="$tech $(tech_badge "Jetpack Compose" "BOM ${compose}" 4285F4 android https://developer.android.com/jetpack/compose)"
tech="$tech $(tech_badge Gradle "$gradle_ver" 02303A gradle https://gradle.org)"
tech="$tech $(tech_badge AGP "$agp" 3DDC84 gradle https://developer.android.com/studio/releases/gradle-plugin)"
tech="$tech $(tech_badge "Min SDK" "$min_sdk" brightgreen android https://developer.android.com/about/versions)"
tech="$tech $(tech_badge "Target SDK" "$target_sdk" blue android https://developer.android.com/about/versions)"
tech="$tech $(tech_badge "Xposed API" "$xposed_level" orange '' https://github.com/libxposed/api)"
tech="$tech $(tech_badge Telegram Group 2CA5E0 telegram https://t.me/+NR2QaQ4dlEgxYmNl)"

for readme in README.md README-EN.md; do
  replace_block "$readme" platform "$plat"
  replace_block "$readme" tech "$tech"
done

echo "XposedSmsCode README badges synced."
