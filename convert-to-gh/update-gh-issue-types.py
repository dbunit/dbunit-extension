"""
Set GitHub issue types based on issue labels.

Usage:
    pip install requests
    export GITHUB_TOKEN=ghp_...

    python update-gh-issue-types.py dbunit/dbunit
    python update-gh-issue-types.py https://github.com/dbunit/dbunit-maven-plugin

Arguments:
    gh-repo   GitHub repository in 'owner/repo' format or full URL

Options:
    --dry-run     Preview without writing to GitHub
    --delay N     Minimum seconds between PATCH requests (default: 0.5)

Label → type mapping (bug takes precedence when an issue has both labels):
    bug          → Bug
    enhancement  → Feature

Issues that already have the correct type are skipped.
Pull requests are ignored.
"""

import argparse
import collections
import os
import re
import sys
import time

import requests


_GH_API = 'https://api.github.com'
_GH_HDRS = {
    'Accept': 'application/vnd.github+json',
    'X-GitHub-Api-Version': '2022-11-28',
}

# Ordered highest-priority first: when an issue has multiple mapped labels,
# the first match wins.
_LABEL_TO_TYPE: list[tuple[str, str]] = [
    ('bug', 'Bug'),
    ('enhancement', 'Feature'),
]


def _normalize_repo(gh_repo: str) -> str:
    m = re.match(r'https?://github\.com/([^/]+/[^/]+?)(?:\.git)?/?$', gh_repo)
    return m.group(1) if m else gh_repo.strip('/')


def parse_args(argv=None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description='Set GitHub issue types based on labels',
        add_help=False,
    )
    p.add_argument('gh_repo', metavar='GH-REPO',
                   help="GitHub repository in 'owner/repo' format or full URL")
    p.add_argument('--dry-run', action='store_true',
                   help='Preview without writing to GitHub')
    p.add_argument('--delay', metavar='SECONDS', type=float, default=0.5,
                   help='Minimum seconds between PATCH requests (default: 0.5)')
    p.add_argument('--help', '-h', action='help')
    return p.parse_args(argv)


def gh_session(token: str) -> requests.Session:
    s = requests.Session()
    s.headers.update(_GH_HDRS)
    s.headers['Authorization'] = 'Bearer ' + token
    return s


class ContentRateBudget:
    """Sliding-window guard for GitHub content-creating request limits (80/min, 500/hr)."""
    _PER_MIN_SAFE = 75
    _PER_HR_SAFE  = 490

    def __init__(self) -> None:
        self._times: collections.deque[float] = collections.deque()

    def wait_if_needed(self) -> None:
        now = time.time()
        while self._times and now - self._times[0] > 3600:
            self._times.popleft()
        if len(self._times) >= self._PER_HR_SAFE:
            wait = self._times[0] + 3600 - now + 0.1
            if wait > 0:
                until = time.strftime('%H:%M:%S', time.localtime(now + wait))
                print(f'\n  Content budget: 500/hr limit approached'
                      f' -- sleeping {wait:.0f}s (until {until})')
                time.sleep(wait)
                now = time.time()
                while self._times and now - self._times[0] > 3600:
                    self._times.popleft()
        cutoff = now - 60
        recent = [t for t in self._times if t > cutoff]
        if len(recent) >= self._PER_MIN_SAFE:
            wait = recent[0] + 60 - now + 0.1
            if wait > 0:
                until = time.strftime('%H:%M:%S', time.localtime(now + wait))
                print(f'\n  Content budget: 80/min limit approached'
                      f' -- sleeping {wait:.1f}s (until {until})')
                time.sleep(wait)

    def record(self) -> None:
        self._times.append(time.time())


