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


class FiftyOneCgContractsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.main = load_main_module()

    def test_builds_canonical_article_url_for_single_video_entry(self):
        self.assertEqual(
            "https://51cg1.com/archives/251299/#video-1",
            self.main.build_51cg_canonical_source_url("https://51cg1.com/archives/251299/", 1),
        )

    def test_builds_canonical_article_url_for_multi_video_entry(self):
        self.assertEqual(
            "https://51cg1.com/archives/251332/#video-2",
            self.main.build_51cg_canonical_source_url("https://51cg1.com/archives/251332/", 2),
        )

    def test_index_mode_defaults_include_51cg_when_not_skipped(self):
        original_mode = self.main.SCRAPER_RUN_MODE
        original_tags = list(self.main.SCRAPER_SOURCE_TAGS)
        original_skip = self.main.SKIP_51CG
        original_pages = self.main.CG_MAX_PAGES
        try:
            self.main.SCRAPER_RUN_MODE = "index"
            self.main.SCRAPER_SOURCE_TAGS = []
            self.main.SKIP_51CG = False
            self.main.CG_MAX_PAGES = 1

            config = self.main.resolve_run_configuration()

            self.assertIn("51cg", config["selected_tags"])
            self.assertTrue(config["run_51cg_main"])
        finally:
            self.main.SCRAPER_RUN_MODE = original_mode
            self.main.SCRAPER_SOURCE_TAGS = original_tags
            self.main.SKIP_51CG = original_skip
            self.main.CG_MAX_PAGES = original_pages


if __name__ == "__main__":
    unittest.main()
