create extension if not exists pgcrypto;

create table if not exists public.scrape_runs (
  id uuid primary key default gen_random_uuid(),
  source text not null,
  started_at timestamptz not null default timezone('utc'::text, now()),
  finished_at timestamptz,
  status text not null default 'running',
  pages_scanned integer not null default 0,
  discovered_count integer not null default 0,
  upserted_count integer not null default 0,
  detail_success_count integer not null default 0,
  detail_fail_count integer not null default 0,
  placeholder_cover_count integer not null default 0,
  blocked_count integer not null default 0,
  error_summary text
);

create index if not exists idx_scrape_runs_started_at
  on public.scrape_runs (started_at desc);

create index if not exists idx_scrape_runs_source_started_at
  on public.scrape_runs (source, started_at desc);
