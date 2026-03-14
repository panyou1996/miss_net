import asyncio
import json
import os
import subprocess
import tempfile
import importlib.util
import pathlib
import sys
import types
import unittest
from unittest import mock


def load_main_module():
    if "playwright.async_api" not in sys.modules:
        sys.modules["playwright.async_api"] = types.SimpleNamespace(async_playwright=None)
    if "playwright_stealth" not in sys.modules:
        sys.modules["playwright_stealth"] = types.SimpleNamespace(Stealth=object)
    if "supabase" not in sys.modules:
        sys.modules["supabase"] = types.SimpleNamespace(create_client=lambda *args, **kwargs: None, Client=object)
    if "dotenv" not in sys.modules:
        sys.modules["dotenv"] = types.SimpleNamespace(load_dotenv=lambda *args, **kwargs: None)
    import importlib
    return importlib.import_module("scraper.main")


def load_queue_module():
    path = pathlib.Path(__file__).resolve().parents[2] / 'scripts' / 'select_null_cover_queue.py'
    spec = importlib.util.spec_from_file_location('select_null_cover_queue', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class NullCoverBackfillTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.main = load_main_module()
        cls.queue = load_queue_module()

    def test_cover_patch_only_updates_cover_related_fields(self):
        existing = {
            "external_id": "abc-1",
            "title": "Video",
            "source_url": "https://missav.ws/abc-1",
            "cover_url": None,
            "release_date": None,
            "actors": [],
            "tags": [],
            "inventory_status": "indexed",
            "detail_status": "pending",
        }

        patched = self.main.apply_cover_patch(existing, "https://fourhoi.com/abc-1/cover-n.jpg")

        self.assertEqual("https://fourhoi.com/abc-1/cover-n.jpg", patched["cover_url"])
        self.assertEqual("valid", patched["cover_status"])
        self.assertEqual("cover_ready", patched["inventory_status"])
        self.assertEqual("partial", patched["detail_status"])
        self.assertEqual("Video", patched["title"])
        self.assertEqual([], patched["actors"])
        self.assertEqual([], patched["tags"])

    def test_cover_patch_keeps_richer_state_when_record_already_has_metadata(self):
        existing = {
            "external_id": "abc-2",
            "title": "Video 2",
            "source_url": "https://missav.ws/abc-2",
            "cover_url": None,
            "release_date": "2026-03-14",
            "actors": ["Actor"],
            "tags": ["tag1"],
            "inventory_status": "indexed",
            "detail_status": "pending",
        }

        patched = self.main.apply_cover_patch(existing, "https://fourhoi.com/abc-2/cover-n.jpg")

        self.assertEqual("detail_ready", patched["inventory_status"])
        self.assertEqual("success", patched["detail_status"])
        self.assertEqual(["Actor"], patched["actors"])
        self.assertEqual(["tag1"], patched["tags"])


    def test_scrape_videos_null_cover_mode_skips_run_config_paths(self):
        class FakePage:
            pass

        class FakeContext:
            def __init__(self):
                self.route_calls = []
                self.new_page_calls = 0
                self.closed = False

            async def route(self, pattern, handler):
                self.route_calls.append((pattern, handler))

            async def new_page(self):
                self.new_page_calls += 1
                return FakePage()

            async def close(self):
                self.closed = True

        class FakeChromium:
            def __init__(self, context):
                self._context = context

            async def launch_persistent_context(self, **kwargs):
                return self._context

        class FakePlaywrightManager:
            def __init__(self, context):
                self.chromium = FakeChromium(context)

            async def __aenter__(self):
                return self

            async def __aexit__(self, exc_type, exc, tb):
                return False

        class FakeStealth:
            async def apply_stealth_async(self, page):
                return None

        fake_context = FakeContext()
        finalize_calls = []

        async def fake_create_scrape_run(supabase, source):
            return 42

        async def fake_finalize_scrape_run(**kwargs):
            finalize_calls.append(kwargs)

        async def fake_scrape_null_cover_backfill(supabase, context, semaphore):
            stats = self.main.make_run_stats()
            stats['upserted_count'] = 1
            return stats, {'null_cover': dict(stats)}

        with mock.patch.object(self.main, 'SCRAPER_RUN_MODE', 'null_cover'), \
             mock.patch.object(self.main, 'NULL_COVER_QUEUE_JSON', '[{"external_id":"abc","source_url":"https://missav.ws/abc"}]'), \
             mock.patch.object(self.main, 'CONCURRENT_DETAIL_PAGES', 1), \
             mock.patch.object(self.main, 'BLOCK_HEAVY_RESOURCES', False), \
             mock.patch.object(self.main, 'SUPABASE_URL', ''), \
             mock.patch.object(self.main, 'SUPABASE_KEY', ''), \
             mock.patch.object(self.main, 'async_playwright', lambda: FakePlaywrightManager(fake_context)), \
             mock.patch.object(self.main, 'Stealth', FakeStealth), \
             mock.patch.object(self.main, 'create_scrape_run', fake_create_scrape_run), \
             mock.patch.object(self.main, 'finalize_scrape_run', fake_finalize_scrape_run), \
             mock.patch.object(self.main, 'scrape_null_cover_backfill', fake_scrape_null_cover_backfill):
            asyncio.run(self.main.scrape_videos())

        self.assertTrue(fake_context.closed)
        self.assertEqual(1, len(finalize_calls))
        self.assertEqual('success', finalize_calls[0]['status'])


    def test_queue_env_output_is_shell_safe(self):
        rows = [
            {
                "external_id": "thz-095",
                "source_url": "https://missav.ws/thz-095",
                "source_site": "missav",
                "source_release_date": "2026-03-09",
                "created_at": "2026-03-11 13:42:49.916451+00",
            }
        ]
        payload = self.queue.render_env_output(rows, 'missav')

        with tempfile.NamedTemporaryFile('w', delete=False) as handle:
            handle.write(payload)
            env_path = handle.name

        try:
            proc = subprocess.run(
                ['bash', '-lc', f'''set -euo pipefail
source {env_path}
[[ "$NULL_COVER_QUEUE_JSON" == *'"external_id": "thz-095"'* ]]
printf '%s' "$NULL_COVER_QUEUE_COUNT"'''],
                check=True,
                capture_output=True,
                text=True,
            )
        finally:
            os.unlink(env_path)

        self.assertEqual('1', proc.stdout.strip())

    def test_select_queue_rows_prefers_newest_release_then_created(self):
        rows = [
            {"external_id": "old", "source_url": "https://missav.ws/old", "source_release_date": "2025-01-01", "created_at": "2026-03-01T10:00:00+00:00"},
            {"external_id": "newer", "source_url": "https://missav.ws/newer", "source_release_date": "2026-03-10", "created_at": "2026-03-10T10:00:00+00:00"},
            {"external_id": "newest_created", "source_url": "https://missav.ws/newest-created", "source_release_date": None, "created_at": "2026-03-11T10:00:00+00:00"},
        ]
        selected = self.queue.select_queue_rows(rows, limit=2)
        self.assertEqual(["newest_created", "newer"], [row["external_id"] for row in selected])

    def test_select_queue_rows_filters_invalid_urls(self):
        rows = [
            {"external_id": "ok", "source_url": "https://missav.ws/ok", "source_release_date": None, "created_at": "2026-03-11T10:00:00+00:00"},
            {"external_id": "bad", "source_url": "", "source_release_date": "2026-03-12", "created_at": "2026-03-12T10:00:00+00:00"},
        ]
        selected = self.queue.select_queue_rows(rows, limit=5)
        self.assertEqual(["ok"], [row["external_id"] for row in selected])


if __name__ == '__main__':
    unittest.main()
