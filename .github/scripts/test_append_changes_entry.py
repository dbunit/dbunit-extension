#!/usr/bin/env python3
"""
Pytest test suite for append-changes-entry.py.

Run from the repo root with:
    pytest .github/scripts/test_append_changes_entry.py -v

Or from this directory:
    pytest test_append_changes_entry.py -v
"""

import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import pytest

SCRIPT = Path(__file__).parent / "append-changes-entry.py"
FIXTURE_DIR = Path(__file__).parent / "fixtures"
FIXTURE = FIXTURE_DIR / "changes.xml"
FIXTURE_EXPECTED = FIXTURE_DIR / "changes_after_first_run.xml"

NAMESPACE = {"c": "http://maven.apache.org/changes/1.0.0"}

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def run_script(tmp_file, **overrides):
    """
    Run append-changes-entry.py against tmp_file.

    Keyword arguments override the default test values.  Returns a
    CompletedProcess with stdout, stderr, and returncode.
    """
    defaults = {
        "pr_number": "123",
        "dependency_name": "org.example:my-dep",
        "previous_version": "1.0.0",
        "new_version": "1.1.0",
        "update_type": "version-update:semver-minor",
        "ecosystem": "maven",
    }
    defaults.update(overrides)

    cmd = [
        sys.executable, str(SCRIPT),
        "--changes-file", str(tmp_file),
        "--pr-number", str(defaults["pr_number"]),
        "--dependency-name", str(defaults["dependency_name"]),
        "--previous-version", str(defaults["previous_version"]),
        "--new-version", str(defaults["new_version"]),
        "--update-type", str(defaults["update_type"]),
        "--ecosystem", str(defaults["ecosystem"]),
    ]
    return subprocess.run(cmd, capture_output=True, text=True)


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def tmp_changes(tmp_path):
    """Copy the test fixture to a temporary file and return its Path."""
    dest = tmp_path / "changes.xml"
    shutil.copy(FIXTURE, dest)
    return dest


@pytest.fixture
def no_snapshot_changes(tmp_path):
    """A changes.xml file where the SNAPSHOT release has been removed."""
    content = FIXTURE.read_text(encoding="utf-8")
    # Strip the -SNAPSHOT suffix so no release matches
    content = content.replace('-SNAPSHOT"', '"')
    dest = tmp_path / "changes.xml"
    dest.write_text(content, encoding="utf-8")
    return dest


@pytest.fixture
def no_release_changes(tmp_path):
    """A changes.xml file with no <release> elements at all."""
    content = (
        '<?xml version="1.0"?>\n'
        '<document xmlns="http://maven.apache.org/changes/1.0.0">\n'
        '  <body>\n'
        '  </body>\n'
        '</document>\n'
    )
    dest = tmp_path / "changes.xml"
    dest.write_text(content, encoding="utf-8")
    return dest


# ---------------------------------------------------------------------------
# Happy path
# ---------------------------------------------------------------------------

