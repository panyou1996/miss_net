select section,
       count(*)::int as row_count,
       max(source_release_date) as latest_release_date
from public.get_home_payload(10, 15)
group by section
order by section;

