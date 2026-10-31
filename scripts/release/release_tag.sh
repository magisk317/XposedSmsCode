#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TOOLKIT_DIR="$("$ROOT_DIR/scripts/resolve_ci_toolkit.sh")"
bash "$TOOLKIT_DIR/ci/verify_toolkit_pin.sh" --root "$ROOT_DIR"

# Source the toolkit's modular release tag
export MAGISK_RELEASE_GUARD_SCRIPT="${SCRIPT_DIR}/check_release_guard.sh"
source "${TOOLKIT_DIR}/release/release_tag.sh"

# XposedSmsCode specific: run pre-push CI checks
run_pre_push_checks() {
  run_common_gradle_checks "$ROOT_DIR" \
    --warning-mode all \
    :presentation:check \
    :runtime:check \
    :app:check \
    :app:compileGithubDebugAndroidTestKotlin \
    :app:compilePlayDebugAndroidTestKotlin \
    :presentation:compilePlayDebugUnitTestKotlin \
    :presentation:generatePlayDebugUnitTestStubRFile \
    :runtime:compileGithubDebugAndroidTestKotlin \
    :runtime:compilePlayDebugAndroidTestKotlin \
    :app:assembleGithubDebug \
    -PbuildSplits
}

# Run the release tag with XposedSmsCode configuration
release_tag "$ROOT_DIR"
