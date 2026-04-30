"""
Create GitHub issues from a SourceForge ticket dump and write a mapping file.

Usage:
    pip install requests
    export GITHUB_TOKEN=ghp_...

    python create-gh-tickets.py sf-tickets-bugs dbunit/dbunit-extension
    python create-gh-tickets.py sf-tickets-bugs https://github.com/dbunit/dbunit-extension

Arguments:
    sf-tickets-file   Path to JSON file produced by download-sf-tickets.py
    gh-repo           GitHub repository in 'owner/repo' format or full URL

Options:
    --label LABEL     Single GitHub label applied to every issue to classify the tracker type
                      (default: derived from ticket_list_name in the JSON file;
                      known mappings: bugs->bug, feature-requests->enhancement,
                      plugintickets->enhancement, else bug)
    --delay SECONDS   Minimum seconds between issues (default: 0.5)
    --dry-run         Preview without touching GitHub or writing the mapping

Output:
    issue-map-<ticket-list-name>.csv  CSV mapping SF ticket numbers to GH issue numbers

Mapping CSV columns:
    sf_tracker, sf_id, sf_created, sf_status, gh_issue_number, gh_url

Created GitHub issue structure:
    Title:   SF ticket summary (falls back to title, then '(no title) #N')
    State:   open               -- 'open', 'open-accepted', 'open-fixed', 'pending'
             closed/completed   -- 'closed', 'closed-fixed', 'closed-accepted',
                                   'closed-remind', 'closed-out-of-date'
             closed/not_planned -- 'closed-wont-fix', 'closed-invalid',
                                   'closed-works-for-me', 'closed-rejected',
                                   'open-rejected', 'pending-rejected'
             closed/duplicate   -- 'closed-duplicate'
    Labels:  Applied to every issue:
               1. from-sourceforge        (always)
               2. --label value           (e.g. bug, enhancement; classifies tracker type)
               3. Each SF label from the ticket's labels field
    Assignees: SF assigned_to mapped to GitHub assignees; silently dropped by GitHub
               if the username does not exist or lacks repo access.
    Milestone: SF custom_fields._milestone mapped to a GitHub milestone (created if needed).
    Body:
        <!-- sf-migration ... -->   Hidden HTML comment with structured SF metadata:
                                    sf-tracker, sf-id, sf-created, sf-status, and sf-url
                                    (the original SF ticket URL, for machine processing)
        Priority / Labels block     Optional; rendered only when present on the SF ticket
        Blockquote header           Visible attribution rendered as a blockquote:
                                      > **Migrated from SourceForge** — [Tracker #N](<sf-url>)
                                      > Originally reported by **author** on <date>
                                      > Assigned to **assignee** (or *(unassigned)*)
                                      > SF status: <status>
                                      > Closed on <mod_date>        (closed tickets only)
                                      > Votes: N                    (when votes_up > 0)
                                    The SF ticket URL is the hyperlink on 'Tracker #N' and is
                                    the primary human-visible link back to the original ticket.
        Description                 SF ticket description text
        Discussion posts            Non-meta SF comments appended after a horizontal rule,
                                    each rendered as '**author** — timestamp' + post text,
                                    followed by any file attachments as '[name](url) (N bytes)'
"""

import argparse
import collections
import csv
import json
import os
import pathlib
import re
import sys
import time

import requests


_GH_API = 'https://api.github.com'
_GH_HDRS = {
    'Accept': 'application/vnd.github+json',
    'X-GitHub-Api-Version': '2022-11-28',
}

_KNOWN_LABEL_DEFAULTS: dict[str, str] = {
    'bugs': 'bug',
    'feature-requests': 'enhancement',
    'plugintickets': 'enhancement',
}

_KNOWN_ISSUE_TYPE_NAMES: dict[str, str] = {
    'bugs': 'Bug',
    'feature-requests': 'Feature',
    'plugintickets': 'Feature',
}

MAPPING_HEADERS = ['sf_tracker', 'sf_id', 'sf_created', 'sf_status', 'gh_issue_number', 'gh_url']


def load_username_map(csv_path: pathlib.Path) -> dict[str, str]:
    """Load SF->GH username mapping from CSV; returns empty dict if file not found."""
    if not csv_path.exists():
        return {}
    result: dict[str, str] = {}
    with open(csv_path, encoding='utf-8', newline='') as f:
        for row in csv.DictReader(f):
            sf = (row.get('sf_username') or '').strip()
            gh = (row.get('gh_username') or '').strip()
            if sf:
                result[sf] = gh
    return result


