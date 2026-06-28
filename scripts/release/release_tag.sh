#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TOOLKIT_DIR="${ROOT_DIR}/scripts/_toolkit"

# Source the toolkit's modular release tag
export MAGISK_RELEASE_GUARD_SCRIPT="${SCRIPT_DIR}/check_release_guard.sh"
source "${TOOLKIT_DIR}/release/release_tag.sh"

# XposedSmsCode specific: run pre-push CI checks
run_pre_push_checks() {
  run_common_gradle_checks "$ROOT_DIR" \
    --warning-mode all \
    :core:check \
    :runtime:check \
    :app:check \
    :app:compileGithubDebugAndroidTestKotlin \
    :app:compilePlayDebugAndroidTestKotlin \
    :core:compilePlayDebugUnitTestKotlin \
    :core:generatePlayDebugUnitTestStubRFile \
    :runtime:compileDebugAndroidTestKotlin \
    :app:assembleGithubDebug \
    -PbuildSplits
}

# Run the release tag with XposedSmsCode configuration
release_tag "$ROOT_DIR"
