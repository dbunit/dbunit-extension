"""
Merge sf-tickets-bugs.json and sf-tickets-feature-requests.json into a single
sf-tickets-dbunittickets.json sorted by created_date ascending.

Each ticket gets ticket_list_name and sf_url embedded at the ticket level so
create-gh-tickets.py can assign the correct label and SF URL per ticket.

Usage:
    python combine-sf-tickets.py
"""

import json
import pathlib

INPUT_FILES = [
    'sf-tickets-bugs.json',
    'sf-tickets-feature-requests.json',
]
OUTPUT_FILE = 'sf-tickets-dbunittickets.json'
OUTPUT_NAME = 'dbunittickets'


def main() -> None:
    all_tickets: list[dict] = []

    for fname in INPUT_FILES:
        path = pathlib.Path(fname)
        with open(path, encoding='utf-8') as f:
            data = json.load(f)

        sf_url: str = data['sf_url']
        ticket_list_name: str = data['ticket_list_name']
        tickets: list[dict] = data['tickets']

        print(f'{fname}: {len(tickets)} tickets  (tracker={ticket_list_name})')

        for ticket in tickets:
            t = dict(ticket)
            t['ticket_list_name'] = ticket_list_name
            t['sf_url'] = sf_url
            all_tickets.append(t)

    all_tickets.sort(key=lambda t: t.get('created_date') or t.get('created') or '')

    payload = {
        'sf_url': '',
        'ticket_list_name': OUTPUT_NAME,
        'tickets': all_tickets,
    }

    with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
        json.dump(payload, f, indent=2, ensure_ascii=False)

    print(f'\nWritten: {OUTPUT_FILE}  ({len(all_tickets)} tickets total, sorted by created_date)')


if __name__ == '__main__':
    main()