def _normalize_repo(gh_repo: str) -> str:
    """Accept 'owner/repo' or 'https://github.com/owner/repo' → 'owner/repo'."""
    m = re.match(r'https?://github\.com/([^/]+/[^/]+?)(?:\.git)?/?$', gh_repo)
    return m.group(1) if m else gh_repo.strip('/')


def _sf_human_ticket_url(sf_rest_url: str, ticket_num: int | str) -> str:
    """Convert REST API base URL + ticket number to human-readable SF URL."""
    human_base = sf_rest_url.replace('/rest/', '/', 1).rstrip('/')
    return f'{human_base}/{ticket_num}/'


def parse_args(argv=None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description='Create GitHub issues from a SourceForge ticket dump',
        add_help=False,
    )
    p.add_argument('sf_tickets_file', metavar='sf-tickets-file',
                   help='Path to JSON file produced by download-sf-tickets.py')
    p.add_argument('gh_repo', metavar='GH-REPO',
                   help="GitHub repository in 'owner/repo' format or full URL")
    p.add_argument('--label', metavar='LABEL',
                   help='Single GitHub label applied to every issue to classify the tracker '
                        'type (default: derived from ticket_list_name in the JSON file; '
                        'known mappings: bugs->bug, feature-requests->enhancement, '
                        'plugintickets->enhancement, else bug)')
    p.add_argument('--delay', metavar='SECONDS', type=float, default=0.5,
                   help='Minimum seconds between issues (default: 0.5); '
                        'content-creation rate (80/min, 500/hr) is managed automatically')
    p.add_argument('--dry-run', action='store_true',
                   help='Preview without touching GitHub or writing the mapping')
    p.add_argument('--help', '-h', action='help')
    return p.parse_args(argv)


def sf_status_to_gh_state(sf_status: str) -> str:
    s = sf_status.lower()
    return 'closed' if s.startswith('closed') or 'rejected' in s else 'open'


def sf_status_to_reason(sf_status: str) -> str:
    s = sf_status.lower()
    if 'duplicate' in s:
        return 'duplicate'
    not_planned_keywords = ('wont-fix', 'invalid', 'works-for-me', 'rejected', 'out-of-date')
    return 'not_planned' if any(k in s for k in not_planned_keywords) else 'completed'


def build_gh_body(ticket: dict, tracker: str, ticket_num: int | str, sf_ticket_url: str) -> str:
    """Build the GitHub issue body from a SourceForge ticket dict."""
    submitter = ticket.get('reported_by') or ticket.get('submitter') or 'unknown'
    assigned_to = ticket.get('assigned_to') or ''
    created = ticket.get('created_date') or ticket.get('created') or ''
    closed = (ticket.get('mod_date') or '') if sf_status_to_gh_state(ticket.get('status') or '') == 'closed' else ''
    sf_status = ticket.get('status') or ''
    votes_up = ticket.get('votes_up') or 0
    priority = (ticket.get('custom_fields') or {}).get('_priority') or ''
    sf_labels = ticket.get('labels') or []
    desc = (ticket.get('description') or '').strip() or '*(no description)*'

    lines = [
        '<!-- sf-migration',
        f'     sf-tracker: {tracker}',
        f'     sf-id:      {ticket_num}',
        f'     sf-created: {created}',
        f'     sf-status:  {sf_status}',
        f'     sf-url:     {sf_ticket_url}',
        '-->',
    ]

    tracker_display = tracker.replace('-', ' ').title()
    lines += [
        '',
        f'> **Migrated from SourceForge** — [{tracker_display} #{ticket_num}]({sf_ticket_url})',
        f'> Originally reported by **{submitter}** on {created}',
        *(([f'> Priority: `{priority}`']) if priority else []),
        *(([f'> Labels: {", ".join(sf_labels)}']) if sf_labels else []),
        f'> Assigned to **{assigned_to}**' if assigned_to else '> Assigned to *(unassigned)*',
        *(([f'> SF status: {sf_status}']) if sf_status else []),
        *(([f'> Closed on {closed}']) if closed else []),
        *(([f'> Votes: {votes_up}']) if votes_up else []),
        '',
        desc,
    ]

    ticket_attachments = [a for a in (ticket.get('attachments') or []) if a.get('url')]
    if ticket_attachments:
        lines.append('')
        for att in ticket_attachments:
            name = att['url'].split('/')[-1]
            size = att.get('bytes') or 0
            lines.append(f'[{name}]({att["url"]}) ({size:,} bytes)')

    related = ticket.get('related_artifacts') or []
    if related:
        lines += ['', '**Related SourceForge artifacts:**']
        for artifact in related:
            url = f'https://sourceforge.net{artifact}' if artifact.startswith('/') else artifact
            lines.append(f'- {url}')

    posts = [p for p in ((ticket.get('discussion_thread') or {}).get('posts') or [])
             if not p.get('is_meta')]
    if posts:
        lines += ['', '---']
        for post in posts:
            author = post.get('author') or post.get('author_id') or 'unknown'
            timestamp = post.get('timestamp') or post.get('date') or ''
            text = (post.get('text') or '').strip()
            post_attachments = [a for a in (post.get('attachments') or []) if a.get('url')]
            if not text and not post_attachments:
                continue
            lines += ['', f'**{author}** — {timestamp}']
            if text:
                lines.append(text)
            for att in post_attachments:
                name = att['url'].split('/')[-1]
                size = att.get('bytes') or 0
                lines.append(f'[{name}]({att["url"]}) ({size:,} bytes)')

    return '\n'.join(lines)


