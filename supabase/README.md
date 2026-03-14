# Supabase workflow

This repository uses the Supabase CLI via `npx`, so no global install is required.

## Common usage

```bash
export SUPABASE_ACCESS_TOKEN=...   # personal access token from Supabase
scripts/supabase.sh login --token "$SUPABASE_ACCESS_TOKEN"
scripts/supabase.sh projects list
scripts/run_remote_sql.py --read-only --file supabase/sql/video_data_diagnostics.sql
```

## Project ref

```bash
scripts/supabase.sh link --project-ref gapmmwdbxzcglvvdhhiu
```

## Migrations

```bash
scripts/supabase.sh migration list
scripts/supabase.sh db push
```

## Recommended manual validation run

Use `workflow_dispatch` with these values first, instead of a full scrape:

- `run_mode=sample`
- `source_tags=new,weekly_hot,monthly_hot`
- `missav_max_pages=5`
- `cg_max_pages=1`
- `skip_51cg=true`
- `early_stop_streak=2`
- `early_stop_min_page=3`

This keeps validation closer to a 10–20 minute sample run instead of a broad multi-hour sweep.

## Targeted backlog inspection

```bash
export SUPABASE_ACCESS_TOKEN=...
scripts/run_remote_sql.py --read-only --file supabase/sql/backfill_priority_queue.sql
```

## Diagnostics

See:

- `supabase/migrations/20260314170000_harden_videos_pipeline.sql`
- `supabase/migrations/20260314171000_create_scrape_runs.sql`
- `supabase/sql/video_data_diagnostics.sql`
- `supabase/sql/backfill_priority_queue.sql`
