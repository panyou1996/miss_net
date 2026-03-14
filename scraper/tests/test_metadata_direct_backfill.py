import asyncio
import importlib
import importlib.util
import os
import pathlib
import subprocess
import sys
import tempfile
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
    return importlib.import_module("scraper.main")


def load_queue_module():
    path = pathlib.Path(__file__).resolve().parents[2] / 'scripts' / 'select_metadata_queue.py'
    spec = importlib.util.spec_from_file_location('select_metadata_queue', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class MetadataDirectBackfillTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.main = load_main_module()
        cls.queue = load_queue_module()

    def test_metadata_patch_updates_metadata_without_clobbering_cover(self):
        existing = {
            "external_id": "abc-1",
            "title": "Video",
            "source_url": "https://missav.ws/abc-1",
            "cover_url": "https://fourhoi.com/abc-1/cover-n.jpg",
            "actors": [],
            "tags": [],
            "release_date": None,
            "inventory_status": "cover_ready",
            "detail_status": "partial",
        }
        details = {
            "actors": ["Actor One"],
            "tags": ["tag-1", "tag-2"],
            "release_date": "2026-03-15",
        }

        patched = self.main.apply_metadata_patch(existing, details)

        self.assertEqual("https://fourhoi.com/abc-1/cover-n.jpg", patched["cover_url"])
        self.assertEqual(["Actor One"], patched["actors"])
        self.assertEqual(["tag-1", "tag-2"], patched["tags"])
        self.assertEqual("2026-03-15", patched["release_date"])
        self.assertEqual("detail_ready", patched["inventory_status"])
        self.assertEqual("success", patched["detail_status"])

    def test_metadata_queue_env_output_is_shell_safe(self):
        rows = [
            {
                "external_id": "abc-2",
                "source_url": "https://missav.ws/abc-2",
                "source_site": "missav",
                "source_release_date": "2026-03-14",
                "created_at": "2026-03-15 01:23:45+00",
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
[[ "$METADATA_QUEUE_JSON" == *'"external_id": "abc-2"'* ]]
printf '%s' "$METADATA_QUEUE_COUNT"'''],
                check=True,
                capture_output=True,
                text=True,
            )
        finally:
            os.unlink(env_path)

        self.assertEqual('1', proc.stdout.strip())

    def test_scrape_videos_metadata_queue_mode_skips_run_config_paths(self):
        class FakePage:
            pass

        class FakeContext:
            def __init__(self):
                self.closed = False

            async def route(self, pattern, handler):
                return None

            async def new_page(self):
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
            return 84

        async def fake_finalize_scrape_run(**kwargs):
            finalize_calls.append(kwargs)

        async def fake_scrape_metadata_backfill(supabase, context, semaphore):
            stats = self.main.make_run_stats()
            stats['detail_success_count'] = 1
            return stats, {'metadata_queue': dict(stats)}

        with mock.patch.object(self.main, 'SCRAPER_RUN_MODE', 'metadata_queue'), \
             mock.patch.object(self.main, 'METADATA_QUEUE_JSON', '[{"external_id":"abc","source_url":"https://missav.ws/abc"}]'), \
             mock.patch.object(self.main, 'CONCURRENT_DETAIL_PAGES', 1), \
             mock.patch.object(self.main, 'BLOCK_HEAVY_RESOURCES', False), \
             mock.patch.object(self.main, 'SUPABASE_URL', ''), \
             mock.patch.object(self.main, 'SUPABASE_KEY', ''), \
             mock.patch.object(self.main, 'async_playwright', lambda: FakePlaywrightManager(fake_context)), \
             mock.patch.object(self.main, 'Stealth', FakeStealth), \
             mock.patch.object(self.main, 'create_scrape_run', fake_create_scrape_run), \
             mock.patch.object(self.main, 'finalize_scrape_run', fake_finalize_scrape_run), \
             mock.patch.object(self.main, 'scrape_metadata_backfill', fake_scrape_metadata_backfill):
            asyncio.run(self.main.scrape_videos())

        self.assertTrue(fake_context.closed)
        self.assertEqual(1, len(finalize_calls))
        self.assertEqual('success', finalize_calls[0]['status'])


if __name__ == '__main__':
    unittest.main()
