#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "${ROOT_DIR}"

# Resolve the same task graph that CI relies on so dependency locks stay aligned
# with the configurations we actually exercise in automation.
bash "${ROOT_DIR}/scripts/with_workspace_gradle_lock.sh" \
  --ignore-submodule-lockfiles \
  --write-locks \
  --warning-mode all \
  :core:check \
  :runtime:check \
  :app:check \
  :app:compileGithubDebugAndroidTestKotlin \
  :app:compilePlayDebugAndroidTestKotlin \
  :runtime:compileGithubDebugAndroidTestKotlin \
  :runtime:compilePlayDebugAndroidTestKotlin \
  assembleGithubDebug \
  :app:koverVerifyGithubDebug \
  :app:koverHtmlReportGithubDebug \
  -PbuildSplits \
  "$@"
