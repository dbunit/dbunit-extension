#!/usr/bin/env python3
"""
Format a maven-changes-plugin `changes:announcement-generate` output file as
a Markdown GitHub Discussion body.

Called by the release GitHub Actions workflow after
`mvnw changes:announcement-generate -Dchanges.version=X.Y.Z` has produced
`target/announcement/announcement.vm`. Extracts only the categorized change
sections (e.g. "Fixed Bugs:", "Changes:") from that plain-text file, discards
the plugin's own boilerplate intro/footer, and renders Markdown, prefixed
with a fixed preamble whose "release focus" sentence comes from the matching
<release description="..."> attribute in changes.xml.

Usage:
    python3 format-announcement.py \\
        --version 3.4.0 \\
        --output discussion-body.md
"""

import argparse
import re
import sys
import xml.etree.ElementTree as ET

SECTION_START_MARKER = "Changes in this version include:"
HEADER_RE = re.compile(r'^[A-Za-z][A-Za-z0-9 /_-]*:$')
BULLET_PREFIX = "o "
ISSUE_RE = re.compile(r'\bIssue:\s*(\d+)\.')

BODY_INTRO_TEMPLATE = (
    "This release reflects a strong focus on {description}.\n"
    "Feedback and bug reports are always welcome via "
    "[GitHub Issues]({issues_url})."
)


def parse_args():
    p = argparse.ArgumentParser(
        description="Format a maven-changes-plugin announcement as a Markdown "
                    "GitHub Discussion body."
    )
    p.add_argument("--version", required=True,
                    help="Release version, e.g. 3.4.0 (must match a <release> "
                         "in changes.xml)")
    p.add_argument("--announcement-file",
                    default="target/announcement/announcement.vm",
                    help="Path to the changes:announcement-generate output "
                         "(override for testing)")
    p.add_argument("--changes-file", default="src/changes/changes.xml",
                    help="Path to changes.xml (override for testing)")
    p.add_argument("--issues-url",
                    default="https://github.com/dbunit/dbunit-extension/issues",
                    help="URL for the GitHub Issues link in the body preamble")
    p.add_argument("--output",
                    help="File to write the Markdown body to (default: stdout)")
    return p.parse_args()


def find_release_description(changes_file, version):
    """Return the description attribute of <release version="version">."""
    try:
        tree = ET.parse(changes_file)
    except ET.ParseError as exc:
        raise ValueError(f"{changes_file} is not valid XML: {exc}") from exc

    root = tree.getroot()
    ns_uri = root.tag.split('}')[0].lstrip('{') if root.tag.startswith('{') else ''
    ns = {"c": ns_uri} if ns_uri else {}
    prefix = "c:" if ns_uri else ""

    body = root.find(f"{prefix}body", ns)
    if body is None:
        raise ValueError(f"No <body> element found in {changes_file}.")

    for release in body.findall(f"{prefix}release", ns):
        if release.get("version") == version:
            description = release.get("description")
            if not description:
                raise ValueError(
                    f'<release version="{version}"> in {changes_file} has no '
                    "description attribute."
                )
            return description

    raise ValueError(f'No <release version="{version}"> found in {changes_file}.')


def extract_sections(announcement_text):
    """Return [(header, [bullet, ...]), ...] parsed from the announcement text.

    Only the categorized sections between the "Changes in this version
    include:" marker and the plugin's closing boilerplate (the first line
    that is neither a header nor a bullet) are returned.
    """
    lines = announcement_text.splitlines()
    try:
        start = next(
            i for i, ln in enumerate(lines) if ln.strip() == SECTION_START_MARKER
        )
    except StopIteration as exc:
        raise ValueError(
            f"Could not find {SECTION_START_MARKER!r} marker in the announcement "
            "file; the maven-changes-plugin template may have changed."
        ) from exc

    sections = []
    current_header = None
    current_bullets = []

    def flush():
        if current_header is not None:
            sections.append((current_header, current_bullets[:]))

    for raw in lines[start + 1:]:
        stripped = raw.strip()
        if not stripped:
            continue
        if stripped.startswith(BULLET_PREFIX):
            if current_header is None:
                continue
            current_bullets.append(stripped[len(BULLET_PREFIX):].strip())
        elif HEADER_RE.match(stripped):
            flush()
            current_header = stripped[:-1]
            current_bullets = []
        else:
            break

    flush()
    return sections


def format_bullet(text):
    """Rewrite trailing 'Issue: NNN.' fragments as '(#NNN)' for GitHub autolinking."""
    return ISSUE_RE.sub(r'(#\1)', text)


def render_section(header, bullets):
    """Render one categorized section (e.g. "Fixed Bugs") as a Markdown block."""
    lines = [f"### {header}", ""]
    lines.extend(f"- {format_bullet(bullet)}" for bullet in bullets)
    return "\n".join(lines)


def main():
    args = parse_args()

    try:
        description = find_release_description(args.changes_file, args.version)
    except (ValueError, FileNotFoundError) as exc:
        sys.exit(f"ERROR: {exc}")

    try:
        with open(args.announcement_file, "r", encoding="utf-8") as fh:
            announcement_text = fh.read()
    except FileNotFoundError as exc:
        sys.exit(f"ERROR: {exc}")

    try:
        sections = extract_sections(announcement_text)
    except ValueError as exc:
        sys.exit(f"ERROR: {exc}")

    if not sections:
        sys.exit(
            f"ERROR: No sections extracted from {args.announcement_file}. "
            "The maven-changes-plugin template may have changed."
        )

    body = (
        BODY_INTRO_TEMPLATE.format(description=description, issues_url=args.issues_url)
        + "\n\n"
        + "\n\n".join(render_section(header, bullets) for header, bullets in sections)
        + "\n"
    )

    if args.output:
        with open(args.output, "w", encoding="utf-8") as fh:
            fh.write(body)
    else:
        print(body)


if __name__ == "__main__":
    main()
