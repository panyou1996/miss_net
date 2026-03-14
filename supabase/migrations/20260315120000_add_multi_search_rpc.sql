create or replace function public.search_videos_multi(
  query_text text,
  limit_count integer default 20,
  offset_count integer default 0
)
returns setof public.videos
language sql
stable
as $$
  with input as (
    select lower(btrim(coalesce(query_text, ''))) as q
  )
  select v.*
  from public.videos v
  cross join input i
  where v.is_active = true
    and i.q <> ''
    and (
      lower(v.title) like ('%' || i.q || '%')
      or exists (
        select 1
        from unnest(coalesce(v.actors, '{}'::text[])) actor
        where lower(actor) like ('%' || i.q || '%')
      )
      or exists (
        select 1
        from unnest(public.normalize_known_taxonomy_aliases(coalesce(v.tags, '{}'::text[]) || coalesce(v.categories, '{}'::text[]))) tag
        where lower(tag) like ('%' || i.q || '%')
      )
    )
  order by
    case
      when lower(v.title) = i.q then 0
      when lower(v.title) like (i.q || '%') then 1
      when exists (
        select 1 from unnest(coalesce(v.actors, '{}'::text[])) actor where lower(actor) = i.q
      ) then 2
      when exists (
        select 1 from unnest(public.normalize_known_taxonomy_aliases(coalesce(v.tags, '{}'::text[]) || coalesce(v.categories, '{}'::text[]))) tag where lower(tag) = i.q
      ) then 3
      else 4
    end,
    v.source_release_date desc nulls last,
    v.created_at desc
  limit greatest(limit_count, 1)
  offset greatest(offset_count, 0);
$$;
