-- High-priority backfill queue candidates

-- 1) Recent records still missing core metadata, grouped by source + state
select
  source_site,
  detail_status,
  count(*)::int as total
from public.videos
where is_active = true
  and (
    cover_url is null
    or detail_status in ('pending', 'partial')
  )
group by 1, 2
order by total desc;

-- 2) Weighted tag backlog for metadata-focused backfill (MissAV-first)
select
  tag,
  count(*)::int as backlog_count,
  count(*) filter (where detail_status = 'pending')::int as pending_count,
  count(*) filter (where detail_status = 'partial')::int as partial_count,
  max(source_release_date) as latest_release_date
from public.videos,
     unnest(tags) as tag
where is_active = true
  and source_site = 'missav'
  and detail_status in ('pending', 'partial')
group by 1
order by partial_count desc, pending_count desc, latest_release_date desc nulls last, tag asc
limit 50;

-- 3) Weighted tag backlog for cover-focused backfill (MissAV-first)
select
  tag,
  count(*)::int as backlog_count,
  count(*) filter (where cover_url is null or cover_status = 'missing')::int as missing_cover_count,
  max(source_release_date) as latest_release_date
from public.videos,
     unnest(tags) as tag
where is_active = true
  and source_site = 'missav'
  and (cover_url is null or cover_status = 'missing')
group by 1
order by missing_cover_count desc, latest_release_date desc nulls last, tag asc
limit 50;

-- 4) Top categories with the largest pending/partial backlog
select
  category,
  count(*)::int as total
from public.videos,
     unnest(categories) as category
where is_active = true
  and detail_status in ('pending', 'partial')
group by 1
order by total desc
limit 50;

-- 5) Recent backlog samples for manual verification
select
  external_id,
  title,
  source_site,
  cover_status,
  detail_status,
  source_release_date,
  created_at,
  source_url
from public.videos
where is_active = true
  and (
    cover_url is null
    or detail_status in ('pending', 'partial')
  )
order by coalesce(source_release_date::text, created_at::text) desc, created_at desc
limit 200;
