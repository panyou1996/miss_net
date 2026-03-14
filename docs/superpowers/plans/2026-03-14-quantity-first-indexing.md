# Quantity-First Indexing Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand MissAV coverage quickly and cheaply by making list-page indexing first-class, while treating duration as optional metadata.

**Architecture:** Introduce a quantity-first contract: list-page data is enough to ingest rows, cover/release/actor/tag enrich them later, and detail-page scraping becomes optional via policy. Add index-mode workflow controls and dynamic MissAV source discovery so breadth increases without hardcoding hundreds of tags.

**Tech Stack:** Python scraper, GitHub Actions, Supabase SQL, Kotlin native app.

---

### Task 1: Lock the quantity-first contract

**Files:**
- Create: `scraper/tests/test_quantity_first_contracts.py`
- Modify: `scraper/main.py`
- Modify: `supabase/migrations/20260314193000_detail_status_and_native_aggregates.sql`

- [ ] Write failing tests for inventory status and index-only policy.
- [ ] Implement `classify_inventory_status` and remove duration from success gating.
- [ ] Verify tests pass.

### Task 2: Add index-only run mode and source discovery

**Files:**
- Modify: `scraper/main.py`
- Modify: `.github/workflows/scraper.yml`
- Modify: `supabase/README.md`

- [ ] Add `SCRAPER_RUN_MODE=index` and `DETAIL_FETCH_POLICY`.
- [ ] Add dynamic MissAV source discovery from seed pages.
- [ ] Expose workflow dispatch inputs for quantity-first runs.
- [ ] Verify scraper syntax.

### Task 3: Keep DB diagnostics aligned

**Files:**
- Modify: `supabase/sql/video_data_diagnostics.sql`
- Modify: `supabase/migrations/20260314170000_harden_videos_pipeline.sql`
- Modify: `supabase/migrations/20260314193000_detail_status_and_native_aggregates.sql`

- [ ] Add `inventory_status` support and recomputation.
- [ ] Verify remote SQL applies.
- [ ] Check status distributions remotely.

### Task 4: Final verification

- [ ] Run `python3 -m unittest scraper.tests.test_quantity_first_contracts -v`
- [ ] Run `python3 -m py_compile scraper/main.py scripts/select_backfill_targets.py`
- [ ] Run `./miss_net_native/gradlew -p miss_net_native :app:assembleDebug :app:lintDebug`
- [ ] Push branch and summarize recommended GitHub Action parameters.
