"""manage_dependency_forces.py — Gradle dependency force rule manager.

Design philosophy (conservative removal strategy):
    A force rule may only be REMOVED if ALL of the following conditions are met:
      1. All successful natural (un-forced) resolutions converge to the same
         version across the checked Gradle configurations.
      2. That natural version is not older than the forced version, and not older
         than any known patched baseline from Dependabot alert history.
      3. The dependency has no CURRENT open Dependabot alert.
      4. If the dependency had historical alerts, the latest resolved alert is
         older than the configured cooldown window.
      5. The GitHub Advisory Database confirms that the natural version is NOT
         within any known vulnerable version range for that package (ecosystem=MAVEN),
         meaning Dependabot should not re-open an alert if the force were dropped.
    If ANY condition fails — or if any network/API call errors out — the force is
    kept. This conservative default prevents the ADD/REMOVE oscillation described in:
    https://app.stilla.ai/m/memo_01kq6zw9k3f2199jtez41bzxqh

Why historical alerts plus cooldown instead of "ever alerted => never remove"?

    Dependabot alerts often close automatically right after a force rule pins a
    patched version. If we only check "open" alerts, the next run may wrongly
    conclude the force is removable, reopen the alert, and start oscillating.

    Historical alerts are therefore treated as a stabilization signal, but not a
    permanent blacklist. Once the dependency has stayed resolved long enough and
    the natural version remains at or above the patched baseline, automation may
    remove the force again.

Why tristate (True / False / None) for _version_in_range?

    The original bool-returning implementation silently returned False on any
    parse failure (ImportError, malformed range, unknown operator). False is
    interpreted as "version NOT in vulnerable range → safe to remove", which is
    the opposite of what we want when we cannot determine safety. The tristate
    design makes parse errors explicit: callers treat None as "unsafe to remove".
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

import tomllib

try:
    from packaging.version import InvalidVersion, Version
except ImportError:
    InvalidVersion = None
    Version = None

AUTO_FORCE_BEGIN = "            // BEGIN AUTO FORCED DEPENDENCIES (managed by workflow)"
AUTO_FORCE_END = "            // END AUTO FORCED DEPENDENCIES (managed by workflow)"
DEFAULT_HISTORICAL_ALERT_COOLDOWN_HOURS = 24 * 7


def load_catalog(toml_path: Path) -> tuple[dict[str, Any], dict[str, Any]]:
    libs = tomllib.loads(toml_path.read_text())
    return libs.get("versions", {}), libs.get("libraries", {})


def resolve_alias(expr: str, versions: dict[str, Any], libraries: dict[str, Any]) -> dict[str, str] | None:
    alias = expr.strip()
    for prefix in ("catalog.", "libs."):
        if alias.startswith(prefix):
            alias = alias[len(prefix):]
            break
    else:
        return None

    alias = alias.replace(".", "-")
    lib = libraries.get(alias)
    if not lib:
        return None

    module = lib.get("module")
    version_ref = lib.get("version", {}).get("ref")
    version = versions.get(version_ref) if version_ref else None
    if not module or not version:
        return None

    group, artifact = module.split(":", 1)
    return {"group": group, "artifact": artifact, "version": version}


def parse_force_line(line: str, versions: dict[str, Any], libraries: dict[str, Any]) -> dict[str, str] | None:
    match = re.search(r"force\((.+)\)", line)
    if not match:
        return None

    expr = match.group(1).strip()
    resolved = resolve_alias(expr, versions, libraries)
    if resolved:
        return {**resolved, "line": line}

    if expr.startswith('"') or expr.startswith("'"):
        coord = expr.strip("\"'")
        parts = coord.split(":")
        if len(parts) >= 3:
            return {
                "group": parts[0],
                "artifact": parts[1],
                "version": ":".join(parts[2:]),
                "line": line,
            }

    return None


def iter_managed_blocks(lines: list[str]) -> list[tuple[int, int]]:
    blocks: list[tuple[int, int]] = []
    start: int | None = None
    for index, line in enumerate(lines):
        if line == AUTO_FORCE_BEGIN:
            start = index
        elif line == AUTO_FORCE_END:
            if start is None:
                raise SystemExit("Auto force block end found before begin in build.gradle.kts")
            blocks.append((start, index))
            start = None
    if start is not None:
        raise SystemExit("Auto force block begin without matching end in build.gradle.kts")
    if not blocks:
        raise SystemExit("Auto force block not found in build.gradle.kts")
    return blocks


def replace_managed_blocks(lines: list[str], block_lines: list[str]) -> list[str]:
    output: list[str] = []
    skip_until: int | None = None
    for index, line in enumerate(lines):
        if skip_until is not None:
            if index <= skip_until:
                continue
            skip_until = None
        if line == AUTO_FORCE_BEGIN:
            _, end_index = next(block for block in iter_managed_blocks(lines) if block[0] == index)
            output.extend(block_lines)
            skip_until = end_index
            continue
        output.append(line)
    return output


def version_key(version: str) -> list[tuple[int, int | str]]:
    parts = re.findall(r"\d+|[A-Za-z]+", version)
    key: list[tuple[int, int | str]] = []
    for part in parts:
        if part.isdigit():
            key.append((0, int(part)))
        else:
            key.append((1, part.lower()))
    return key


def version_gte(left: str, right: str) -> bool:
    return version_key(left) >= version_key(right)


def command_read_forces(args: argparse.Namespace) -> None:
    versions, libraries = load_catalog(Path(args.toml_file))
    forces = []
    lines = Path(args.build_file).read_text().splitlines()
    for start, end in iter_managed_blocks(lines):
        for line in lines[start + 1 : end]:
            resolved = parse_force_line(line, versions, libraries)
            if resolved:
                forces.append(resolved)

    Path(args.output).write_text(json.dumps(forces, indent=2) + "\n")
    print(f"Forced entries: {len(forces)}")


def command_strip_force_lines(args: argparse.Namespace) -> None:
    path = Path(args.build_file)
    original = path.read_text()
    lines = original.splitlines()
    new_lines = replace_managed_blocks(lines, [AUTO_FORCE_BEGIN, AUTO_FORCE_END])
    path.write_text("\n".join(new_lines) + ("\n" if original.endswith("\n") else ""))


def extract_resolved_version(text: str, group: str, artifact: str) -> str:
    pattern = re.compile(rf"{re.escape(group)}:{re.escape(artifact)}:([\w.\-]+)")
    match = pattern.search(text)
    return match.group(1) if match else ""


def command_resolve_natural(args: argparse.Namespace) -> None:
    forced = json.loads(Path(args.forced_json).read_text())
    results: dict[str, dict[str, dict[str, Any]]] = {}

    build_text = ""
    build_returncode = 0
    if args.include_build_environment:
        build_proc = subprocess.run(
            [args.gradlew, "-q", "buildEnvironment"],
            capture_output=True,
            text=True,
            check=False,
        )
        build_text = build_proc.stdout + "\n" + build_proc.stderr
        build_returncode = build_proc.returncode

    for item in forced:
        group = item["group"]
        artifact = item["artifact"]
        dep = f"{group}:{artifact}"
        results[dep] = {}

        if args.include_build_environment:
            results[dep]["buildscript.classpath"] = {
                "resolved": extract_resolved_version(build_text, group, artifact),
                "returncode": build_returncode,
                "config_missing": False,
            }
            if not results[dep]["buildscript.classpath"]["resolved"]:
                print(f"  WARN: no buildscript.classpath version found for {dep}")

        for config in args.config:
            print(f"Resolving {dep} in {config}...")
            cmd = [
                args.gradlew,
                f"{args.project}:dependencyInsight",
                "--dependency",
                dep,
                "--configuration",
                config,
                "--console=plain",
            ]
            proc = subprocess.run(cmd, capture_output=True, text=True, check=False)
            text = proc.stdout + "\n" + proc.stderr

            config_missing = (
                proc.returncode != 0 and "configuration" in text.lower() and "not found" in text.lower()
            )
            results[dep][config] = {
                "resolved": extract_resolved_version(text, group, artifact),
                "returncode": proc.returncode,
                "config_missing": config_missing,
            }

            if config_missing:
                print(f"  INFO: configuration {config} not found, skipping")
            elif not results[dep][config]["resolved"]:
                print(f"  WARN: no resolved version found for {dep} in {config}")

    Path(args.output).write_text(json.dumps(results, indent=2) + "\n")


def command_resolve_alert_natural(args: argparse.Namespace) -> None:
    alerts = load_alerts(Path(args.alerts_json))
    deps = sorted(
        {
            alert.get("dependency", {}).get("package", {}).get("name")
            for alert in alerts
            if _is_open_alert(alert)
            if alert.get("dependency", {}).get("package", {}).get("ecosystem") == "maven"
            and ":" in (alert.get("dependency", {}).get("package", {}).get("name") or "")
        }
    )

    results: dict[str, dict[str, dict[str, Any]]] = {}
    build_proc = subprocess.run(
        [args.gradlew, "-q", "buildEnvironment"],
        capture_output=True,
        text=True,
        check=False,
    )
    build_text = build_proc.stdout + "\n" + build_proc.stderr

    for dep in deps:
        group, artifact = dep.split(":", 1)
        results[dep] = {
            "buildscript.classpath": {
                "resolved": extract_resolved_version(build_text, group, artifact),
                "returncode": build_proc.returncode,
                "config_missing": False,
            }
        }
        if not results[dep]["buildscript.classpath"]["resolved"]:
            print(f"  WARN: no buildscript.classpath version found for {dep}")

        for config in args.config:
            print(f"Resolving security alert dependency {dep} in {config}...")
            cmd = [
                args.gradlew,
                f"{args.project}:dependencyInsight",
                "--dependency",
                dep,
                "--configuration",
                config,
                "--console=plain",
            ]
            proc = subprocess.run(cmd, capture_output=True, text=True, check=False)
            text = proc.stdout + "\n" + proc.stderr
            config_missing = (
                proc.returncode != 0 and "configuration" in text.lower() and "not found" in text.lower()
            )
            results[dep][config] = {
                "resolved": extract_resolved_version(text, group, artifact),
                "returncode": proc.returncode,
                "config_missing": config_missing,
            }
            if config_missing:
                print(f"  INFO: configuration {config} not found for {dep}, skipping")
            elif not results[dep][config]["resolved"]:
                print(f"  WARN: no resolved version found for {dep} in {config}")

    Path(args.output).write_text(json.dumps(results, indent=2) + "\n")


def load_alerts(alerts_path: Path) -> list[dict[str, Any]]:
    raw = json.loads(alerts_path.read_text())
    if isinstance(raw, list):
        return [item for item in raw if isinstance(item, dict)]
    if isinstance(raw, dict):
        message = raw.get("message")
        if message:
            print(f"WARN: dependabot alerts response is not a list: {message}")
        alerts = raw.get("alerts")
        if isinstance(alerts, list):
            return [item for item in alerts if isinstance(item, dict)]
    return []


def line_dep(expr: str, versions: dict[str, Any], libraries: dict[str, Any]) -> str | None:
    resolved = resolve_alias(expr, versions, libraries)
    if resolved:
        return f"{resolved['group']}:{resolved['artifact']}"
    if ":" in expr:
        parts = expr.split(":")
        if len(parts) >= 2:
            return f"{parts[0]}:{parts[1]}"
    return None


# ---------------------------------------------------------------------------
# Conservative removal helpers
# ---------------------------------------------------------------------------


def _parse_github_timestamp(raw: str | None) -> datetime | None:
    if not raw:
        return None
    try:
        return datetime.fromisoformat(raw.replace("Z", "+00:00")).astimezone(timezone.utc)
    except ValueError:
        return None


def _alert_resolution_timestamp(alert: dict[str, Any]) -> datetime | None:
    for field in ("fixed_at", "dismissed_at", "auto_dismissed_at", "updated_at", "created_at"):
        timestamp = _parse_github_timestamp(alert.get(field))
        if timestamp is not None:
            return timestamp
    return None


def _iter_dep_alerts(dep: str, alerts: list[dict[str, Any]]) -> list[dict[str, Any]]:
    matched: list[dict[str, Any]] = []
    for alert in alerts:
        pkg = alert.get("dependency", {}).get("package", {})
        if pkg.get("ecosystem", "").lower() == "maven" and pkg.get("name") == dep:
            matched.append(alert)
    return matched


def _is_open_alert(alert: dict[str, Any]) -> bool:
    return (alert.get("state") or "").lower() == "open"


def _has_open_alert(dep: str, alerts: list[dict[str, Any]]) -> bool:
    return any(_is_open_alert(alert) for alert in _iter_dep_alerts(dep, alerts))


def _latest_resolved_alert_timestamp(dep: str, alerts: list[dict[str, Any]]) -> datetime | None:
    timestamps = [
        timestamp
        for alert in _iter_dep_alerts(dep, alerts)
        if (alert.get("state") or "").lower() != "open"
        for timestamp in [_alert_resolution_timestamp(alert)]
        if timestamp is not None
    ]
    return max(timestamps) if timestamps else None


def _patched_baseline(dep: str, alerts: list[dict[str, Any]]) -> str | None:
    baseline: str | None = None
    for alert in _iter_dep_alerts(dep, alerts):
        patched = (
            (alert.get("security_vulnerability") or {}).get("first_patched_version") or {}
        ).get("identifier") or (
            (alert.get("security_vulnerability") or {}).get("first_patched_version") or {}
        ).get("version")
        if not patched:
            continue
        if baseline is None or version_gte(patched, baseline):
            baseline = patched
    return baseline


def _cooldown_elapsed(
    dep: str,
    alerts: list[dict[str, Any]],
    cooldown_hours: int,
    now: datetime | None = None,
) -> bool:
    latest_resolved = _latest_resolved_alert_timestamp(dep, alerts)
    if latest_resolved is None:
        return True
    current = now or datetime.now(timezone.utc)
    return current - latest_resolved >= timedelta(hours=cooldown_hours)


def _parse_maven_version(version_str: str) -> Any:
    """Parse a Maven version string into a comparable object.

    Strips common qualifiers like '.Final', '.RELEASE', '.GA' before parsing
    so that ``packaging.version.parse`` (PEP 440) can handle them.
    Falls back to the raw string if parsing fails.
    """
    if Version is None or InvalidVersion is None:
        return version_str

    # Strip common Maven suffixes that PEP-440 doesn't understand
    cleaned = re.sub(r"\.(Final|RELEASE|GA|SP\d*)$", "", version_str, flags=re.IGNORECASE)
    # Normalise remaining separators
    cleaned = re.sub(r"[._-]", ".", cleaned)
    try:
        return Version(cleaned)
    except InvalidVersion:
        return version_str


def _version_in_range(version_str: str, vulnerable_range: str) -> bool | None:
    """Return True if *version_str* is in *vulnerable_range*, False if definitely not,
    or None if the result cannot be determined (parse failure, unknown operator, etc.).

    Callers MUST treat None as "unsafe to remove" — it must NOT be silently
    interpreted as "not in range" (which was the bug in the previous implementation).

    Range format examples (GitHub Advisory Database):
      ``>= 4.1.0, < 4.1.118.Final``
      ``< 4.0.56.Final``
      ``>= 0``
    """
    ver = _parse_maven_version(version_str)
    if Version is None:
        return None  # packaging library not available → undetermined
    if not isinstance(ver, Version):
        return None  # version_str could not be parsed → undetermined

    saw_valid_clause = False

    for clause in vulnerable_range.split(","):
        clause = clause.strip()
        if not clause:
            continue
        m = re.match(r"([><=!]+)\s*(.+)", clause)
        if not m:
            return None  # malformed clause → undetermined

        op, bound_str = m.group(1), m.group(2).strip()
        bound = _parse_maven_version(bound_str)
        if not isinstance(bound, Version):
            return None  # bound could not be parsed → undetermined
        saw_valid_clause = True

        # Each clause constrains membership: if the version fails ANY clause the
        # version is definitely outside the range (return False).
        if op == ">=" and not (ver >= bound):
            return False
        elif op == ">" and not (ver > bound):
            return False
        elif op == "<=" and not (ver <= bound):
            return False
        elif op == "<" and not (ver < bound):
            return False
        elif op in ("=", "==") and not (ver == bound):
            return False
        elif op == "!=" and not (ver != bound):
            return False
        elif op not in (">=", ">", "<=", "<", "=", "==", "!="):
            return None  # unknown operator → undetermined

    if not saw_valid_clause:
        return None

    # All clauses satisfied → version is within the range
    return True


def _query_advisories_for_dep(group: str, artifact: str, gh_token: str) -> list[dict[str, Any]] | None:
    """Query GitHub GraphQL for all security advisories affecting ``group:artifact`` (MAVEN).

    Returns a list of advisory nodes with ``vulnerableVersionRange`` and
    ``firstPatchedVersion``, or *None* on any error (caller should treat *None*
    conservatively and refuse to remove the force rule).
    """
    package_name = f"{group}:{artifact}"
    # Template the package name into the query string (it is not user-supplied at
    # runtime, so this is safe in this context).
    query_template = """
