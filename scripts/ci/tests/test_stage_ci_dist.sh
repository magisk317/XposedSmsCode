#!/usr/bin/env bash
# Regression test for scripts/ci/stage_ci_dist.sh.
#
# What this pins down:
#   1. the version-keyed layout: directory name == version.txt == manifest versionName;
#   2. the manifest carries the *allocated* versionCode, read back from the workspace
#      file the way after_script sees it (an export from `script:` does not cross);
#   3. the provenance fields the in-app prompt localizes (branch / commit);
#   4. that an unparseable APK name, a missing APK, or a missing allocated code fail
#      the job loudly instead of leaving a green job with nothing staged.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root_dir="$(cd "$script_dir/../../.." && pwd)"
stager="$root_dir/scripts/ci/stage_ci_dist.sh"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

version='0.4.2-20261027_070707'
apk_file='universal_XposedSmsCode_v0.4.2-20261027_070707_debug.apk'
channel_root='ci-dist/com.github.tianma8023.xposed.smscode'

apk_dir="$tmp_dir/work/app/build/outputs/apk/github/debug"
mkdir -p "$apk_dir"
printf 'apk' > "$apk_dir/$apk_file"

# The helper is normally the pinned toolkit; fall back to a stub that mirrors its
# contract so the test also runs on a developer machine.
mkdir -p "$tmp_dir/toolkit/ci"
real_helper="${MAGISK_CI_TOOLKIT_DIR:-$root_dir/.magisk-ci-toolkit}/ci/allocated_version_code.sh"
if [ -f "$real_helper" ]; then
  cp "$real_helper" "$tmp_dir/toolkit/ci/allocated_version_code.sh"
else
  cat > "$tmp_dir/toolkit/ci/allocated_version_code.sh" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
code="${MAGISK_VERSION_CODE:-}"
file="${MAGISK_VERSION_CODE_FILE:-$CI_PROJECT_DIR/.magisk-version-code}"
if [ -z "$code" ] && [ -f "$file" ]; then
  code="$(tr -d '[:space:]' < "$file")"
fi
if [ -z "$code" ]; then
  echo "no allocated versionCode available" >&2
  exit 1
fi
printf '%s\n' "$code"
STUB
fi
chmod +x "$tmp_dir/toolkit/ci/allocated_version_code.sh"

run_stager() {
  env -u MAGISK_VERSION_CODE -u MAGISK_VERSION_CODE_FILE -u CI_COMMIT_SHORT_SHA \
    CI_PROJECT_DIR="$tmp_dir/work" \
    MAGISK_CI_TOOLKIT_DIR="$tmp_dir/toolkit" \
    CI_COMMIT_REF_NAME=beta \
    CI_PIPELINE_IID=777 \
    bash "$stager"
}

stage_dir="$tmp_dir/work/$channel_root/$version"
manifest="$stage_dir/app-ci-manifest.json"

# --- happy path -----------------------------------------------------------------
printf '1623\n' > "$tmp_dir/work/.magisk-version-code"
run_stager

for f in "$manifest" "$stage_dir/app-ci.apk" "$stage_dir/app-ci.apk.sha256" "$tmp_dir/work/$channel_root/version.txt"; do
  [ -s "$f" ] || { echo "FAIL: $f was not staged" >&2; exit 1; }
done
grep -Fq '"versionCode":1623' "$manifest" || {
  echo "FAIL: the manifest does not carry the allocated code:" >&2; cat "$manifest" >&2; exit 1
}
grep -Fq '"minSupportedVersionCode":1623' "$manifest" || {
  echo "FAIL: minSupportedVersionCode is not the allocated code:" >&2; cat "$manifest" >&2; exit 1
}
grep -Fq "\"versionName\":\"$version\"" "$manifest" || {
  echo "FAIL: versionName was not parsed from the APK file name:" >&2; cat "$manifest" >&2; exit 1
}
grep -Fq '"branch":"beta"' "$manifest" || {
  echo "FAIL: the branch the prompt localizes is missing:" >&2; cat "$manifest" >&2; exit 1
}
grep -Fq '"commit":""' "$manifest" || {
  echo "FAIL: a workspace without a commit must declare an empty one:" >&2; cat "$manifest" >&2; exit 1
}
[ "$(cat "$tmp_dir/work/$channel_root/version.txt")" = "$version" ] || {
  echo "FAIL: version.txt does not name the staged directory" >&2; exit 1
}
expected_sha=$(sha256sum "$stage_dir/app-ci.apk" | cut -d' ' -f1)
grep -Fq "$expected_sha" "$stage_dir/app-ci.apk.sha256" || {
  echo "FAIL: the sha256 sidecar does not match the staged APK" >&2; exit 1
}

