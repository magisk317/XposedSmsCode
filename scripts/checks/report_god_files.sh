#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

warning_lines=900
exception_lines=1200
limit=10
mode="${1:---report}"
baseline="scripts/config/god_file_limits.txt"

if [[ "$mode" != "--report" && "$mode" != "--verify" ]]; then
  echo "Usage: $0 [--report|--verify]" >&2
  exit 2
fi

python3 - "$warning_lines" "$exception_lines" "$limit" "$mode" "$baseline" <<'PY'
from pathlib import Path
import sys

warning_lines = int(sys.argv[1])
exception_lines = int(sys.argv[2])
limit = int(sys.argv[3])
mode = sys.argv[4]
baseline_path = Path(sys.argv[5])
root = Path('.')
top_excluded = {'smscode', 'magisk-ui-kit', 'magisk-xposed-kit', 'build-logic'}
anywhere_excluded = {'.git', '.gradle', 'build'}
rows = []
for path in root.rglob('*.kt'):
    relative = path.relative_to(root)
    if relative.parts[0] in top_excluded:
        continue
    if any(part in anywhere_excluded for part in relative.parts):
        continue
    if 'src' not in relative.parts:
        continue
    source_index = relative.parts.index('src')
    source_set = relative.parts[source_index + 1] if len(relative.parts) > source_index + 1 else 'unknown'
    if source_set not in {'main', 'commonMain', 'androidMain', 'jvmMain'}:
        continue
    try:
        lines = sum(1 for _ in path.open(encoding='utf-8', errors='replace'))
    except OSError:
        continue
    module = '/'.join(relative.parts[:source_index]) or '.'
    rows.append((lines, str(relative), module, source_set))

if mode == '--report':
    print('God-file report (report-only; no build failure)')
    print(f'Excluded roots: {", ".join(sorted(top_excluded))}')
    print(f'Warning threshold: {warning_lines} lines; exception-review threshold: {exception_lines} lines')
    print()
    print('LINES  LEVEL      MODULE              SOURCE SET  FILE')
    print('-----  ---------  ------------------  ----------  ----')
    for lines, path, module, source_set in sorted(rows, reverse=True)[:limit]:
        if lines >= exception_lines:
            level = 'EXCEPTION'
        elif lines >= warning_lines:
            level = 'WARNING'
        else:
            level = 'observe'
        print(f'{lines:5d}  {level:9}  {module:18}  {source_set:10}  {path}')

    flagged = [row for row in rows if row[0] >= warning_lines]
    exceptions = [row for row in rows if row[0] >= exception_lines]
    print()
    print(f'Summary: {len(flagged)} file(s) at or above warning threshold; {len(exceptions)} at or above exception-review threshold.')
    print('Action: files at or above 900 lines are pinned by scripts/config/god_file_limits.txt; new files or line-count growth fail verifyGodFileLimits.')
    raise SystemExit(0)

if not baseline_path.is_file():
    print(f'Missing god-file limit baseline: {baseline_path}', file=sys.stderr)
    raise SystemExit(1)

baseline = {}
errors = []
for line_number, raw in enumerate(baseline_path.read_text(encoding='utf-8').splitlines(), 1):
    line = raw.strip()
    if not line or line.startswith('#'):
        continue
    parts = line.split('|', 2)
    if len(parts) != 3:
        errors.append(f'{baseline_path}:{line_number}: expected PATH|LINES|REASON')
        continue
    path, maximum_text, reason = (part.strip() for part in parts)
    try:
        maximum = int(maximum_text)
    except ValueError:
        errors.append(f'{baseline_path}:{line_number}: invalid line count {maximum_text!r}')
        continue
    if maximum < warning_lines:
        errors.append(f'{baseline_path}:{line_number}: limit must be >= {warning_lines}')
    if not reason:
        errors.append(f'{baseline_path}:{line_number}: review reason must not be blank')
    if path in baseline:
        errors.append(f'{baseline_path}:{line_number}: duplicate path {path}')
    baseline[path] = maximum

current = {path: lines for lines, path, _module, _source_set in rows}
flagged = {path: lines for path, lines in current.items() if lines >= warning_lines}
for path, lines in sorted(flagged.items()):
    expected = baseline.get(path)
    if expected is None:
        errors.append(f'new god file requires split or reviewed baseline entry: {path} ({lines} lines)')
    elif lines != expected:
        direction = 'grew' if lines > expected else 'shrunk'
        errors.append(
            f'god-file baseline is stale: {path} {direction} from {expected} to {lines} lines; '
            'growth is forbidden, while reductions require lowering/removing the reviewed entry'
        )

for path, expected in sorted(baseline.items()):
    lines = current.get(path)
    if lines is None:
        errors.append(f'god-file baseline path is missing: {path}')
    elif lines < warning_lines:
        errors.append(f'god-file baseline is stale: {path} is now {lines} lines; remove its entry')
    elif path not in flagged:
        errors.append(f'god-file baseline is stale: {path} expected {expected} lines')

if errors:
    print('God-file limit check failed.', file=sys.stderr)
    print('The baseline is review debt, not a budget: do not raise a limit to accept growth.', file=sys.stderr)
    print(file=sys.stderr)
    for error in errors:
        print(f'- {error}', file=sys.stderr)
    raise SystemExit(1)

print(f'God-file limit check passed ({len(flagged)} reviewed file(s), no new growth).')
PY
