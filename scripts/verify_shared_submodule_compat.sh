#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR"

bash scripts/with_workspace_gradle_lock.sh \
  --ignore-submodule-lockfiles \
  :smscode-core:domain:testDebugUnitTest \
  :core:testGithubDebugUnitTest \
  :core:compileGithubDebugKotlin \
  :app:check
