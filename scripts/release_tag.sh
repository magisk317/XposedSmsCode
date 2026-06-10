#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_FILE="$ROOT_DIR/gradle/libs.versions.toml"

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
    bash scripts/with_workspace_gradle_lock.sh --ignore-submodule-lockfiles --warning-mode all \
      :core:check \
      :runtime:check \
      :app:check \
      assembleGithubDebug \
      -PbuildSplits
  )

  echo "Running pre-push Detekt command..."
  (
    cd "$ROOT_DIR"
    bash scripts/with_workspace_gradle_lock.sh \
      :app:detekt \
      :core:detekt \
      :runtime:detekt \
      --continue
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

"$ROOT_DIR/scripts/check_release_guard.sh" "$TAG_NAME"
run_pre_push_checks

require_clean_worktree() {
  if [[ -n "$(git -C "$ROOT_DIR" status --porcelain)" ]]; then
    echo "ERROR: working tree is not clean. Commit/stash/remove changes before tagging." >&2
    git -C "$ROOT_DIR" status --short >&2
    exit 1
  fi
}

require_clean_worktree

sync_branch_with_remote() {
  local remote_ref="$REMOTE_NAME/$current_branch"
  local local_sha=""
  local remote_sha=""
  local base_sha=""

  echo "Fetching remote branch state: $remote_ref"
  git -C "$ROOT_DIR" fetch "$REMOTE_NAME" "$current_branch"

  if ! git -C "$ROOT_DIR" rev-parse -q --verify "$remote_ref" >/dev/null; then
    echo "WARN: remote branch not found, skipping branch sync: $remote_ref"
    return 0
  fi

  local_sha="$(git -C "$ROOT_DIR" rev-parse HEAD)"
  remote_sha="$(git -C "$ROOT_DIR" rev-parse "$remote_ref")"

  if [[ "$local_sha" == "$remote_sha" ]]; then
    echo "Branch already matches remote: $remote_ref"
    return 0
  fi

  base_sha="$(git -C "$ROOT_DIR" merge-base HEAD "$remote_ref")"

  if [[ "$base_sha" == "$local_sha" ]]; then
    echo "Local branch is behind remote; fast-forwarding to $remote_ref"
    git -C "$ROOT_DIR" merge --ff-only "$remote_ref"
    BRANCH_SYNC_CHANGED=1
    return 0
  fi

  if [[ "$base_sha" == "$remote_sha" ]]; then
    echo "Local branch is ahead of remote; no sync needed"
    return 0
  fi

  echo "Local branch diverged from remote!"
  read -p "Do you want to FORCE PUSH local changes to overwrite remote? (y/n) " -n 1 -r
  echo
  if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "Force pushing to $remote_ref..."
    if ! git -C "$ROOT_DIR" push --force-with-lease "$REMOTE_NAME" "$current_branch"; then
      echo "ERROR: Force push failed." >&2
      exit 1
    fi
    # Force push syncs remote to local, no re-check needed.
    return 0
  else
    echo "Attempting to rebase onto $remote_ref..."
    if ! git -C "$ROOT_DIR" rebase "$remote_ref"; then
      echo "ERROR: failed to rebase onto $remote_ref. Resolve conflicts or run 'git rebase --abort'." >&2
      exit 1
    fi
    BRANCH_SYNC_CHANGED=1
  fi
}

sync_branch_with_remote

if (( BRANCH_SYNC_CHANGED != 0 )); then
  echo "Branch changed after remote sync; re-running release checks..."
  "$ROOT_DIR/scripts/check_release_guard.sh" "$TAG_NAME"
  run_pre_push_checks

  require_clean_worktree
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
git -C "$ROOT_DIR" push "$REMOTE_NAME" "$current_branch"
git -C "$ROOT_DIR" push "$REMOTE_NAME" "$TAG_NAME"

echo "Created and pushed tag: $TAG_NAME (branch: $current_branch, remote: $REMOTE_NAME)"
