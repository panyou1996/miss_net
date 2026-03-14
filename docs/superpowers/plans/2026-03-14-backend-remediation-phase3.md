# Backend Remediation Phase 3 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make metadata completeness trustworthy by fixing detail-status consistency, add targeted cover backfill, and reduce native client query fan-out through backend aggregation.

**Architecture:** Keep the current GitHub Action scraper → Supabase → native app architecture, but harden the contract in three layers. First, scraper and SQL must agree on what “success/partial/pending” means. Second, missing-cover recovery gets its own targeted selection path instead of waiting for full scrapes. Third, homepage/search/actor/tag access should move from many client-side direct queries toward server-side SQL/RPC aggregation with stable taxonomy.

**Tech Stack:** Python scraper, GitHub Actions, Supabase Postgres/SQL, Kotlin Android app, Supabase Kotlin client.

---

## File Map

- Modify: `scraper/main.py`
  - Finalize metadata completeness evaluation, persist `detail_status` consistently, track cover-backfill oriented stats, and expose explicit run metadata for new workflows.
- Modify: `.github/workflows/targeted-backfill.yml`
  - Add dedicated cover-backfill mode inputs and summaries.
- Modify: `.github/workflows/scraper.yml`
  - Keep sample/full workflows aligned with new status/cover behavior if needed.
- Create/Modify: `supabase/migrations/20260314xxxxxx_recompute_detail_status.sql`
  - Batch recompute status/cover state for existing rows and add any missing indexes/functions used by the new aggregation path.
- Create/Modify: `supabase/sql/*.sql`
  - Diagnostics, backfill selectors, and aggregation SQL prototypes.
- Create/Modify: `supabase/README.md`
  - Operational usage for targeted detail/cover backfill and verification commands.
- Modify: `miss_net_native/app/src/main/java/com/panyou/missnet/data/model/Video.kt`
  - Add fields required by aggregation payloads only if needed.
- Modify: `miss_net_native/app/src/main/java/com/panyou/missnet/data/repository/VideoRepository.kt`
  - Prefer RPC/aggregated queries for home/actors/tags/search where feasible; keep fallbacks minimal.
- Modify: `miss_net_native/app/src/main/java/com/panyou/missnet/ui/viewmodel/HomeViewModel.kt`
  - Consume aggregate home payload instead of N separate fan-out queries if repository interface changes.
- Test/Verify: `python3 -m py_compile scraper/main.py`, targeted remote SQL diagnostics, `./miss_net_native/gradlew -p miss_net_native :app:assembleDebug :app:lintDebug`.

## Chunk 1: Detail status consistency (A)

### Task 1: Codify detail completeness in scraper

**Files:**
- Modify: `scraper/main.py`

- [ ] **Step 1: Write a failing focused check**

Create a minimal local assertion harness or helper test block in a temporary Python snippet covering cases:
- valid cover + actors/tags/release/duration → `success`
- missing one of the detail fields → `partial`
- almost no metadata → `pending`

- [ ] **Step 2: Run the check to verify current behavior is missing/inconsistent**

Run a small Python snippet against current helper logic.
Expected: no shared canonical evaluator exists or status does not reflect duration/covers consistently.

- [ ] **Step 3: Implement canonical completeness helpers**

In `scraper/main.py`:
- extract pure helpers like `normalize_cover_url`, `has_meaningful_duration`, `classify_detail_status(record)`
- ensure upsert payload sets:
  - `detail_status`
  - `detail_fetched_at`
  - `cover_status`
  - `last_detail_error` / `detail_fail_count` as appropriate
- ensure success/partial/pending are recomputed after merge-with-existing metadata, not before

- [ ] **Step 4: Re-run the focused check**

Expected: classifications match the contract.

- [ ] **Step 5: Commit**

```bash
git add scraper/main.py
git commit -m "fix: classify scraper detail completeness consistently"
```

### Task 2: Recompute historical detail status in SQL

**Files:**
- Create: `supabase/migrations/20260314xxxxxx_recompute_detail_status.sql`
- Modify: `supabase/sql/video_data_diagnostics.sql`

- [ ] **Step 1: Write the failing verification query**

Use remote SQL to show existing contradictions, e.g. rows with duration/actors/tags present but `detail_status != 'success'`.

- [ ] **Step 2: Verify failure**

Run the query against remote DB.
Expected: mismatched rows exist.

- [ ] **Step 3: Implement migration**

Write SQL that:
- recomputes `cover_status`
- recomputes `detail_status` from stored metadata
- updates `detail_fetched_at` where status becomes `success/partial`
- optionally adds helper SQL function if it keeps logic readable

- [ ] **Step 4: Apply/verify**

Run the migration remotely and re-run diagnostics.
Expected: `detail_status='success'` rises materially; contradictions go to zero.

- [ ] **Step 5: Commit**

```bash
git add supabase/migrations supabase/sql
git commit -m "fix: recompute historical detail status"
```

## Chunk 2: Missing cover targeted backfill (B)

