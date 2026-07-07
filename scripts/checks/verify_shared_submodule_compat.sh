#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "$ROOT_DIR"
TOOLKIT_DIR="$("$ROOT_DIR/scripts/resolve_ci_toolkit.sh")"

bash "$TOOLKIT_DIR/gradle/run_gradle_with_retry.sh" \
  verifyModuleBoundaries \
  :smscode-core:domain:testDebugUnitTest \
  :smscode-core:verification:detekt \
  :smscode-core:hook:lintDebug \
  :smscode-core:runtime:lintDebug \
  :core:testGithubDebugUnitTest \
  :core:compileGithubDebugKotlin \
  :app:check