def gh_session(token: str) -> requests.Session:
    s = requests.Session()
    s.headers.update(_GH_HDRS)
    s.headers['Authorization'] = 'Bearer ' + token
    return s


class ContentRateBudget:
    """Sliding-window guard for GitHub's content-creating request limits.

    GitHub imposes a separate limit on "content-creating" requests (POST/PATCH/PUT on
    REST, or GraphQL mutations) on top of the normal 5,000 req/hr primary limit:
      - 80 content-creating requests per minute
      - 500 content-creating requests per hour

    These limits apply equally to REST and GraphQL mutations, so every createIssue and
    closeIssue mutation counts against the same budget as REST POSTs.

    How the implementation works:
      - A deque records the timestamp of every content-creating request made.
      - Before each request, wait_if_needed() checks both windows (last 60s and last
        3600s).  If either is at the safe cap, it sleeps only as long as needed for the
        oldest entry in that window to expire — no more.
      - record() appends the current time after the request completes.
      - The safe caps (75/min, 490/hr) leave a small buffer below the hard limits so
        minor clock skew or burst rounding doesn't trip the real limit.

    The --delay argument is a separate, per-ticket courtesy floor (default 0.5s).  It
    ensures a minimum gap between issues regardless of budget headroom, but it is NOT
    the primary throttle — ContentRateBudget is.  Because createIssue now also sets the
    issue type in the same GraphQL call (one mutation instead of the old create + PATCH
    + PATCH), each ticket consumes at most two content-creating requests (one for
    createIssue, one for closeIssue on closed tickets), so the 0.5s floor is sufficient
    without over-throttling.
    """
    _PER_MIN_SAFE = 75   # 80/min hard limit minus 5 for clock-skew headroom
    _PER_HR_SAFE  = 490  # 500/hr hard limit minus 10 for burst headroom

    def __init__(self) -> None:
        self._times: collections.deque[float] = collections.deque()

    def _fmt(self, now: float) -> str:
        cutoff_hr  = now - 3600
        cutoff_min = now - 60
        hr_count   = sum(1 for t in self._times if t > cutoff_hr)
        min_count  = sum(1 for t in self._times if t > cutoff_min)
        window_start = time.strftime('%H:%M:%S', time.localtime(self._times[0])) if self._times else '-'
        return (f'hr={hr_count}/{self._PER_HR_SAFE}  min={min_count}/{self._PER_MIN_SAFE}'
                f'  total={len(self._times)}  window-start={window_start}')

    def wait_if_needed(self) -> None:
        now = time.time()
        while self._times and now - self._times[0] > 3600:
            self._times.popleft()

        if len(self._times) >= self._PER_HR_SAFE:
            wait = self._times[0] + 3600 - now + 0.1
            if wait > 0:
                until = time.strftime('%H:%M:%S', time.localtime(now + wait))
                print(f'\n  Content budget: 500/hr limit approached'
                      f' -- sleeping {wait:.0f}s (until {until})'
                      f'\n  [{self._fmt(now)}]')
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
                      f' -- sleeping {wait:.1f}s (until {until})'
                      f'\n  [{self._fmt(now)}]')
                time.sleep(wait)

    def record(self) -> None:
        self._times.append(time.time())


