"""
Build and maintain sf-to-gh-usernames.csv from the dev= and due-to= attributes
in both changes.xml files, then resolve each entry against SourceForge and
GitHub to populate display names and flag username mismatches.

Run order per row (idempotent — each step is skipped when already complete):
  0. Extract all sf_usernames from changes.xml files and merge with any
     existing CSV data; new entries start with gh_username = NOTFOUND.
  1. sf_name     — SourceForge display name from SF user profile API
  2. gh_username — resolved by looking up sf_username directly on GitHub
  3. gh_name     — GitHub display name for the resolved gh_username
  4. gh_by_name  — GitHub login found by searching GitHub for the sf_name;
                   NONE if no match, AMBIGUOUS if too many to resolve

Flag (always recomputed from current state):
  MISMATCH  — name search found exactly one user whose login differs from
               sf_username, suggesting the same handle belongs to different
               people on each platform
  AMBIGUOUS — name search returned too many results to pick one
  (empty)   — clean: name search confirmed the match, found nothing, or
               could not run (sf_name unavailable)

CSV columns: sf_username, gh_username, sf_name, gh_name, gh_by_name, flag

Usage:
    export GITHUB_TOKEN=ghp_...
    python create-sf-to-gh-usernames.py
"""

import csv
import os
import pathlib
import re
import sys
import time

import requests

SCRIPT_DIR = pathlib.Path(__file__).parent

CSV_PATH   = SCRIPT_DIR / 'sf-to-gh-usernames.csv'
FIELDNAMES = ['sf_username', 'gh_username', 'sf_name', 'gh_name', 'gh_by_name', 'flag']

CHANGES_XML_FILES = [
    (SCRIPT_DIR / '..' / 'src' / 'changes' / 'changes.xml').resolve(),
    (SCRIPT_DIR / '..' / '..' / 'dbunit-maven-plugin' / 'src' / 'changes' / 'changes.xml').resolve(),
]

GH_API = 'https://api.github.com'
SF_API = 'https://sourceforge.net/rest/u'

GH_USERNAME_RE = re.compile(r'^[a-zA-Z0-9][a-zA-Z0-9-]{0,37}$')
SF_USERNAME_RE = re.compile(r'^[a-zA-Z0-9][a-zA-Z0-9._-]*$')

SEARCH_THRESHOLD = 5    # more than this many results → AMBIGUOUS
API_DELAY        = 0.3
SEARCH_DELAY     = 2.1  # GH search API: 30 req/min; stay comfortably under


# ---------------------------------------------------------------------------
# Phase 0 — build the row list from changes.xml + existing CSV
# ---------------------------------------------------------------------------

def extract_usernames(paths: list[pathlib.Path]) -> set[str]:
    """Return every dev= and due-to= attribute value found in the XML files."""
    usernames: set[str] = set()
    for path in paths:
        if not path.exists():
            print(f'WARNING: {path} not found — skipping')
            continue
        content = path.read_text(encoding='utf-8')
        for m in re.finditer(r'\b(?:dev|due-to)=(["\'])([^"\']+)\1', content):
            usernames.add(m.group(2).strip())
    return usernames


def load_existing(path: pathlib.Path) -> dict[str, dict]:
    """Load existing CSV into {sf_username: row} preserving all populated data."""
    if not path.exists():
        return {}
    with open(path, encoding='utf-8', newline='') as f:
        return {row['sf_username']: row for row in csv.DictReader(f)}


def build_rows(usernames: set[str], existing: dict[str, dict]) -> list[dict]:
    """
    Merge extracted usernames with existing CSV data.
    Existing rows are kept as-is (preserving manual edits).
    New usernames get an empty NOTFOUND row.
    Result is sorted case-insensitively by sf_username.
    """
    rows: dict[str, dict] = {}

    for sf_user, row in existing.items():
        row.setdefault('sf_name',    '')
        row.setdefault('gh_name',    '')
        row.setdefault('gh_by_name', '')
        row.setdefault('flag',       '')
        rows[sf_user] = row

    for sf_user in usernames:
        if sf_user not in rows:
            rows[sf_user] = {
                'sf_username': sf_user,
                'gh_username': 'NOTFOUND',
                'sf_name':    '',
                'gh_name':    '',
                'gh_by_name': '',
                'flag':       '',
            }

    return sorted(rows.values(), key=lambda r: r['sf_username'].lower())


# ---------------------------------------------------------------------------
# API helpers
# ---------------------------------------------------------------------------

def sf_lookup_name(session: requests.Session, username: str) -> str:
    """Return SF display name for username, or '' if not found or not lookupable."""
    if not SF_USERNAME_RE.match(username):
        return ''
    try:
        resp = session.get(f'{SF_API}/{username}/profile', timeout=10)
        if resp.status_code == 200:
            data = resp.json()
            return (data.get('name') or data.get('display_name') or '').strip()
    except requests.RequestException:
        pass
    return ''


def gh_lookup_user(session: requests.Session, username: str) -> tuple[str, str]:
    """Return (login, display_name) for a GitHub username, or ('', '') if not found."""
    resp = session.get(f'{GH_API}/users/{username}', timeout=10)
    if resp.status_code == 200:
        data = resp.json()
        return data['login'], (data.get('name') or '').strip()
    return '', ''


