#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

if ! command -v gh >/dev/null 2>&1; then
  echo "gh is required" >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

ALERTS_JSON="/tmp/dependabot-alerts.json"
REMOVABLE_JSON="/tmp/removable.json"

echo "Fetching open Dependabot alerts..."
if ! gh api --paginate "/repos/{owner}/{repo}/dependabot/alerts?state=open" 2>/dev/null \
  | jq -s 'flatten' > "$ALERTS_JSON"; then
  echo '[]' > "$ALERTS_JSON"
fi
echo '[]' > "$REMOVABLE_JSON"

echo "Applying security force updates..."
python3 scripts/security/manage_dependency_forces.py apply-updates \
  --build-file build.gradle.kts \
  --toml-file gradle/libs.versions.toml \
  --removable-json "$REMOVABLE_JSON" \
  --alerts-json "$ALERTS_JSON"

if git diff --quiet -- build.gradle.kts; then
  echo "No force changes generated. Skipping build validation."
  exit 0
fi

echo "Force entries changed. Running build/test validation before opening PR..."
./gradlew --no-daemon \
  --warning-mode all \
  :app:assembleDebug \
  :app:testDebugUnitTest \
  -PbuildSplits \
  -Pkotlin.incremental=false

echo "Janitor validation passed."
