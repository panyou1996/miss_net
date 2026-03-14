#!/usr/bin/env python3
import argparse
import json
import os
import sys
import urllib.error
import urllib.request

AVAILABLE_TAGS = [
    'new', 'monthly_hot', 'weekly_hot', 'uncensored', 'subtitled', 'exclusive',
    'creampie', 'single', 'big_tits', 'mature', 'amateur', 'beautiful', 'oral',
    'group', 'nympho', 'school', 'voyeur', 'story', 'sister', 'pov', '51cg', '51mrds'
]

BACKFILL_FALLBACKS = {
    'missav': {
        'cover': ['exclusive', 'creampie', 'single', 'big_tits', 'mature', 'amateur'],
        'metadata': ['new', 'weekly_hot', 'monthly_hot', 'subtitled', 'exclusive', 'uncensored'],
        'mixed': ['exclusive', 'creampie', 'new', 'weekly_hot', 'single', 'monthly_hot'],
    },
    '51cg': {
        'cover': ['51cg', '51mrds'],
        'metadata': ['51cg', '51mrds'],
        'mixed': ['51cg', '51mrds'],
    },
    'all': {
        'cover': ['exclusive', 'creampie', 'single', 'big_tits', '51cg', '51mrds'],
        'metadata': ['new', 'weekly_hot', 'monthly_hot', 'subtitled', '51cg', '51mrds'],
        'mixed': ['exclusive', 'creampie', 'new', 'weekly_hot', '51cg', '51mrds'],
    },
}


def ordered_unique(items):
    output = []
    seen = set()
    for item in items:
        if item and item not in seen:
            seen.add(item)
            output.append(item)
    return output


def resolve_fallback_tags(focus: str, source_site: str, include_51cg: bool) -> list[str]:
    source_key = source_site if source_site in BACKFILL_FALLBACKS else 'all'
    focus_key = focus if focus in {'cover', 'metadata', 'mixed'} else 'mixed'
    tags = list(BACKFILL_FALLBACKS[source_key][focus_key])
    if include_51cg and source_key != '51cg':
        tags.extend(['51cg', '51mrds'])
    elif not include_51cg:
        tags = [tag for tag in tags if tag not in {'51cg', '51mrds'}]
    return [tag for tag in ordered_unique(tags) if tag in AVAILABLE_TAGS]


def select_tags_from_rows(rows, limit: int, focus: str, source_site: str, include_51cg: bool) -> list[str]:
    selected = [str(row.get('tag') or '').strip() for row in rows]
    selected = [tag for tag in selected if tag in AVAILABLE_TAGS and (include_51cg or tag not in {'51cg', '51mrds'})]
    fallback = resolve_fallback_tags(focus=focus, source_site=source_site, include_51cg=include_51cg)
    merged = ordered_unique(selected + fallback)
    return merged[: max(int(limit), 1)]


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


def build_query(limit: int, source_site: str, include_51cg: bool, focus: str) -> str:
    tags = AVAILABLE_TAGS if include_51cg else [tag for tag in AVAILABLE_TAGS if tag not in {'51cg', '51mrds'}]
    values = ', '.join(f"('{tag}')" for tag in tags)
    source_filter = ''
    if source_site in {'missav', '51cg'}:
        source_filter = f" and v.source_site = '{source_site}'"

    if focus == 'cover':
        order_clause = "missing_cover_count desc, backlog_count desc, latest_release_date desc nulls last, tag asc"
    elif focus == 'metadata':
        order_clause = "partial_count desc, pending_count desc, backlog_count desc, latest_release_date desc nulls last, tag asc"
    else:
        order_clause = "(missing_cover_count * 2 + partial_count + pending_count) desc, latest_release_date desc nulls last, tag asc"

    return f"""
with source_tags(tag) as (
  values {values}
), backlog as (
  select
    st.tag,
    count(v.id)::int as backlog_count,
    count(v.id) filter (where v.cover_url is null)::int as missing_cover_count,
    count(v.id) filter (where v.detail_status = 'pending')::int as pending_count,
    count(v.id) filter (where v.detail_status = 'partial')::int as partial_count,
    count(v.id) filter (where v.source_site = 'missav')::int as missav_count,
    count(v.id) filter (where v.source_site = '51cg')::int as cg_count,
    max(v.source_release_date) as latest_release_date
  from source_tags st
  left join public.videos v
    on v.is_active = true
   and (st.tag = any(v.tags) or st.tag = any(v.categories))
   and (v.cover_url is null or v.detail_status in ('pending', 'partial'))
   {source_filter}
  group by st.tag
)
select *
from backlog
where backlog_count > 0
order by {order_clause}
limit {int(limit)};
"""


def main():
    parser = argparse.ArgumentParser(description='Pick recommended source tags for targeted backfill.')
    parser.add_argument('--project-ref', default=os.environ.get('SUPABASE_PROJECT_REF', 'gapmmwdbxzcglvvdhhiu'))
    parser.add_argument('--limit', type=int, default=4)
    parser.add_argument('--source-site', choices=['all', 'missav', '51cg'], default='missav')
    parser.add_argument('--include-51cg', action='store_true')
    parser.add_argument('--focus', choices=['metadata', 'cover', 'mixed'], default='mixed')
    parser.add_argument('--output', choices=['json', 'env'], default='json')
    args = parser.parse_args()

    token = os.environ.get('SUPABASE_ACCESS_TOKEN')
    if not token:
        print('SUPABASE_ACCESS_TOKEN is required', file=sys.stderr)
        sys.exit(1)

    rows = run_sql(args.project_ref, token, build_query(args.limit, args.source_site, args.include_51cg, args.focus))
    selected_tags = select_tags_from_rows(
        rows,
        limit=args.limit,
        focus=args.focus,
        source_site=args.source_site,
        include_51cg=args.include_51cg,
    )
    skip_51cg = not any(tag in {'51cg', '51mrds'} for tag in selected_tags)
    result = {
        'selected_tags': selected_tags,
        'skip_51cg': skip_51cg,
        'rows': rows,
        'source_site': args.source_site,
        'focus': args.focus,
    }

    if args.output == 'env':
        print(f"SCRAPER_SOURCE_TAGS={','.join(selected_tags)}")
        print(f"SKIP_51CG={'true' if skip_51cg else 'false'}")
        print(f"BACKFILL_SOURCE_SITE={args.source_site}")
        print(f"BACKFILL_FOCUS={args.focus}")
    else:
        print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    try:
        main()
    except urllib.error.HTTPError as error:
        print(error.read().decode(), file=sys.stderr)
        raise