class TestHappyPath:
    def test_exits_zero(self, tmp_changes):
        result = run_script(tmp_changes)
        assert result.returncode == 0, result.stderr + result.stdout

    def test_action_element_appended(self, tmp_changes):
        run_script(tmp_changes)
        content = tmp_changes.read_text(encoding="utf-8")
        assert 'dev="dependabot"' in content
        assert "org.example:my-dep" in content
        assert "1.0.0" in content
        assert "1.1.0" in content
        assert "(#123)" in content

    def test_output_is_valid_xml(self, tmp_changes):
        run_script(tmp_changes)
        ET.parse(tmp_changes)  # raises on invalid XML

    def test_action_is_inside_snapshot_release(self, tmp_changes):
        run_script(tmp_changes)
        tree = ET.parse(tmp_changes)
        body = tree.find("c:body", NAMESPACE)
        first_release = body.find("c:release", NAMESPACE)
        assert first_release.get("version").endswith("-SNAPSHOT")
        actions = first_release.findall("c:action", NAMESPACE)
        dep_actions = [a for a in actions if a.get("dev") == "dependabot"]
        assert len(dep_actions) == 1

    def test_released_blocks_untouched(self, tmp_changes):
        run_script(tmp_changes)
        tree = ET.parse(tmp_changes)
        body = tree.find("c:body", NAMESPACE)
        releases = body.findall("c:release", NAMESPACE)
        for rel in releases[1:]:  # skip SNAPSHOT
            dep_actions = [
                a for a in rel.findall("c:action", NAMESPACE)
                if a.get("dev") == "dependabot"
            ]
            assert dep_actions == [], (
                f"Dependabot action wrongly added to release {rel.get('version')}"
            )

    def test_idempotency_marker_present(self, tmp_changes):
        run_script(tmp_changes)
        content = tmp_changes.read_text(encoding="utf-8")
        assert "dependabot:dep=org.example:my-dep:new=1.1.0" in content

    def test_no_system_attribute_on_dependabot_action(self, tmp_changes):
        """Dependabot entries must NOT carry system= (no issue number to link,
        and an unrecognised system value breaks the changes-report table)."""
        run_script(tmp_changes)
        tree = ET.parse(tmp_changes)
        body = tree.find("c:body", NAMESPACE)
        first_release = body.find("c:release", NAMESPACE)
        dep_action = next(
            a for a in first_release.findall("c:action", NAMESPACE)
            if a.get("dev") == "dependabot"
        )
        assert dep_action.get("system") is None

    def test_due_to_attribute_is_Dependabot(self, tmp_changes):
        run_script(tmp_changes)
        tree = ET.parse(tmp_changes)
        body = tree.find("c:body", NAMESPACE)
        first_release = body.find("c:release", NAMESPACE)
        dep_action = next(
            a for a in first_release.findall("c:action", NAMESPACE)
            if a.get("dev") == "dependabot"
        )
        assert dep_action.get("due-to") == "Dependabot"

    def test_golden_file(self, tmp_path):
        """Output must exactly match the pre-computed golden file."""
        dest = tmp_path / "changes.xml"
        shutil.copy(FIXTURE, dest)
        result = run_script(
            dest,
            pr_number="42",
            dependency_name="org.example:my-dep",
            previous_version="1.0.0",
            new_version="1.1.0",
            update_type="version-update:semver-minor",
            ecosystem="maven",
        )
        assert result.returncode == 0, result.stderr + result.stdout
        actual = dest.read_text(encoding="utf-8")
        expected = FIXTURE_EXPECTED.read_text(encoding="utf-8")
        assert actual == expected


# ---------------------------------------------------------------------------
# Idempotency
# ---------------------------------------------------------------------------

class TestIdempotency:
    def test_second_run_exits_zero(self, tmp_changes):
        run_script(tmp_changes)
        result = run_script(tmp_changes)
        assert result.returncode == 0

    def test_second_run_produces_skip_message(self, tmp_changes):
        run_script(tmp_changes)
        result = run_script(tmp_changes)
        assert "Skipping" in result.stdout

    def test_second_run_does_not_modify_file(self, tmp_changes):
        run_script(tmp_changes)
        after_first = tmp_changes.read_text(encoding="utf-8")
        run_script(tmp_changes)
        after_second = tmp_changes.read_text(encoding="utf-8")
        assert after_first == after_second

    def test_different_dep_same_new_version_is_added(self, tmp_changes):
        """A different dependency at the same new-version is NOT idempotency-blocked."""
        run_script(tmp_changes)
        result = run_script(
            tmp_changes,
            dependency_name="org.other:another-dep",
            new_version="1.1.0",
        )
        assert result.returncode == 0
        content = tmp_changes.read_text(encoding="utf-8")
        assert content.count('dev="dependabot"') == 2

    def test_same_dep_different_new_version_is_added(self, tmp_changes):
        """The same dependency at a different new-version is NOT idempotency-blocked."""
        run_script(tmp_changes)
        result = run_script(tmp_changes, new_version="1.2.0")
        assert result.returncode == 0
        content = tmp_changes.read_text(encoding="utf-8")
        assert content.count('dev="dependabot"') == 2


# ---------------------------------------------------------------------------
# Multiple dependencies in the same release
# ---------------------------------------------------------------------------

