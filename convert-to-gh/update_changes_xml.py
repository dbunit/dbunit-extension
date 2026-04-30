"""
Update Maven Changes Plugin changes.xml files to replace SourceForge issue IDs
with GitHub issue numbers, and SourceForge usernames with GitHub usernames,
using the mapping CSV(s) produced by create-gh-tickets.py and the username
mapping CSV.

Usage:
    python update_changes_xml.py [OPTIONS] <changes.xml> [<changes.xml> ...]

Options:
    --mapping FILE          Path to issue-map-<name> CSV; may be repeated for
                            multiple ticket lists (e.g. --mapping issue-map-bugs
                            --mapping issue-map-feature-requests)
    --usernames FILE        Path to sf-to-gh-usernames.csv; rewrites the dev=
                            and due-to= attributes in every <action> tag.
                            Optional — omit to skip username conversion.
                            Entries marked NOTFOUND in the CSV are silently left
                            unchanged; SF usernames absent from the CSV entirely
                            are warned and left unchanged.
    --backup / --no-backup  Write a .bak copy before modifying (default: on)
    --dry-run               Print changes without writing files
    --url                   Replace with full GitHub URL instead of bare number
    --system-map KEY:TRACKER  Teach the script how to interpret a system= attribute
                            value it does not know about.
                              KEY     — the exact string that appears in system="…"
                                        in the XML (e.g. sfplugintickets)
                              TRACKER — the sf_tracker value used in the mapping CSV
                                        for that same ticket list (e.g. plugintickets)
                            Three mappings are built in and need not be specified:
                              sfbugs      → bugs
                              sffeatures  → feature-requests
                              sftickets   → plugintickets  (dbunit-maven-plugin)
                            Use this option only when your changes.xml uses a
                            system= value other than those three.  May be repeated.
    --report FILE           Write a summary CSV of all replacements to FILE
    --help                  Show this message

How it works:
    The Maven Changes Plugin uses <action issue="NNN" system="sfbugs"> (or
    system="sffeatures") attributes in changes.xml.  NNN is the SourceForge
    ticket number.  This script loads the mapping CSV(s)
    (sf_tracker, sf_id → gh_issue_number / gh_url) and rewrites every matching
    issue="…" attribute in each XML file.

    Each SourceForge tracker has its own independent numbering, so the same
    number can appear in both the bugs tracker and the feature-requests tracker.
    The system= attribute in the XML tells the script which tracker an issue
    belongs to, so it can look up the right GitHub number.

    --system-map bridges the gap between what the XML says (system="sfbugs")
    and what the mapping CSV calls that tracker (sf_tracker = "bugs").  The
    built-in mappings cover the standard DbUnit trackers.  If your project's
    changes.xml uses a different system= value (e.g. system="sfplugintickets"),
    you must tell the script how to resolve it:
        --system-map sfplugintickets:plugintickets
    This says: "when the XML says system='sfplugintickets', look up entries
    whose sf_tracker column equals 'plugintickets' in the mapping CSV."

    When system= is absent or its value has no mapping, the script falls back
    to the action's type= attribute: type="fix" resolves to the bugs tracker,
    and any other type= value resolves to feature-requests.  This handles the
    combined-tracker CSV (bugs + feature-requests merged and sorted by date)
    where both trackers share the same SF ID sequence.  Only if type= is also
    absent does the script fall back to an unqualified lookup (first tracker wins).

    If --usernames is given, the script also rewrites the dev= and due-to=
    attributes on each <action> tag from SourceForge usernames to GitHub
    usernames, using the sf_username → gh_username mapping in that CSV.
    NOTFOUND entries are silently left unchanged; SF usernames not present in
    the CSV at all are warned.

    The system= attribute on every <action> tag is always rewritten to "github",
    regardless of its original value.  This happens unconditionally — no option
    controls it.

    Unmapped issue IDs are left untouched and reported as warnings.

Examples:
    # dbunit core (two ticket lists + username mapping)
    python update_changes_xml.py \\
        --mapping issue-map-bugs \\
        --mapping issue-map-feature-requests \\
        --usernames sf-to-gh-usernames.csv \\
        --backup --report report.csv \\
        src/changes/changes.xml

    # dbunit Maven plugin (sftickets is built-in, no --system-map needed)
    python update_changes_xml.py \\
        --mapping issue-map-plugintickets \\
        --usernames sf-to-gh-usernames.csv \\
        path/to/plugin/src/changes/changes.xml
"""

