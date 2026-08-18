from pathlib import Path


def fresh_db(tmp_path):
    from app import db

    db.DB_PATH = str(tmp_path / "missnets-test.db")
    db.init_db()
    return db


def test_query_recent_videos_applies_offset_after_dedupe(tmp_path):
    db = fresh_db(tmp_path)
    db.save_videos([
        {"external_id": "a1", "title": "同标题", "categories": ["new"], "source_release_date": "2026-06-05"},
        {"external_id": "a2", "title": "同标题", "categories": ["new"], "source_release_date": "2026-06-04"},
        {"external_id": "b", "title": "标题 B", "categories": ["new"], "source_release_date": "2026-06-03"},
        {"external_id": "c", "title": "标题 C", "categories": ["new"], "source_release_date": "2026-06-02"},
        {"external_id": "d", "title": "标题 D", "categories": ["new"], "source_release_date": "2026-06-01"},
    ])

    ids = [video["id"] for video in db.query_recent_videos("new", limit=2, offset=2)]

    assert ids == ["c", "d"]


def test_search_videos_applies_offset_after_dedupe(tmp_path):
    db = fresh_db(tmp_path)
    db.save_videos([
        {"external_id": "a1", "title": "篠田 同标题", "source_release_date": "2026-06-05"},
        {"external_id": "a2", "title": "篠田 同标题", "source_release_date": "2026-06-04"},
        {"external_id": "b", "title": "篠田 标题 B", "source_release_date": "2026-06-03"},
        {"external_id": "c", "title": "篠田 标题 C", "source_release_date": "2026-06-02"},
        {"external_id": "d", "title": "篠田 标题 D", "source_release_date": "2026-06-01"},
    ])

    ids = [video["id"] for video in db.search_videos("篠田", limit=2, offset=2)]

    assert ids == ["c", "d"]


def test_list_save_does_not_mark_detail_success_or_overwrite_detail_timestamp(tmp_path):
    db = fresh_db(tmp_path)
    db.save_videos([
        {"external_id": "list-only", "title": "列表项", "categories": ["new"], "source_release_date": "2026-06-01"}
    ])
    list_only = db.query_video_by_id("list-only")

    assert list_only["detail_status"] == "pending"
    assert list_only["detail_fetched_at"] is None

    db.save_videos([
        {
            "external_id": "detail-item",
            "title": "详情项",
            "categories": ["new"],
            "detail_status": "success",
            "detail_fetched_at": "2026-06-01T00:00:00Z",
        }
    ])
    db.save_videos([
        {"external_id": "detail-item", "title": "详情项列表刷新", "categories": ["new"]}
    ])
    detail_item = db.query_video_by_id("detail-item")

    assert detail_item["detail_status"] == "success"
    assert detail_item["detail_fetched_at"] == "2026-06-01T00:00:00Z"


def test_scraper_client_imports_json_for_51cg_config_parsing():
    source = Path("app/scraper_client.py").read_text()

    assert "import json" in source
    assert "json.loads" in source
