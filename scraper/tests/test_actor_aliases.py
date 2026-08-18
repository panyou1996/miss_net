import importlib


def fresh_db(tmp_path):
    from app import db

    db.DB_PATH = str(tmp_path / "missnets-test.db")
    db.init_db()
    return db


def test_actor_aliases_split_parenthesized_names():
    from app import db

    assert db.actor_aliases("篠田優(篠田ゆう)") == [
        "篠田優(篠田ゆう)",
        "篠田優 (篠田ゆう)",
        "篠田優",
        "篠田ゆう",
    ]
    assert db.actor_aliases("篠田優 (篠田ゆう)") == [
        "篠田優 (篠田ゆう)",
        "篠田優(篠田ゆう)",
        "篠田優",
        "篠田ゆう",
    ]


def test_query_videos_by_actor_merges_parenthesized_aliases(tmp_path):
    db = fresh_db(tmp_path)
    db.save_videos([
        {
            "external_id": "exact-spaced",
            "title": "作品 A",
            "actors": ["篠田優 (篠田ゆう)"],
            "source_release_date": "2026-06-01",
        },
        {
            "external_id": "kanji-only",
            "title": "作品 B 篠田優",
            "actors": ["篠田優"],
            "source_release_date": "2026-06-02",
        },
        {
            "external_id": "kana-only",
            "title": "作品 C",
            "actors": ["篠田ゆう"],
            "source_release_date": "2026-06-03",
        },
    ])

    ids = [video["id"] for video in db.query_videos_by_actor("篠田優(篠田ゆう)", limit=10)]

    assert ids == ["kana-only", "kanji-only", "exact-spaced"]


def test_query_directory_entry_matches_actor_alias(tmp_path):
    db = fresh_db(tmp_path)
    db.save_directory_entries("actor", [
        {
            "name": "篠田優 (篠田ゆう)",
            "source_url": "https://missav.ws/cn/actresses/%E7%AF%A0%E7%94%B0%E5%84%AA",
            "video_count": 123,
        }
    ])

    entry = db.query_directory_entry("actor", "篠田優(篠田ゆう)")

    assert entry is not None
    assert entry["name"] == "篠田優 (篠田ゆう)"
    assert entry["video_count"] == 123