### Task 3: Add cover-priority selection path

**Files:**
- Modify: `scripts/select_backfill_targets.py`
- Create/Modify: `supabase/sql/backfill_priority_queue.sql`

- [ ] **Step 1: Write a failing selector check**

Run selector with a cover-focused mode expectation.
Expected: current selector only ranks by pending/partial backlog, not missing covers.

- [ ] **Step 2: Implement cover-priority mode**

Add selector support for modes like:
- `metadata`
- `cover`
- `mixed`

For `cover`, prefer tags/sources with highest `cover_url is null` backlog.

- [ ] **Step 3: Verify selector output**

Run the selector in `cover` mode.
Expected: env/json output favors tags with large null-cover backlog.

- [ ] **Step 4: Commit**

```bash
git add scripts/select_backfill_targets.py supabase/sql/backfill_priority_queue.sql
git commit -m "feat: prioritize missing-cover backfill targets"
```

### Task 4: Wire workflow support for cover backfill

**Files:**
- Modify: `.github/workflows/targeted-backfill.yml`
- Modify: `supabase/README.md`

- [ ] **Step 1: Write failing workflow expectation**

Inspect workflow inputs and confirm there is no explicit cover mode.

- [ ] **Step 2: Implement workflow inputs and summaries**

Add inputs such as:
- `backfill_focus=metadata|cover|mixed`
- pass focus into selector
- enrich `GITHUB_STEP_SUMMARY` with focus and chosen tags

- [ ] **Step 3: Verify YAML/script wiring**

Use local grep / dry inspection to ensure env vars line up with selector flags.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/targeted-backfill.yml supabase/README.md
git commit -m "feat: add cover-focused targeted backfill workflow mode"
```

## Chunk 3: Backend aggregation for native data flows (C)

### Task 5: Add SQL aggregation primitives

**Files:**
- Create: `supabase/sql/native_home_payload.sql`
- Create: `supabase/sql/native_browse_aggregates.sql`
- Optional migration if choosing RPCs/views: `supabase/migrations/20260314xxxxxx_native_aggregates.sql`

- [ ] **Step 1: Write failing usage target**

Document current client fan-out points:
- home sections queried separately
- actor/tag pages synthesized client-side

- [ ] **Step 2: Implement aggregation SQL**

Provide either SQL functions/views for:
- home payload (task strip + key sections)
- actor stats aggregate
- tag stats aggregate
- search metadata summary if low-cost

Keep schema additions minimal; reuse existing `videos` table.

- [ ] **Step 3: Verify SQL against remote DB**

Run sample aggregate queries remotely.
Expected: payloads return usable rows with stable taxonomy names.

- [ ] **Step 4: Commit**

```bash
git add supabase/sql supabase/migrations
git commit -m "feat: add native aggregation SQL primitives"
```

### Task 6: Consume aggregation path in native repository

**Files:**
- Modify: `miss_net_native/app/src/main/java/com/panyou/missnet/data/model/Video.kt`
- Modify: `miss_net_native/app/src/main/java/com/panyou/missnet/data/repository/VideoRepository.kt`
- Modify: `miss_net_native/app/src/main/java/com/panyou/missnet/ui/viewmodel/HomeViewModel.kt`

- [ ] **Step 1: Write failing compile/test target**

Identify methods whose signatures must change for aggregated payloads.
Expected: current repository lacks aggregate accessors.

- [ ] **Step 2: Implement minimal repository additions**

Add methods that use SQL/RPC-backed endpoints for:
- home payload
- actor/tag aggregates

Keep current per-query fallback only if aggregate call fails.

- [ ] **Step 3: Update view models**

Shift `HomeViewModel` and any browse data loaders to the new repository methods.
Preserve current UI behavior.

- [ ] **Step 4: Verify Android build**

Run:
```bash
export JAVA_HOME=/home/panyou/jdk-17.0.18+8
export PATH="$JAVA_HOME/bin:$PATH"
./miss_net_native/gradlew -p miss_net_native :app:assembleDebug :app:lintDebug
```
Expected: build passes.

- [ ] **Step 5: Commit**

```bash
git add miss_net_native/app/src/main/java/com/panyou/missnet/data miss_net_native/app/src/main/java/com/panyou/missnet/ui/viewmodel/HomeViewModel.kt
git commit -m "refactor: use backend aggregates for native home and browse"
```

## Final verification

- [ ] Run scraper syntax verification:
```bash
cd /home/panyou/Projects/MissNet/.worktrees/backend-remediation
python3 -m py_compile scraper/main.py
```
- [ ] Run remote diagnostics for detail/cover counts and aggregate query sanity.
- [ ] Run Android verification:
```bash
export JAVA_HOME=/home/panyou/jdk-17.0.18+8
export PATH="$JAVA_HOME/bin:$PATH"
./miss_net_native/gradlew -p miss_net_native :app:assembleDebug :app:lintDebug
```
- [ ] Push branch and summarize remote workflow implications.

