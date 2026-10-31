#!/usr/bin/env bash
# Stage the CI-track artifacts the in-app updater reads.
#
# Layout (same shape in XposedSmsCode, xinyi-relay and MiPushFramework):
#   ci-dist/<applicationId>/version.txt                       plain-text version sidecar
#   ci-dist/<applicationId>/<versionName>/app-ci.apk          the update download target
#   ci-dist/<applicationId>/<versionName>/app-ci.apk.sha256
#   ci-dist/<applicationId>/<versionName>/app-ci-manifest.json  UpdateManifest (schema 1)
#
# Why the artifacts live in a version-keyed directory: the APK that is currently
# offered stays addressable after the next pipeline overwrites nothing, so a
# manifest staged weeks ago still points at bytes that exist. `version.txt` is the
# stable pointer the updater reads first; GitLab exposes no per-file artifact
# listing, so the version has to travel beside the build.
#
# Why a file instead of an inline after_script block: GitLab runs after_script in
# its own shell (exports from script do not cross), and both the version parse and
# the allocated-code handoff have already failed silently once across this family.
# scripts/ci/tests/test_stage_ci_dist.sh runs this the way CI does.
set -euo pipefail

root="${CI_PROJECT_DIR:-$(pwd)}"
channel_root="ci-dist/com.github.tianma8023.xposed.smscode"
cd "$root"

# The universal debug APK is what the updater ships; `find` rather than a glob
# because pipefail turns "no such directory" into a silent abort of the stage step.
apk_path=$(find app/build/outputs/apk/github/debug -type f -name 'universal_*.apk' 2>/dev/null | sort | tail -n 1)
if [ -z "${apk_path:-}" ]; then
  echo "no universal debug APK to stage under app/build/outputs/apk/github/debug" >&2
  exit 1
fi

apk_name=$(basename "$apk_path")
apk_version=$(printf '%s' "$apk_name" | sed -n 's/^universal_.*_v\(.*\)_debug\.apk$/\1/p')
# The stage directory is named after this value, and a name only survives the
# client if it carries the build timestamp: that timestamp is what keeps two
# pipelines of one versionName in two directories instead of overwriting each
# other's APK. ui-kit applies the same shape when it reads version.txt
# (AppUpdateChecker.kt, ciSidecarVersionShape), so anything accepted here would
# otherwise stage a path no client resolves -- a green job whose update channel
# reports "up to date" forever. Fail the build instead.
if ! printf '%s' "$apk_version" | grep -Eq '^[A-Za-z0-9._+-]+-[0-9]{8}_[0-9]{6}$'; then
  echo "cannot resolve versionName from the APK name: $apk_name (got: ${apk_version:-none})" >&2
  exit 1
fi

stage_dir="$channel_root/$apk_version"
mkdir -p "$stage_dir"
cp "$apk_path" "$stage_dir/app-ci.apk"
printf '%s' "$apk_version" > "$channel_root/version.txt"

apk_sha=$(sha256sum "$stage_dir/app-ci.apk" | cut -d' ' -f1)
printf '%s  app-ci.apk\n' "$apk_sha" > "$stage_dir/app-ci.apk.sha256"

# after_script runs in its own shell, so the allocated number is read back from the
# workspace file the build template wrote (the env var only helps when a runner
# happens to carry it). Failing here is deliberate: a silently missing manifest
# puts the updater back on the pipeline-iid scale.
allocated_code="$(bash "${MAGISK_CI_TOOLKIT_DIR:-$root/.magisk-ci-toolkit}/ci/allocated_version_code.sh")"

# Provenance for the in-app prompt: the client renders these as a localized note,
# so an unresolvable commit stages as empty rather than failing the job.
branch="${CI_COMMIT_REF_NAME:-$(git rev-parse --abbrev-ref HEAD 2>/dev/null || true)}"
commit="${CI_COMMIT_SHORT_SHA:-$(git rev-parse --short=8 HEAD 2>/dev/null || true)}"

printf '{"schema":1,"variant":"github","versionName":"%s","versionCode":%s,"sha256":"%s","force":true,"minSupportedVersionCode":%s,"branch":"%s","commit":"%s"}\n' \
  "$apk_version" "$allocated_code" "$apk_sha" "$allocated_code" "$branch" "$commit" \
  > "$stage_dir/app-ci-manifest.json"

# The updater resolves both the artifact and the manifest through the version in
# version.txt, so all three must exist and must agree.
test -s "$stage_dir/app-ci.apk"
test -s "$stage_dir/app-ci-manifest.json"
test "$(cat "$channel_root/version.txt")" = "$apk_version"
test "$(sha256sum "$stage_dir/app-ci.apk" | cut -d' ' -f1)" = "$apk_sha"

echo "Staged $stage_dir (versionCode $allocated_code, branch ${branch:-none}, commit ${commit:-none})"
