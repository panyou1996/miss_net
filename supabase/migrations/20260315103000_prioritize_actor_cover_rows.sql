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
        order by (public.video_cover_status(cover_url) = 'valid') desc, source_release_date desc nulls last, created_at desc
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
  order by (public.video_cover_status(cover_url) = 'valid') desc, video_count desc, latest_release_date desc nulls last, actor asc
  limit greatest(limit_count, 1);
$$;