def gh_request(session: requests.Session, method: str, url: str,
               budget: ContentRateBudget | None = None, **kwargs) -> requests.Response:
    """Make a GitHub API request; retry automatically on rate-limit responses."""
    is_content = method.upper() in ('POST', 'PATCH', 'PUT')
    if is_content and budget is not None:
        budget.wait_if_needed()

    secondary_wait = 60
    for attempt in range(8):
        resp = session.request(method, url, **kwargs)
        if resp.status_code == 429:
            wait = max(int(resp.headers.get('Retry-After', 60)), 60)
            print(f'\n  Rate limited (429) -- sleeping {wait}s ...')
            time.sleep(wait)
            continue
        if resp.status_code == 403:
            try:
                msg = resp.json().get('message', '')
            except Exception:
                msg = ''
            if 'secondary rate limit' in msg.lower():
                retry_after = resp.headers.get('Retry-After')
                wait = max(int(retry_after), 60) if retry_after else secondary_wait
                secondary_wait = min(secondary_wait * 2, 3600)
                print(f'\n  Secondary rate limit (403) -- sleeping {wait}s'
                      f' (attempt {attempt + 1}/8) ...')
                time.sleep(wait)
                continue
            if resp.headers.get('X-RateLimit-Remaining') == '0':
                reset = int(resp.headers.get('X-RateLimit-Reset', time.time() + 60))
                wait = max(reset - int(time.time()), 1)
                print(f'\n  Primary rate limit (403) -- sleeping {wait}s ...')
                time.sleep(wait)
                continue
        if is_content and budget is not None:
            budget.record()
        return resp
    if is_content and budget is not None:
        budget.record()
    return resp


def gh_load_issue_types(session: requests.Session, org: str) -> dict[str, int]:
    """Load org issue types; returns {name: id} for all types, empty if unavailable.

    Requires a token with read:org scope (classic) or
    "Organization issue types: Read" (fine-grained PAT).
    """
    resp = gh_request(session, 'GET', f'{_GH_API}/orgs/{org}/issue-types')
    if resp.status_code == 403:
        print(f'  WARN: issue types API returned 403 for org {org!r} -- '
              f'token may need read:org scope (classic PAT) or '
              f'"Organization issue types: Read" (fine-grained PAT)')
        return {}
    if resp.status_code in (404, 422):
        scopes_resp = session.get(f'{_GH_API}/rate_limit')
        scopes = scopes_resp.headers.get('X-OAuth-Scopes', '')
        if scopes:
            if 'read:org' not in scopes and 'admin:org' not in scopes:
                print(f'  WARN: issue types endpoint returned {resp.status_code} for org '
                      f'{org!r}; token scopes are [{scopes}] -- '
                      f'add read:org to your classic PAT to load issue types')
        else:
            print(f'  WARN: issue types endpoint returned {resp.status_code} for org {org!r}; '
                  f'if this org has issue types, the token may need read:org scope '
                  f'(classic PAT) or "Organization issue types: Read" (fine-grained PAT)')
        return {}
    if not resp.ok:
        print(f'  WARN: issue types API returned {resp.status_code} for org {org!r}: '
              f'{resp.text[:200]}')
        return {}
    data = resp.json()
    items = data if isinstance(data, list) else data.get('issue_types', [])
    return {it['name']: it['node_id'] for it in items if it.get('is_enabled') is not False}


_BATCH_SIZE = 20


