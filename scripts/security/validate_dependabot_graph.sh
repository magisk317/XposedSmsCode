#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLKIT_SCRIPT="${SCRIPT_DIR}/../_toolkit/security/validate_dependabot_graph.sh"

# XposedSmsCode configuration
export MAGISK_ROOT_DEPTH=3

exec bash "${TOOLKIT_SCRIPT}" "$@"