query($after: String) {
  securityVulnerabilities(ecosystem: MAVEN, package: "%s", first: 100, after: $after) {
    pageInfo { hasNextPage endCursor }
    nodes {
      vulnerableVersionRange
      firstPatchedVersion { identifier }
    }
  }
}
"""
    query = query_template % package_name

    results: list[dict[str, Any]] = []
    after: str | None = None

    for _ in range(20):  # safety cap: max 20 pages → 2 000 advisories
        variables: dict[str, Any] = {}
        if after:
            variables["after"] = after

        payload = json.dumps({"query": query, "variables": variables}).encode()
        req = urllib.request.Request(
            "https://api.github.com/graphql",
            data=payload,
            headers={
                "Authorization": f"Bearer {gh_token}",
                "Content-Type": "application/json",
                "Accept": "application/json",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                data = json.loads(resp.read())
        except Exception as exc:
            print(f"  WARN: GraphQL query failed for {package_name}: {exc}")
            return None

        if "errors" in data:
            print(f"  WARN: GraphQL errors for {package_name}: {data['errors']}")
            return None

        vulns = data.get("data", {}).get("securityVulnerabilities", {})
        nodes = vulns.get("nodes", [])
        results.extend(nodes)

        page_info = vulns.get("pageInfo", {})
        if not page_info.get("hasNextPage"):
            break
        after = page_info.get("endCursor")

    return results


def is_safe_to_remove(
    dep: str,
    natural_version: str,
    forced_version: str,
    alerts: list[dict[str, Any]],
    cooldown_hours: int,
    check_advisories: bool = True,
    gh_token: str | None = None,
    now: datetime | None = None,
) -> bool:
    """Return True only if it is safe to remove the force rule for *dep*.

    Conditions (ALL must hold):
      1. *natural_version* is truthy and not older than the forced version.
      2. The dependency has no open Dependabot alert.
      3. If the dependency has historical resolved alerts, the configured cooldown
         period has elapsed since the latest resolution/dismissal.
      4. The natural version is not older than the strongest patched baseline seen
         in alert history.
      5. (If *check_advisories*) The natural version does not fall within any
         known advisory's ``vulnerableVersionRange`` (GitHub Advisory Database).

    On any uncertainty or error, returns False (conservative default).
    """
    if not natural_version:
        print(f"  INFO: skipping removal of {dep} — natural version missing")
        return False

    if not version_gte(natural_version, forced_version):
        print(
            f"  INFO: skipping removal of {dep} — "
            f"natural version {natural_version} is older than forced {forced_version}"
        )
        return False

    if _has_open_alert(dep, alerts):
        print(f"  INFO: skipping removal of {dep} — dependency still has an open Dependabot alert")
        return False

    if not _cooldown_elapsed(dep, alerts, cooldown_hours, now=now):
        latest_resolved = _latest_resolved_alert_timestamp(dep, alerts)
        print(
            f"  INFO: skipping removal of {dep} — "
            f"historical alert cooldown still active since {latest_resolved.isoformat() if latest_resolved else 'unknown'}"
        )
        return False

    patched_baseline = _patched_baseline(dep, alerts)
    if patched_baseline and not version_gte(natural_version, patched_baseline):
        print(
            f"  INFO: skipping removal of {dep} — "
            f"natural version {natural_version} is below patched baseline {patched_baseline}"
        )
        return False

    if check_advisories:
        if not gh_token:
            print(
                f"  INFO: skipping removal of {dep} — GH_TOKEN not set, "
                "cannot verify advisories (conservative)"
            )
            return False
        parts = dep.split(":", 1)
        if len(parts) != 2:
            print(f"  INFO: skipping removal of {dep} — cannot parse group:artifact")
            return False
        group, artifact = parts
        advisories = _query_advisories_for_dep(group, artifact, gh_token)
        if advisories is None:
            print(f"  INFO: skipping removal of {dep} — advisory query failed (conservative)")
            return False
        for adv in advisories:
            vrange = adv.get("vulnerableVersionRange") or ""
            if not vrange:
                continue
            result = _version_in_range(natural_version, vrange)
            if result is True:
                first_patched = (adv.get("firstPatchedVersion") or {}).get("identifier", "unknown")
                print(
                    f"  INFO: skipping removal of {dep}@{natural_version} — "
                    f"natural version matches advisory range '{vrange}' "
                    f"(first patched: {first_patched})"
                )
                return False
            if result is None:
                print(
                    f"  INFO: skipping removal of {dep} — "
                    f"advisory range '{vrange}' could not be evaluated; treating as unsafe"
                )
                return False
            # result is False: version is definitively NOT in this advisory's range;
            # continue checking remaining advisories.

    return True


def command_determine_removable(args: argparse.Namespace) -> None:
    forced = json.loads(Path(args.forced_json).read_text())
    natural = json.loads(Path(args.natural_json).read_text())
    alerts = load_alerts(Path(args.alerts_json))
    gh_token = os.environ.get("GH_TOKEN")
    removable: set[str] = set()

    for item in forced:
        dep = f"{item['group']}:{item['artifact']}"
        resolved_by_config = natural.get(dep, {})
        successful = [
            entry["resolved"]
            for entry in resolved_by_config.values()
            if not entry.get("config_missing") and entry.get("resolved")
        ]

        if not successful:
            print(f"  INFO: skipping removal of {dep} — no successful natural resolution found")
            continue
        natural_versions = sorted(set(successful), key=version_key)
        if len(natural_versions) != 1:
            print(
                f"  INFO: skipping removal of {dep} — "
                f"natural versions diverge across configs: {natural_versions}"
            )
            continue

        natural_version = natural_versions[0]
        if not version_gte(natural_version, item["version"]):
            natural_versions = sorted(set(successful))
            print(
                f"  INFO: skipping removal of {dep} — "
                f"natural version {natural_version} is below forced {item['version']}"
            )
            continue

        if not is_safe_to_remove(
            dep,
            natural_version,
            item["version"],
            alerts,
            cooldown_hours=args.historical_alert_cooldown_hours,
            check_advisories=args.check_advisories,
            gh_token=gh_token,
        ):
            continue

        removable.add(dep)
        print(f"  INFO: {dep}@{natural_version} is safe to remove — all conditions met")

    Path(args.output).write_text(json.dumps(sorted(removable), indent=2) + "\n")
    print(f"Removable forces: {len(removable)}")


def command_apply_updates(args: argparse.Namespace) -> None:
    build_path = Path(args.build_file)
    versions, libraries = load_catalog(Path(args.toml_file))
    text = build_path.read_text().splitlines()
    existing_forces: dict[str, str] = {}
    for start, end in iter_managed_blocks(text):
        for line in text[start + 1 : end]:
            resolved = parse_force_line(line, versions, libraries)
            if not resolved:
                continue
            dep = f"{resolved['group']}:{resolved['artifact']}"
            current = existing_forces.get(dep)
            if current is None or version_key(resolved["version"]) > version_key(current):
                existing_forces[dep] = resolved["version"]

    removable = set(json.loads(Path(args.removable_json).read_text()))
    natural_versions = json.loads(Path(args.natural_json).read_text()) if args.natural_json else {}
    alerts = load_alerts(Path(args.alerts_json))
    security_forces: dict[str, str] = {}
    for alert in alerts:
        if not _is_open_alert(alert):
            continue
        ecosystem = alert.get("dependency", {}).get("package", {}).get("ecosystem")
        dep = alert.get("dependency", {}).get("package", {}).get("name")
        patched = alert.get("security_vulnerability", {}).get("first_patched_version", {}) or {}
        target = patched.get("identifier") or patched.get("version")
        # This workflow writes Gradle resolutionStrategy.force(...) entries, so only
        # Maven coordinates are valid here. Ignore cargo/npm/etc alerts.
        if ecosystem == "maven" and dep and ":" in dep and target:
            selected = target
            existing = existing_forces.get(dep)
            if existing and version_key(existing) > version_key(selected):
                selected = existing
            for entry in natural_versions.get(dep, {}).values():
                resolved = entry.get("resolved")
                if resolved and version_key(resolved) > version_key(selected):
                    selected = resolved
            current = security_forces.get(dep)
            if current is None or version_key(selected) > version_key(current):
                security_forces[dep] = selected

    # This workflow owns only the managed force block. We preserve existing
    # non-removable entries already inside that block, then merge in open-alert
    # security forces. Non-managed force(...) lines elsewhere in the file are
    # intentionally left untouched.
    merged_forces: dict[str, str] = {}
    for dep, version in existing_forces.items():
        if dep in removable:
            continue
        merged_forces[dep] = version
    for dep, version in security_forces.items():
        current = merged_forces.get(dep)
        if current is None or version_key(version) > version_key(current):
            merged_forces[dep] = version

    block_lines = [AUTO_FORCE_BEGIN]
    for dep in sorted(merged_forces):
        block_lines.append(f"            force(\"{dep}:{merged_forces[dep]}\")")
    block_lines.append(AUTO_FORCE_END)

    new_text = replace_managed_blocks(text, block_lines)
    build_path.write_text("\n".join(new_text) + "\n")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    read_forces = subparsers.add_parser("read-forces")
    read_forces.add_argument("--build-file", required=True)
    read_forces.add_argument("--toml-file", required=True)
    read_forces.add_argument("--output", required=True)
    read_forces.set_defaults(func=command_read_forces)

    strip_force_lines = subparsers.add_parser("strip-force-lines")
    strip_force_lines.add_argument("--build-file", required=True)
    strip_force_lines.set_defaults(func=command_strip_force_lines)

    resolve_natural = subparsers.add_parser("resolve-natural")
    resolve_natural.add_argument("--gradlew", default="./gradlew")
    resolve_natural.add_argument("--project", default=":app")
    resolve_natural.add_argument("--forced-json", required=True)
    resolve_natural.add_argument("--output", required=True)
    resolve_natural.add_argument("--config", action="append", default=[])
    resolve_natural.add_argument("--include-build-environment", action="store_true")
    resolve_natural.set_defaults(func=command_resolve_natural)

    determine_removable = subparsers.add_parser("determine-removable")
    determine_removable.add_argument("--forced-json", required=True)
    determine_removable.add_argument("--natural-json", required=True)
    determine_removable.add_argument("--alerts-json", required=True)
    determine_removable.add_argument(
        "--repo",
        default="",
        help="OWNER/REPO (informational; token is read from GH_TOKEN env var)",
    )
    determine_removable.add_argument(
        "--historical-alert-cooldown-hours",
        type=int,
        default=DEFAULT_HISTORICAL_ALERT_COOLDOWN_HOURS,
        help=(
            "Require this many hours to pass since the latest resolved/dismissed "
            "historical alert before a force rule may be auto-removed"
        ),
    )
    determine_removable.add_argument(
        "--check-advisories",
        action=argparse.BooleanOptionalAction,
        default=True,
        help=(
            "Query GitHub Advisory Database to verify the natural version is not "
            "vulnerable before removing a force rule (default: True)"
        ),
    )
    determine_removable.add_argument("--output", required=True)
    determine_removable.set_defaults(func=command_determine_removable)

    apply_updates = subparsers.add_parser("apply-updates")
    apply_updates.add_argument("--build-file", required=True)
    apply_updates.add_argument("--toml-file", required=True)
    apply_updates.add_argument("--removable-json", required=True)
    apply_updates.add_argument("--alerts-json", required=True)
    apply_updates.add_argument("--natural-json")
    apply_updates.set_defaults(func=command_apply_updates)

    resolve_alert_natural = subparsers.add_parser("resolve-alert-natural")
    resolve_alert_natural.add_argument("--gradlew", default="./gradlew")
    resolve_alert_natural.add_argument("--project", default=":app")
    resolve_alert_natural.add_argument("--alerts-json", required=True)
    resolve_alert_natural.add_argument("--output", required=True)
    resolve_alert_natural.add_argument("--config", action="append", default=[])
    resolve_alert_natural.set_defaults(func=command_resolve_alert_natural)

    return parser


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
