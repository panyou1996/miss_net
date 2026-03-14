create extension if not exists pg_trgm;

create or replace function public.normalize_known_taxonomy_aliases(input_values text[])
returns text[]
language sql
immutable
as $$
  select coalesce(array_agg(distinct normalized order by normalized), '{}'::text[])
  from (
    select case
      when value is null or btrim(value) = '' then null
      when lower(btrim(value)) in ('new', 'new_release', 'new_releases') then 'new'
      when lower(btrim(value)) in ('monthly_hot', 'monthly') then 'monthly_hot'
      when lower(btrim(value)) in ('weekly_hot', 'weekly') then 'weekly_hot'
      when lower(btrim(value)) = 'uncensored' then 'uncensored'
      when btrim(value) = '中文字幕' or lower(btrim(value)) in ('subtitled', 'subtitles', 'chinese_subtitle') then 'subtitled'
      when lower(btrim(value)) = 'exclusive' then 'exclusive'
      when lower(btrim(value)) = 'creampie' then 'creampie'
      when lower(btrim(value)) = 'single' then 'single'
      when lower(btrim(value)) in ('bigtits', 'big_tits') then 'big_tits'
      when lower(btrim(value)) = 'mature' then 'mature'
      when lower(btrim(value)) = 'amateur' then 'amateur'
      when lower(btrim(value)) = 'beautiful' then 'beautiful'
      when lower(btrim(value)) = 'oral' then 'oral'
      when lower(btrim(value)) = 'group' then 'group'
      when lower(btrim(value)) = 'nympho' then 'nympho'
      when lower(btrim(value)) = 'school' then 'school'
      when lower(btrim(value)) = 'voyeur' then 'voyeur'
      when lower(btrim(value)) = 'story' then 'story'
      when lower(btrim(value)) = 'sister' then 'sister'
      when lower(btrim(value)) = 'office' then 'office'
      when lower(btrim(value)) = 'pov' then 'pov'
      when lower(btrim(value)) = 'vr' then 'vr'
      when lower(btrim(value)) in ('51cg', '51mrds') then lower(btrim(value))
      else btrim(value)
    end as normalized
    from unnest(coalesce(input_values, '{}'::text[])) as value
  ) mapped
  where normalized is not null;
$$;

alter table public.videos
  add column if not exists source_site text,
  add column if not exists source_release_date date,
  add column if not exists first_seen_at timestamptz,
  add column if not exists last_seen_at timestamptz,
  add column if not exists cover_status text,
  add column if not exists detail_status text,
  add column if not exists detail_fetched_at timestamptz,
  add column if not exists detail_fail_count integer,
  add column if not exists last_detail_error text,
  add column if not exists last_verified_at timestamptz,
  add column if not exists verify_status text,
  add column if not exists verify_fail_count integer;

alter table public.videos
  alter column detail_fail_count set default 0,
  alter column verify_fail_count set default 0,
  alter column cover_status set default 'missing',
  alter column detail_status set default 'pending',
  alter column verify_status set default 'pending';

update public.videos
set detail_fail_count = coalesce(detail_fail_count, 0),
    verify_fail_count = coalesce(verify_fail_count, 0);

update public.videos
set cover_url = null
where cover_url like 'data:image%';

update public.videos
set tags = public.normalize_known_taxonomy_aliases(tags),
    categories = public.normalize_known_taxonomy_aliases(categories);

update public.videos
set source_site = case
    when source_url ilike '%51cg1.com%' or external_id like '51cg_%' then '51cg'
    when source_url ilike '%missav%' or source_url ilike '%fourhoi.com%' then 'missav'
    else coalesce(nullif(source_site, ''), 'unknown')
  end,
  source_release_date = case
    when btrim(coalesce(release_date, '')) ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' then btrim(release_date)::date
    else source_release_date
  end,
  first_seen_at = coalesce(first_seen_at, created_at),
  last_seen_at = coalesce(last_seen_at, created_at),
  cover_status = case
    when cover_url is null or btrim(cover_url) = '' then 'missing'
    else 'valid'
  end,
  detail_status = case
    when (
      case
        when duration is null or btrim(duration) in ('', '0', 'Unknown') then null
        else btrim(duration)
      end
    ) is null
      and (
        case
          when release_date is null or btrim(release_date) in ('', 'Unknown') then null
          else btrim(release_date)
        end
      ) is null
      and coalesce(array_length(actors, 1), 0) = 0 then 'pending'
    when (
      case
        when duration is null or btrim(duration) in ('', '0', 'Unknown') then null
        else btrim(duration)
      end
    ) is not null
      and (
        (
          case
            when release_date is null or btrim(release_date) in ('', 'Unknown') then null
            else btrim(release_date)
          end
        ) is not null
        or coalesce(array_length(actors, 1), 0) > 0
      ) then 'success'
    else 'partial'
  end
where true;

create unique index if not exists videos_external_id_key
  on public.videos (external_id);

create index if not exists idx_videos_active_created_at
  on public.videos (is_active, created_at desc);

create index if not exists idx_videos_active_release_date
  on public.videos (is_active, source_release_date desc);

create index if not exists idx_videos_first_seen_at
  on public.videos (first_seen_at desc);

create index if not exists idx_videos_last_verified_at
  on public.videos (last_verified_at asc);

create index if not exists idx_videos_actors_gin
  on public.videos using gin (actors);

create index if not exists idx_videos_tags_gin
  on public.videos using gin (tags);

create index if not exists idx_videos_categories_gin
  on public.videos using gin (categories);

create index if not exists idx_videos_title_trgm
  on public.videos using gin (title gin_trgm_ops);
