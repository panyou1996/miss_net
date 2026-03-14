select *
from public.get_actor_aggregates(20);

select *
from public.get_tag_aggregates(30);

select count(*) filter (where detail_status = 'success') as success_count,
       count(*) filter (where detail_status = 'partial') as partial_count,
       count(*) filter (where detail_status = 'pending') as pending_count,
       count(*) filter (where cover_status = 'missing') as missing_cover_count
from public.videos
where is_active = true;