def _fmt_ratelimit_headers(resp: requests.Response) -> str:
    h = resp.headers
    reset_ts = h.get('X-RateLimit-Reset')
    reset_str = (f'  reset={time.strftime("%H:%M:%S", time.localtime(int(reset_ts)))}' if reset_ts else '')
    parts = []
    for key in ('X-RateLimit-Resource', 'X-RateLimit-Limit', 'X-RateLimit-Used',
                'X-RateLimit-Remaining'):
        val = h.get(key)
        if val is not None:
            parts.append(f'{key.lower().removeprefix("x-ratelimit-")}={val}')
    retry = h.get('Retry-After')
    if retry:
        parts.append(f'retry-after={retry}')
    return ('  [' + '  '.join(parts) + reset_str + ']') if parts else ''


def gh_request(session: requests.Session, method: str, url: str,
               budget: ContentRateBudget | None = None, **kwargs) -> requests.Response:
    """Make a GitHub API request; retry automatically on rate-limit responses.

    budget: pass for content-creating requests only (REST POST/PATCH/PUT, or GraphQL
    mutations).  GraphQL read queries also use POST but are NOT content-creating —
    callers must omit budget for those, otherwise they burn content quota unnecessarily.
    """
    is_content = method.upper() in ('POST', 'PATCH', 'PUT')
    if is_content and budget is not None:
        budget.wait_if_needed()

    secondary_wait = 60  # grows exponentially on each secondary-rate-limit hit
    for attempt in range(8):
        resp = session.request(method, url, **kwargs)
        if resp.status_code == 429:
            # Honor Retry-After; GitHub says minimum 1 minute
            wait = max(int(resp.headers.get('Retry-After', 60)), 60)
            print(f'\n  Rate limited (429) -- sleeping {wait}s ...'
                  f'\n{_fmt_ratelimit_headers(resp)}')
            time.sleep(wait)
            continue
        if resp.status_code == 403:
            try:
                msg = resp.json().get('message', '')
            except Exception:
                msg = ''
            if 'secondary rate limit' in msg.lower():
                # Honor Retry-After when present; otherwise exponential backoff.
                # GitHub docs: honor Retry-After, else wait at least 1 minute,
                # and use exponential backoff for persistent failures.
                retry_after = resp.headers.get('Retry-After')
                wait = max(int(retry_after), 60) if retry_after else secondary_wait
                secondary_wait = min(secondary_wait * 2, 3600)
                print(f'\n  Secondary rate limit (403) -- sleeping {wait}s'
                      f' (attempt {attempt + 1}/8) ...'
                      f'\n{_fmt_ratelimit_headers(resp)}')
                time.sleep(wait)
                continue
            if resp.headers.get('X-RateLimit-Remaining') == '0':
                reset = int(resp.headers.get('X-RateLimit-Reset', time.time() + 60))
                wait = max(reset - int(time.time()), 1)
                print(f'\n  Primary rate limit (403) -- sleeping {wait}s ...'
                      f'\n{_fmt_ratelimit_headers(resp)}')
                time.sleep(wait)
                continue
        if is_content and budget is not None:
            budget.record()
        return resp
    if is_content and budget is not None:
        budget.record()
    return resp


def gh_ensure_label(
    session: requests.Session,
    gh_repo: str,
    name: str,
    color: str,
    description: str,
    budget: ContentRateBudget | None = None,
) -> str | None:
    """Ensure label exists; returns its node_id or None on failure."""
    url = f'{_GH_API}/repos/{gh_repo}/labels/{requests.utils.quote(name, safe="")}'
    resp = gh_request(session, 'GET', url)
    if resp.status_code == 200:
        return resp.json().get('node_id')
    if resp.status_code == 404:
        r = gh_request(session, 'POST', f'{_GH_API}/repos/{gh_repo}/labels',
                       budget=budget,
                       json={'name': name, 'color': color, 'description': description})
        if r.status_code == 422:
            errors = r.json().get('errors', [])
            if not any(e.get('code') == 'already_exists' for e in errors):
                print(f'    WARN: label {name!r} rejected by GitHub (422) -- will be skipped on issues')
                return None
            resp2 = gh_request(session, 'GET', url)
            return resp2.json().get('node_id') if resp2.ok else None
        else:
            r.raise_for_status()
            return r.json().get('node_id')
    return None


