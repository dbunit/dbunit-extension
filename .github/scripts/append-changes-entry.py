#!/usr/bin/env python3
"""
Append a single <action> entry for a Dependabot dependency update to
src/changes/changes.xml under the current -SNAPSHOT <release> block.

Called by the dependabot-changelog GitHub Actions workflow after each
Dependabot PR is merged. Safe to run multiple times with identical
arguments — idempotent via an embedded HTML comment marker.

Usage:
    python3 append-changes-entry.py \\
        --pr-number 123 \\
        --dependency-name org.example:my-lib \\
        --previous-version 1.0.0 \\
        --new-version 1.1.0 \\
        --update-type version-update:semver-minor \\
        --ecosystem maven
"""

import argparse
import re
import sys
import xml.etree.ElementTree as ET


def parse_args():
    p = argparse.ArgumentParser(
        description="Append a Dependabot update entry to changes.xml"
    )
    p.add_argument("--pr-number", required=True, type=int,
                   help="GitHub PR number that introduced the update")
    p.add_argument("--dependency-name", required=True,
                   help="Dependency identifier, e.g. org.foo:bar or actions/checkout")
    p.add_argument("--previous-version", required=True,
                   help="Version before the update")
    p.add_argument("--new-version", required=True,
                   help="Version after the update")
    p.add_argument("--update-type", required=True,
                   help="Semver update type, e.g. version-update:semver-patch")
    p.add_argument("--ecosystem", required=True,
                   help="Package ecosystem, e.g. maven, github-actions")
    p.add_argument("--changes-file", default="src/changes/changes.xml",
                   help="Path to changes.xml (override for testing)")
    return p.parse_args()


def find_snapshot_release_start(text):
    """Return the start index of the first -SNAPSHOT <release> open tag, or None."""
    m = re.search(r'<release\b[^>]*\bversion="[^"]*-SNAPSHOT"[^>]*>', text)
    return m.start() if m else None


def find_closing_release_tag(text, after):
    """Return the start index of the first </release> at or after `after`."""
    m = re.search(r'</release>', text[after:])
    if not m:
        return None
    return after + m.start()


def detect_action_indent(block):
    """Return the leading whitespace used before <action> elements in this block."""
    m = re.search(r'^(\s+)<action\b', block, re.MULTILINE)
    return m.group(1) if m else "      "  # default: 6 spaces, matching the real file


def build_action_line(indent, dep_name, prev_ver, new_ver, pr_num, ecosystem):
    """Return a single-line <action> element with an embedded idempotency marker."""
    marker = (
        f"<!-- dependabot:dep={dep_name}:new={new_ver}:pr={pr_num} -->"
    )
    body = (
        f"Update {ecosystem} dependency {dep_name} "
        f"from {prev_ver} to {new_ver} (#{pr_num})."
    )
    return (
        f'{indent}<action dev="dependabot" type="update" '
        f'system="github" due-to="Dependabot">{marker}{body}</action>'
    )


def main():
    args = parse_args()

    with open(args.changes_file, "r", encoding="utf-8") as fh:
        text = fh.read()

    # Validate the input file before touching it
    try:
        ET.fromstring(text)
    except ET.ParseError as exc:
        sys.exit(f"ERROR: {args.changes_file} is not valid XML: {exc}")

    # Locate the -SNAPSHOT release
    snap_start = find_snapshot_release_start(text)
    if snap_start is None:
        sys.exit(
            "ERROR: No -SNAPSHOT release found in changes.xml.\n"
            "Please manually add a new <release version='X.Y.Z-SNAPSHOT' "
            "date='TBD' description='...'> element before running this script."
        )

    # Find the </release> that closes the SNAPSHOT block.
    # <release> elements never nest, so the first </release> after snap_start
    # is the correct closing tag.
    close_tag_pos = find_closing_release_tag(text, snap_start)
    if close_tag_pos is None:
        sys.exit("ERROR: Could not find </release> closing tag for the SNAPSHOT release.")

    snapshot_block = text[snap_start:close_tag_pos + len("</release>")]

    # Idempotency: skip if this dep+version combo is already recorded
    idempotency_key = f"dependabot:dep={args.dependency_name}:new={args.new_version}"
    if idempotency_key in snapshot_block:
        print(
            f"INFO: Changelog entry already exists for "
            f"{args.dependency_name} -> {args.new_version} "
            f"(PR #{args.pr_number}). Skipping."
        )
        sys.exit(0)

    indent = detect_action_indent(snapshot_block)
    new_line = build_action_line(
        indent,
        args.dependency_name,
        args.previous_version,
        args.new_version,
        args.pr_number,
        args.ecosystem,
    )

    # Find the insertion point: just before the newline+whitespace that precedes
    # </release>, so the new entry lands at the end of the SNAPSHOT block.
    insert_at = text.rfind("\n", snap_start, close_tag_pos)
    if insert_at < 0:
        # Degenerate case: no newline before </release>; insert directly before it
        insert_at = close_tag_pos
        new_text = text[:insert_at] + "\n" + new_line + "\n" + text[insert_at:]
    else:
        new_text = text[:insert_at] + "\n" + new_line + text[insert_at:]

    # Validate the result before writing
    try:
        ET.fromstring(new_text)
    except ET.ParseError as exc:
        sys.exit(f"ERROR: Modified changes.xml is not valid XML: {exc}")

    with open(args.changes_file, "w", encoding="utf-8") as fh:
        fh.write(new_text)

    print(
        f"INFO: Added changelog entry: {args.dependency_name} "
        f"{args.previous_version} -> {args.new_version} (PR #{args.pr_number})"
    )


if __name__ == "__main__":
    main()
