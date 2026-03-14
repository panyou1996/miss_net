create or replace function public.video_cover_status(input_cover_url text)
returns text
language sql
immutable
as $$
  select case
    when input_cover_url is null or btrim(input_cover_url) = '' then 'missing'
    when lower(btrim(input_cover_url)) like 'data:image%' then 'missing'
    when lower(btrim(input_cover_url)) like 'blob:%' then 'missing'
    when lower(btrim(input_cover_url)) like 'about:blank%' then 'missing'
    else 'valid'
  end;
$$;

alter table public.videos
  add column if not exists inventory_status text;

create or replace function public.video_has_meaningful_duration(input_duration text)
returns boolean
language sql
immutable
as $$
  select case
    when input_duration is null then false
    when btrim(input_duration) in ('', '0', '00:00', '00:00:00', 'Unknown') then false
    else true
  end;
$$;

create or replace function public.video_inventory_status(
  input_external_id text,
  input_title text,
  input_source_url text,
  input_cover_url text,
  input_release_date text,
  input_actors text[],
  input_tags text[]
)
returns text
language sql
immutable
as $$
  select case
    when coalesce(btrim(input_external_id), '') = '' or coalesce(btrim(input_title), '') = '' or coalesce(btrim(input_source_url), '') = '' then 'pending'
    when public.video_cover_status(input_cover_url) = 'valid'
      and (
        public.video_has_release_date(input_release_date)
        or coalesce(array_length(input_actors, 1), 0) > 0
        or coalesce(array_length(input_tags, 1), 0) > 0
      ) then 'detail_ready'
    when public.video_cover_status(input_cover_url) = 'valid' then 'cover_ready'
    else 'indexed'
  end;
$$;

create or replace function public.video_has_release_date(input_release_date text)
returns boolean
language sql
immutable
as $$
  select case
    when input_release_date is null then false
    when btrim(input_release_date) in ('', 'Unknown') then false
    else true
  end;
$$;

create or replace function public.video_detail_status(
  input_cover_url text,
  input_duration text,
  input_release_date text,
  input_actors text[],
  input_tags text[]
)
returns text
language sql
immutable
as $$
  select case
    when public.video_cover_status(input_cover_url) = 'valid'
      and (
        public.video_has_release_date(input_release_date)
        or coalesce(array_length(input_actors, 1), 0) > 0
        or coalesce(array_length(input_tags, 1), 0) > 0
      ) then 'success'
    when public.video_cover_status(input_cover_url) = 'valid'
      or public.video_has_release_date(input_release_date)
      or coalesce(array_length(input_actors, 1), 0) > 0
      or coalesce(array_length(input_tags, 1), 0) > 0 then 'partial'
    else 'pending'
  end;
$$;

update public.videos
set cover_status = public.video_cover_status(cover_url),
    inventory_status = public.video_inventory_status(external_id, title, source_url, cover_url, release_date, actors, tags),
    detail_status = public.video_detail_status(cover_url, duration, release_date, actors, tags),
    detail_fetched_at = case
      when public.video_detail_status(cover_url, duration, release_date, actors, tags) in ('success', 'partial')
        then coalesce(detail_fetched_at, created_at, now())
      else detail_fetched_at
    end;

update public.videos
set duration = null
where duration is not null
  and btrim(duration) in ('', '0', '00:00', '00:00:00', 'Unknown');

update public.videos
set release_date = null
where release_date is not null
  and btrim(release_date) in ('', 'Unknown');

update public.videos
set cover_status = public.video_cover_status(cover_url),
    inventory_status = public.video_inventory_status(external_id, title, source_url, cover_url, release_date, actors, tags),
    detail_status = public.video_detail_status(cover_url, duration, release_date, actors, tags),
    detail_fetched_at = case
      when public.video_detail_status(cover_url, duration, release_date, actors, tags) in ('success', 'partial')
        then coalesce(detail_fetched_at, created_at, now())
      else detail_fetched_at
    end;

create or replace function public.get_actor_aggregates(limit_count integer default 20)
returns table (
  actor text,
  cover_url text,
  video_count integer,
  latest_release_date text
)
language sql
stable
as $$
  with exploded as (
    select
      unnest(v.actors) as actor,
      v.cover_url,
      v.source_release_date,
      v.created_at
    from public.videos v
    where v.is_active = true
      and coalesce(array_length(v.actors, 1), 0) > 0
  ),
  ranked as (
    select
      actor,
      cover_url,
      count(*) over (partition by actor)::int as video_count,
      source_release_date,
      row_number() over (
        partition by actor
        order by (cover_url is not null) desc, source_release_date desc nulls last, created_at desc
      ) as row_num
    from exploded
  )
  select
    actor,
    cover_url,
    video_count,
    source_release_date::text as latest_release_date
  from ranked
  where row_num = 1
  order by video_count desc, latest_release_date desc nulls last, actor asc
  limit greatest(limit_count, 1);
$$;

drop function if exists public.get_popular_actors(integer);
create or replace function public.get_popular_actors(limit_count integer default 20)
returns table (actor text)
language sql
stable
as $$
  select actor
  from public.get_actor_aggregates(limit_count);
$$;

