#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TOOLKIT_DIR="$("$ROOT_DIR/scripts/resolve_ci_toolkit.sh")"
TOOLKIT_SCRIPT="${TOOLKIT_DIR}/security/validate_dependabot_graph.sh"

# XposedSmsCode configuration
export MAGISK_ROOT_DEPTH=3

exec bash "${TOOLKIT_SCRIPT}" "$@"