def gh_set_issue_types_batch(
    session: requests.Session,
    updates: list[tuple[str, str]],
    budget: ContentRateBudget | None = None,
) -> tuple[list[bool], list[str | None]]:
    """Execute multiple updateIssue mutations in one GraphQL request.

    updates: [(issue_node_id, type_node_id), ...]
    Returns (results, errors) parallel to updates:
        results: True = type set successfully, False = error
        errors:  error message string, or None on success
    """
    params = ', '.join(f'$iid{i}:ID! $tid{i}:ID!' for i in range(len(updates)))
    bodies = ' '.join(
        f'u{i}:updateIssue(input:{{id:$iid{i},issueTypeId:$tid{i}}})'
        f'{{issue{{id}}}}'
        for i in range(len(updates))
    )
    variables = {}
    for i, (iid, tid) in enumerate(updates):
        variables[f'iid{i}'] = iid
        variables[f'tid{i}'] = tid

    resp = gh_request(session, 'POST', f'{_GH_API}/graphql',
                      budget=budget,
                      json={'query': f'mutation({params}){{{bodies}}}',
                            'variables': variables})
    resp.raise_for_status()
    data = resp.json()

    gql_data = data.get('data') or {}
    alias_errors: dict[str, str] = {}
    for err in (data.get('errors') or []):
        path = err.get('path') or []
        if path:
            alias_errors[path[0]] = err.get('message', str(err))

    results: list[bool] = []
    errors: list[str | None] = []
    for i in range(len(updates)):
        alias = f'u{i}'
        if alias in alias_errors:
            results.append(False)
            errors.append(alias_errors[alias])
        else:
            item = gql_data.get(alias) or {}
            results.append((item.get('issue') or {}).get('id') is not None)
            errors.append(None)
    return results, errors


_GQL_LIST_ISSUES = (
    'query($owner:String!,$name:String!,$after:String){'
    'repository(owner:$owner,name:$name){'
    'issues(first:100,after:$after,states:[OPEN,CLOSED]){'
    'nodes{number title id issueType{name} labels(first:20){nodes{name}}}'
    'pageInfo{hasNextPage endCursor}}}}'
)


def gh_list_all_issues(session: requests.Session, gh_repo: str) -> list[dict]:
    """Return all issues (open and closed, no PRs) for the repo, including issueType."""
    owner, name = gh_repo.split('/', 1)
    results = []
    after = None
    while True:
        variables: dict = {'owner': owner, 'name': name}
        if after:
            variables['after'] = after
        resp = gh_request(session, 'POST', f'{_GH_API}/graphql',
                          json={'query': _GQL_LIST_ISSUES, 'variables': variables})
        resp.raise_for_status()
        data = resp.json()
        issues_conn = ((data.get('data') or {})
                       .get('repository') or {}).get('issues') or {}
        for node in (issues_conn.get('nodes') or []):
            results.append({
                'number': node['number'],
                'title': node['title'],
                'node_id': node['id'],
                'issue_type': node.get('issueType'),
                'labels': [{'name': lbl['name']}
                           for lbl in (node.get('labels') or {}).get('nodes', [])],
            })
        page_info = issues_conn.get('pageInfo') or {}
        if not page_info.get('hasNextPage'):
            break
        after = page_info.get('endCursor')
    return results