create or replace function public.get_tag_aggregates(limit_count integer default 30)
returns table (
  tag text,
  video_count integer,
  latest_release_date text
)
language sql
stable
as $$
  with exploded as (
    select
      unnest(public.normalize_known_taxonomy_aliases(coalesce(v.tags, '{}'::text[]) || coalesce(v.categories, '{}'::text[]))) as tag,
      v.source_release_date,
      v.created_at
    from public.videos v
    where v.is_active = true
  )
  select
    tag,
    count(*)::int as video_count,
    max(source_release_date)::text as latest_release_date
  from exploded
  where tag not in ('new', 'monthly_hot', 'weekly_hot', '51cg', '51mrds')
  group by tag
  order by video_count desc, latest_release_date desc nulls last, tag asc
  limit greatest(limit_count, 1);
$$;

drop function if exists public.get_popular_tags(integer);
create or replace function public.get_popular_tags(limit_count integer default 30)
returns table (tag text)
language sql
stable
as $$
  select tag
  from public.get_tag_aggregates(limit_count);
$$;

create or replace function public.search_videos_title(query_text text, limit_count integer default 20)
returns setof public.videos
language sql
stable
as $$
  select *
  from public.videos v
  where v.is_active = true
    and coalesce(query_text, '') <> ''
    and v.title ilike ('%' || query_text || '%')
  order by v.source_release_date desc nulls last, v.created_at desc
  limit greatest(limit_count, 1);
$$;

create or replace function public.get_home_payload(section_limit integer default 10, weekly_limit integer default 15)
returns table (
  section text,
  id text,
  external_id text,
  title text,
  cover_url text,
  source_url text,
  duration text,
  source_release_date text,
  created_at text,
  actors text[],
  tags text[]
)
language sql
stable
as $$
  (
    select
      'new'::text as section,
      v.id::text,
      v.external_id,
      v.title,
      v.cover_url,
      v.source_url,
      v.duration,
      v.source_release_date::text,
      v.created_at::text,
      v.actors,
      v.tags
    from public.videos v
    where v.is_active = true
    order by v.source_release_date desc nulls last, v.created_at desc
    limit greatest(section_limit, 1)
  )
  union all
  (
    select
      'monthly_hot'::text,
      v.id::text,
      v.external_id,
      v.title,
      v.cover_url,
      v.source_url,
      v.duration,
      v.source_release_date::text,
      v.created_at::text,
      v.actors,
      v.tags
    from public.videos v
    where v.is_active = true
      and ('monthly_hot' = any(v.tags) or 'monthly_hot' = any(v.categories))
    order by v.source_release_date desc nulls last, v.created_at desc
    limit greatest(section_limit, 1)
  )
  union all
  (
    select
      'weekly_hot'::text,
      v.id::text,
      v.external_id,
      v.title,
      v.cover_url,
      v.source_url,
      v.duration,
      v.source_release_date::text,
      v.created_at::text,
      v.actors,
      v.tags
    from public.videos v
    where v.is_active = true
      and ('weekly_hot' = any(v.tags) or 'weekly_hot' = any(v.categories))
    order by v.source_release_date desc nulls last, v.created_at desc
    limit greatest(weekly_limit, 1)
  )
  union all
  (
    select
      'uncensored'::text,
      v.id::text,
      v.external_id,
      v.title,
      v.cover_url,
      v.source_url,
      v.duration,
      v.source_release_date::text,
      v.created_at::text,
      v.actors,
      v.tags
    from public.videos v
    where v.is_active = true
      and ('uncensored' = any(v.tags) or 'uncensored' = any(v.categories))
    order by v.source_release_date desc nulls last, v.created_at desc
    limit greatest(section_limit, 1)
  )
  union all
  (
    select
      'subtitled'::text,
      v.id::text,
      v.external_id,
      v.title,
      v.cover_url,
      v.source_url,
      v.duration,
      v.source_release_date::text,
      v.created_at::text,
      v.actors,
      v.tags
    from public.videos v
    where v.is_active = true
      and ('subtitled' = any(v.tags) or 'subtitled' = any(v.categories))
    order by v.source_release_date desc nulls last, v.created_at desc
    limit greatest(section_limit, 1)
  )
  union all
  (
    select
      'vr'::text,
      v.id::text,
      v.external_id,
      v.title,
      v.cover_url,
      v.source_url,
      v.duration,
      v.source_release_date::text,
      v.created_at::text,
      v.actors,
      v.tags
    from public.videos v
    where v.is_active = true
      and ('vr' = any(v.tags) or 'vr' = any(v.categories))
    order by v.source_release_date desc nulls last, v.created_at desc
    limit greatest(section_limit, 1)
  )
  union all
  (
    select
      '51cg'::text,
      v.id::text,
      v.external_id,
      v.title,
      v.cover_url,
      v.source_url,
      v.duration,
      v.source_release_date::text,
      v.created_at::text,
      v.actors,
      v.tags
    from public.videos v
    where v.is_active = true
      and ('51cg' = any(v.tags) or '51cg' = any(v.categories) or v.source_site = '51cg')
    order by v.source_release_date desc nulls last, v.created_at desc
    limit greatest(section_limit, 1)
  );
$$;
