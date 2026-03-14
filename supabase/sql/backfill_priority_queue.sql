-- High-priority backfill queue candidates

-- 1) Recent records still missing core metadata
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

-- 2) Top categories with the largest pending/partial backlog
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

-- 3) Top tags with the largest pending/partial backlog
select
  tag,
  count(*)::int as total
from public.videos,
     unnest(tags) as tag
where is_active = true
  and detail_status in ('pending', 'partial')
group by 1
order by total desc
limit 50;

-- 4) Recent backlog samples for manual verification
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