def gh_load_milestones(session: requests.Session, gh_repo: str) -> dict[str, str]:
    """Load all existing milestones (open and closed) into a title->node_id cache."""
    cache: dict[str, str] = {}
    for state in ('open', 'closed'):
        page = 1
        while True:
            resp = gh_request(session, 'GET', f'{_GH_API}/repos/{gh_repo}/milestones',
                              params={'state': state, 'per_page': 100, 'page': page})
            resp.raise_for_status()
            milestones = resp.json()
            if not milestones:
                break
            for ms in milestones:
                cache[ms['title']] = ms['node_id']
            if len(milestones) < 100:
                break
            page += 1
    return cache


def gh_ensure_milestone(
    session: requests.Session,
    gh_repo: str,
    title: str,
    cache: dict[str, str],
    budget: ContentRateBudget | None = None,
) -> str:
    """Return the GitHub milestone node_id for title, creating it if needed."""
    if title in cache:
        return cache[title]
    resp = gh_request(session, 'POST', f'{_GH_API}/repos/{gh_repo}/milestones',
                      budget=budget, json={'title': title})
    resp.raise_for_status()
    node_id = resp.json()['node_id']
    cache[title] = node_id
    return node_id


def gh_get_repo_node_id(session: requests.Session, gh_repo: str) -> str:
    resp = gh_request(session, 'GET', f'{_GH_API}/repos/{gh_repo}')
    resp.raise_for_status()
    return resp.json()['node_id']


def gh_resolve_user_node_ids(session: requests.Session, logins: list[str]) -> dict[str, str]:
    """Batch-resolve GitHub login names to GraphQL node IDs. Returns {login: node_id}."""
    result: dict[str, str] = {}
    for i in range(0, len(logins), 50):
        batch = logins[i:i + 50]
        params = ', '.join(f'$l{j}:String!' for j in range(len(batch)))
        bodies = ' '.join(f'u{j}:user(login:$l{j}){{id}}' for j in range(len(batch)))
        variables = {f'l{j}': login for j, login in enumerate(batch)}
        resp = gh_request(session, 'POST', f'{_GH_API}/graphql',
                          json={'query': f'query({params}){{{bodies}}}',
                                'variables': variables})
        resp.raise_for_status()
        data = resp.json().get('data') or {}
        for j, login in enumerate(batch):
            nid = (data.get(f'u{j}') or {}).get('id')
            if nid:
                result[login] = nid
            else:
                print(f'  WARN: GitHub user {login!r} not found — will be skipped as assignee')
    return result


