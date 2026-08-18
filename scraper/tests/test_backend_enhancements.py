import importlib
import sys
import types


def fresh_db(tmp_path):
    from app import db
    db.DB_PATH = str(tmp_path / "enhancements.db")
    db.init_db()
    return db


def test_refresh_jobs_lifecycle_and_metrics(tmp_path):
    db = fresh_db(tmp_path)

    job_id = db.create_refresh_job("category", "new:released_at:1", source_url="https://example.test/new")
    running = db.start_refresh_job(job_id)
    finished = db.finish_refresh_job(job_id, "success", payload_count=12)

    assert running["status"] == "running"
    assert finished["status"] == "success"
    assert finished["payload_count"] == 12
    jobs = db.list_refresh_jobs(limit=5)
    assert jobs[0]["id"] == job_id
    assert jobs[0]["duration_ms"] is not None
    metrics = db.refresh_job_metrics()
    assert metrics["total"] == 1
    assert metrics["by_status"]["success"] == 1


def test_advanced_search_supports_fts_filters_facets_and_match_reason(tmp_path):
    db = fresh_db(tmp_path)
    db.save_videos([
        {"external_id": "a", "title": "篠田優 中文字幕 作品", "actors": ["篠田優"], "tags": ["中文字幕", "巨乳"], "categories": ["new"], "source_release_date": "2026-06-02"},
        {"external_id": "b", "title": "其他演员 VR", "actors": ["其他"], "tags": ["VR"], "categories": ["vr"], "source_release_date": "2026-06-01"},
    ])

    result = db.search_videos_advanced(query="篠田", actor="篠田優", tags=["中文字幕"], categories=["new"], limit=10, include_facets=True)

    assert [item["id"] for item in result["items"]] == ["a"]
    assert result["items"][0]["match_reason"] in {"title", "actor", "fulltext"}
    assert result["facets"]["actors"]["篠田優"] == 1
    assert result["facets"]["tags"]["中文字幕"] == 1


def test_user_actions_playback_recommendations_and_quality(tmp_path):
    db = fresh_db(tmp_path)
    db.save_videos([
        {"external_id": "a", "title": "A", "actors": ["篠田優"], "tags": ["中文字幕"], "categories": ["new"], "duration": "https://cdn.test/a.m3u8", "cover_url": "https://img.test/a.jpg", "detail_status": "success"},
        {"external_id": "b", "title": "B", "actors": ["篠田優"], "tags": ["中文字幕"], "categories": ["new"], "duration": "12:00", "cover_url": "", "detail_status": "pending"},
    ])

    db.set_user_video_action("default", "a", "favorite", True)
    db.record_watch_progress("default", "a", position_seconds=42, duration_seconds=120)

    assert db.list_user_video_actions("default", "favorite")[0]["video_id"] == "a"
    assert db.get_video_playback("a")["playback_url"] == "https://cdn.test/a.m3u8"
    assert [v["id"] for v in db.recommend_similar_videos("a", limit=5)] == ["b"]
    quality = db.data_quality_summary()
    assert quality["missing_cover"] == 1
    assert quality["pending_details"] == 1


def test_admin_and_feature_endpoints_use_new_backend_capabilities(tmp_path, monkeypatch):
    db = fresh_db(tmp_path)
    db.save_videos([
        {"external_id": "a", "title": "篠田優 中文字幕 作品", "actors": ["篠田優"], "tags": ["中文字幕"], "categories": ["new"], "duration": "https://cdn.test/a.m3u8", "cover_url": "https://img.test/a.jpg", "detail_status": "success"},
        {"external_id": "b", "title": "篠田優 另一部", "actors": ["篠田優"], "tags": ["VR"], "categories": ["vr"], "duration": "10:00", "detail_status": "pending"},
    ])
    fake_scraper = types.SimpleNamespace(
        start=lambda: None,
        stop=lambda: None,
        cg_domains=["https://51cg.example"],
        active_cg_domain="https://51cg.example",
    )
    fake_module = types.SimpleNamespace(scraper=fake_scraper)
    monkeypatch.setitem(sys.modules, "app.scraper_client", fake_module)
    main = importlib.import_module("app.main")
    importlib.reload(main)
    main.db.DB_PATH = db.DB_PATH

    from fastapi.testclient import TestClient
    client = TestClient(main.app)

    assert client.get("/api/v1/admin/metrics").json()["db"]["videos"] == 2
    assert "missav" in client.get("/api/v1/admin/source-status").json()
    search = client.post("/api/v1/search", json={"q": "篠田", "actor": "篠田優", "limit": 5}).json()
    assert search["count"] >= 1
    playback = client.get("/api/v1/videos/a/playback").json()
    assert playback["playback_url"] == "https://cdn.test/a.m3u8"
    recs = client.get("/api/v1/videos/a/recommendations?limit=5").json()
    assert recs["items"][0]["id"] == "b"
    quality = client.get("/api/v1/admin/data-quality").json()
    assert quality["pending_details"] == 1