import argparse
import csv
import re
import shutil
import sys
from pathlib import Path

_BASE_SYSTEM_TO_TRACKER: dict[str, str] = {
    'sfbugs': 'bugs',
    'sffeatures': 'feature-requests',
    'sftickets': 'plugintickets',   # dbunit-maven-plugin changes.xml
}

ACTION_TAG_RE = re.compile(r'<action\b[^>]*>', re.DOTALL)


def _parse_system_map(spec: str) -> tuple[str, str]:
    parts = spec.split(':')
    if len(parts) != 2:
        raise argparse.ArgumentTypeError(
            f"Invalid system-map spec '{spec}': expected 'system_attr_value:tracker_name'"
        )
    return parts[0], parts[1]


def parse_args(argv=None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description='Rewrite SourceForge issue IDs -> GitHub issue numbers in changes.xml',
        add_help=False,
    )
    p.add_argument('files', nargs='*', metavar='changes.xml',
                   help='One or more changes.xml files to update')
    p.add_argument('--mapping', dest='mappings', action='append', metavar='FILE',
                   help='Path to issue-map-<name> CSV; may be repeated for multiple '
                        'ticket lists (e.g. --mapping issue-map-bugs '
                        '--mapping issue-map-feature-requests)')
    p.add_argument('--usernames', metavar='FILE',
                   help='Path to sf-to-gh-usernames.csv; rewrites dev= and due-to= attributes')
    p.add_argument('--backup', dest='backup', action='store_true',
                   help='Write .bak file before modifying (default)')
    p.add_argument('--no-backup', dest='backup', action='store_false',
                   help='Skip .bak copies')
    p.set_defaults(backup=True)
    p.add_argument('--dry-run', action='store_true',
                   help='Print changes without writing files')
    p.add_argument('--url', action='store_true',
                   help='Replace with full GitHub URL instead of bare issue number')
    p.add_argument('--system-map', dest='system_maps', action='append',
                   type=_parse_system_map, metavar='KEY:TRACKER',
                   help='Map a system= attribute value to a tracker name; may be repeated. '
                        'Built-in: sfbugs->bugs, sffeatures->feature-requests, '
                        'sftickets->plugintickets')
    p.add_argument('--report', metavar='FILE',
                   help='Write a summary CSV of all replacements to FILE')
    p.add_argument('--help', '-h', action='help')
    return p.parse_args(argv)


def load_mapping(csv_path: Path, use_url: bool) -> tuple[dict, dict, list[str]]:
    """
    Returns:
        qualified   – {(sf_tracker, sf_id): replacement_value}
        unqualified – {sf_id: replacement_value}
        warnings    – list of collision warning strings
    """
    if not csv_path.exists():
        print(f'ERROR: Mapping file not found: {csv_path}', file=sys.stderr)
        sys.exit(1)

    field = 'gh_url' if use_url else 'gh_issue_number'
    rows: list[tuple[str, str, str]] = []
    with open(csv_path, encoding='utf-8') as fh:
        for row in csv.DictReader(fh):
            sf_id = str(row['sf_id']).strip()
            tracker = str(row['sf_tracker']).strip()
            rows.append((sf_id, tracker, str(row[field]).strip()))

    qualified: dict[tuple[str, str], str] = {}
    unqualified: dict[str, str] = {}
    first_seen: dict[str, str] = {}

    for sf_id, tracker, value in rows:
        qualified[(tracker, sf_id)] = value
        if sf_id not in first_seen:
            first_seen[sf_id] = tracker
            unqualified[sf_id] = value

    return qualified, unqualified, []


