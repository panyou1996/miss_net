drop function if exists public.get_home_payload(integer, integer);

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
  tags text[],
  inventory_status text,
  detail_status text
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
      v.tags,
      v.inventory_status,
      v.detail_status
    from public.videos v
    where v.is_active = true
    order by v.source_release_date desc nulls last, v.created_at desc
    limit greatest(section_limit, 1)
  )
  union all
  (
    select
      'monthly_hot'::text as section,
      v.id::text,
      v.external_id,
      v.title,
      v.cover_url,
      v.source_url,
      v.duration,
      v.source_release_date::text,
      v.created_at::text,
      v.actors,
      v.tags,
      v.inventory_status,
      v.detail_status
    from public.videos v
    where v.is_active = true
      and 'monthly_hot' = any(public.normalize_known_taxonomy_aliases(coalesce(v.tags, '{}'::text[]) || coalesce(v.categories, '{}'::text[])))
    order by v.source_release_date desc nulls last, v.created_at desc
    limit greatest(section_limit, 1)
  )
  union all
  (
    select
      'weekly_hot'::text as section,
      v.id::text,
      v.external_id,
      v.title,
      v.cover_url,
      v.source_url,
      v.duration,
      v.source_release_date::text,
      v.created_at::text,
      v.actors,
      v.tags,
      v.inventory_status,
      v.detail_status
    from public.videos v
    where v.is_active = true
      and 'weekly_hot' = any(public.normalize_known_taxonomy_aliases(coalesce(v.tags, '{}'::text[]) || coalesce(v.categories, '{}'::text[])))
    order by v.source_release_date desc nulls last, v.created_at desc
    limit greatest(weekly_limit, 1)
  )
  union all
  (
    select
      'uncensored'::text as section,
      v.id::text,
      v.external_id,
      v.title,
      v.cover_url,
      v.source_url,
      v.duration,
      v.source_release_date::text,
      v.created_at::text,
      v.actors,
      v.tags,
      v.inventory_status,
      v.detail_status
    from public.videos v
    where v.is_active = true
      and 'uncensored' = any(public.normalize_known_taxonomy_aliases(coalesce(v.tags, '{}'::text[]) || coalesce(v.categories, '{}'::text[])))
    order by v.source_release_date desc nulls last, v.created_at desc
    limit greatest(section_limit, 1)
  )
  union all
  (
    select
      'subtitled'::text as section,
      v.id::text,
      v.external_id,
      v.title,
      v.cover_url,
      v.source_url,
      v.duration,
      v.source_release_date::text,
      v.created_at::text,
      v.actors,
      v.tags,
      v.inventory_status,
      v.detail_status
    from public.videos v
    where v.is_active = true
      and 'subtitled' = any(public.normalize_known_taxonomy_aliases(coalesce(v.tags, '{}'::text[]) || coalesce(v.categories, '{}'::text[])))
    order by v.source_release_date desc nulls last, v.created_at desc
    limit greatest(section_limit, 1)
  )
  union all
  (
    select
      'vr'::text as section,
      v.id::text,
      v.external_id,
      v.title,
      v.cover_url,
      v.source_url,
      v.duration,
      v.source_release_date::text,
      v.created_at::text,
      v.actors,
      v.tags,
      v.inventory_status,
      v.detail_status
    from public.videos v
    where v.is_active = true
      and 'vr' = any(public.normalize_known_taxonomy_aliases(coalesce(v.tags, '{}'::text[]) || coalesce(v.categories, '{}'::text[])))
    order by v.source_release_date desc nulls last, v.created_at desc
    limit greatest(section_limit, 1)
  )
  union all
  (
    select
      '51cg'::text as section,
      v.id::text,
      v.external_id,
      v.title,
      v.cover_url,
      v.source_url,
      v.duration,
      v.source_release_date::text,
      v.created_at::text,
      v.actors,
      v.tags,
      v.inventory_status,
      v.detail_status
    from public.videos v
    where v.is_active = true
      and '51cg' = any(public.normalize_known_taxonomy_aliases(coalesce(v.tags, '{}'::text[]) || coalesce(v.categories, '{}'::text[])))
    order by v.source_release_date desc nulls last, v.created_at desc
    limit greatest(section_limit, 1)
  );
$$;