class TestMultipleDeps:
    def test_two_deps_both_appended(self, tmp_changes):
        run_script(tmp_changes)
        run_script(
            tmp_changes,
            pr_number="200",
            dependency_name="org.other:second-dep",
            previous_version="1.9.0",
            new_version="2.0.0",
            update_type="version-update:semver-major",
        )
        content = tmp_changes.read_text(encoding="utf-8")
        assert content.count('dev="dependabot"') == 2

    def test_chronological_order_preserved(self, tmp_changes):
        """Entries appended later appear after earlier entries."""
        run_script(tmp_changes, dependency_name="org.example:first-dep")
        run_script(tmp_changes, dependency_name="org.example:second-dep",
                   new_version="2.0.0")
        content = tmp_changes.read_text(encoding="utf-8")
        pos1 = content.index("org.example:first-dep")
        pos2 = content.index("org.example:second-dep")
        assert pos1 < pos2

    def test_released_release_action_count_unchanged(self, tmp_changes):
        """Adding entries to SNAPSHOT must not affect already-released blocks."""
        tree_before = ET.parse(FIXTURE)
        body_before = tree_before.find("c:body", NAMESPACE)
        released_counts_before = {
            rel.get("version"): len(rel.findall("c:action", NAMESPACE))
            for rel in body_before.findall("c:release", NAMESPACE)
            if not rel.get("version", "").endswith("-SNAPSHOT")
        }

        run_script(tmp_changes)

        tree_after = ET.parse(tmp_changes)
        body_after = tree_after.find("c:body", NAMESPACE)
        released_counts_after = {
            rel.get("version"): len(rel.findall("c:action", NAMESPACE))
            for rel in body_after.findall("c:release", NAMESPACE)
            if not rel.get("version", "").endswith("-SNAPSHOT")
        }

        assert released_counts_before == released_counts_after


# ---------------------------------------------------------------------------
# No SNAPSHOT release
# ---------------------------------------------------------------------------

class TestNoSnapshotRelease:
    def test_exits_nonzero(self, no_snapshot_changes):
        result = run_script(no_snapshot_changes)
        assert result.returncode != 0

    def test_error_mentions_snapshot(self, no_snapshot_changes):
        result = run_script(no_snapshot_changes)
        combined = result.stdout + result.stderr
        assert "SNAPSHOT" in combined

    def test_error_mentions_error(self, no_snapshot_changes):
        result = run_script(no_snapshot_changes)
        combined = result.stdout + result.stderr
        assert "ERROR" in combined

    def test_file_not_modified_on_failure(self, no_snapshot_changes):
        original = no_snapshot_changes.read_text(encoding="utf-8")
        run_script(no_snapshot_changes)
        assert no_snapshot_changes.read_text(encoding="utf-8") == original

    def test_empty_body_exits_nonzero(self, no_release_changes):
        result = run_script(no_release_changes)
        assert result.returncode != 0


# ---------------------------------------------------------------------------
# Whitespace and formatting preservation
# ---------------------------------------------------------------------------

class TestWhitespacePreservation:
    def test_xml_declaration_preserved(self, tmp_changes):
        run_script(tmp_changes)
        content = tmp_changes.read_text(encoding="utf-8")
        assert content.startswith("<?xml version")

    def test_new_action_matches_sibling_indentation(self, tmp_changes):
        run_script(tmp_changes)
        content = tmp_changes.read_text(encoding="utf-8")
        action_lines = [ln for ln in content.splitlines() if "<action " in ln]
        indents = {len(ln) - len(ln.lstrip()) for ln in action_lines}
        assert len(indents) == 1, (
            f"Mixed indentation levels found: {indents}. "
            f"Action lines: {action_lines}"
        )

    def test_content_before_snapshot_unchanged(self, tmp_changes):
        original = tmp_changes.read_text(encoding="utf-8")
        run_script(tmp_changes)
        modified = tmp_changes.read_text(encoding="utf-8")
        # Everything before the SNAPSHOT <release> tag must be byte-identical
        snap_marker = '<release version="3.1.0-SNAPSHOT"'
        snap_pos = original.index(snap_marker)
        assert modified[:snap_pos] == original[:snap_pos]

    def test_content_after_snapshot_block_unchanged(self, tmp_changes):
        snap_marker = '<release version="3.1.0-SNAPSHOT"'
        original = tmp_changes.read_text(encoding="utf-8")
        run_script(tmp_changes)
        modified = tmp_changes.read_text(encoding="utf-8")
        # Locate </release> end in *each* copy independently: the new entry is
        # inserted before the closing tag, so the file lengths differ.
        orig_snap_start = original.index(snap_marker)
        orig_after = original.index("</release>", orig_snap_start) + len("</release>")
        mod_snap_start = modified.index(snap_marker)
        mod_after = modified.index("</release>", mod_snap_start) + len("</release>")
        assert modified[mod_after:] == original[orig_after:]

    def test_minimal_diff_line_count(self, tmp_changes):
        """Exactly two new lines are added: the idempotency comment and the <action>."""
        original_lines = tmp_changes.read_text(encoding="utf-8").splitlines()
        run_script(tmp_changes)
        modified_lines = tmp_changes.read_text(encoding="utf-8").splitlines()
        assert len(modified_lines) == len(original_lines) + 2