def load_usernames(csv_path: Path) -> dict[str, str | None]:
    """
    Returns {sf_username: gh_username}, where NOTFOUND entries map to None.
    """
    if not csv_path.exists():
        print(f'ERROR: Username mapping file not found: {csv_path}', file=sys.stderr)
        sys.exit(1)
    result: dict[str, str | None] = {}
    with open(csv_path, encoding='utf-8') as fh:
        for row in csv.DictReader(fh):
            sf = str(row['sf_username']).strip()
            gh = str(row['gh_username']).strip()
            result[sf] = None if gh.upper() == 'NOTFOUND' else gh
    return result


def rewrite_content(
    content: str,
    qualified: dict[tuple[str, str], str],
    unqualified: dict[str, str],
    user_map: dict[str, str | None],
    source_label: str,
    system_to_tracker: dict[str, str],
) -> tuple[str, list[dict]]:
    """
    Replace issue="SF_ID", dev="SF_USER", due-to="SF_USER", and system="*" in each
    <action> tag.  system= is always set to "github".

    Uses the original system= value to pick the right tracker for issue lookups
    (qualified lookup), falling back to the unqualified mapping when system= is
    absent or unmapped.

    Returns:
        new_content  – updated string
        replacements – list of dicts describing every replacement made
    """
    replacements: list[dict] = []
    unmapped_issues: set[str] = set()
    unmapped_users: set[str] = set()

    def replace_tag(m: re.Match) -> str:
        tag = m.group(0)
        line_num = content[:m.start()].count('\n') + 1

        # issue= replacement
        issue_m = re.search(r'\bissue=(["\'])([^"\']+)\1', tag)
        if issue_m:
            quote = issue_m.group(1)
            sf_id = issue_m.group(2).strip()

            sys_m = re.search(r'\bsystem=(["\'])([^"\']+)\1', tag)
            tracker = None
            if sys_m:
                tracker = system_to_tracker.get(sys_m.group(2))

            # When system= doesn't resolve, use type= to disambiguate:
            # type="fix" → bugs tracker; anything else → feature-requests.
            if tracker is None:
                type_m = re.search(r'\btype=(["\'])([^"\']+)\1', tag)
                if type_m:
                    tracker = 'bugs' if type_m.group(2).strip().lower() == 'fix' else 'feature-requests'

            gh_value = None
            if tracker is not None:
                gh_value = qualified.get((tracker, sf_id))
            if gh_value is None:
                gh_value = unqualified.get(sf_id)

            if gh_value is not None:
                replacements.append({'file': source_label, 'line': line_num,
                                     'attr': 'issue', 'sf_value': sf_id, 'gh_value': gh_value})
                tag = re.sub(
                    r'\bissue=(["\'])' + re.escape(sf_id) + r'\1',
                    f'issue={quote}{gh_value}{quote}',
                    tag,
                )
            else:
                unmapped_issues.add(sf_id)

        # dev= and due-to= replacements
        if user_map:
            for attr in ('dev', 'due-to'):
                attr_re = re.compile(r'\b' + re.escape(attr) + r'=(["\'])([^"\']+)\1')
                attr_m = attr_re.search(tag)
                if not attr_m:
                    continue
                quote = attr_m.group(1)
                sf_user = attr_m.group(2).strip()
                if sf_user not in user_map:
                    unmapped_users.add(sf_user)
                    continue
                gh_user = user_map[sf_user]
                if gh_user is None:
                    continue
                replacements.append({'file': source_label, 'line': line_num,
                                     'attr': attr, 'sf_value': sf_user, 'gh_value': gh_user})
                tag = attr_re.sub(f'{attr}={quote}{gh_user}{quote}', tag, count=1)

        # system= replacement — always set to "github"
        sys_m = re.search(r'\bsystem=(["\'])([^"\']+)\1', tag)
        if sys_m:
            old_sys = sys_m.group(2)
            if old_sys != 'github':
                quote = sys_m.group(1)
                replacements.append({'file': source_label, 'line': line_num,
                                     'attr': 'system', 'sf_value': old_sys, 'gh_value': 'github'})
                tag = re.sub(r'\bsystem=(["\'])[^"\']+\1', f'system={quote}github{quote}', tag, count=1)

        return tag

    new_content = ACTION_TAG_RE.sub(replace_tag, content)

    for sf_id in sorted(unmapped_issues):
        print(f"  ⚠  No mapping for issue id '{sf_id}' in {source_label} — left unchanged",
              file=sys.stderr)
    for sf_user in sorted(unmapped_users):
        print(f"  ⚠  SF user '{sf_user}' not in username mapping — left unchanged",
              file=sys.stderr)

    return new_content, replacements


