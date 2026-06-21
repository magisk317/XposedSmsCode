#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERSION_FILE="$ROOT_DIR/gradle/libs.versions.toml"

working_tree_dirty() {
  if ! git -C "$ROOT_DIR" diff --quiet || ! git -C "$ROOT_DIR" diff --cached --quiet; then
    return 0
  fi
  [[ -n "$(git -C "$ROOT_DIR" status --porcelain)" ]]
}

count_sarif_results() {
  local sarif_file="$1"
  if command -v jq >/dev/null 2>&1; then
    jq '[.runs[]?.results[]?] | length' "$sarif_file"
  else
    # Fallback: count ruleId occurrences when jq is unavailable.
    grep -o '"ruleId"' "$sarif_file" | wc -l | tr -d '[:space:]'
  fi
}

run_pre_push_checks() {
  echo "Running pre-push CI command..."
  (
    cd "$ROOT_DIR"
    ./gradlew --no-daemon --warning-mode all \
      :core:check \
      :runtime:check \
      :app:check \
      assembleGithubDebug \
      -PbuildSplits
  )

  echo "Running pre-push Detekt command..."
  (
    cd "$ROOT_DIR"
    ./gradlew --no-daemon detekt --continue
  )

  local sarif_files=(
    "$ROOT_DIR/app/build/reports/detekt/detekt.sarif"
    "$ROOT_DIR/core/build/reports/detekt/detekt.sarif"
    "$ROOT_DIR/runtime/build/reports/detekt/detekt.sarif"
  )
  local found_report=0
  local total_findings=0
  local findings=0
  local sarif_file
  for sarif_file in "${sarif_files[@]}"; do
    if [[ -f "$sarif_file" ]]; then
      found_report=1
      findings="$(count_sarif_results "$sarif_file")"
      findings="${findings:-0}"
      total_findings=$((total_findings + findings))
      echo "Detekt findings: $findings ($sarif_file)"
    fi
  done

  if [[ "$found_report" -eq 0 ]]; then
    echo "ERROR: no Detekt SARIF reports found after detekt run." >&2
    exit 1
  fi

  if [[ "$total_findings" -ne 0 ]]; then
    echo "ERROR: Detekt findings must be 0 before push. total_findings=$total_findings" >&2
    exit 1
  fi

  echo "Pre-push checks passed: CI success and Detekt findings=0"
}

extract_toml_value() {
  local key="$1"
  local file="$2"
  sed -nE "s/^${key}[[:space:]]*=[[:space:]]*\"([^\"]+)\"/\1/p" "$file" | head -n1
}

VERSION_NAME="$(extract_toml_value "versionName" "$VERSION_FILE")"
if [[ -z "$VERSION_NAME" ]]; then
  echo "ERROR: failed to parse versionName from $VERSION_FILE" >&2
  exit 2
fi

TAG_NAME="v$VERSION_NAME"
REMOTE_NAME="${RELEASE_REMOTE:-origin}"
BRANCH_SYNC_CHANGED=0

current_branch="$(git -C "$ROOT_DIR" branch --show-current)"
if [[ -z "$current_branch" ]]; then
  echo "ERROR: detached HEAD is not supported for release_tag.sh" >&2
  exit 1
fi

"$ROOT_DIR/scripts/release/check_release_guard.sh" "$TAG_NAME"
run_pre_push_checks

if working_tree_dirty; then
  echo "ERROR: working tree is not clean. Commit/stash changes before tagging." >&2
  exit 1
fi

delete_local_tag_if_exists() {
  if git -C "$ROOT_DIR" rev-parse -q --verify "refs/tags/$TAG_NAME" >/dev/null; then
    local old_ref
    old_ref="$(git -C "$ROOT_DIR" rev-list -n 1 "$TAG_NAME" 2>/dev/null || true)"
    echo "WARN: local tag exists, deleting before retag: $TAG_NAME (${old_ref:-unknown})"
    git -C "$ROOT_DIR" tag -d "$TAG_NAME" >/dev/null
  fi
}

delete_remote_tag_if_exists() {
  local remote_output
  local remote_ref
  if ! remote_output="$(git -C "$ROOT_DIR" ls-remote --tags "$REMOTE_NAME" "refs/tags/$TAG_NAME")"; then
    echo "ERROR: failed to query remote tags from $REMOTE_NAME" >&2
    exit 1
  fi
  remote_ref="$(printf '%s\n' "$remote_output" | awk '{print $1}' | head -n1)"
  if [[ -n "$remote_ref" ]]; then
    echo "WARN: remote tag exists, deleting before retag: $TAG_NAME ($remote_ref)"
    if ! git -C "$ROOT_DIR" push "$REMOTE_NAME" ":refs/tags/$TAG_NAME"; then
      echo "ERROR: failed to delete remote tag $TAG_NAME from $REMOTE_NAME" >&2
      exit 1
    fi
  fi
}

delete_local_tag_if_exists
delete_remote_tag_if_exists

git -C "$ROOT_DIR" tag -s "$TAG_NAME" -m "$TAG_NAME"
git -C "$ROOT_DIR" push --force-with-lease "$REMOTE_NAME" "$current_branch"
git -C "$ROOT_DIR" push "$REMOTE_NAME" "$TAG_NAME"

echo "Created and pushed tag: $TAG_NAME (branch: $current_branch, remote: $REMOTE_NAME)"
