-- Function to get popular actors based on video count
create or replace function get_popular_actors(limit_count int)
returns table (actor text, video_count bigint)
language sql
as $$
  select a as actor, count(*) as video_count
  from videos, unnest(actors) as a
  where a is not null and length(a) > 1
  group by a
  order by video_count desc
  limit limit_count;
$$;

-- Function to get popular tags based on video count
create or replace function get_popular_tags(limit_count int)
returns table (tag text, video_count bigint)
language sql
as $$
  select t as tag, count(*) as video_count
  from videos, unnest(tags) as t
  where t is not null and length(t) > 1
  group by t
  order by video_count desc
  limit limit_count;
$$;
