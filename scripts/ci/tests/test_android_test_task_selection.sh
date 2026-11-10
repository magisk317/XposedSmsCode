#!/usr/bin/env bash
# Keep beta/full coverage at least as strict as the affected hook and root gates.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root_dir="$(cd "$script_dir/../../.." && pwd)"
config="$root_dir/scripts/config/android-test-tasks.json"
toolkit_dir="${MAGISK_CI_TOOLKIT_DIR:-$root_dir/.magisk-ci-toolkit}"
selector="$toolkit_dir/ci/select_gradle_tasks.py"

assert_tasks_selected() {
  local scenario="$1"
  local selected="$2"
  shift 2
  local task
  for task in "$@"; do
    if ! grep -Fqx -- "$task" <<< "$selected"; then
      printf 'FAIL - %s does not select %s\n' "$scenario" "$task" >&2
      exit 1
    fi
  done
  printf 'ok   - %s selects the hook and root regression gates\n' "$scenario"
}

full_selection="$(
  env -u CI_COMMIT_TAG -u CI_COMMIT_BRANCH -u GITHUB_REF_TYPE -u GITHUB_REF_NAME \
    python3 "$selector" --root "$root_dir" --toolkit "$toolkit_dir" --config "$config" \
      --paths-file <(printf 'full\n')
)"
beta_selection="$(
  env -u CI_COMMIT_TAG -u GITHUB_REF_TYPE -u GITHUB_REF_NAME CI_COMMIT_BRANCH=beta \
    python3 "$selector" --root "$root_dir" --toolkit "$toolkit_dir" --config "$config"
)"
required_tasks=(
  :magisk-xposed-kit:testDebugUnitTest
  :magisk-xposed-kit:contract:test
  :magisk-xposed-kit:logging:jvmTest
  :magisk-xposed-kit:diagnostics:testDebugUnitTest
  :magisk-xposed-kit:capability:testDebugUnitTest
  :magisk-xposed-kit:runtime:testDebugUnitTest
  :magisk-xposed-kit:permission:testDebugUnitTest
  :magisk-xposed-kit:service:testDebugUnitTest
  :hook:check
  :hook:compileGithubDebugKotlin
  verifyModuleBoundaries
  verifyNoGlobalWarningMute
  verifyGodFileLimits
)
assert_tasks_selected "full impact" "$full_selection" "${required_tasks[@]}"
assert_tasks_selected "beta branch" "$beta_selection" "${required_tasks[@]}"

hook_selection="$(
  env -u CI_COMMIT_TAG -u CI_COMMIT_BRANCH -u GITHUB_REF_TYPE -u GITHUB_REF_NAME \
    python3 "$selector" --root "$root_dir" --toolkit "$toolkit_dir" --config "$config" \
      --paths-file <(printf 'impact\nhook/src/main/kotlin/HookEntry.kt\n')
)"
assert_tasks_selected "hook change" "$hook_selection" :hook:check :hook:compileGithubDebugKotlin
ui_update_variant_tasks=(
  :magisk-ui-kit:update:testGithubDebugUnitTest
  :magisk-ui-kit:update:testPlayDebugUnitTest
)
assert_tasks_selected "beta update channel coverage" "$beta_selection" "${ui_update_variant_tasks[@]}"
for changed_path in magisk-ui-kit magisk-ui-kit/update/src/main/kotlin/UpdateSource.kt; do
  ui_selection="$(
    env -u CI_COMMIT_TAG -u CI_COMMIT_BRANCH -u GITHUB_REF_TYPE -u GITHUB_REF_NAME \
      python3 "$selector" --root "$root_dir" --toolkit "$toolkit_dir" --config "$config" \
        --paths-file <(printf 'impact\n%s\n' "$changed_path")
  )"
  assert_tasks_selected "$changed_path" "$ui_selection" "${ui_update_variant_tasks[@]}"
done

echo "all Android test-task selector checks passed"
