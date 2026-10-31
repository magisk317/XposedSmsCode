#!/usr/bin/env bash
# Kotlin 2.5 flags every discarded result. These repos used to mute the two diagnostics
# build-wide with -Xwarning-level in the root build, which also hid the next accidental
# discard. Tolerated failures must stay explicit at the call site (`.onFailure { ... }`).
# This repository has no frozen vendor tree, so no build-wide mute and no file-scoped
# exemption of these diagnostics is allowed anywhere.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
status=0

muted=$(git ls-files -z '*.gradle.kts' | xargs -0 -r grep -l -- '-Xwarning-level=RETURN_VALUE_NOT_USED' || true)
if [ -n "${muted}" ]; then
  printf '::error::%s mutes RETURN_VALUE_NOT_USED build-wide; convert the call sites instead\n' "${muted}"
  status=1
fi
while IFS= read -r file; do
  [ -n "${file}" ] || continue
  printf '::error::%s carries a file-scoped RETURN_VALUE_NOT_USED exemption\n' "${file}"
  status=1
done < <(git ls-files -z '*.kt' | xargs -0 -r grep -l -- '@file:Suppress("RETURN_VALUE_NOT_USED"' || true)

if [ "${status}" -eq 0 ]; then
  printf 'no build-wide or stray RETURN_VALUE_NOT_USED mute: ok\n'
fi
exit "${status}"
