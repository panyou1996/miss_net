import importlib.util
import pathlib
import unittest


def load_selector_module():
    path = pathlib.Path(__file__).resolve().parents[2] / 'scripts' / 'select_backfill_targets.py'
    spec = importlib.util.spec_from_file_location('select_backfill_targets', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class TargetedBackfillSelectorTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.selector = load_selector_module()

    def test_cover_focus_uses_cover_first_missav_fallbacks(self):
        fallback = self.selector.resolve_fallback_tags(focus='cover', source_site='missav', include_51cg=False)
        self.assertEqual(['exclusive', 'creampie', 'single', 'big_tits'], fallback[:4])
        self.assertNotIn('51cg', fallback)

    def test_metadata_focus_uses_recent_metadata_fallbacks(self):
        fallback = self.selector.resolve_fallback_tags(focus='metadata', source_site='missav', include_51cg=False)
        self.assertEqual(['new', 'weekly_hot', 'monthly_hot', 'subtitled'], fallback[:4])

    def test_select_tags_from_rows_fills_with_focus_fallbacks(self):
        rows = [
            {'tag': 'big_tits'},
            {'tag': 'exclusive'},
        ]
        selected = self.selector.select_tags_from_rows(
            rows,
            limit=4,
            focus='cover',
            source_site='missav',
            include_51cg=False,
        )
        self.assertEqual(['big_tits', 'exclusive', 'creampie', 'single'], selected)

    def test_select_tags_for_51cg_prefers_51cg_family(self):
        rows = []
        selected = self.selector.select_tags_from_rows(
            rows,
            limit=2,
            focus='mixed',
            source_site='51cg',
            include_51cg=True,
        )
        self.assertEqual(['51cg', '51mrds'], selected)


if __name__ == '__main__':
    unittest.main()