# --- the exported short sha wins when the runner provides one ------------------
rm -rf "$tmp_dir/work/ci-dist"
printf '1623\n' > "$tmp_dir/work/.magisk-version-code"
env CI_PROJECT_DIR="$tmp_dir/work" MAGISK_CI_TOOLKIT_DIR="$tmp_dir/toolkit" \
  CI_COMMIT_REF_NAME=beta CI_COMMIT_SHORT_SHA=7865a560 \
  bash "$stager" >/dev/null
grep -Fq '"commit":"7865a560"' "$manifest" || {
  echo "FAIL: CI_COMMIT_SHORT_SHA was not declared in the manifest:" >&2; cat "$manifest" >&2; exit 1
}

# --- failure path: an unparseable APK name must fail the job -------------------
# The stage directory is named after the parsed version; accepting a name without
# one would leave the updater pointing at a path nobody staged, with a green job.
mv "$apk_dir/$apk_file" "$tmp_dir/apk.bak"
printf 'apk' > "$apk_dir/universal_XposedSmsCode_debug.apk"
rm -rf "$tmp_dir/work/ci-dist"
if run_stager > "$tmp_dir/badname.log" 2>&1; then
  echo "FAIL: the stager accepted an APK name without a version" >&2
  cat "$tmp_dir/badname.log" >&2
  exit 1
fi
grep -Fq 'cannot resolve versionName' "$tmp_dir/badname.log" || {
  echo "FAIL: the unparseable APK name was not reported:" >&2; cat "$tmp_dir/badname.log" >&2; exit 1
}
[ -z "$(find "$tmp_dir/work/ci-dist" -mindepth 1 -print 2>/dev/null | head -1)" ] || {
  echo "FAIL: something was staged despite the unparseable name" >&2; exit 1
}
rm "$apk_dir/universal_XposedSmsCode_debug.apk"
mv "$tmp_dir/apk.bak" "$apk_dir/$apk_file"

# --- failure path: a version without the build timestamp must be refused -------
# The timestamp is what keeps two pipelines of one versionName in two stage
# directories, and ui-kit only accepts it when resolving the {versionName} path
# (AppUpdateChecker.kt, ciSidecarVersionShape). Accepting a bare version here
# would publish a directory no client can name: a green job and an update channel
# that reports "up to date" forever.
mv "$apk_dir/$apk_file" "$tmp_dir/apk.bak2"
printf 'apk' > "$apk_dir/universal_XposedSmsCode_v0.4.2_debug.apk"
rm -rf "$tmp_dir/work/ci-dist"
printf '1623\n' > "$tmp_dir/work/.magisk-version-code"
if run_stager > "$tmp_dir/nostamp.log" 2>&1; then
  echo "FAIL: the stager accepted a versionName without a build timestamp" >&2
  cat "$tmp_dir/nostamp.log" >&2
  exit 1
fi
grep -Fq 'cannot resolve versionName' "$tmp_dir/nostamp.log" || {
  echo "FAIL: the timestamp-less versionName was not reported:" >&2
  cat "$tmp_dir/nostamp.log" >&2
  exit 1
}
[ -z "$(find "$tmp_dir/work/ci-dist" -mindepth 1 -print 2>/dev/null | head -1)" ] || {
  echo "FAIL: something was staged despite the unresolvable versionName" >&2
  exit 1
}
rm "$apk_dir/universal_XposedSmsCode_v0.4.2_debug.apk"
mv "$tmp_dir/apk.bak2" "$apk_dir/$apk_file"

# --- failure path: no allocated code anywhere must fail loudly -----------------
rm -rf "$tmp_dir/work/ci-dist" "$tmp_dir/work/.magisk-version-code"
if run_stager > "$tmp_dir/nocode.log" 2>&1; then
  echo "FAIL: the stager succeeded with no allocated versionCode" >&2
  cat "$tmp_dir/nocode.log" >&2
  exit 1
fi
[ ! -e "$manifest" ] || { echo "FAIL: a manifest was staged from an unknown versionCode" >&2; exit 1; }

# --- failure path: no APK at all must fail loudly ------------------------------
rm -f "$apk_dir/$apk_file"
printf '1623\n' > "$tmp_dir/work/.magisk-version-code"
if run_stager > "$tmp_dir/noapk.log" 2>&1; then
  echo "FAIL: the stager succeeded with no universal APK present" >&2
  cat "$tmp_dir/noapk.log" >&2
  exit 1
fi

echo "PASS: $(basename "$stager") staged, and refused, exactly as CI expects"
