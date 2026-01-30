-- Enable the pg_trgm extension if not already enabled (good for ILIKE, but we are moving to FTS)
-- create extension if not exists pg_trgm;

-- Create a GIN index for Full Text Search on the 'title' column
-- This supports the .textSearch() query in the Flutter app.
-- Using 'simple' configuration to support mixed languages (English/Chinese/Japanese) better than 'english'.

CREATE INDEX IF NOT EXISTS videos_title_fts_idx 
ON videos 
USING gin(to_tsvector('simple', title));

-- Optional: If you want to search multiple columns (title + actors) efficiently in the future:
-- 1. Create a generated column that combines them:
-- alter table videos add column fts tsvector generated always as (to_tsvector('simple', title || ' ' || array_to_string(actors, ' '))) stored;
-- 2. Create index on that column:
-- create index videos_fts_idx on videos using gin(fts);
-- 3. In Flutter: .textSearch('fts', query)