def gh_load_issue_types(session: requests.Session, org: str) -> dict[str, int]:
    """Load org issue types; returns {name: id} for all types, empty if unavailable.

    Requires a token with read:org scope (classic) or
    "Organization issue types: Read" (fine-grained PAT).
    """
    resp = gh_request(session, 'GET', f'{_GH_API}/orgs/{org}/issue-types')
    if resp.status_code == 403:
        print(f'  WARN: issue types API returned 403 for org {org!r} — '
              f'token may need read:org scope (classic PAT) or '
              f'"Organization issue types: Read" (fine-grained PAT)')
        return {}
    if resp.status_code in (404, 422):
        # GitHub returns 404 (not 403) when the token lacks org read access.
        # Check token scopes via a cheap endpoint to give an actionable message.
        scopes_resp = session.get(f'{_GH_API}/rate_limit')
        scopes = scopes_resp.headers.get('X-OAuth-Scopes', '')
        if scopes:
            has_org = 'read:org' in scopes or 'admin:org' in scopes
            if not has_org:
                print(f'  WARN: issue types endpoint returned {resp.status_code} for org '
                      f'{org!r}; token scopes are [{scopes}] — '
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
    # Exclude only explicitly-disabled types; treat missing/null is_enabled as enabled.
    return {it['name']: it['node_id'] for it in items if it.get('is_enabled') is not False}


_GQL_CREATE_ISSUE = (
    'mutation($repoId:ID!,$title:String!,$body:String!,'
    '$typeId:ID,$labelIds:[ID!],$milestoneId:ID,$assigneeIds:[ID!]){'
    'createIssue(input:{repositoryId:$repoId,title:$title,body:$body,'
    'issueTypeId:$typeId,labelIds:$labelIds,milestoneId:$milestoneId,'
    'assigneeIds:$assigneeIds}){issue{id number url}}}'
)

_GQL_CLOSE_ISSUE = (
    'mutation($id:ID!,$reason:IssueClosedStateReason!){'
    'closeIssue(input:{issueId:$id,stateReason:$reason})'
    '{issue{id}}}'
)

_SF_REASON_TO_GQL: dict[str, str] = {
    'completed': 'COMPLETED',
    'not_planned': 'NOT_PLANNED',
    'duplicate': 'NOT_PLANNED',
}


def gh_create_issue(
    session: requests.Session,
    repo_node_id: str,
    title: str,
    body: str,
    label_node_ids: list[str],
    state: str,
    sf_status: str,
    milestone_node_id: str | None = None,
    assignee_node_ids: list[str] | None = None,
    issue_type_id: str | None = None,
    budget: ContentRateBudget | None = None,
) -> dict:
    variables: dict = {
        'repoId': repo_node_id,
        'title': title,
        'body': body,
        'labelIds': label_node_ids,
    }
    if milestone_node_id:
        variables['milestoneId'] = milestone_node_id
    if assignee_node_ids:
        variables['assigneeIds'] = assignee_node_ids
    if issue_type_id:
        variables['typeId'] = issue_type_id

    resp = gh_request(session, 'POST', f'{_GH_API}/graphql',
                      budget=budget,
                      json={'query': _GQL_CREATE_ISSUE, 'variables': variables})
    resp.raise_for_status()
    data = resp.json()
    if data.get('errors') and assignee_node_ids:
        print(f'    WARN: createIssue failed with assignees, retrying without')
        variables.pop('assigneeIds')
        resp = gh_request(session, 'POST', f'{_GH_API}/graphql',
                          budget=budget,
                          json={'query': _GQL_CREATE_ISSUE, 'variables': variables})
        resp.raise_for_status()
        data = resp.json()
    if data.get('errors'):
        msgs = '; '.join(e.get('message', str(e)) for e in data['errors'])
        raise requests.HTTPError(f'createIssue GraphQL: {msgs}', response=resp)

    issue = data['data']['createIssue']['issue']

    if state == 'closed':
        gql_reason = _SF_REASON_TO_GQL.get(sf_status_to_reason(sf_status), 'COMPLETED')
        close_resp = gh_request(session, 'POST', f'{_GH_API}/graphql',
                                budget=budget,
                                json={'query': _GQL_CLOSE_ISSUE,
                                      'variables': {'id': issue['id'], 'reason': gql_reason}})
        close_resp.raise_for_status()
        close_data = close_resp.json()
        if close_data.get('errors'):
            msgs = '; '.join(e.get('message', str(e)) for e in close_data['errors'])
            raise requests.HTTPError(f'closeIssue GraphQL: {msgs}', response=close_resp)

    return {'number': issue['number'], 'html_url': issue['url']}


def _prompt_continue(warning: str) -> bool:
    """Print a warning and ask the user whether to continue or abort. Returns True to continue."""
    print(f'\nWARNING: {warning}')
    while True:
        try:
            answer = input('Continue anyway? [y/N] ').strip().lower()
        except (EOFError, KeyboardInterrupt):
            print()
            return False
        if answer in ('y', 'yes'):
            return True
        if answer in ('', 'n', 'no'):
            return False
        print("  Enter 'y' to continue or press Enter / 'n' to stop.")


def main(argv=None) -> None:
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    args = parse_args(argv)

    GITHUB_TOKEN = os.environ.get('GITHUB_TOKEN')
    if not GITHUB_TOKEN:
        print('ERROR: Set GITHUB_TOKEN env var before running.')
        sys.exit(1)

    with open(args.sf_tickets_file, encoding='utf-8') as f:
        payload = json.load(f)

    sf_url = payload['sf_url']
    ticket_list_name = payload['ticket_list_name']
    tickets: list[dict] = payload['tickets']

    gh_repo = _normalize_repo(args.gh_repo)
    delay = args.delay
    dry_run = args.dry_run

    gh_label = args.label or _KNOWN_LABEL_DEFAULTS.get(ticket_list_name, 'bug')
    out_file = f'issue-map-{ticket_list_name}.csv'

    already_mapped: set[str] = set()
    map_path = pathlib.Path(out_file)
    if not dry_run and map_path.exists():
        with open(map_path, encoding='utf-8', newline='') as f:
            for row in csv.DictReader(f):
                sf_id = (row.get('sf_id') or '').strip()
                if sf_id:
                    already_mapped.add(sf_id)

    username_map = load_username_map(pathlib.Path(__file__).parent / 'sf-to-gh-usernames.csv')

    print(f'SF tickets    : {args.sf_tickets_file}  ({len(tickets)} tickets)')
    print(f'GH repo       : {gh_repo}')
    print(f'Label         : {gh_label}')
    print(f'Output file   : {out_file}')
    if already_mapped:
        print(f'Resuming      : {len(already_mapped)} tickets already mapped, skipping')
    print(f'Username map  : {len(username_map)} SF->GH mappings loaded')
    if dry_run:
        print('(DRY-RUN -- no GitHub writes)\n')

    session = gh_session(GITHUB_TOKEN)
    budget = ContentRateBudget()

    milestone_cache: dict[str, str] = {}
    issue_type_map: dict[str, str] = {}
    label_nid_map: dict[str, str] = {}
    repo_node_id: str = ''
    user_node_id_map: dict[str, str] = {}

    if not dry_run:
        print('Ensuring base GitHub labels exist ...')
        for _lbl_name, _lbl_color, _lbl_desc in [
            ('bug', 'd73a4a', "Something isn't working"),
            ('enhancement', 'a2eeef', 'New feature or request'),
            ('from-sourceforge', 'e4e669', 'Migrated from SourceForge'),
        ]:
            _nid = gh_ensure_label(session, gh_repo, _lbl_name, _lbl_color, _lbl_desc, budget)
            if _nid:
                label_nid_map[_lbl_name] = _nid

        all_sf_labels: set[str] = set()
        for t in tickets:
            all_sf_labels.update(t.get('labels') or [])
        if all_sf_labels:
            print(f'Ensuring {len(all_sf_labels)} SF label(s) exist as GH labels ...')
            for lbl in sorted(all_sf_labels):
                _nid = gh_ensure_label(session, gh_repo, lbl, 'c5def5', f'SourceForge label: {lbl}', budget)
                if _nid:
                    label_nid_map[lbl] = _nid

        repo_node_id = gh_get_repo_node_id(session, gh_repo)

        all_sf_milestones: set[str] = set()
        for t in tickets:
            ms = (t.get('custom_fields') or {}).get('_milestone') or ''
            if ms:
                all_sf_milestones.add(ms)
        if all_sf_milestones:
            print(f'Loading existing milestones ...')
            milestone_cache = gh_load_milestones(session, gh_repo)
            print(f'Ensuring {len(all_sf_milestones)} milestone(s) exist ...')
            for ms_title in sorted(all_sf_milestones):
                gh_ensure_milestone(session, gh_repo, ms_title, milestone_cache, budget)
                print(f'  milestone {ms_title!r} ensured')

        all_gh_users: set[str] = set()
        for t in tickets:
            _at = t.get('assigned_to') or ''
            if _at and not _at.startswith('*'):
                _gh_user = username_map.get(_at, _at)
                if _gh_user != 'NOTFOUND':
                    all_gh_users.add(_gh_user)
        if all_gh_users:
            print(f'Resolving {len(all_gh_users)} assignee login(s) to node IDs ...')
            user_node_id_map = gh_resolve_user_node_ids(session, sorted(all_gh_users))

        org = gh_repo.split('/')[0]
        issue_type_map = gh_load_issue_types(session, org)
        if issue_type_map:
            print(f'Issue types loaded: {sorted(issue_type_map)}')
            _scopes_resp = session.get(f'{_GH_API}/rate_limit')
            _scopes = _scopes_resp.headers.get('X-OAuth-Scopes', '')
            if _scopes:
                _has_write_org = 'write:org' in _scopes or 'admin:org' in _scopes
                if not _has_write_org:
                    if not _prompt_continue(
                        f'Token scopes [{_scopes}] are missing write:org — '
                        f'GitHub will silently ignore the issue_type field.\n'
                        f'  Add write:org to your classic PAT, or use a fine-grained PAT '
                        f'with "Organization issue types: Write".\n'
                        f'  Issues will be created without a type.'
                    ):
                        sys.exit(1)
        else:
            print(f'Issue types not available for org {org!r} — skipping')

        needed_type_names = {
            _KNOWN_ISSUE_TYPE_NAMES[t.get('ticket_list_name') or ticket_list_name]
            for t in tickets
            if (t.get('ticket_list_name') or ticket_list_name) in _KNOWN_ISSUE_TYPE_NAMES
        }
        if needed_type_names:
            if not issue_type_map:
                if not _prompt_continue(
                    f'Issue types could not be loaded for org {org!r}.\n'
                    f'  Needed: {sorted(needed_type_names)}\n'
                    f'  Issues will be created without a type.'
                ):
                    sys.exit(1)
            else:
                missing = needed_type_names - set(issue_type_map)
                if missing:
                    if not _prompt_continue(
                        f'Expected issue type(s) not found in org {org!r}: {sorted(missing)}\n'
                        f'  Available: {sorted(issue_type_map)}\n'
                        f'  Affected issues will be created without a type.'
                    ):
                        sys.exit(1)

    mapping_fh = None
    mapping_writer = None
    if not dry_run:
        mode = 'a' if already_mapped else 'w'
        mapping_fh = open(out_file, mode, encoding='utf-8', newline='')
        mapping_writer = csv.DictWriter(mapping_fh, fieldnames=MAPPING_HEADERS)
        if not already_mapped:
            mapping_writer.writeheader()

    total_created = 0
    try:
        for i, ticket in enumerate(tickets, 1):
            ticket_num = ticket.get('ticket_num') or ticket.get('id')
            # Per-ticket tracker/URL/label support combined JSON files with mixed trackers.
            ticket_tracker = ticket.get('ticket_list_name') or ticket_list_name
            ticket_sf_url = ticket.get('sf_url') or sf_url
            ticket_gh_label = args.label or _KNOWN_LABEL_DEFAULTS.get(ticket_tracker, 'bug')

            if str(ticket_num) in already_mapped:
                print(f'  [{i}/{len(tickets)}] {ticket_tracker}/{ticket_num} -- already mapped, skipping')
                continue

            sf_status = ticket.get('status') or 'open'
            title = ticket.get('summary') or ticket.get('title') or f'(no title) #{ticket_num}'
            gh_state = sf_status_to_gh_state(sf_status)
            created = ticket.get('created_date') or ticket.get('created') or ''
            sf_ticket_url = _sf_human_ticket_url(ticket_sf_url, ticket_num)
            sf_labels = ticket.get('labels') or []
            assigned_to = ticket.get('assigned_to') or ''
            sf_milestone = (ticket.get('custom_fields') or {}).get('_milestone') or ''

            status_display = f'[{sf_status}]' if sf_status else ''
            print(f'  [{i}/{len(tickets)}] {ticket_tracker}/{ticket_num} {status_display} {title}')

            body = build_gh_body(ticket, ticket_tracker, ticket_num, sf_ticket_url)
            labels = ['from-sourceforge', ticket_gh_label] + sf_labels
            if assigned_to and not assigned_to.startswith('*'):
                gh_user = username_map.get(assigned_to, assigned_to)
                assignees = [] if gh_user == 'NOTFOUND' else [gh_user]
            else:
                assignees = []
            milestone_nid = milestone_cache.get(sf_milestone) if sf_milestone else None
            issue_type_name = _KNOWN_ISSUE_TYPE_NAMES.get(ticket_tracker, '')
            issue_type_id = issue_type_map.get(issue_type_name) if issue_type_name else None

            if dry_run:
                extras = []
                if issue_type_name:
                    extras.append(f'issue_type={issue_type_name!r}')
                if sf_milestone:
                    extras.append(f'milestone={sf_milestone!r}')
                if assignees:
                    extras.append(f'assignees={assignees}')
                extras_display = ('  ' + '  '.join(extras)) if extras else ''
                print(f'    DRY-RUN: labels={labels}{extras_display}')
                total_created += 1
                continue

            try:
                time.sleep(delay)
                label_nids = [nid for lbl in labels if (nid := label_nid_map.get(lbl)) is not None]
                assignee_nids = [nid for u in assignees if (nid := user_node_id_map.get(u)) is not None]
                issue = gh_create_issue(session, repo_node_id, title, body, label_nids,
                                        gh_state, sf_status,
                                        milestone_node_id=milestone_nid,
                                        assignee_node_ids=assignee_nids or None,
                                        issue_type_id=issue_type_id, budget=budget)
                gh_num = issue['number']
                gh_url = issue['html_url']
                print(f'    OK GH#{gh_num}  ({gh_url})')
                total_created += 1
                mapping_writer.writerow({
                    'sf_tracker': ticket_tracker,
                    'sf_id': ticket_num,
                    'sf_created': created,
                    'sf_status': sf_status,
                    'gh_issue_number': gh_num,
                    'gh_url': gh_url,
                })
                mapping_fh.flush()
            except requests.HTTPError as exc:
                print(f'    FAILED GitHub {exc.response.status_code}: {exc.response.text}')
                time.sleep(delay)
    finally:
        if mapping_fh:
            mapping_fh.close()

    action = 'would be created' if dry_run else 'created'
    print(f'\nDone.  {total_created} issues {action}.')
    if not dry_run:
        print(f'Mapping written to: {out_file}')
        print('=' * 60)


if __name__ == '__main__':
    main()
