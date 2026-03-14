import importlib
import sys
import types
import unittest


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


class QuantityFirstContractsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.main = load_main_module()

    def test_inventory_status_treats_duration_as_optional(self):
        record = {
            "external_id": "abc-123",
            "title": "Example Title",
            "source_url": "https://missav.ws/abc-123",
            "cover_url": "https://fourhoi.com/abc-123/cover-n.jpg",
            "release_date": "2026-03-10",
            "actors": [],
            "tags": [],
            "duration": None,
        }

        self.assertEqual("detail_ready", self.main.classify_inventory_status(record))
        self.assertEqual("success", self.main.classify_detail_status(record))

    def test_inventory_status_marks_index_only_records(self):
        record = {
            "external_id": "idx-001",
            "title": "Indexed from list page",
            "source_url": "https://missav.ws/idx-001",
            "cover_url": None,
            "release_date": None,
            "actors": [],
            "tags": [],
            "duration": None,
        }

        self.assertEqual("indexed", self.main.classify_inventory_status(record))
        self.assertEqual("pending", self.main.classify_detail_status(record))

    def test_index_only_policy_skips_detail_fetch_for_known_indexed_row(self):
        existing = {
            "external_id": "idx-002",
            "title": "Already indexed",
            "source_url": "https://missav.ws/idx-002",
            "cover_url": "https://fourhoi.com/idx-002/cover-n.jpg",
            "release_date": None,
            "actors": [],
            "tags": [],
            "duration": None,
            "inventory_status": "cover_ready",
            "detail_status": "partial",
        }

        self.assertFalse(self.main.should_fetch_details(existing, detail_fetch_policy="none"))
        self.assertTrue(self.main.should_fetch_details(existing, detail_fetch_policy="smart"))

    def test_index_mode_discovery_is_not_restricted_to_seed_tags(self):
        seeds = [
            {"url": "https://missav.ws/new", "tag": "new"},
            {"url": "https://missav.ws/dm169/weekly-hot?sort=weekly_views", "tag": "weekly_hot"},
            {"url": "https://missav.ws/dm263/monthly-hot?sort=monthly_views", "tag": "monthly_hot"},
            {"url": "https://missav.ws/chinese-subtitle", "tag": "subtitled"},
        ]
        discovered = [
            {"url": "https://missav.ws/dm114/genres/%E5%B7%A8%E4%B9%B3", "tag": "big_tits"},
            {"url": "https://missav.ws/dm127/genres/%E4%B8%AD%E5%87%BA", "tag": "creampie"},
        ]

        widened = self.main.filter_discovered_sources_for_run(
            seed_sources=seeds,
            discovered_sources=discovered,
            selected_tags={"new", "weekly_hot", "monthly_hot", "subtitled"},
            run_mode="index",
            manual_source_tags=False,
        )

        tags = {item["tag"] for item in widened}
        self.assertIn("big_tits", tags)
        self.assertIn("creampie", tags)


if __name__ == "__main__":
    unittest.main()