def gh_search_by_name(
    session: requests.Session,
    name: str,
    sf_username: str,
) -> tuple[str, str]:
    """
    Search GitHub users by display name.

    Returns (gh_by_name, flag):
      gh_by_name : matched login | 'AMBIGUOUS' | 'NONE' | '' (API error)
      flag       : 'MISMATCH' | 'AMBIGUOUS' | ''
    """
    resp = session.get(
        f'{GH_API}/search/users',
        params={'q': f'{name} in:name', 'per_page': SEARCH_THRESHOLD + 1},
        timeout=15,
    )
    if resp.status_code != 200:
        print(f'    search error {resp.status_code}')
        return '', ''

    data   = resp.json()
    total  = data.get('total_count', 0)
    logins = [item['login'] for item in data.get('items', [])]

    if total == 0:
        return 'NONE', ''

    if total > SEARCH_THRESHOLD:
        return 'AMBIGUOUS', 'AMBIGUOUS'

    sf_lower = sf_username.lower()
    sf_match = next((l for l in logins if l.lower() == sf_lower), None)
    if sf_match:
        # Name search corroborates the username-based match.
        return sf_match, ''

    if len(logins) == 1:
        # One result but a different person.
        return logins[0], 'MISMATCH'

    # Several results, none matching sf_username.
    return 'AMBIGUOUS', 'AMBIGUOUS'


def compute_flag(gh_by_name: str, sf_username: str) -> str:
    if not gh_by_name or gh_by_name == 'NONE':
        return ''
    if gh_by_name == 'AMBIGUOUS':
        return 'AMBIGUOUS'
    if gh_by_name.lower() != sf_username.lower():
        return 'MISMATCH'
    return ''


def write_csv(path: pathlib.Path, rows: list[dict]) -> None:
    with open(path, 'w', encoding='utf-8', newline='') as f:
        w = csv.DictWriter(f, fieldnames=FIELDNAMES)
        w.writeheader()
        w.writerows(rows)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    token = os.environ.get('GITHUB_TOKEN', '')
    if not token:
        print('ERROR: Set GITHUB_TOKEN env var before running.')
        sys.exit(1)

    gh_session = requests.Session()
    gh_session.headers.update({
        'Accept': 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28',
        'Authorization': f'Bearer {token}',
    })
    sf_session = requests.Session()

    # Phase 0 — build row list
    print('Extracting usernames from changes.xml files ...')
    for p in CHANGES_XML_FILES:
        print(f'  {p}')
    usernames = extract_usernames(CHANGES_XML_FILES)
    existing  = load_existing(CSV_PATH)
    rows      = build_rows(usernames, existing)

    new_count = sum(1 for r in rows if r['sf_username'] not in existing)
    print(f'  {len(usernames)} usernames extracted  |  '
          f'{len(existing)} existing rows  |  '
          f'{new_count} new  |  '
          f'{len(rows)} total\n')

    # Write initial state so the file exists even if the script is interrupted.
    write_csv(CSV_PATH, rows)

    # Phases 1-4 — API lookups
    for row in rows:
        sf_user = row['sf_username']
        print(f'\n{sf_user}')

        # Step 1 — SF display name
        if not row['sf_name']:
            name = sf_lookup_name(sf_session, sf_user)
            row['sf_name'] = name
            print(f'  sf_name     : {name or "(none)"}')
            time.sleep(API_DELAY)
        else:
            print(f'  sf_name     : {row["sf_name"]} (cached)')

        # Step 2 — GH username by sf_username
        if row['gh_username'] == 'NOTFOUND' and GH_USERNAME_RE.match(sf_user):
            login, name = gh_lookup_user(gh_session, sf_user)
            if login:
                row['gh_username'] = login
                row['gh_name']     = name
                print(f'  gh_username : {login}')
                print(f'  gh_name     : {name or "(none)"}')
            else:
                print(f'  gh_username : not found on GitHub')
            time.sleep(API_DELAY)

        # Step 3 — GH display name for already-known username
        elif row['gh_username'] not in ('', 'NOTFOUND') and not row['gh_name']:
            _, name = gh_lookup_user(gh_session, row['gh_username'])
            row['gh_name'] = name
            print(f'  gh_name     : {name or "(none)"}')
            time.sleep(API_DELAY)

        # Step 4 — GH name search
        # Use sf_name when available; fall back to sf_username when it looks
        # like a real name (contains a space, e.g. "Andres Almiray").
        if not row['gh_by_name']:
            search_term = row['sf_name'] or (sf_user if ' ' in sf_user else '')
            if search_term:
                gh_by_name, _ = gh_search_by_name(gh_session, search_term, sf_user)
                row['gh_by_name'] = gh_by_name
                print(f'  gh_by_name  : {gh_by_name or "(no result)"}')
                time.sleep(SEARCH_DELAY)
            else:
                print(f'  gh_by_name  : (skipped — no name to search)')
        else:
            print(f'  gh_by_name  : {row["gh_by_name"]} (cached)')

        # Always recompute flag from current state
        row['flag'] = compute_flag(row['gh_by_name'], sf_user)
        if row['flag']:
            print(f'  *** FLAG    : {row["flag"]}')

    write_csv(CSV_PATH, rows)

    flagged = sum(1 for r in rows if r['flag'])
    print(f'\nDone.  {len(rows)} rows written to {CSV_PATH.name}  '
          f'({flagged} flagged for review)')


if __name__ == '__main__':
    main()
