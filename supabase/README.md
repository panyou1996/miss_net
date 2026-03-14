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

## Diagnostics

See:

- `supabase/migrations/20260314170000_harden_videos_pipeline.sql`
- `supabase/migrations/20260314171000_create_scrape_runs.sql`
- `supabase/sql/video_data_diagnostics.sql`
- `supabase/sql/backfill_priority_queue.sql`
