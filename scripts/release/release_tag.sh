#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TOOLKIT_DIR="${ROOT_DIR}/scripts/_toolkit"

# Source the toolkit's modular release tag
source "${TOOLKIT_DIR}/release/release_tag.sh"

# XposedSmsCode specific: run detekt SARIF check
run_pre_push_checks() {
  run_detekt_sarif_check "$ROOT_DIR"
}

# Run the release tag with XposedSmsCode configuration
release_tag "$ROOT_DIR"
