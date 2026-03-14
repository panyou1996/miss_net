#!/usr/bin/env python3
import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description='Run SQL against Supabase Management API database/query endpoint.')
    parser.add_argument('--project-ref', default=os.environ.get('SUPABASE_PROJECT_REF', 'gapmmwdbxzcglvvdhhiu'))
    parser.add_argument('--file', help='Path to SQL file')
    parser.add_argument('--query', help='Inline SQL query')
    parser.add_argument('--read-only', action='store_true')
    args = parser.parse_args()

    token = os.environ.get('SUPABASE_ACCESS_TOKEN')
    if not token:
        print('SUPABASE_ACCESS_TOKEN is required', file=sys.stderr)
        sys.exit(1)

    if bool(args.file) == bool(args.query):
        print('Specify exactly one of --file or --query', file=sys.stderr)
        sys.exit(1)

    query = Path(args.file).read_text(encoding='utf-8') if args.file else args.query
    url = f'https://api.supabase.com/v1/projects/{args.project_ref}/database/query'
    payload = {'query': query}
    if args.read_only:
        payload['read_only'] = True

    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode(),
        method='POST',
        headers={
            'Authorization': f'Bearer {token}',
            'User-Agent': 'SupabaseCLI/2.78.1',
            'Content-Type': 'application/json',
        },
    )

    try:
        with urllib.request.urlopen(request, timeout=300) as response:
            print(response.read().decode())
    except urllib.error.HTTPError as error:
        print(error.read().decode(), file=sys.stderr)
        sys.exit(error.code or 1)


if __name__ == '__main__':
    main()
