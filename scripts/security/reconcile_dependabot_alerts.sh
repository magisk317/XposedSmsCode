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

REPO="${GITHUB_REPOSITORY:-}"
if [[ -z "${REPO}" ]]; then
  origin_url="$(git config --get remote.origin.url || true)"
  REPO="$(
    sed -E \
      -e 's#^git@github.com:##' \
      -e 's#^https://github.com/##' \
      -e 's#\.git$##' \
      <<< "${origin_url}"
  )"
  if [[ "${REPO}" != */* ]]; then
    REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner)"
  fi
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

ALERTS_FILE="${TMP_DIR}/alerts.json"
FORCED_FILE="${TMP_DIR}/forced.json"

echo "Fetching open Dependabot alerts..."
ALERTS_ERR="${TMP_DIR}/alerts.err"
if gh api --paginate "/repos/${REPO}/dependabot/alerts?state=open&per_page=100" --jq '.[]' > "${ALERTS_FILE}" 2> "${ALERTS_ERR}"; then
  jq -s '.' "${ALERTS_FILE}" > "${TMP_DIR}/alerts_array.json" && mv "${TMP_DIR}/alerts_array.json" "${ALERTS_FILE}"
else
  if grep -qi 'Dependabot alerts are disabled' "${ALERTS_ERR}"; then
    echo "Dependabot alerts are disabled for ${REPO}; skipping alert cross-check." >&2
    : > "${ALERTS_FILE}"
    echo '[]' > "${ALERTS_FILE}"
  else
    cat "${ALERTS_ERR}" >&2
    echo "Failed to list Dependabot alerts for ${REPO}." >&2
    exit 1
  fi
fi

echo "Reading current force rules..."
python3 scripts/security/manage_dependency_forces.py read-forces \
  --build-file build.gradle.kts \
  --toml-file gradle/libs.versions.toml \
  --output "${FORCED_FILE}"

# Convert array format to lookup map: {"group:artifact": "version"}
FORCED_MAP="${TMP_DIR}/forced_map.json"
jq 'reduce .[] as $e ({}; . + {($e.group + ":" + $e.artifact): $e.version})' "${FORCED_FILE}" > "${FORCED_MAP}"

covered=0
uncovered=0
total=$(jq 'length' "${ALERTS_FILE}")

while IFS= read -r line; do
  id="$(echo "${line}" | jq -r '.number')"
  pkg="$(echo "${line}" | jq -r '.dependency.package.name')"
  severity="$(echo "${line}" | jq -r '.security_vulnerability.severity')"
  patched="$(echo "${line}" | jq -r '.security_vulnerability.first_patched_version.identifier // ""')"
  forced_version="$(jq -r --arg dep "${pkg}" '.[$dep] // ""' "${FORCED_MAP}")"

  if [[ -z "${patched}" || -z "${forced_version}" ]]; then
    echo "UNMATCHED #${id} ${pkg} (severity=${severity}, patched=${patched:-n/a}, forced=${forced_version:-n/a})"
    uncovered=$((uncovered + 1))
    continue
  fi

  if printf '%s\n%s\n' "${forced_version}" "${patched}" | sort -V | tail -n1 | grep -q "^${forced_version}$"; then
    covered=$((covered + 1))
    echo "COVERED #${id} ${pkg}: forced=${forced_version}, patched=${patched}, severity=${severity}"
  else
    echo "STALE_FORCE #${id} ${pkg}: forced=${forced_version}, patched=${patched}, severity=${severity}"
    uncovered=$((uncovered + 1))
  fi
done < <(jq -c '.[]' "${ALERTS_FILE}")

{
  echo "## Dependabot alert reconciliation"
  echo ""
  echo "- Open alerts scanned: ${total}"
  echo "- Covered by force rules: ${covered}"
  echo "- Not covered: ${uncovered}"
} >> "${GITHUB_STEP_SUMMARY:-/dev/null}"

echo "Reconciliation done (covered=${covered}, uncovered=${uncovered})"
