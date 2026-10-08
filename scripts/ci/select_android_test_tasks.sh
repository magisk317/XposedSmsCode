#!/usr/bin/env bash
set -euo pipefail
root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
toolkit_dir="${1:-${MAGISK_CI_TOOLKIT_DIR:-$root_dir/.magisk-ci-toolkit}}"
paths_file="${2:-}"
full_tasks=( :core:check :runtime:check :app:check :magisk-ui-kit:testAndroidHostTest :magisk-ui-kit:jvmTest :magisk-ui-kit:billing:testDebugUnitTest :smscode-core:contract:test :smscode-core:domain:test :smscode-core:hook:testDebugUnitTest :smscode-core:runtime:testDebugUnitTest :smscode-core:verification:detekt :smscode-core:verification:test :smscode-core:rule:test :magisk-xposed-kit:testDebugUnitTest :magisk-xposed-kit:logging:testDebugUnitTest :magisk-xposed-kit:diagnostics:testDebugUnitTest :app:compileGithubDebugAndroidTestKotlin :app:compilePlayDebugAndroidTestKotlin :core:compilePlayDebugUnitTestKotlin :core:generatePlayDebugUnitTestStubRFile :runtime:compileDebugAndroidTestKotlin :app:koverVerifyGithubDebug :app:koverHtmlReportGithubDebug )
if [[ -n "${CI_COMMIT_TAG:-}" || "${GITHUB_REF_TYPE:-}" == tag || "${CI_COMMIT_BRANCH:-}" == beta || "${CI_COMMIT_BRANCH:-}" == master || "${GITHUB_REF_NAME:-}" == beta || "${GITHUB_REF_NAME:-}" == master ]]; then printf '%s\n' "${full_tasks[@]}"; exit 0; fi
if [[ -z "$paths_file" ]]; then paths_file="$(mktemp)"; trap 'rm -f "$paths_file"' EXIT; bash "$toolkit_dir/ci/changed_paths.sh" "$paths_file"; fi
if [[ "$(sed -n '1p' "$paths_file")" == full ]]; then printf '%s\n' "${full_tasks[@]}"; exit 0; fi
declare -A selected=()
select_task() { selected["$1"]=1; }
while IFS= read -r path; do
  [[ -z "$path" ]] && continue
  case "$path" in
    impact|.gitlab-ci.yml|.github/workflows/*|docs/*|README*|LICENSE*|CHANGELOG*) continue ;;
    build.gradle*|settings.gradle*|gradle.properties|gradle/*|build-logic/*|.gitmodules|scripts/*|.magisk-ci-toolkit/*) printf '%s\n' "${full_tasks[@]}"; exit 0 ;;
    app/*) select_task :app:check; select_task :app:compileGithubDebugAndroidTestKotlin; select_task :app:compilePlayDebugAndroidTestKotlin; select_task :app:koverVerifyGithubDebug; select_task :app:koverHtmlReportGithubDebug ;;
    core/*) select_task :core:check; select_task :core:compilePlayDebugUnitTestKotlin; select_task :core:generatePlayDebugUnitTestStubRFile ;;
    runtime/*) select_task :runtime:check; select_task :runtime:compileDebugAndroidTestKotlin ;;
    hook/*) select_task :core:check ;;
    smscode/*) select_task :smscode-core:contract:test; select_task :smscode-core:domain:test; select_task :smscode-core:hook:testDebugUnitTest; select_task :smscode-core:runtime:testDebugUnitTest; select_task :smscode-core:verification:detekt; select_task :smscode-core:verification:test; select_task :smscode-core:rule:test ;;
    magisk-ui-kit/*) select_task :magisk-ui-kit:testAndroidHostTest; select_task :magisk-ui-kit:jvmTest; select_task :magisk-ui-kit:billing:testDebugUnitTest ;;
    magisk-xposed-kit/*) select_task :magisk-xposed-kit:testDebugUnitTest; select_task :magisk-xposed-kit:logging:testDebugUnitTest; select_task :magisk-xposed-kit:diagnostics:testDebugUnitTest ;;
    *) printf '%s\n' "${full_tasks[@]}"; exit 0 ;;
  esac
done < <(sed -n '2,$p' "$paths_file")
if ((${#selected[@]} > 0)); then printf '%s\n' "${!selected[@]}" | sort; fi
