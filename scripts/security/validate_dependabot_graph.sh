#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

SNAPSHOT_FILE=""
if [[ "${1:-}" == "--snapshot" ]]; then
  SNAPSHOT_FILE="${2:-}"
  if [[ -z "${SNAPSHOT_FILE}" ]]; then
    echo "--snapshot requires a dependency graph JSON file" >&2
    exit 1
  fi
fi

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

ALERTS_FILE="${TMP_DIR}/alerts.ndjson"
PACKAGES_FILE="${TMP_DIR}/packages.tsv"

gh api --paginate "/repos/${REPO}/dependabot/alerts?state=open&per_page=100" --jq '.[]' > "${ALERTS_FILE}"

if [[ -n "${SNAPSHOT_FILE}" ]]; then
  if [[ ! -f "${SNAPSHOT_FILE}" ]]; then
    echo "Missing dependency graph snapshot: ${SNAPSHOT_FILE}" >&2
    exit 1
  fi

  if jq -e '.manifests' "${SNAPSHOT_FILE}" >/dev/null; then
    jq -r '
      (.manifests[]?.resolved // {})
      | to_entries[]
      | .key
      | select(test("^[^:]+:[^:]+:.+$"))
      | capture("^(?<name>[^:]+:[^:]+):(?<version>.+)$")
      | [.name, .version]
      | @tsv
    ' "${SNAPSHOT_FILE}" | sort -u > "${PACKAGES_FILE}"
  elif jq -e '.sbom.packages' "${SNAPSHOT_FILE}" >/dev/null; then
    jq -r '
      .sbom.packages[]?
      | select(.name and .versionInfo)
      | [.name, .versionInfo]
      | @tsv
    ' "${SNAPSHOT_FILE}" | sort -u > "${PACKAGES_FILE}"
  else
    echo "Unsupported dependency graph snapshot format: ${SNAPSHOT_FILE}" >&2
    exit 1
  fi
else
  SBOM_FILE="${TMP_DIR}/sbom.json"
  gh api "/repos/${REPO}/dependency-graph/sbom" > "${SBOM_FILE}"
  jq -r '
    .sbom.packages[]?
    | select(.name and .versionInfo)
    | [.name, .versionInfo]
    | @tsv
  ' "${SBOM_FILE}" | sort -u > "${PACKAGES_FILE}"
fi

version_cmp() {
  local left="${1}"
  local right="${2}"

  if [[ "${left}" == "${right}" ]]; then
    echo 0
    return
  fi

  local first
  first="$(printf "%s\n%s\n" "${left}" "${right}" | sort -V | head -n1)"
  if [[ "${first}" == "${left}" ]]; then
    echo -1
  else
    echo 1
  fi
}

range_part_matches() {
  local version="${1}"
  local part="${2}"
  local op=""
  local target=""

  part="${part#"${part%%[![:space:]]*}"}"
  part="${part%"${part##*[![:space:]]}"}"

  case "${part}" in
    "<= "*)
      op="<="
      target="${part#<= }"
      ;;
    ">= "*)
      op=">="
      target="${part#>= }"
      ;;
    "< "*)
      op="<"
      target="${part#< }"
      ;;
    "> "*)
      op=">"
      target="${part#> }"
      ;;
    "= "*)
      op="="
      target="${part#= }"
      ;;
    *)
      echo "Unsupported vulnerable version range segment: ${part}" >&2
      return 1
      ;;
  esac

  local cmp
  cmp="$(version_cmp "${version}" "${target}")"

  case "${op}" in
    "<") [[ "${cmp}" -lt 0 ]] ;;
    "<=") [[ "${cmp}" -le 0 ]] ;;
    ">") [[ "${cmp}" -gt 0 ]] ;;
    ">=") [[ "${cmp}" -ge 0 ]] ;;
    "=") [[ "${cmp}" -eq 0 ]] ;;
  esac
}

version_in_range() {
  local version="${1}"
  local range="${2}"
  local part

  IFS=',' read -ra parts <<< "${range}"
  for part in "${parts[@]}"; do
    if ! range_part_matches "${version}" "${part}"; then
      return 1
    fi
  done
  return 0
}

scanned_alerts=0
matched_alerts=0
vulnerable_matches=0

while IFS= read -r alert; do
  ecosystem="$(jq -r '.dependency.package.ecosystem' <<< "${alert}")"
  if [[ "${ecosystem}" != "maven" ]]; then
    continue
  fi

  scanned_alerts=$((scanned_alerts + 1))
  number="$(jq -r '.number' <<< "${alert}")"
  pkg="$(jq -r '.dependency.package.name' <<< "${alert}")"
  manifest="$(jq -r '.dependency.manifest_path' <<< "${alert}")"
  severity="$(jq -r '.security_vulnerability.severity' <<< "${alert}")"
  range="$(jq -r '.security_vulnerability.vulnerable_version_range' <<< "${alert}")"
  versions="$(awk -F'\t' -v p="${pkg}" '$1==p {print $2}' "${PACKAGES_FILE}" | sort -u)"

  if [[ -z "${versions}" ]]; then
    continue
  fi

  matched_alerts=$((matched_alerts + 1))

  while IFS= read -r version; do
    if version_in_range "${version}" "${range}"; then
      vulnerable_matches=$((vulnerable_matches + 1))
      echo "VULNERABLE_GRAPH #${number} ${pkg}:${version} (${severity}, ${range}, manifest=${manifest})"
    fi
  done <<< "${versions}"
done < "${ALERTS_FILE}"

{
  echo "## Dependabot graph validation"
  echo ""
  echo "- Maven open alerts scanned: ${scanned_alerts}"
  echo "- Alerts with package entries in graph: ${matched_alerts}"
  echo "- Vulnerable graph entries: ${vulnerable_matches}"
} >> "${GITHUB_STEP_SUMMARY:-/dev/null}"

if [[ "${vulnerable_matches}" -gt 0 ]]; then
  echo "Dependency graph still contains versions covered by open Dependabot alerts." >&2
  exit 1
fi

echo "Dependency graph has no package versions matching open Dependabot alert ranges."