def main(argv=None) -> None:
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    args = parse_args(argv)

    token = os.environ.get('GITHUB_TOKEN')
    if not token:
        print('ERROR: Set GITHUB_TOKEN env var before running.')
        sys.exit(1)

    gh_repo = _normalize_repo(args.gh_repo)
    dry_run = args.dry_run
    delay = args.delay
    org = gh_repo.split('/')[0]

    print(f'GH repo : {gh_repo}')
    if dry_run:
        print('(DRY-RUN -- no GitHub writes)\n')

    session = gh_session(token)
    budget = ContentRateBudget()

    issue_type_map = gh_load_issue_types(session, org)
    if not issue_type_map:
        print(f'ERROR: No issue types available for org {org!r} -- cannot proceed.')
        sys.exit(1)
    print(f'Issue types loaded: {sorted(issue_type_map)}')
    _scopes_resp = session.get(f'{_GH_API}/rate_limit')
    _scopes = _scopes_resp.headers.get('X-OAuth-Scopes', '')
    if _scopes:
        _has_write_org = 'write:org' in _scopes or 'admin:org' in _scopes
        if not _has_write_org:
            print(f'ERROR: Token scopes [{_scopes}] are missing write:org — '
                  f'GitHub will silently ignore the issue_type field.\n'
                  f'  Add write:org to your classic PAT, or use a fine-grained PAT '
                  f'with "Organization issue types: Write".')
            sys.exit(1)

    needed = {type_name for _, type_name in _LABEL_TO_TYPE}
    missing = needed - set(issue_type_map)
    if missing:
        print(f'ERROR: Expected type(s) not found in org {org!r}: {sorted(missing)}')
        print(f'  Available: {sorted(issue_type_map)}')
        sys.exit(1)

    # Fetch all issues once, then filter by label locally (avoids API label-query quirks).
    print('\nFetching all issues ...')
    all_issues = gh_list_all_issues(session, gh_repo)
    print(f'  {len(all_issues)} issue(s) fetched')

    label_counts: dict[str, int] = {label: 0 for label, _ in _LABEL_TO_TYPE}
    issues_to_update: dict[int, tuple[str, dict]] = {}  # {number: (type_name, issue)}

    for issue in all_issues:
        issue_labels = {lbl['name'].lower() for lbl in (issue.get('labels') or [])}
        for label, type_name in _LABEL_TO_TYPE:  # first match wins (priority order)
            if label.lower() in issue_labels:
                label_counts[label] += 1
                num = issue['number']
                if num not in issues_to_update:
                    issues_to_update[num] = (type_name, issue)
                break

    for label, type_name in _LABEL_TO_TYPE:
        print(f'  {label_counts[label]:4d} issue(s) with label {label!r} → {type_name!r}')

    if not issues_to_update:
        print('\nNo issues to update.')
        return

    print(f'\nProcessing {len(issues_to_update)} issue(s) ...')
    updated = skipped = failed = 0
    total = len(issues_to_update)
    to_update: list[tuple[str, str, int]] = []  # (issue_node_id, type_node_id, gh_num)

    for i, (num, (type_name, issue)) in enumerate(sorted(issues_to_update.items()), 1):
        title = (issue.get('title') or '')[:60]
        current_type = (issue.get('issue_type') or {}).get('name') or ''
        type_id = issue_type_map[type_name]

        if current_type == type_name:
            print(f'  [{i}/{total}] GH#{num} — already {type_name!r}, skipping')
            skipped += 1
            continue

        current_display = f'currently {current_type!r}' if current_type else 'no type'
        print(f'  [{i}/{total}] GH#{num} ({current_display}) → {type_name!r}  {title!r}')

        if dry_run:
            updated += 1
        else:
            to_update.append((issue['node_id'], type_id, num))

    if to_update:
        n_batches = (len(to_update) + _BATCH_SIZE - 1) // _BATCH_SIZE
        print(f'\nApplying {len(to_update)} update(s) in {n_batches} batch(es) of up to {_BATCH_SIZE} ...')
        for batch_start in range(0, len(to_update), _BATCH_SIZE):
            batch = to_update[batch_start:batch_start + _BATCH_SIZE]
            batch_num = batch_start // _BATCH_SIZE + 1
            lo, hi = batch[0][2], batch[-1][2]
            print(f'  Batch {batch_num}/{n_batches} (GH#{lo}..#{hi}) ...', flush=True)
            pairs = [(node_id, type_id) for node_id, type_id, _ in batch]
            try:
                time.sleep(delay)
                results, errors = gh_set_issue_types_batch(session, pairs, budget)
                batch_ok = batch_fail = 0
                for (_, _, num), ok, err in zip(batch, results, errors):
                    if ok:
                        updated += 1
                        batch_ok += 1
                    else:
                        msg = f': {err}' if err else ''
                        print(f'    WARN: GH#{num} issue_type not set{msg}')
                        failed += 1
                        batch_fail += 1
                status = f'{batch_ok} updated'
                if batch_fail:
                    status += f', {batch_fail} failed'
                print(f'    → {status}')
            except requests.HTTPError as exc:
                print(f'    FAILED: {exc.response.status_code}: {exc.response.text[:200]}')
                failed += len(batch)

    action = 'would be updated' if dry_run else 'updated'
    print(f'\nDone.  {updated} issue(s) {action}, {skipped} already correct, {failed} failed.')


if __name__ == '__main__':
    main()
