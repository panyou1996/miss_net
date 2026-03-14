create or replace function public.get_videos_by_category(
  category_text text,
  limit_count integer default 20,
  offset_count integer default 0
)
returns setof public.videos
language sql
stable
as $$
  select *
  from public.videos v
  where v.is_active = true
    and coalesce(btrim(category_text), '') <> ''
    and category_text = any(
      public.normalize_known_taxonomy_aliases(
        coalesce(v.tags, '{}'::text[]) || coalesce(v.categories, '{}'::text[])
      )
    )
  order by v.source_release_date desc nulls last, v.created_at desc
  offset greatest(offset_count, 0)
  limit greatest(limit_count, 1);
$$;

create or replace function public.get_videos_by_actor(
  actor_name text,
  limit_count integer default 20,
  offset_count integer default 0
)
returns setof public.videos
language sql
stable
as $$
  select *
  from public.videos v
  where v.is_active = true
    and coalesce(btrim(actor_name), '') <> ''
    and actor_name = any(coalesce(v.actors, '{}'::text[]))
  order by v.source_release_date desc nulls last, v.created_at desc
  offset greatest(offset_count, 0)
  limit greatest(limit_count, 1);
$$;
