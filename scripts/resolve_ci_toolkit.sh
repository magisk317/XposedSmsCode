#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLKIT_DIR="${MAGISK_CI_TOOLKIT_DIR:-${ROOT_DIR}/.magisk-ci-toolkit}"
TOOLKIT_REPOSITORY="${MAGISK_CI_TOOLKIT_REPOSITORY:-https://gitlab.com/magisk3171/magisk-ci-toolkit.git}"
TOOLKIT_REF="${MAGISK_CI_TOOLKIT_REF:-main}"

if [[ ! -d "$TOOLKIT_DIR/.git" ]]; then
  rm -rf "$TOOLKIT_DIR"
  git clone --depth 1 --branch "$TOOLKIT_REF" "$TOOLKIT_REPOSITORY" "$TOOLKIT_DIR" >&2
else
  git -C "$TOOLKIT_DIR" remote set-url origin "$TOOLKIT_REPOSITORY" >&2
  git -C "$TOOLKIT_DIR" fetch --depth 1 origin "$TOOLKIT_REF" >&2
  git -C "$TOOLKIT_DIR" checkout --detach FETCH_HEAD >&2
fi

printf '%s\n' "$TOOLKIT_DIR"
