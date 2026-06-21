import json
import sys
import unittest
from datetime import datetime, timezone
from pathlib import Path
import tempfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import manage_dependency_forces as mdf


def alert(
    dep: str,
    state: str,
    *,
    created_at: str = "2026-04-20T00:00:00Z",
    updated_at: str = "2026-04-20T00:00:00Z",
    fixed_at: str | None = None,
    dismissed_at: str | None = None,
    patched: str | None = None,
) -> dict:
    first_patched_version = {}
    if patched is not None:
        first_patched_version["identifier"] = patched
    return {
        "state": state,
        "created_at": created_at,
        "updated_at": updated_at,
        "fixed_at": fixed_at,
        "dismissed_at": dismissed_at,
        "dependency": {
            "package": {
                "ecosystem": "maven",
                "name": dep,
            }
        },
        "security_vulnerability": {
            "first_patched_version": first_patched_version,
        },
    }


class ManageDependencyForcesTest(unittest.TestCase):
    def make_apply_args(
        self,
        tmpdir: str,
        *,
        build_lines: list[str],
        removable: list[str] | None = None,
        alerts: list[dict] | None = None,
        natural: dict | None = None,
    ):
        build_file = Path(tmpdir) / "build.gradle.kts"
        toml_file = Path(tmpdir) / "libs.versions.toml"
        removable_json = Path(tmpdir) / "removable.json"
        alerts_json = Path(tmpdir) / "alerts.json"
        natural_json = Path(tmpdir) / "natural.json"

        build_file.write_text("\n".join(build_lines) + "\n")
        toml_file.write_text("[versions]\n[libraries]\n")
        removable_json.write_text(json.dumps(removable or [], indent=2) + "\n")
        alerts_json.write_text(json.dumps(alerts or [], indent=2) + "\n")
        natural_json.write_text(json.dumps(natural or {}, indent=2) + "\n")

        return type(
            "Args",
            (),
            {
                "build_file": str(build_file),
                "toml_file": str(toml_file),
                "removable_json": str(removable_json),
                "alerts_json": str(alerts_json),
                "natural_json": str(natural_json),
            },
        )(), build_file

    def test_version_in_range_returns_none_for_empty_range(self):
        self.assertIsNone(mdf._version_in_range("4.1.132.Final", ""))
        self.assertIsNone(mdf._version_in_range("4.1.132.Final", " , , "))

    def test_is_safe_to_remove_rejects_open_alert(self):
        result = mdf.is_safe_to_remove(
            dep="io.netty:netty-codec-http",
            natural_version="4.1.132.Final",
            forced_version="4.1.125.Final",
            alerts=[alert("io.netty:netty-codec-http", "open", patched="4.1.125.Final")],
            cooldown_hours=168,
            check_advisories=False,
        )
        self.assertFalse(result)

    def test_open_alert_filter_only_keeps_open_alerts_for_force_additions(self):
        alerts = [
            alert("io.netty:netty-codec-http", "open", patched="4.1.125.Final"),
            alert("io.netty:netty-codec-http", "fixed", patched="4.1.125.Final"),
        ]

        open_alerts = [item for item in alerts if mdf._is_open_alert(item)]

        self.assertEqual(1, len(open_alerts))
        self.assertEqual("open", open_alerts[0]["state"])

    def test_apply_updates_preserves_non_removable_existing_forces_when_open_alerts_empty(self):
        with tempfile.TemporaryDirectory() as tmp:
            args, build_file = self.make_apply_args(
                tmp,
                build_lines=[
                    "buildscript {",
                    "    configurations.all {",
                    "        resolutionStrategy {",
                    "            // BEGIN AUTO FORCED DEPENDENCIES (managed by workflow)",
                    "            force(\"com.google.code.gson:gson:2.14.0\")",
                    "            force(\"org.apache.commons:commons-lang3:3.20.0\")",
                    "            // END AUTO FORCED DEPENDENCIES (managed by workflow)",
                    "        }",
                    "    }",
                    "}",
                ],
            )

            mdf.command_apply_updates(args)

            text = build_file.read_text()
            self.assertIn('force("com.google.code.gson:gson:2.14.0")', text)
            self.assertIn('force("org.apache.commons:commons-lang3:3.20.0")', text)

    def test_apply_updates_removes_only_existing_forces_marked_as_removable(self):
        with tempfile.TemporaryDirectory() as tmp:
            args, build_file = self.make_apply_args(
                tmp,
                build_lines=[
                    "buildscript {",
                    "    configurations.all {",
                    "        resolutionStrategy {",
                    "            // BEGIN AUTO FORCED DEPENDENCIES (managed by workflow)",
                    "            force(\"com.google.code.gson:gson:2.14.0\")",
                    "            force(\"org.apache.commons:commons-lang3:3.20.0\")",
                    "            // END AUTO FORCED DEPENDENCIES (managed by workflow)",
                    "        }",
                    "    }",
                    "}",
                ],
                removable=["com.google.code.gson:gson"],
            )

            mdf.command_apply_updates(args)

            text = build_file.read_text()
            self.assertNotIn('force("com.google.code.gson:gson:2.14.0")', text)
            self.assertIn('force("org.apache.commons:commons-lang3:3.20.0")', text)

    def test_apply_updates_merges_existing_and_security_forces_preferring_highest_version(self):
        with tempfile.TemporaryDirectory() as tmp:
            args, build_file = self.make_apply_args(
                tmp,
                build_lines=[
                    "buildscript {",
                    "    configurations.all {",
                    "        resolutionStrategy {",
                    "            // BEGIN AUTO FORCED DEPENDENCIES (managed by workflow)",
                    "            force(\"com.google.code.gson:gson:2.14.0\")",
                    "            force(\"com.google.guava:guava:33.6.0-jre\")",
                    "            // END AUTO FORCED DEPENDENCIES (managed by workflow)",
                    "        }",
                    "    }",
                    "}",
                ],
                alerts=[
                    alert("com.google.code.gson:gson", "open", patched="2.15.0"),
                    alert("com.google.guava:guava", "open", patched="33.5.0-jre"),
                ],
                natural={
                    "com.google.code.gson:gson": {
                        "buildscript.classpath": {"resolved": "2.15.0"},
                    },
                    "com.google.guava:guava": {
                        "buildscript.classpath": {"resolved": "33.5.0-jre"},
                    },
                },
            )

            mdf.command_apply_updates(args)

            text = build_file.read_text()
            self.assertIn('force("com.google.code.gson:gson:2.15.0")', text)
            self.assertNotIn('force("com.google.code.gson:gson:2.14.0")', text)
            self.assertIn('force("com.google.guava:guava:33.6.0-jre")', text)
            self.assertNotIn('force("com.google.guava:guava:33.5.0-jre")', text)

    def test_is_safe_to_remove_rejects_recent_historical_alert(self):
        result = mdf.is_safe_to_remove(
            dep="io.netty:netty-codec-http",
            natural_version="4.1.132.Final",
            forced_version="4.1.125.Final",
            alerts=[
                alert(
                    "io.netty:netty-codec-http",
                    "fixed",
                    fixed_at="2026-04-27T04:15:39Z",
                    patched="4.1.125.Final",
                )
            ],
            cooldown_hours=168,
            check_advisories=False,
            now=datetime(2026, 4, 28, 0, 0, tzinfo=timezone.utc),
        )
        self.assertFalse(result)

    def test_is_safe_to_remove_allows_old_historical_alert_after_cooldown(self):
        result = mdf.is_safe_to_remove(
            dep="io.netty:netty-codec-http",
            natural_version="4.1.132.Final",
            forced_version="4.1.125.Final",
            alerts=[
                alert(
                    "io.netty:netty-codec-http",
                    "fixed",
                    fixed_at="2026-04-01T00:00:00Z",
                    patched="4.1.125.Final",
                )
            ],
            cooldown_hours=168,
            check_advisories=False,
            now=datetime(2026, 4, 28, 0, 0, tzinfo=timezone.utc),
        )
        self.assertTrue(result)

    def test_is_safe_to_remove_rejects_below_patched_baseline(self):
        result = mdf.is_safe_to_remove(
            dep="io.netty:netty-codec-http",
            natural_version="4.1.124.Final",
            forced_version="4.1.124.Final",
            alerts=[
                alert(
                    "io.netty:netty-codec-http",
                    "fixed",
                    fixed_at="2026-04-01T00:00:00Z",
                    patched="4.1.125.Final",
                )
            ],
            cooldown_hours=168,
            check_advisories=False,
            now=datetime(2026, 4, 28, 0, 0, tzinfo=timezone.utc),
        )
        self.assertFalse(result)


if __name__ == "__main__":
    unittest.main()