def main(argv=None) -> None:
    args = parse_args(argv)

    if not args.files:
        print('No input files specified.  Pass one or more changes.xml paths.')
        print('Use --help for usage information.')
        sys.exit(1)

    if not args.mappings:
        print('ERROR: No mapping file specified.', file=sys.stderr)
        print('Use --mapping issue-map-<name> (may be repeated).', file=sys.stderr)
        sys.exit(1)

    system_to_tracker = dict(_BASE_SYSTEM_TO_TRACKER)
    if args.system_maps:
        for key, tracker in args.system_maps:
            print(f'Custom system map: {key} → {tracker}')
            system_to_tracker[key] = tracker

    qualified: dict[tuple[str, str], str] = {}
    unqualified: dict[str, str] = {}

    for mapping_path_str in args.mappings:
        mapping_path = Path(mapping_path_str)
        print(f'Loading mapping from: {mapping_path}')
        q, uq, warnings = load_mapping(mapping_path, args.url)
        for w in warnings:
            print(f'  {w}')
        qualified.update(q)
        for sf_id, value in uq.items():
            if sf_id not in unqualified:
                unqualified[sf_id] = value

    print(f'  {len(qualified)} SF ticket(s) loaded ({len(unqualified)} unique IDs across trackers).\n')

    user_map: dict[str, str | None] = {}
    if args.usernames:
        print(f'Loading username mapping from: {args.usernames}')
        user_map = load_usernames(Path(args.usernames))
        print(f'  {len(user_map)} SF username(s) loaded.\n')

    all_replacements: list[dict] = []

    for xml_path_str in args.files:
        xml_path = Path(xml_path_str)
        if not xml_path.exists():
            print(f'SKIP: file not found: {xml_path}', file=sys.stderr)
            continue

        print(f'Processing: {xml_path}')
        original = xml_path.read_text(encoding='utf-8')
        new_content, replacements = rewrite_content(
            original, qualified, unqualified, user_map, str(xml_path), system_to_tracker,
        )

        if not replacements:
            print('  No replacements matched — file unchanged.\n')
            continue

        print(f'  {len(replacements)} replacement(s):')
        for r in replacements:
            print(f'    line {r["line"]:>4}:  {r["attr"]}="{r["sf_value"]}"'
                  f'  →  {r["attr"]}="{r["gh_value"]}"')

        all_replacements.extend(replacements)

        if args.dry_run:
            print(f'  DRY-RUN: would overwrite {xml_path}\n')
            continue

        if args.backup:
            suffix = xml_path.suffix or ''
            bak = xml_path.with_suffix(suffix + '.bak')
            shutil.copy2(xml_path, bak)
            print(f'  Backup written: {bak}')

        xml_path.write_text(new_content, encoding='utf-8')
        print(f'  ✓  Written: {xml_path}\n')

    if args.report:
        report_path = Path(args.report)
        with open(report_path, 'w', encoding='utf-8', newline='') as fh:
            w = csv.DictWriter(fh, fieldnames=['file', 'line', 'attr', 'sf_value', 'gh_value'])
            w.writeheader()
            w.writerows(all_replacements)
        print(f'Report written: {report_path}')

    dry_suffix = ' (dry-run — XML files unchanged)' if args.dry_run else ''
    total = len(all_replacements)
    n_files = len(args.files)
    print(f'\nDone.  {total} replacement(s) across {n_files} file(s){dry_suffix}.')


if __name__ == '__main__':
    main()
