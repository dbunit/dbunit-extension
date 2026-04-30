"""
Download SourceForge tickets from a tracker and save to a JSON file.

Usage:
    pip install requests
    python download-sf-tickets.py <sf-url> <ticket-list-name>

Arguments:
    sf-url            SourceForge REST API base URL for the tracker
                      e.g. https://sourceforge.net/rest/p/dbunit/bugs
    ticket-list-name  Name for the output file (e.g. bugs, feature-requests)

Options:
    --delay SECONDS   Seconds between detail requests (default: 0.5)

Output:
    sf-tickets-<ticket-list-name>.json  JSON file with all ticket data
"""

import argparse
import json
import sys
import time

import requests


def parse_args(argv=None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description='Download SourceForge tickets to a local JSON file',
        add_help=False,
    )
    p.add_argument('sf_url', metavar='SF-URL',
                   help='SourceForge REST API base URL for the tracker '
                        '(e.g. https://sourceforge.net/rest/p/dbunit/bugs)')
    p.add_argument('ticket_list_name', metavar='TICKET-LIST-NAME',
                   help='Name for the output file (e.g. bugs, feature-requests)')
    p.add_argument('--delay', metavar='SECONDS', type=float, default=0.5,
                   help='Seconds between detail requests (default: 0.5)')
    p.add_argument('--help', '-h', action='help')
    return p.parse_args(argv)


def fetch_all_stubs(sf_url: str) -> list[dict]:
    """Return every ticket stub from a SourceForge tracker (paginated)."""
    base = sf_url.rstrip('/')
    tickets: list[dict] = []
    limit = 100
    page = 0

    print(f"  Fetching SF tracker '{sf_url}' ...")
    while True:
        resp = requests.get(base, params={'limit': limit, 'page': page})
        resp.raise_for_status()
        batch = resp.json().get('tickets', [])
        tickets.extend(batch)
        if len(batch) < limit:
            break
        page += 1
    print(f'  -> {len(tickets)} tickets total')
    return tickets


def fetch_ticket_detail(sf_url: str, ticket_num: int | str) -> dict:
    """Fetch full detail for one ticket (includes description + comments)."""
    url = f'{sf_url.rstrip("/")}/{ticket_num}'
    resp = requests.get(url)
    resp.raise_for_status()
    return resp.json().get('ticket', {})


def main(argv=None) -> None:
    args = parse_args(argv)
    sf_url = args.sf_url.rstrip('/')
    name = args.ticket_list_name
    out_file = f'sf-tickets-{name}.json'

    print(f'SF URL        : {sf_url}')
    print(f'Ticket list   : {name}')
    print(f'Output file   : {out_file}')
    print()

    stubs = fetch_all_stubs(sf_url)

    print(f'\nFetching full details for {len(stubs)} tickets ...')
    tickets: list[dict] = []
    for i, stub in enumerate(stubs, 1):
        ticket_num = stub.get('ticket_num') or stub.get('id')
        print(f'  [{i}/{len(stubs)}] SF#{ticket_num} ...', end='\r')
        try:
            detail = fetch_ticket_detail(sf_url, ticket_num)
            if not detail:
                detail = stub
        except Exception as exc:
            print(f'  [{i}/{len(stubs)}] SF#{ticket_num} (!) Detail fetch failed: {exc} -- using stub')
            detail = stub
        tickets.append(detail)
        if args.delay and i < len(stubs):
            time.sleep(args.delay)

    print(f'\n  -> {len(tickets)} tickets fetched')

    tickets.sort(key=lambda t: t.get('ticket_num') or t.get('id') or 0)

    payload = {
        'sf_url': sf_url,
        'ticket_list_name': name,
        'tickets': tickets,
    }
    with open(out_file, 'w', encoding='utf-8') as f:
        json.dump(payload, f, indent=2, ensure_ascii=False)

    print(f'Written: {out_file}')


if __name__ == '__main__':
    main()
