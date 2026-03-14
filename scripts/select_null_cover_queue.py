#!/usr/bin/env python3
import argparse
import json
import os
import shlex
import sys
import urllib.error
import urllib.request
from datetime import datetime


def run_sql(project_ref: str, token: str, query: str):
    url = f'https://api.supabase.com/v1/projects/{project_ref}/database/query'
    payload = {'query': query, 'read_only': True}
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
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.loads(response.read().decode())


def build_query(limit: int, source_site: str = 'missav') -> str:
    source_filter = ''
    if source_site in {'missav', '51cg'}:
        source_filter = f" and source_site = '{source_site}'"
    return f"""
select
  external_id,
  source_url,
  source_site,
  source_release_date,
  created_at
from public.videos
where coalesce(is_active, true) = true
  and cover_url is null
  and coalesce(btrim(source_url), '') <> ''
  {source_filter}
order by source_release_date desc nulls last, created_at desc
limit {int(limit)};
"""


def _sort_key(row: dict):
    release = row.get('source_release_date') or ''
    created = row.get('created_at') or ''
    primary = release or created
    return (primary, created)


def select_queue_rows(rows, limit: int):
    filtered = []
    for row in rows:
        if str(row.get('source_url') or '').strip():
            filtered.append(dict(row))
    filtered.sort(key=_sort_key, reverse=True)
    return filtered[: max(int(limit), 1)]


def render_env_output(rows, source_site: str) -> str:
    payload = json.dumps(rows, ensure_ascii=False)
    return "\n".join([
        f"NULL_COVER_QUEUE_JSON={shlex.quote(payload)}",
        f"NULL_COVER_QUEUE_COUNT={len(rows)}",
        f"NULL_COVER_SOURCE_SITE={shlex.quote(source_site)}",
    ])


def main():
    parser = argparse.ArgumentParser(description='Pick concrete null-cover rows for detail-only cover patching.')
    parser.add_argument('--project-ref', default=os.environ.get('SUPABASE_PROJECT_REF', 'gapmmwdbxzcglvvdhhiu'))
    parser.add_argument('--source-site', choices=['all', 'missav', '51cg'], default='missav')
    parser.add_argument('--limit', type=int, default=50)
    parser.add_argument('--output', choices=['json', 'env'], default='json')
    args = parser.parse_args()

    token = os.environ.get('SUPABASE_ACCESS_TOKEN')
    if not token:
        print('SUPABASE_ACCESS_TOKEN is required', file=sys.stderr)
        sys.exit(1)

    rows = run_sql(args.project_ref, token, build_query(args.limit, args.source_site))
    selected = select_queue_rows(rows, args.limit)

    if args.output == 'env':
        print(render_env_output(selected, args.source_site))
    else:
        print(json.dumps({'source_site': args.source_site, 'count': len(selected), 'rows': selected}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    try:
        main()
    except urllib.error.HTTPError as error:
        print(error.read().decode(), file=sys.stderr)
        raise
