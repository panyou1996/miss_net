-- Placeholder covers
select count(*) as placeholder_cover_count
from public.videos
where cover_url like 'data:image%';

-- Cover status distribution
select cover_status, count(*)
from public.videos
group by cover_status
order by count(*) desc;

-- Detail status distribution
select detail_status, count(*)
from public.videos
group by detail_status
order by count(*) desc;

-- Rows whose persisted detail_status disagrees with computed status
select count(*) as mismatched_detail_status
from public.videos
where detail_status is distinct from public.video_detail_status(cover_url, duration, release_date, actors, tags);

-- Release year distribution
select coalesce(left(source_release_date::text, 4), 'unknown') as release_year, count(*)
from public.videos
group by 1
order by 1;

-- New rows with old release dates
select external_id, title, first_seen_at, source_release_date
from public.videos
where first_seen_at >= now() - interval '30 days'
  and source_release_date < current_date - interval '365 days'
order by first_seen_at desc
limit 100;

-- Taxonomy distribution in tags
select tag, count(*)
from public.videos, unnest(tags) as tag
group by tag
order by count(*) desc
limit 100;

-- Taxonomy distribution in categories
select category, count(*)
from public.videos, unnest(categories) as category
group by category
order by count(*) desc
limit 100;
