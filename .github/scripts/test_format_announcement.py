#!/usr/bin/env python3
"""
Pytest test suite for format-announcement.py.

Run from the repo root with:
    pytest .github/scripts/test_format_announcement.py -v

Or from this directory:
    pytest test_format_announcement.py -v
"""

import subprocess
import sys
from pathlib import Path

SCRIPT = Path(__file__).parent / "format-announcement.py"
FIXTURE_DIR = Path(__file__).parent / "fixtures"
ANNOUNCEMENT = FIXTURE_DIR / "announcement-sample.vm"
CHANGES = FIXTURE_DIR / "changes-sample.xml"


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def run_script(**overrides):
    """
    Run format-announcement.py with the given flag overrides.

    Keys use underscores (e.g. announcement_file) and are translated to
    --dashed-flags. A value of None omits the flag entirely. Returns a
    CompletedProcess with stdout, stderr, and returncode.
    """
    defaults = {
        "version": "3.4.0",
        "announcement_file": str(ANNOUNCEMENT),
        "changes_file": str(CHANGES),
    }
    defaults.update(overrides)

    cmd = [sys.executable, str(SCRIPT)]
    for key, value in defaults.items():
        if value is None:
            continue
        cmd += [f"--{key.replace('_', '-')}", str(value)]
    return subprocess.run(cmd, capture_output=True, text=True)


# ---------------------------------------------------------------------------
# Happy path
# ---------------------------------------------------------------------------

class TestHappyPath:
    def test_exits_zero(self):
        result = run_script()
        assert result.returncode == 0, result.stderr

    def test_preamble_uses_release_description(self):
        result = run_script()
        assert (
            "This release reflects a strong focus on Test-suite hardening "
            "and correctness fixes across export formats and timestamp "
            "handling." in result.stdout
        )

    def test_preamble_has_markdown_issues_link(self):
        result = run_script()
        assert (
            "[GitHub Issues](https://github.com/dbunit/dbunit-extension/issues)"
            in result.stdout
        )

    def test_section_headers_rendered_as_markdown_h3(self):
        result = run_script()
        assert "### Fixed Bugs" in result.stdout
        assert "### Changes" in result.stdout

    def test_bullets_rendered_as_markdown_list_items(self):
        result = run_script()
        assert "- Fix a bug in TimestampDataType." in result.stdout

    def test_issue_reference_rewritten_to_autolink_form(self):
        result = run_script()
        assert "(#831)" in result.stdout
        assert "Issue: 831." not in result.stdout

    def test_existing_pr_reference_untouched(self):
        result = run_script()
        assert "(#900)" in result.stdout

    def test_boilerplate_intro_and_footer_excluded(self):
        result = run_script()
        assert "pleased to announce" not in result.stdout
        assert "Have fun!" not in result.stdout

    def test_custom_issues_url_used_in_preamble(self):
        result = run_script(issues_url="https://example.invalid/issues")
        assert "[GitHub Issues](https://example.invalid/issues)" in result.stdout

    def test_output_file_written(self, tmp_path):
        out = tmp_path / "body.md"
        result = run_script(output=str(out))
        assert result.returncode == 0, result.stderr
        content = out.read_text(encoding="utf-8")
        assert "### Fixed Bugs" in content
        assert "This release reflects a strong focus on" in content


# ---------------------------------------------------------------------------
# Errors
# ---------------------------------------------------------------------------

class TestErrors:
    def test_unknown_version_exits_nonzero(self):
        result = run_script(version="9.9.9")
        assert result.returncode != 0
        assert "9.9.9" in result.stderr

    def test_missing_changes_file_exits_nonzero(self, tmp_path):
        result = run_script(changes_file=str(tmp_path / "missing.xml"))
        assert result.returncode != 0

    def test_missing_announcement_file_exits_nonzero(self, tmp_path):
        result = run_script(announcement_file=str(tmp_path / "missing.vm"))
        assert result.returncode != 0

    def test_missing_description_exits_nonzero(self, tmp_path):
        changes = tmp_path / "changes.xml"
        changes.write_text(
            '<?xml version="1.0"?>\n'
            '<document xmlns="http://maven.apache.org/changes/2.0.0">\n'
            "  <body>\n"
            '    <release version="3.4.0" date="2026-07-28"></release>\n'
            "  </body>\n"
            "</document>\n",
            encoding="utf-8",
        )
        result = run_script(changes_file=str(changes))
        assert result.returncode != 0
        assert "description" in result.stderr

    def test_missing_marker_exits_nonzero(self, tmp_path):
        announcement = tmp_path / "announcement.vm"
        announcement.write_text("Nothing useful here.\n", encoding="utf-8")
        result = run_script(announcement_file=str(announcement))
        assert result.returncode != 0

    def test_no_sections_after_marker_exits_nonzero(self, tmp_path):
        announcement = tmp_path / "announcement.vm"
        announcement.write_text(
            "Changes in this version include:\n\nHave fun!\n", encoding="utf-8"
        )
        result = run_script(announcement_file=str(announcement))
        assert result.returncode != 0

    def test_snapshot_suffixed_release_not_matched_by_bare_version(self, tmp_path):
        """Intentional: releasing.adoc's process always de-SNAPSHOTs changes.xml's
        release version before the tag is pushed, so this stays a strict match —
        a lenient one would silently mask a skipped manual release step instead
        of failing loudly."""
        changes = tmp_path / "changes.xml"
        changes.write_text(
            '<?xml version="1.0"?>\n'
            '<document xmlns="http://maven.apache.org/changes/2.0.0">\n'
            "  <body>\n"
            '    <release version="3.4.0-SNAPSHOT" date="TBD" description="In progress"></release>\n'
            "  </body>\n"
            "</document>\n",
            encoding="utf-8",
        )
        result = run_script(version="3.4.0", changes_file=str(changes))
        assert result.returncode != 0
        assert "3.4.0" in result.stderr
