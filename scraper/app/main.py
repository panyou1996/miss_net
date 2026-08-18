import asyncio
import json
import logging
import os
import re
import time
import urllib.parse
import urllib.request
from typing import List, Dict, Any, Optional
from fastapi import FastAPI, Request, Query, Header, HTTPException
from fastapi.responses import JSONResponse, Response, HTMLResponse
from pydantic import BaseModel

from . import db
from .scraper_client import scraper

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("backend_main")

app = FastAPI(title="MissNet PostgREST Facade", version="1.0.0")
refreshing_categories: set[str] = set()
refreshing_actors: set[str] = set()
refreshing_details: set[str] = set()
refreshing_directories: set[str] = set()
refreshing_searches: set[str] = set()
def _env_int(name: str, default: int, minimum: int, maximum: int) -> int:
    try:
        value = int(os.environ.get(name, str(default)))
    except ValueError:
        value = default
    return max(minimum, min(value, maximum))


RPC_CACHE_TTL_SECONDS = 30
NEGATIVE_CACHE_TTL_SECONDS = _env_int("MISSNET_NEGATIVE_CACHE_TTL_SECONDS", 300, 30, 3600)
MAX_LIST_LIMIT = _env_int("MISSNET_MAX_LIST_LIMIT", 100, 20, 500)
MAX_DIRECTORY_LIMIT = _env_int("MISSNET_MAX_DIRECTORY_LIMIT", 360, 24, 500)
MAX_OFFSET = _env_int("MISSNET_MAX_OFFSET", 5000, 100, 50000)
MAX_QUERY_LENGTH = _env_int("MISSNET_MAX_QUERY_LENGTH", 120, 20, 300)
FILTERED_ACTOR_INITIAL_LIMIT = _env_int("MISSNET_FILTERED_ACTOR_INITIAL_LIMIT", 72, 24, 240)
MISSAV_SOURCE_PAGE_SIZE = _env_int("MISSNET_MISSAV_SOURCE_PAGE_SIZE", 12, 6, 60)
CG_SOURCE_PAGE_SIZE = _env_int("MISSNET_51CG_SOURCE_PAGE_SIZE", 20, 6, 60)
ADMIN_TOKEN = os.environ.get("MISSNET_ADMIN_TOKEN", "").strip()
# Known-dead 51cg CDN hosts (404 or timeout confirmed).
# Live hosts as of 2026-05-31: pic.uoupfrl.cn, pic.sgytlkqm.cn
# Add new dead hosts to MISSNET_STALE_51CG_COVER_HOSTS env var without redeploying.
_DEFAULT_STALE_HOSTS = (
    "pic.jphjxk.cn,pic.fcyfzk.cn,pic.esljpt.cn,"
    "pic.zdpxxq.cn,pic.hbccndd.cn,pic.pbgkny.cn"
)
STALE_51CG_COVER_HOSTS: set[str] = {
    host.strip().lower()
    for host in os.environ.get("MISSNET_STALE_51CG_COVER_HOSTS", _DEFAULT_STALE_HOSTS).split(",")
    if host.strip()
}
home_payload_cache: dict[tuple[int, int], tuple[float, List[Dict[str, Any]]]] = {}
actor_aggregate_cache: dict[Any, tuple[float, List[Dict[str, Any]]]] = {}
tag_aggregate_cache: dict[int, tuple[float, List[Dict[str, Any]]]] = {}

CATEGORY_FALLBACK_ALIASES: dict[str, list[str]] = {
    "big_tits": ["巨乳", "巨乳作品"],
    "chinese_subtitle": ["subtitled", "chinese-subtitle", "中文字幕", "字幕", "中文"],
    "chinese-subtitle": ["subtitled", "chinese_subtitle", "中文字幕", "字幕", "中文"],
    "subtitled": ["chinese_subtitle", "chinese-subtitle", "中文字幕", "字幕", "中文"],
    "creampie": ["中出", "中出作品"],
    "exclusive": ["独家", "獨家", "独占", "獨佔"],
    "mature": ["熟女", "人妻", "少妇"],
    "single": ["单体作品", "單體作品", "单体", "單體"],
    "uncensored": ["uncensored-leak", "无码破解", "无码流出", "無碼", "无码", "破解", "流出"],
    "uncensored-leak": ["uncensored", "无码破解", "无码流出", "無碼", "无码", "破解", "流出"],
    "vr": ["VR", "vr作品"],
    "monthly_hot": ["monthly-hot", "monthly_views", "本月热播", "本月热门", "月榜"],
    "monthly-hot": ["monthly_hot", "monthly_views", "本月热播", "本月热门", "月榜"],
    "weekly_hot": ["weekly-hot", "weekly_views", "本周热播", "本周热门", "周榜"],
    "weekly-hot": ["weekly_hot", "weekly_views", "本周热播", "本周热门", "周榜"],
    "today_hot": ["today-hot", "今日热播", "今日热门", "日榜"],
    "today-hot": ["today_hot", "今日热播", "今日热门", "日榜"],
    "new": ["release", "最新", "新作"],
    "release": ["new", "最新", "新作"],
}


def clamp_int(value: Any, default: int, minimum: int, maximum: int) -> int:
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        parsed = default
    return max(minimum, min(parsed, maximum))


def clamp_limit(value: Any, default: int = 20, maximum: int = MAX_LIST_LIMIT) -> int:
    return clamp_int(value, default, 1, maximum)


def clamp_offset(value: Any, default: int = 0) -> int:
    return clamp_int(value, default, 0, MAX_OFFSET)


def normalize_text(value: Any, maximum: int = MAX_QUERY_LENGTH) -> str:
    return str(value or "").strip()[:maximum]


def normalize_video_sort(value: Any) -> str:
    normalized = normalize_text(value or "released_at", maximum=32).lower()
    return normalized if normalized in {"released_at", "views"} else "released_at"


def normalize_actress_sort(value: Any) -> str:
    normalized = normalize_text(value or "videos", maximum=32).lower()
    return normalized if normalized in {"videos", "debut"} else "videos"


def actor_directory_filters(body: Dict[str, Any]) -> Dict[str, str]:
    filters: dict[str, str] = {}
    for key in ("height", "cup", "age", "debut"):
        value = normalize_text(body.get(key), maximum=32)
        if value:
            filters[key] = value
    return filters


def actor_directory_rows(entries: List[Dict[str, Any]], limit: int) -> List[Dict[str, Any]]:
    return [
        {
            "actor": entry.get("name", ""),
            "cover_url": entry.get("cover_url"),
            "video_count": int(entry.get("video_count") or 0),
            "latest_release_date": entry.get("latest_release_date"),
        }
        for entry in entries[:limit]
        if entry.get("name")
    ]


def postgrest_sort_from_order(order: str | None) -> str:
    normalized = normalize_text(order, maximum=160).lower()
    if any(token in normalized for token in ("watched_count", "views", "view_count")):
        return "views"
    return "released_at"


def parse_postgrest_array_filter(value: str) -> str | None:
    if not value:
        return None
    match = re.search(r"(?:cs|contains)\.\{([^}]+)\}", value)
    if match:
        return normalize_text(urllib.parse.unquote(match.group(1)))
    match = re.search(r"cs\.\[([^\]]+)\]", value)
    if match:
        return normalize_text(urllib.parse.unquote(match.group(1)).strip('"'))
    return None


def parse_postgrest_or_filters(value: str) -> Dict[str, str]:
    filters: dict[str, str] = {}
    if not value:
        return filters
    inner = value.strip()
    if inner.startswith("(") and inner.endswith(")"):
        inner = inner[1:-1]
    for clause in inner.split(","):
        clause = clause.strip()
        for field in ("actors", "tags", "categories"):
            prefix = f"{field}."
            if clause.startswith(prefix):
                parsed = parse_postgrest_array_filter(clause[len(prefix):])
                if parsed:
                    filters[field] = parsed
    return filters


def fallback_category_queries(category: str) -> list[str]:
    queries: list[str] = []
    normalized_category = normalize_text(category).casefold()

    def add(value: str | None) -> None:
        cleaned = normalize_text(value)
        if cleaned and cleaned not in queries:
            queries.append(cleaned)

    add(category)

    for key, aliases in CATEGORY_FALLBACK_ALIASES.items():
        alias_group = [key, *aliases]
        if any(normalize_text(alias).casefold() == normalized_category for alias in alias_group):
            add(key)
            for alias in alias_group:
                add(alias)

    for alias in CATEGORY_FALLBACK_ALIASES.get(category, []):
        add(alias)
    entry = db.query_directory_entry("genre", category)
    if entry and entry.get("source_url"):
        path = urllib.parse.urlparse(str(entry["source_url"])).path
        slug = urllib.parse.unquote(path.rstrip("/").split("/")[-1])
        add(slug)
    return queries


def merge_video_results(primary: List[Dict[str, Any]], fallback: List[Dict[str, Any]], limit: int) -> List[Dict[str, Any]]:
    merged: list[dict[str, Any]] = []
    seen: set[str] = set()

    def identity(video: Dict[str, Any]) -> str:
        return str(
            video.get("id")
            or video.get("external_id")
            or video.get("source_url")
            or video.get("title")
            or ""
        )

    for video in [*primary, *fallback]:
        key = identity(video)
        if not key or key in seen:
            continue
        seen.add(key)
        merged.append(video)
        if len(merged) >= limit:
            break
    return merged


def local_category_fallback(category: str, limit: int, offset: int, sort_by: str = "released_at") -> List[Dict[str, Any]]:
    queries = fallback_category_queries(category)
    minimum_rows = min(limit, 6)
    merged: list[dict[str, Any]] = []
    for query in queries:
        videos = db.query_recent_videos(query, limit, offset, sort_by=sort_by)
        if videos:
            merged = merge_video_results(merged, videos, limit)
            logger.info(
                "Category local fallback hit query=%s for category=%s rows=%s merged=%s",
                query,
                category,
                len(videos),
                len(merged),
            )
            if len(merged) >= minimum_rows:
                return merged
    for query in queries:
        videos = db.search_videos(query, limit, offset)
        if videos:
            merged = merge_video_results(merged, videos, limit)
            logger.info(
                "Category search fallback hit query=%s for category=%s rows=%s merged=%s",
                query,
                category,
                len(videos),
                len(merged),
            )
            if len(merged) >= minimum_rows:
                return merged
    return merged


def local_actor_fallback(actor: str, limit: int, offset: int, sort_by: str = "released_at") -> List[Dict[str, Any]]:
    videos = db.query_videos_by_actor(actor, limit, offset, sort_by=sort_by)
    if videos:
        return videos
    videos = db.search_videos(actor, limit, offset)
    if videos:
        logger.info("Actor search fallback hit actor=%s", actor)
    return videos


def cover_host(url: str | None) -> str:
    try:
        return urllib.parse.urlparse(url or "").hostname or ""
    except Exception:
        return ""


def is_51cg_video(video: Dict[str, Any]) -> bool:
    video_id = str(video.get("id") or video.get("external_id") or "")
    source_url = str(video.get("source_url") or "")
    tags = video.get("tags") or []
    categories = video.get("categories") or []
    return (
        video_id.startswith("51cg_")
        or "51cg" in source_url.lower()
        or "51cg" in [str(tag).lower() for tag in tags]
        or "51cg" in [str(category).lower() for category in categories]
    )


def _51cg_cover_missing(video: Dict[str, Any]) -> bool:
    """Return True only if a 51cg video has a genuinely unusable cover."""
    cover_url = str(video.get("cover_url") or "").strip()
    if not cover_url or cover_url.startswith("data:image"):
        return True
    # If operator has explicitly configured stale hosts, honour them.
    if STALE_51CG_COVER_HOSTS and cover_host(cover_url).lower() in STALE_51CG_COVER_HOSTS:
        return True
    return False


def has_stale_51cg_covers(videos: List[Dict[str, Any]]) -> bool:
    return any(
        _51cg_cover_missing(video)
        for video in videos
        if is_51cg_video(video)
    )


def remove_stale_51cg_covers(videos: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    return [
        video for video in videos
        if not is_51cg_video(video) or not _51cg_cover_missing(video)
    ]


def query_51cg_videos(limit: int, offset: int, sort_by: str = "released_at") -> List[Dict[str, Any]]:
    # Fetch from the start before filtering stale covers. 51cg has many rows with
    # identical source dates and some stale CDN hosts; capping this at
    # MAX_LIST_LIMIT made later windows short or duplicate-heavy after filtering.
    fetch_limit = min(MAX_OFFSET, max(offset + limit * 8, limit * 8, 80))
    candidates = db.query_recent_videos("51cg", fetch_limit, 0, sort_by=sort_by)
    fresh = remove_stale_51cg_covers(candidates)
    return fresh[offset:offset + limit]


def source_page_for_offset(category: str, offset: int) -> int:
    page_size = CG_SOURCE_PAGE_SIZE if "51cg" in normalize_text(category).casefold() else MISSAV_SOURCE_PAGE_SIZE
    return (max(offset, 0) // max(page_size, 1)) + 1


def has_stale_51cg_home_rows(rows: List[Dict[str, Any]]) -> bool:
    return has_stale_51cg_covers(
        [
            {
                "id": row.get("id"),
                "external_id": row.get("external_id"),
                "source_url": row.get("source_url"),
                "cover_url": row.get("cover_url"),
                "tags": ["51cg"] if row.get("section") == "51cg" else row.get("tags", []),
                "categories": ["51cg"] if row.get("section") == "51cg" else row.get("categories", []),
            }
            for row in rows
        ]
    )


def first_present(mapping: Dict[str, Any], *keys: str, default: Any = None) -> Any:
    for key in keys:
        if key in mapping and mapping[key] is not None:
            return mapping[key]
    return default


def refresh_backoff_active(kind: str, *parts: Any) -> bool:
    key = ":".join([kind, *[str(part) for part in parts]])
    return (
        db.is_cache_valid(f"empty:{key}", NEGATIVE_CACHE_TTL_SECONDS)
        or db.is_cache_valid(f"error:{key}", NEGATIVE_CACHE_TTL_SECONDS)
    )


def require_admin_token(request: Request) -> None:
    if not ADMIN_TOKEN:
        raise HTTPException(status_code=403, detail="Write endpoints are disabled")
    token = request.headers.get("x-missnet-admin-token") or request.headers.get("authorization", "")
    if token.startswith("Bearer "):
        token = token[7:]
    if token != ADMIN_TOKEN:
        raise HTTPException(status_code=403, detail="Invalid admin token")

def get_cached_response(cache: dict, key):
    entry = cache.get(key)
    if entry is None:
        return None
    created_at, payload = entry
    if time.monotonic() - created_at > RPC_CACHE_TTL_SECONDS:
        cache.pop(key, None)
        return None
    return payload

def set_cached_response(cache: dict, key, payload):
    cache[key] = (time.monotonic(), payload)

def invalidate_rpc_caches():
    home_payload_cache.clear()
    actor_aggregate_cache.clear()
    tag_aggregate_cache.clear()


def _cache_status_entry(status: str, timestamp: int) -> Dict[str, Any]:
    age = max(0, int(time.time()) - int(timestamp)) if timestamp else None
    return {
        "status": status,
        "last_updated": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(timestamp)) if timestamp else None,
        "age_seconds": age,
    }


def refresh_status_for_categories(categories: List[str], sort_key: str = "source_default", page: int = 1) -> Dict[str, Dict[str, Any]]:
    """Expose source refresh success/error/empty state without changing row payload shape."""
    status: dict[str, dict[str, Any]] = {}
    for category in categories:
        keys = [
            ("error", f"error:category:{category}:{sort_key}:{page}"),
            ("empty", f"empty:category:{category}:{sort_key}:{page}"),
            ("success", f"category:{category}:{sort_key}:{page}"),
        ]
        newest_status = "unknown"
        newest_ts = 0
        for candidate_status, key in keys:
            ts = db.get_cache_time(key)
            if ts >= newest_ts and ts > 0:
                newest_status = candidate_status
                newest_ts = ts
        status[category] = _cache_status_entry(newest_status, newest_ts)
    return status


def response_with_refresh_status(payload: Any, status: Dict[str, Any]) -> JSONResponse:
    headers = {"X-Missnet-Refresh-Status": json.dumps(status, ensure_ascii=False, separators=(",", ":"))}
    return JSONResponse(content=payload, headers=headers)

def hydrate_live_videos(videos: List[Dict[str, Any]], limit: int) -> List[Dict[str, Any]]:
    hydrated: List[Dict[str, Any]] = []
    for video in videos[:limit]:
        video_id = video.get("id") or video.get("external_id")
        stored = db.query_video_by_id(video_id) if video_id else None
        if stored:
            hydrated.append(stored)
            if needs_detail_enrichment(stored):
                schedule_video_detail_refresh(video_id, resolve_detail_source_url(video_id, stored))
        elif video_id:
            live_video = {**video, "id": video_id}
            hydrated.append(live_video)
            if needs_detail_enrichment(live_video):
                schedule_video_detail_refresh(video_id, resolve_detail_source_url(video_id, live_video))
        else:
            hydrated.append(video)
    return db.dedupe_video_results(hydrated, limit, prefer_title=True)

# Lifespan context to manage Playwright browser
@app.on_event("startup")
async def startup_event():
    db.init_db()
    # Start Playwright in the background
    asyncio.create_task(scraper.start())

@app.on_event("shutdown")
async def shutdown_event():
    await scraper.stop()


@app.middleware("http")
async def log_request_timing(request: Request, call_next):
    started_at = time.perf_counter()
    response = await call_next(request)
    duration_ms = (time.perf_counter() - started_at) * 1000
    response.headers["X-Process-Time-Ms"] = f"{duration_ms:.1f}"
    if duration_ms >= 1500:
        logger.warning(
            "Slow request path=%s status=%s duration_ms=%.1f",
            request.url.path,
            response.status_code,
            duration_ms,
        )
    return response


@app.get("/healthz")
async def healthz():
    try:
        db.query_recent_videos("new", 1, 0)
        db_ok = True
    except Exception:
        logger.exception("Health check DB probe failed")
        db_ok = False
    status = {
        "status": "ok" if db_ok else "degraded",
        "db": "ok" if db_ok else "error",
        "refreshing": {
            "categories": len(refreshing_categories),
            "actors": len(refreshing_actors),
            "details": len(refreshing_details),
            "directories": len(refreshing_directories),
            "searches": len(refreshing_searches),
        },
        "refresh_status": refresh_status_for_categories(["new", "monthly_hot", "weekly_hot", "uncensored", "subtitled", "vr", "51cg"], sort_key="released_at"),
    }
    return JSONResponse(content=status, status_code=200 if db_ok else 503)


@app.get("/api/v1/refresh-status")
async def get_refresh_status(categories: str = Query("new,monthly_hot,weekly_hot,uncensored,subtitled,vr,51cg"), sort_key: str = Query("released_at"), page: int = Query(1)):
    category_list = [normalize_text(cat, maximum=64) for cat in categories.split(",") if normalize_text(cat, maximum=64)]
    return JSONResponse(content=refresh_status_for_categories(category_list, normalize_text(sort_key, maximum=64), max(1, int(page or 1))))

def schedule_category_refresh(category: str, page: int = 1, sort_by: str | None = None):
    page = max(1, int(page or 1))
    sort_key = normalize_video_sort(sort_by) if sort_by is not None else "source_default"
    if refresh_backoff_active("category", category, sort_key, page):
        logger.info("Skipping category refresh during backoff: category=%s sort=%s page=%s", category, sort_key, page)
        return None
    refresh_key = f"{category}:{sort_key}:{page}"
    if refresh_key in refreshing_categories:
        return None
    refreshing_categories.add(refresh_key)
    job_id = db.create_refresh_job("category", refresh_key)

    async def refresh():
        db.start_refresh_job(job_id)
        try:
            videos = await scraper.scrape_category(category, page=page, sort_by=sort_by)
            if videos:
                invalidate_rpc_caches()
                db.finish_refresh_job(job_id, "success", payload_count=len(videos))
            else:
                db.finish_refresh_job(job_id, "empty", payload_count=0)
            return videos
        except Exception as exc:
            db.finish_refresh_job(job_id, "error", error=str(exc)[:1000])
            logger.exception("Background category refresh failed: category=%s sort=%s page=%s", category, sort_key, page)
            return []
        finally:
            refreshing_categories.discard(refresh_key)

    return asyncio.create_task(refresh())

def schedule_actor_refresh(actor: str, page: int = 1, sort_by: str = "released_at"):
    normalized = actor.strip()
    if not normalized:
        return None
    page = max(1, int(page or 1))
    sort_by = normalize_video_sort(sort_by)
    if refresh_backoff_active("actor", normalized, sort_by, page):
        logger.info("Skipping actor refresh during backoff: actor=%s sort=%s page=%s", normalized, sort_by, page)
        return None
    refresh_key = f"{normalized}:{sort_by}:{page}"
    if refresh_key in refreshing_actors:
        return None
    refreshing_actors.add(refresh_key)
    job_id = db.create_refresh_job("actor", refresh_key)

    async def refresh():
        db.start_refresh_job(job_id)
        try:
            videos = await scraper.scrape_actor_videos(normalized, page=page, sort_by=sort_by)
            if videos:
                invalidate_rpc_caches()
                db.finish_refresh_job(job_id, "success", payload_count=len(videos))
            else:
                db.finish_refresh_job(job_id, "empty", payload_count=0)
            return videos
        except Exception as exc:
            db.finish_refresh_job(job_id, "error", error=str(exc)[:1000])
            logger.exception("Background actor refresh failed: actor=%s sort=%s page=%s", normalized, sort_by, page)
            return []
        finally:
            refreshing_actors.discard(refresh_key)

    return asyncio.create_task(refresh())

def schedule_actor_directory_refresh(limit: int = 100):
    target = max(24, min(limit, 360))
    if refresh_backoff_active("directory", "actor"):
        logger.info("Skipping actor directory refresh during backoff")
        return None
    if "actor" in refreshing_directories:
        return None
    refreshing_directories.add("actor")
    job_id = db.create_refresh_job("directory", "actor")

    async def refresh():
        db.start_refresh_job(job_id)
        try:
            rows = await asyncio.wait_for(scraper.scrape_actor_directory(limit=target), timeout=45)
            invalidate_rpc_caches()
            db.finish_refresh_job(job_id, "success" if rows else "empty", payload_count=len(rows or []))
            return rows
        except Exception as exc:
            db.finish_refresh_job(job_id, "error", error=str(exc)[:1000])
            logger.exception("Actor directory refresh failed")
            return []
        finally:
            refreshing_directories.discard("actor")

    return asyncio.create_task(refresh())


def schedule_filtered_actor_directory_refresh(
    cache_key: Any,
    limit: int,
    filters: Dict[str, str],
    sort_by: str,
):
    target = max(24, min(limit, MAX_DIRECTORY_LIMIT))
    refresh_key = f"actor_filtered:{cache_key}"
    if refresh_key in refreshing_directories:
        return None
    refreshing_directories.add(refresh_key)
    job_id = db.create_refresh_job("directory", refresh_key)

    async def refresh():
        db.start_refresh_job(job_id)
        try:
            entries = await scraper.scrape_actor_directory(
                limit=target,
                filters=filters,
                sort_by=sort_by,
                persist=True,
            )
            rows = actor_directory_rows(entries, target)
            if rows:
                set_cached_response(actor_aggregate_cache, cache_key, rows)
            db.finish_refresh_job(job_id, "success" if rows else "empty", payload_count=len(rows or []))
            return rows
        except Exception as exc:
            db.finish_refresh_job(job_id, "error", error=str(exc)[:1000])
            logger.exception(
                "Filtered actor directory refresh failed: limit=%s sort=%s filters=%s",
                target,
                sort_by,
                filters,
            )
            return []
        finally:
            refreshing_directories.discard(refresh_key)

    return asyncio.create_task(refresh())


def schedule_genre_directory_refresh():
    if refresh_backoff_active("directory", "genre"):
        logger.info("Skipping genre directory refresh during backoff")
        return None
    if "genre" in refreshing_directories:
        return None
    refreshing_directories.add("genre")
    job_id = db.create_refresh_job("directory", "genre")

    async def refresh():
        db.start_refresh_job(job_id)
        try:
            rows = await asyncio.wait_for(scraper.scrape_genre_directory(), timeout=25)
            invalidate_rpc_caches()
            db.finish_refresh_job(job_id, "success" if rows else "empty", payload_count=len(rows or []))
            return rows
        except Exception as exc:
            db.finish_refresh_job(job_id, "error", error=str(exc)[:1000])
            logger.exception("Genre directory refresh failed")
            return []
        finally:
            refreshing_directories.discard("genre")

    return asyncio.create_task(refresh())

async def wait_for_background_tasks(tasks, timeout_seconds: float, label: str):
    active_tasks = {task for task in tasks if task is not None}
    if not active_tasks or timeout_seconds <= 0:
        return
    done, pending = await asyncio.wait(active_tasks, timeout=timeout_seconds)
    if pending:
        logger.info("%s returned while %s refresh task(s) continue in background", label, len(pending))
    for task in done:
        if task.cancelled():
            continue
        exc = task.exception()
        if exc:
            logger.error("%s refresh failed", label, exc_info=(type(exc), exc, exc.__traceback__))

async def wait_for_task_result(task, timeout_seconds: float, label: str):
    if task is None or timeout_seconds <= 0:
        return None
    done, pending = await asyncio.wait({task}, timeout=timeout_seconds)
    if pending:
        logger.info("%s returned while refresh continues in background", label)
        return None
    finished = next(iter(done))
    if finished.cancelled():
        return None
    exc = finished.exception()
    if exc:
        logger.error("%s refresh failed", label, exc_info=(type(exc), exc, exc.__traceback__))
        return None
    return finished.result()


async def wait_for_refresh_result(task, timeout_seconds: float, label: str) -> List[Dict[str, Any]]:
    result = await wait_for_task_result(task, timeout_seconds, label)
    return result or []

def schedule_search_refresh(query: str, page: int = 1):
    normalized = query.strip().lower()
    if len(normalized) < 2:
        return None
    page = max(1, int(page or 1))
    refresh_key = f"{normalized}:{page}"
    if refresh_backoff_active("search", normalized, page):
        logger.info("Skipping search refresh during backoff: query=%s page=%s", normalized, page)
        return None
    if refresh_key in refreshing_searches:
        return None
    refreshing_searches.add(refresh_key)
    job_id = db.create_refresh_job("search", refresh_key)

    async def refresh():
        db.start_refresh_job(job_id)
        try:
            videos = await scraper.scrape_search(query, page=page)
            if videos:
                invalidate_rpc_caches()
            db.finish_refresh_job(job_id, "success" if videos else "empty", payload_count=len(videos or []))
            return videos
        except Exception as exc:
            db.finish_refresh_job(job_id, "error", error=str(exc)[:1000])
            logger.exception("Background search refresh failed: query=%s page=%s", query, page)
            return []
        finally:
            refreshing_searches.discard(refresh_key)

    return asyncio.create_task(refresh())

def resolve_detail_source_url(video_id: str, video: dict | None = None) -> str:
    source_url = (video or {}).get("source_url") or ""
    if source_url:
        # Strip the hash index for detail scraping requests
        if "#video-" in source_url:
            return source_url.split("#video-")[0]
        return source_url
    if "51cg_" in video_id:
        raw_id = video_id.replace("51cg_", "")
        # Handle suffix for secondary videos (e.g., 51cg_251299_2 -> 251299)
        article_id = raw_id.split('_')[0]
        return f"https://51cgm43.com/archives/{article_id}/"
    return f"https://missav.ws/cn/{video_id}"

def needs_detail_enrichment(video: dict | None) -> bool:
    if not video:
        return True
    video_id = video.get("id") or video.get("external_id") or ""
    tags = video.get("tags") or []
    # 51cg videos do not have listed actresses/actors, so we skip the actor check for them
    is_cg = "51cg" in video_id or any("51cg" in str(t).lower() for t in tags)
    if is_cg:
        duration = video.get("duration")
        if not duration or not duration.startswith("http"):
            return True
        fetched_at = video.get("detail_fetched_at")
        if fetched_at:
            try:
                import datetime
                dt = datetime.datetime.strptime(fetched_at.rstrip('Z'), "%Y-%m-%dT%H:%M:%S")
                now = datetime.datetime.utcnow()
                if (now - dt).total_seconds() > 3 * 3600:  # 3 hours expiration
                    return True
            except Exception:
                return True
        else:
            return True
        return len(tags) <= 1 or not video.get("source_release_date")
    return not video.get("actors") or len(tags) <= 1 or not video.get("source_release_date")

def schedule_video_detail_refresh(video_id: str, source_url: str, force_refresh: bool = False):
    if not source_url:
        return None
    # Use base_video_id to handle detail request synchronization for multiple sub-videos
    base_video_id = video_id
    if "51cg_" in video_id:
        parts = video_id.split('_')
        base_video_id = parts[0] + "_" + parts[1]

    if not force_refresh and db.is_cache_valid(f"detail:{base_video_id}", 3 * 3600):
        return None
    if not force_refresh and refresh_backoff_active("detail", base_video_id):
        logger.info("Skipping detail refresh during backoff: video_id=%s", base_video_id)
        return None
    if base_video_id in refreshing_details:
        return None
    refreshing_details.add(base_video_id)
    job_id = db.create_refresh_job("detail", base_video_id, source_url=source_url)

    async def refresh():
        db.start_refresh_job(job_id)
        try:
            result = await scraper.scrape_detail(video_id, source_url)
            if result:
                db.update_cache_time(f"detail:{base_video_id}")
                db.finish_refresh_job(job_id, "success", payload_count=1)
            else:
                db.update_cache_time(f"empty:detail:{base_video_id}")
                db.finish_refresh_job(job_id, "empty", payload_count=0)
            return result
        except Exception as exc:
            db.finish_refresh_job(job_id, "error", error=str(exc)[:1000])
            logger.exception("Background detail refresh failed: video_id=%s", video_id)
            return None
        finally:
            refreshing_details.discard(base_video_id)

    return asyncio.create_task(refresh())

async def seed_actor_details(limit: int = 8, wait_seconds: int = 12):
    videos = db.query_videos_missing_actors(limit)
    tasks = []
    for video in videos:
        task = schedule_video_detail_refresh(video["id"], resolve_detail_source_url(video["id"], video))
        if task is not None:
            tasks.append(task)
    if tasks:
        done, pending = await asyncio.wait(tasks, timeout=wait_seconds)
        if pending:
            logger.info("Actor detail seed returned while %s detail task(s) continue in background", len(pending))
        for task in done:
            if task.cancelled():
                continue
            exc = task.exception()
            if exc:
                logger.error("Actor detail seed failed", exc_info=(type(exc), exc, exc.__traceback__))

async def ensure_actor_directory(limit: int = 100, wait_seconds: float = 6.0):
    target = max(24, min(limit, 360))
    if db.is_cache_valid("directory:actor", 24 * 3600) and db.count_directory_entries("actor") >= target:
        return
    task = schedule_actor_directory_refresh(limit=target)
    if task is not None:
        await wait_for_background_tasks([task], wait_seconds, "Actor directory")

async def ensure_genre_directory(wait_seconds: float = 5.0):
    if db.is_cache_valid("directory:genre", 24 * 3600) and db.count_directory_entries("genre") > 0:
        return
    task = schedule_genre_directory_refresh()
    if task is not None:
        await wait_for_background_tasks([task], wait_seconds, "Genre directory")

async def ensure_category_videos(category: str, limit: int, offset: int, sort_by: str = "released_at", wait_seconds: float = 5.0):
    if not category:
        return
    target_count = max(limit, offset + limit)
    sort_by = normalize_video_sort(sort_by)
    page_size = CG_SOURCE_PAGE_SIZE if "51cg" in normalize_text(category).casefold() else MISSAV_SOURCE_PAGE_SIZE
    start_page = max(1, (offset // page_size) + 1)
    end_page = max(start_page, ((offset + limit - 1) // page_size) + 1)
    max_page = min(end_page + 1, 50)

    current_count = len(db.query_recent_videos(category, target_count, 0, sort_by=sort_by))
    if current_count >= target_count:
        for page in range(start_page, max_page + 1):
            if not db.is_cache_valid(f"category:{category}:{sort_by}:{page}", 1800):
                schedule_category_refresh(category, page=page, sort_by=sort_by)
        return

    tasks = []
    for page in range(start_page, max_page + 1):
        if not db.is_cache_valid(f"category:{category}:{sort_by}:{page}", 1800):
            task = schedule_category_refresh(category, page=page, sort_by=sort_by)
            if task is not None:
                tasks.append(task)
    if tasks:
        await wait_for_background_tasks(tasks, wait_seconds, f"Category {category} page refresh")

async def ensure_actor_videos(actor: str, limit: int, offset: int, sort_by: str = "released_at", force_refresh: bool = False, wait_seconds: float = 5.0):
    if not actor:
        return
    target_count = max(limit, offset + limit)
    page_size = MISSAV_SOURCE_PAGE_SIZE
    start_page = max(1, (offset // page_size) + 1)
    end_page = max(start_page, ((offset + limit - 1) // page_size) + 1)
    sort_by = normalize_video_sort(sort_by)

    current_count = len(db.query_videos_by_actor(actor, target_count, 0, sort_by=sort_by))
    if not force_refresh and current_count >= target_count:
        for page in range(start_page, end_page + 1):
            if not db.is_cache_valid(f"actor:{actor}:{sort_by}:{page}", 1800):
                schedule_actor_refresh(actor, page=page, sort_by=sort_by)
        return

    tasks = []
    for page in range(start_page, min(end_page + 1, 50) + 1):
        page_cache_valid = db.is_cache_valid(f"actor:{actor}:{sort_by}:{page}", 1800)
        if force_refresh or not page_cache_valid:
            task = schedule_actor_refresh(actor, page=page, sort_by=sort_by)
            if task is not None:
                tasks.append(task)
    if tasks:
        await wait_for_background_tasks(tasks, wait_seconds, f"Actor {actor} page refresh")

# --- GoTrue Auth Mock Endpoints ---
@app.get("/auth/v1/user")
async def get_auth_user(request: Request):
    logger.info("Auth user check requested")
    # Return dummy logged-in user to prevent app authentication crashes
    dummy_user = {
        "id": "00000000-0000-0000-0000-000000000000",
        "aud": "authenticated",
        "role": "authenticated",
        "email": "user@example.com",
        "email_confirmed_at": "2026-05-23T10:00:00Z",
        "phone": "",
        "confirmed_at": "2026-05-23T10:00:00Z",
        "last_sign_in_at": "2026-05-23T10:00:00Z",
        "app_metadata": {"provider": "email", "providers": ["email"]},
        "user_metadata": {},
        "identities": [],
        "created_at": "2026-05-23T10:00:00Z",
        "updated_at": "2026-05-23T10:00:00Z"
    }
    return JSONResponse(content=dummy_user)

@app.post("/auth/v1/logout")
async def auth_logout():
    logger.info("Auth logout requested")
    return JSONResponse(status_code=204, content=None)

@app.post("/auth/v1/token")
async def auth_token():
    logger.info("Auth token requested")
    return JSONResponse(content={
        "access_token": "dummy_token",
        "token_type": "bearer",
        "expires_in": 3600,
        "refresh_token": "dummy_refresh_token",
        "user": {
            "id": "00000000-0000-0000-0000-000000000000",
            "email": "user@example.com"
        }
    })

# --- MissNet API Read Interface ---
@app.get("/api/v1/videos")
@app.get("/rest/v1/videos")
async def get_videos(request: Request):
    params = dict(request.query_params)
    logger.info(f"PostgREST query parameters: {params}")

    # 1. Parse limit and offset
    limit = 20
    offset = 0
    range_header = request.headers.get("Range") or request.headers.get("range")
    if range_header:
        import re
        m = re.match(r"(\d+)-(\d+)", range_header)
        if m:
            offset = int(m.group(1))
            limit = int(m.group(2)) - offset + 1

    if "limit" in params:
        try:
            limit = int(params["limit"])
        except ValueError:
            pass
    if "offset" in params:
        try:
            offset = int(params["offset"])
        except ValueError:
            pass
    limit = clamp_limit(limit)
    offset = clamp_offset(offset)
    sort_by = normalize_video_sort(params["sort_by"]) if "sort_by" in params else postgrest_sort_from_order(params.get("order"))

    # 2. Check for ID filter (e.g. id=eq.dldss-495)
    id_val = None
    for key, val in params.items():
        if key == "id":
            id_val = val[3:] if val.startswith("eq.") else val
            break

    if id_val:
        id_val = normalize_text(id_val)
        video = db.query_video_by_id(id_val)
        if not video:
            source_url = resolve_detail_source_url(id_val)
            task = schedule_video_detail_refresh(id_val, source_url)
            video = await wait_for_task_result(task, 4, f"Detail {id_val}") or db.query_video_by_id(id_val)
        elif needs_detail_enrichment(video):
            source_url = resolve_detail_source_url(id_val, video)
            task = schedule_video_detail_refresh(id_val, source_url)
            if task is not None:
                logger.info("Scheduled background detail refresh: video_id=%s", id_val)
            video = await wait_for_task_result(task, 4, f"Detail enrichment {id_val}") or db.query_video_by_id(id_val) or video
        return JSONResponse(content=[video] if video else [])

    # 3. Parse actor or category filters
    # PostgREST uses cs.{val} or contains.{val} for array contains
    actor_filter = None
    category_filter = None
    tag_filter = None

    for key, val in params.items():
        if key in ("actor", "actor_name", "actor_text"):
            actor_filter = normalize_text(val) or actor_filter
        elif key in ("category", "category_name", "category_text"):
            category_filter = normalize_text(val) or category_filter
        elif key in ("tag", "tag_text"):
            tag_filter = normalize_text(val) or tag_filter
        elif key == "actors":
            actor_filter = parse_postgrest_array_filter(val) or actor_filter
        elif key == "tags":
            tag_filter = parse_postgrest_array_filter(val) or tag_filter
        elif key == "categories":
            category_filter = parse_postgrest_array_filter(val) or category_filter
        elif key == "or":
            or_filters = parse_postgrest_or_filters(val)
            actor_filter = or_filters.get("actors") or actor_filter
            tag_filter = or_filters.get("tags") or tag_filter
            category_filter = or_filters.get("categories") or category_filter

    if actor_filter:
        actor_filter = normalize_text(actor_filter)
        page = source_page_for_offset(actor_filter, offset)
        videos = db.query_videos_by_actor(actor_filter, limit, offset, sort_by=sort_by)
        if not videos:
            live_videos = await wait_for_refresh_result(
                schedule_actor_refresh(actor_filter, page=page, sort_by=sort_by),
                4 if offset == 0 else 6,
                f"PostgREST actor {actor_filter} page {page}",
            )
            if live_videos:
                videos = db.query_videos_by_actor(actor_filter, limit, offset, sort_by=sort_by)
                if videos:
                    return JSONResponse(content=videos)
                return JSONResponse(content=hydrate_live_videos(live_videos, limit))
            if offset == 0:
                task = schedule_search_refresh(actor_filter)
                await wait_for_background_tasks([task], 4, f"Actor fallback search {actor_filter}")
                videos = db.query_videos_by_actor(actor_filter, limit, offset, sort_by=sort_by)
        return JSONResponse(content=videos)

    filter_category = category_filter or tag_filter
    if filter_category:
        filter_category = normalize_text(filter_category)
        page = source_page_for_offset(filter_category, offset)
        cache_key = f"category:{filter_category}:{sort_by}:{page}"
        if not db.is_cache_valid(cache_key, 1800) or len(db.query_recent_videos(filter_category, offset + 1, 0, sort_by=sort_by)) <= offset:
            existing_count = len(db.query_recent_videos(filter_category, 5, 0))
            task = schedule_category_refresh(filter_category, page=page, sort_by=sort_by)
            if existing_count == 0 or offset > 0:
                live_videos = await wait_for_refresh_result(task, 4 if offset == 0 else 6, f"PostgREST category {filter_category} page {page}")
                if live_videos:
                    videos = db.query_recent_videos(filter_category, limit, offset, sort_by=sort_by)
                    if videos:
                        return JSONResponse(content=videos)
                    return JSONResponse(content=hydrate_live_videos(live_videos, limit))

        videos = db.query_recent_videos(filter_category, limit, offset, sort_by=sort_by)
        if not videos:
            videos = local_category_fallback(filter_category, limit, offset, sort_by=sort_by)
        return JSONResponse(content=videos)

    # 4. Default fallback: new videos (sorted by requested order or release date)
    videos = db.query_recent_videos("new", limit, offset, sort_by=sort_by)
    return JSONResponse(content=videos)

# --- PostgREST Write Interface (to support old scraper writes if run manually) ---
@app.post("/rest/v1/videos")
async def post_videos(request: Request):
    require_admin_token(request)
    payload = await request.json()
    if isinstance(payload, dict):
        payload = [payload]
    logger.info(f"Received payload to write/upsert {len(payload)} videos")
    # Remap external_id to id if missing
    for v in payload:
        if "external_id" in v and "id" not in v:
            v["id"] = v["external_id"]
    db.save_videos(payload)
    invalidate_rpc_caches()
    return JSONResponse(content={"status": "success", "count": len(payload)})

@app.post("/rest/v1/scrape_runs")
async def post_scrape_runs(request: Request):
    require_admin_token(request)
    # Dummy scrape_runs to support old scraper statistics
    return JSONResponse(content=[{"id": "dummy-run-id"}])

@app.patch("/rest/v1/scrape_runs")
async def patch_scrape_runs(request: Request):
    require_admin_token(request)
    return JSONResponse(content={"status": "success"})

# --- MissNet API RPC Endpoints ---
@app.post("/api/v1/home")
@app.post("/rest/v1/rpc/get_home_payload")
async def rpc_get_home_payload(request: Request):
    try:
        body = await request.json()
    except Exception:
        body = {}

    section_limit = clamp_limit(body.get("section_limit", 10), default=10, maximum=30)
    weekly_limit = clamp_limit(body.get("weekly_limit", 15), default=15, maximum=40)
    force_refresh = bool(body.get("force_refresh", False))
    logger.info(f"RPC get_home_payload: section_limit={section_limit}, weekly_limit={weekly_limit}, force_refresh={force_refresh}")
    cache_key = (section_limit, weekly_limit)
    categories = ["new", "monthly_hot", "weekly_hot", "uncensored", "subtitled", "vr", "51cg"]

    if force_refresh:
        home_payload_cache.pop(cache_key, None)
    else:
        cached = get_cached_response(home_payload_cache, cache_key)
        if cached is not None and not has_stale_51cg_home_rows(cached):
            return response_with_refresh_status(cached, refresh_status_for_categories(categories))
        if cached is not None:
            home_payload_cache.pop(cache_key, None)

    category_cache = {cat: db.query_recent_videos(cat, 1, 0) for cat in categories}
    has_any_cached = any(category_cache.values())
    cold_start_tasks = []

    # Check cache / refresh without blocking the first screen on every category.
    for cat in categories:
        cached = category_cache[cat]
        if force_refresh:
            task = schedule_category_refresh(cat, page=1)
            if task is not None and cat in ("new", "51cg"):
                cold_start_tasks.append(task)
        else:
            if not cached:
                task = schedule_category_refresh(cat, page=1)
                if not has_any_cached and task is not None and cat in ("new", "51cg"):
                    cold_start_tasks.append(task)
            elif cat == "51cg" and has_stale_51cg_covers(cached):
                task = schedule_category_refresh(cat, page=1)
                if task is not None:
                    cold_start_tasks.append(task)
            elif not db.is_cache_valid(f"category:{cat}:source_default:1", 3600):
                schedule_category_refresh(cat, page=1)

    if cold_start_tasks:
        timeout_val = 8.0 if force_refresh else 12.0
        done, pending = await asyncio.wait(cold_start_tasks, timeout=timeout_val)
        if pending:
            logger.info("Home payload wait returned while %s category refresh task(s) continue in background", len(pending))
        for task in done:
            if task.cancelled():
                continue
            exc = task.exception()
            if exc:
                logger.error("Home category refresh failed", exc_info=(type(exc), exc, exc.__traceback__))

    # Build payload rows
    rows = []
    for cat in categories:
        limit = weekly_limit if cat == "weekly_hot" else section_limit
        videos = query_51cg_videos(limit, 0) if cat == "51cg" else db.query_recent_videos(cat, limit, 0)
        for v in videos:
            rows.append({
                "section": cat,
                "id": v["id"],
                "external_id": v["external_id"],
                "title": v["title"],
                "cover_url": v["cover_url"],
                "source_url": v["source_url"],
                "duration": v["duration"],
                "source_release_date": v["source_release_date"],
                "created_at": v["created_at"],
                "actors": v["actors"],
                "tags": v["tags"],
                "inventory_status": v.get("inventory_status", "detail_ready"),
                "detail_status": v.get("detail_status", "success")
            })
    set_cached_response(home_payload_cache, cache_key, rows)
    return response_with_refresh_status(rows, refresh_status_for_categories(categories))

@app.post("/api/v1/actors")
@app.post("/rest/v1/rpc/get_actor_aggregates")
async def rpc_get_actor_aggregates(request: Request):
    try:
        body = await request.json()
    except Exception:
        body = {}
    limit = clamp_limit(first_present(body, "limit_count", "limit", default=20), default=20, maximum=MAX_DIRECTORY_LIMIT)
    force_refresh = bool(body.get("force_refresh", False))
    filters = actor_directory_filters(body)
    sort_by = normalize_actress_sort(first_present(body, "sort_by", "sort", default="videos"))
    cache_key = (limit, sort_by, tuple(sorted(filters.items())))
    has_source_filters = bool(filters) or sort_by != "videos"
    logger.info(f"RPC get_actor_aggregates: limit={limit}, sort={sort_by}, filters={filters}, force_refresh={force_refresh}")

    if has_source_filters:
        cached = get_cached_response(actor_aggregate_cache, cache_key)
        if cached is not None:
            if force_refresh:
                schedule_filtered_actor_directory_refresh(cache_key, limit, filters, sort_by)
            return JSONResponse(content=cached)
        initial_limit = min(limit, FILTERED_ACTOR_INITIAL_LIMIT)
        rows = await wait_for_task_result(
            schedule_filtered_actor_directory_refresh(cache_key, initial_limit, filters, sort_by),
            6.0,
            f"Filtered actor directory {filters}",
        ) or []
        if rows and limit > initial_limit:
            schedule_filtered_actor_directory_refresh(cache_key, limit, filters, sort_by)
        return JSONResponse(content=rows)

    if force_refresh:
        actor_aggregate_cache.pop(limit, None)
        task = schedule_actor_directory_refresh(limit)
        if task is not None:
            await wait_for_background_tasks([task], 15.0, "Actor directory forced refresh")
    else:
        cached = get_cached_response(actor_aggregate_cache, limit)
        if cached is not None:
            return JSONResponse(content=cached)

    actors = db.query_actor_aggregates(limit)
    actor_directory_count = db.count_directory_entries("actor")
    if not force_refresh and actors and (actor_directory_count > 0 or len(actors) >= limit):
        if not db.is_cache_valid("directory:actor", 24 * 3600) or actor_directory_count < max(24, min(limit, 360)):
            schedule_actor_directory_refresh(limit)
        set_cached_response(actor_aggregate_cache, limit, actors)
        return JSONResponse(content=actors)

    task = schedule_actor_directory_refresh(limit)
    if task is not None:
        await wait_for_background_tasks([task], 8, "Actor directory")
    actors = db.query_actor_aggregates(limit)
    if not actors:
        await seed_actor_details(limit=min(max(limit, 4), 8), wait_seconds=12)
        actors = db.query_actor_aggregates(limit)
    set_cached_response(actor_aggregate_cache, limit, actors)
    return JSONResponse(content=actors)

@app.post("/api/v1/tags")
@app.post("/rest/v1/rpc/get_tag_aggregates")
async def rpc_get_tag_aggregates(request: Request):
    try:
        body = await request.json()
    except Exception:
        body = {}
    limit = clamp_limit(first_present(body, "limit_count", "limit", default=30), default=30, maximum=MAX_DIRECTORY_LIMIT)
    force_refresh = bool(body.get("force_refresh", False))
    logger.info(f"RPC get_tag_aggregates: limit={limit}, force_refresh={force_refresh}")

    if force_refresh:
        tag_aggregate_cache.pop(limit, None)
        task = schedule_genre_directory_refresh()
        if task is not None:
            await wait_for_background_tasks([task], 10.0, "Genre directory forced refresh")
    else:
        cached = get_cached_response(tag_aggregate_cache, limit)
        if cached is not None:
            return JSONResponse(content=cached)

    tags = db.query_tag_aggregates(limit)
    genre_directory_count = db.count_directory_entries("genre")
    if not force_refresh and tags and (genre_directory_count > 0 or len(tags) >= limit):
        if not db.is_cache_valid("directory:genre", 24 * 3600) or genre_directory_count == 0:
            schedule_genre_directory_refresh()
        set_cached_response(tag_aggregate_cache, limit, tags)
        return JSONResponse(content=tags)

    task = schedule_genre_directory_refresh()
    if task is not None:
        await wait_for_background_tasks([task], 6, "Genre directory")
    tags = db.query_tag_aggregates(limit)
    set_cached_response(tag_aggregate_cache, limit, tags)
    return JSONResponse(content=tags)

@app.post("/api/v1/actors/popular")
@app.post("/rest/v1/rpc/get_popular_actors")
async def rpc_get_popular_actors(request: Request):
    try:
        body = await request.json()
    except Exception:
        body = {}
    limit = clamp_limit(first_present(body, "limit_count", "limit", default=20), default=20, maximum=MAX_DIRECTORY_LIMIT)
    logger.info(f"RPC get_popular_actors: limit={limit}")
    await ensure_actor_directory(limit, wait_seconds=5)
    actors = db.query_actor_aggregates(limit)
    res = [{"actor": a["actor"]} for a in actors]
    return JSONResponse(content=res)

@app.post("/api/v1/tags/popular")
@app.post("/rest/v1/rpc/get_popular_tags")
async def rpc_get_popular_tags(request: Request):
    try:
        body = await request.json()
    except Exception:
        body = {}
    limit = clamp_limit(first_present(body, "limit_count", "limit", default=30), default=30, maximum=MAX_DIRECTORY_LIMIT)
    logger.info(f"RPC get_popular_tags: limit={limit}")
    await ensure_genre_directory(wait_seconds=4)
    tags = db.query_tag_aggregates(limit)
    res = [{"tag": t["tag"]} for t in tags]
    return JSONResponse(content=res)

@app.post("/api/v1/categories/videos")
@app.post("/rest/v1/rpc/get_videos_by_category")
async def rpc_get_videos_by_category(request: Request):
    try:
        body = await request.json()
    except Exception:
        body = {}
    category = normalize_text(first_present(body, "category_text", "category_name", "category", "tag_text", "tag", default="new")) or "new"
    limit = clamp_limit(first_present(body, "limit_count", "limit", default=20), default=20)
    offset = clamp_offset(first_present(body, "offset_count", "offset", default=0))
    force_refresh = bool(body.get("force_refresh", False))
    sort_by = normalize_video_sort(first_present(body, "sort_by", "sort", default="released_at"))
    logger.info(f"RPC get_videos_by_category: category={category}, limit={limit}, offset={offset}, sort={sort_by}, force_refresh={force_refresh}")
    page = source_page_for_offset(category, offset)

    page_cache_valid = db.is_cache_valid(f"category:{category}:{sort_by}:{page}", 1800)
    if (force_refresh or not page_cache_valid):
        task = schedule_category_refresh(category, page=page, sort_by=sort_by)
        if task is not None and force_refresh:
            await wait_for_refresh_result(task, 3.0 if offset == 0 else 2.0, f"Category {category} page {page} force refresh")
        page_cache_valid = True

    videos = query_51cg_videos(limit, offset, sort_by=sort_by) if category == "51cg" else db.query_recent_videos(category, limit, offset, sort_by=sort_by)
    if videos:
        if category == "51cg" and len(videos) < limit:
            live_videos = await wait_for_refresh_result(
                schedule_category_refresh(category, page=page, sort_by=sort_by),
                8,
                f"Category {category} fresh cover page {page}",
            )
            if live_videos:
                live_fresh = remove_stale_51cg_covers(hydrate_live_videos(live_videos, limit))
                if live_fresh:
                    return JSONResponse(content=live_fresh[:limit])
            videos = query_51cg_videos(limit, offset, sort_by=sort_by)
        if not force_refresh and (len(videos) < limit or not page_cache_valid):
            await ensure_category_videos(
                category,
                limit,
                offset,
                sort_by=sort_by,
                wait_seconds=8.0 if len(videos) < limit else 0,
            )
            refreshed = query_51cg_videos(limit, offset, sort_by=sort_by) if category == "51cg" else db.query_recent_videos(category, limit, offset, sort_by=sort_by)
            if len(refreshed) > len(videos):
                return JSONResponse(content=refreshed)
        return JSONResponse(content=videos)

    videos = local_category_fallback(category, limit, offset, sort_by=sort_by)
    if videos:
        schedule_category_refresh(category, page=page, sort_by=sort_by)
        return JSONResponse(content=videos)

    if offset == 0:
        schedule_genre_directory_refresh()
    live_videos = await wait_for_refresh_result(
        schedule_category_refresh(category, page=page, sort_by=sort_by),
        6 if offset == 0 else 8,
        f"Category {category} page {page}",
    )
    if live_videos:
        refreshed = query_51cg_videos(limit, offset, sort_by=sort_by) if category == "51cg" else db.query_recent_videos(category, limit, offset, sort_by=sort_by)
        if refreshed:
            return JSONResponse(content=refreshed)
        return JSONResponse(content=hydrate_live_videos(live_videos, limit))

    await ensure_category_videos(category, limit, offset, sort_by=sort_by, wait_seconds=0)
    videos = query_51cg_videos(limit, offset, sort_by=sort_by) if category == "51cg" else db.query_recent_videos(category, limit, offset, sort_by=sort_by)
    if not videos and category != "51cg":
        videos = local_category_fallback(category, limit, offset, sort_by=sort_by)
    return JSONResponse(content=videos)

@app.post("/api/v1/actors/videos")
@app.post("/rest/v1/rpc/get_videos_by_actor")
async def rpc_get_videos_by_actor(request: Request):
    try:
        body = await request.json()
    except Exception:
        body = {}
    actor = normalize_text(first_present(body, "actor_name", "actor_text", "actor", "name", default=""))
    limit = clamp_limit(first_present(body, "limit_count", "limit", default=20), default=20)
    offset = clamp_offset(first_present(body, "offset_count", "offset", default=0))
    force_refresh = bool(body.get("force_refresh", False))
    sort_by = normalize_video_sort(first_present(body, "sort_by", "sort", default="released_at"))
    logger.info(f"RPC get_videos_by_actor: actor={actor}, limit={limit}, offset={offset}, sort={sort_by}, force_refresh={force_refresh}")

    page = source_page_for_offset(actor, offset)
    page_cache_valid = db.is_cache_valid(f"actor:{actor}:{sort_by}:{page}", 1800)

    if (force_refresh or not page_cache_valid) and actor:
        task = schedule_actor_refresh(actor, page=page, sort_by=sort_by)
        if task is not None and force_refresh:
            await wait_for_refresh_result(task, 3.0 if offset == 0 else 2.0, f"Actor {actor} page {page} force refresh")
        page_cache_valid = True

    videos = db.query_videos_by_actor(actor, limit, offset, sort_by=sort_by)
    if videos:
        if not force_refresh and (len(videos) < limit or not page_cache_valid):
            await ensure_actor_videos(
                actor,
                limit,
                offset,
                sort_by=sort_by,
                wait_seconds=8.0 if len(videos) < limit else 0,
            )
            refreshed = db.query_videos_by_actor(actor, limit, offset, sort_by=sort_by)
            if len(refreshed) > len(videos):
                return JSONResponse(content=refreshed)
        return JSONResponse(content=videos)

    if actor:
        if offset == 0:
            schedule_actor_directory_refresh(limit=240)
        live_videos = await wait_for_refresh_result(
            schedule_actor_refresh(actor, page=page, sort_by=sort_by),
            6 if offset == 0 else 8,
            f"Actor {actor} page {page}",
        )
        if live_videos:
            refreshed = db.query_videos_by_actor(actor, limit, offset, sort_by=sort_by)
            if refreshed:
                return JSONResponse(content=refreshed)
            return JSONResponse(content=hydrate_live_videos(live_videos, limit))
        await ensure_actor_videos(actor, limit, offset, sort_by=sort_by, wait_seconds=0)
        videos = db.query_videos_by_actor(actor, limit, offset, sort_by=sort_by)
    if not videos and actor:
        schedule_actor_refresh(actor, page=page, sort_by=sort_by)
        videos = local_actor_fallback(actor, limit, offset, sort_by=sort_by)
    if not videos and offset == 0:
        task = schedule_search_refresh(actor)
        if task is not None:
            await wait_for_background_tasks([task], 4, f"Actor fallback search {actor}")
        videos = local_actor_fallback(actor, limit, offset, sort_by=sort_by)
    return JSONResponse(content=videos)

@app.post("/api/v1/search/videos")
@app.post("/rest/v1/rpc/search_videos_multi")
async def rpc_search_videos_multi(request: Request):
    try:
        body = await request.json()
    except Exception:
        body = {}
    query = normalize_text(first_present(body, "query_text", "query", "q", default=""))
    limit = clamp_limit(first_present(body, "limit_count", "limit", default=20), default=20)
    offset = clamp_offset(first_present(body, "offset_count", "offset", default=0))
    force_refresh = bool(body.get("force_refresh", False))
    logger.info(f"RPC search_videos_multi: query='{query}', limit={limit}, offset={offset}, force_refresh={force_refresh}")

    if not query:
        return JSONResponse(content=[])

    # Calculate required search source page
    page = (offset // MISSAV_SOURCE_PAGE_SIZE) + 1
    cache_valid = db.is_cache_valid(f"search:{query.strip().lower()}:{page}", 1800)

    # If force_refresh or cache expired or local results are insufficient, actively scrape upstream!
    local_results = db.search_videos(query, limit, offset)
    if (force_refresh or not cache_valid or len(local_results) < limit) and len(query) >= 2:
        task = schedule_search_refresh(query, page=page)
        if task is not None:
            # Wait up to 3.5s for live search on page 1 (or 2.5s on pagination) so user gets live results immediately
            wait_time = 3.5 if offset == 0 else 2.5
            live_videos = await wait_for_refresh_result(task, wait_time, f"Search {query} page {page}")
            if live_videos:
                # Re-query local database after scrape results are stored
                local_results = db.search_videos(query, limit, offset)

    results = local_results
    if offset == 0 and query and len(results) < min(limit, 6):
        category_results = local_category_fallback(query, limit, 0)
        if category_results:
            logger.info("Search category fallback hit query=%s rows=%s", query, len(category_results))
            results = merge_video_results(results, category_results, limit)
    return JSONResponse(content=results)


# --- Enhanced backend feature endpoints ---
def _json_body_value(body: dict, *names: str, default=None):
    for name in names:
        if name in body:
            return body[name]
    return default


@app.get("/api/v1/admin/metrics")
async def admin_metrics():
    counts = db.db_counts()
    quality = db.data_quality_summary()
    jobs = db.refresh_job_metrics()
    return JSONResponse(content={
        "db": {
            "videos": counts.get("videos", 0),
            "directory_entries": counts.get("directory_entries", 0),
            "cache_metadata": counts.get("cache_metadata", 0),
            "refresh_jobs": counts.get("refresh_jobs", 0),
        },
        "quality": quality,
        "jobs": jobs,
        "refreshing": {
            "categories": len(refreshing_categories),
            "actors": len(refreshing_actors),
            "details": len(refreshing_details),
            "directories": len(refreshing_directories),
            "searches": len(refreshing_searches),
        },
        "limits": {
            "max_list_limit": MAX_LIST_LIMIT,
            "max_directory_limit": MAX_DIRECTORY_LIMIT,
            "max_offset": MAX_OFFSET,
        },
    })


@app.get("/api/v1/admin/jobs")
async def admin_jobs(limit: int = Query(50), status: str = Query(""), kind: str = Query("")):
    return JSONResponse(content={
        "items": db.list_refresh_jobs(limit=limit, status=status or None, kind=kind or None),
        "metrics": db.refresh_job_metrics(),
    })


@app.post("/api/v1/admin/jobs/{job_id}/retry")
async def admin_retry_job(job_id: int, request: Request):
    require_admin_token(request)
    job = db.finish_refresh_job(job_id, "cancelled", error="retry requested; original job closed")
    new_id = db.create_refresh_job(job.get("kind", "manual"), job.get("job_key", str(job_id)), source_url=job.get("source_url")) if job else db.create_refresh_job("manual", str(job_id))
    return JSONResponse(content={"status": "queued", "job_id": new_id, "previous_job": job})


@app.get("/api/v1/admin/source-status")
async def admin_source_status():
    missav_status = refresh_status_for_categories(["new", "monthly_hot", "weekly_hot", "uncensored", "subtitled", "vr"], "released_at", 1)
    cg_status = refresh_status_for_categories(["51cg"], "released_at", 1).get("51cg", {})
    return JSONResponse(content={
        "missav": {
            "categories": missav_status,
            "active_refreshes": len(refreshing_categories) + len(refreshing_actors),
        },
        "51cg": {
            "status": cg_status,
            "domains": getattr(scraper, "cg_domains", []),
            "active_domain": getattr(scraper, "active_cg_domain", None),
        },
        "jobs": db.source_status_summary(),
    })


@app.post("/api/v1/search")
@app.post("/rest/v1/rpc/search")
async def api_search(request: Request):
    try:
        body = await request.json()
    except Exception:
        body = {}
    query = normalize_text(_json_body_value(body, "q", "query", "search_text", default=""))
    actor = normalize_text(_json_body_value(body, "actor", "actor_name", default="")) or None
    tags = _json_body_value(body, "tags", "tag_list", default=[]) or []
    categories = _json_body_value(body, "categories", "category_list", default=[]) or []
    limit = clamp_limit(_json_body_value(body, "limit", "limit_count", default=20))
    offset = clamp_offset(_json_body_value(body, "offset", "offset_count", default=0))
    sort_by = normalize_video_sort(_json_body_value(body, "sort", "sort_by", default="released_at"))
    force_refresh = bool(body.get("force_refresh", False))

    if isinstance(tags, str):
        tags = [tags]
    if isinstance(categories, str):
        categories = [categories]

    if query and len(query) >= 2:
        page = (offset // MISSAV_SOURCE_PAGE_SIZE) + 1
        cache_valid = db.is_cache_valid(f"search:{query.strip().lower()}:{page}", 1800)
        if force_refresh or not cache_valid:
            task = schedule_search_refresh(query, page=page)
            if task is not None:
                await wait_for_refresh_result(task, 3.5 if offset == 0 else 2.0, f"Advanced search {query}")

    result = db.search_videos_advanced(
        query=query,
        actor=actor,
        tags=[normalize_text(t) for t in tags if normalize_text(t)],
        categories=[normalize_text(c) for c in categories if normalize_text(c)],
        limit=limit,
        offset=offset,
        sort_by=sort_by,
        include_facets=bool(body.get("include_facets", True)),
    )
    return JSONResponse(content=result)


@app.post("/api/v1/users/{user_id}/actions")
async def set_video_action(user_id: str, request: Request):
    body = await request.json()
    video_id = normalize_text(_json_body_value(body, "video_id", "id", default=""), maximum=128)
    action = normalize_text(_json_body_value(body, "action", default="favorite"), maximum=32)
    value = bool(_json_body_value(body, "value", default=True))
    if not video_id:
        raise HTTPException(status_code=400, detail="video_id is required")
    return JSONResponse(content=db.set_user_video_action(user_id, video_id, action, value))


@app.get("/api/v1/users/{user_id}/actions")
async def list_video_actions(user_id: str, action: str = Query(""), limit: int = Query(100)):
    return JSONResponse(content={"items": db.list_user_video_actions(user_id, action or None, limit)})


@app.post("/api/v1/users/{user_id}/watch-progress")
async def set_watch_progress(user_id: str, request: Request):
    body = await request.json()
    video_id = normalize_text(_json_body_value(body, "video_id", "id", default=""), maximum=128)
    if not video_id:
        raise HTTPException(status_code=400, detail="video_id is required")
    return JSONResponse(content=db.record_watch_progress(
        user_id,
        video_id,
        position_seconds=clamp_int(_json_body_value(body, "position_seconds", "position", default=0), 0, 0, 24 * 3600),
        duration_seconds=clamp_int(_json_body_value(body, "duration_seconds", "duration", default=0), 0, 0, 24 * 3600),
    ))


def _missav_source_url_for_id(video_id: str) -> str:
    return f"https://missav.ws/{urllib.parse.quote(video_id, safe='-_.~')}"


def _playback_proxy_url(request: Request, video_id: str) -> str:
    return str(request.url_for("proxy_video_playback_playlist", video_id=video_id))


def _fetch_url_bytes(url: str, referer: str = "", range_header: str | None = None) -> tuple[int, dict[str, str], bytes]:
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept": "*/*",
    }
    if referer:
        headers["Referer"] = referer
    if range_header:
        headers["Range"] = range_header
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=25) as resp:
            return resp.status, dict(resp.headers.items()), resp.read()
    except urllib.error.HTTPError as exc:
        return exc.code, dict(exc.headers.items()), exc.read()


def _rewrite_hls_playlist(body: bytes, playlist_url: str, request: Request, video_id: str) -> bytes:
    text = body.decode("utf-8", errors="replace")

    def proxied(uri: str) -> str:
        absolute = urllib.parse.urljoin(playlist_url, uri)
        parsed_path = urllib.parse.urlparse(absolute).path.lower()
        route_name = "proxy_video_playback_asset" if parsed_path.endswith(".m3u8") else "proxy_video_playback_segment"
        return str(request.url_for(route_name, video_id=video_id)) + "?url=" + urllib.parse.quote(absolute, safe="")

    rewritten: list[str] = []
    for line in text.splitlines():
        stripped = line.strip()
        if stripped and not stripped.startswith("#"):
            rewritten.append(proxied(stripped))
        elif 'URI="' in line:
            rewritten.append(re.sub(r'URI="([^"]+)"', lambda m: f'URI="{proxied(m.group(1))}"', line))
        else:
            rewritten.append(line)
    return ("\n".join(rewritten) + "\n").encode("utf-8")


async def _ensure_playback(video_id: str) -> dict | None:
    normalized_id = normalize_text(video_id, maximum=128)
    playback = db.get_video_playback(normalized_id)
    video = db.query_video_by_id(normalized_id)
    source_url = resolve_detail_source_url(normalized_id, video) if video else _missav_source_url_for_id(normalized_id)
    if not playback or not playback.get("is_ready"):
        task = schedule_video_detail_refresh(normalized_id, source_url, force_refresh=True)
        refreshed = await wait_for_task_result(task, 12, f"Playback detail refresh {normalized_id}")
        if refreshed:
            playback = db.get_video_playback(normalized_id)
        else:
            playback = db.get_video_playback(normalized_id) or playback
    return playback


@app.get("/api/v1/videos/{video_id}/playback")
async def get_video_playback(video_id: str, request: Request, index: int | None = Query(None)):
    normalized_id = normalize_text(video_id, maximum=128)
    playback = await _ensure_playback(normalized_id)
    if not playback:
        raise HTTPException(status_code=404, detail="video not found")

    if index and index > 1:
        playback = db.get_video_playback(normalized_id, episode_index=index) or playback

    if playback.get("is_ready") and playback.get("playback_url"):
        raw_url = playback.get("playback_url")
        playback = dict(playback)
        playback["raw_playback_url"] = raw_url
        playback["playback_url"] = _playback_proxy_url(request, playback.get("video_id", normalized_id))
        playback["proxy_playback_url"] = playback["playback_url"]
        playback["source_type"] = "hls"

        # Proxy playback URLs inside videos array too
        if playback.get("videos"):
            proxied_videos = []
            for v in playback["videos"]:
                item = dict(v)
                if item.get("is_ready"):
                    item["playback_url"] = _playback_proxy_url(request, item["id"])
                    item["proxy_playback_url"] = item["playback_url"]
                proxied_videos.append(item)
            playback["videos"] = proxied_videos

    return JSONResponse(content=playback)


@app.get("/api/v1/videos/{video_id}/playback-test", response_class=HTMLResponse)
async def playback_test_page(video_id: str, request: Request):
    normalized_id = normalize_text(video_id, maximum=128)
    playlist_url = _playback_proxy_url(request, normalized_id)
    html = f"""<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>MissNet playback test {normalized_id}</title>
<style>body{{background:#111;color:#eee;font-family:sans-serif;padding:16px}} video{{width:100%;max-width:960px;background:#000}} pre{{white-space:pre-wrap;background:#222;padding:12px}}</style>
</head><body>
<h3>MissNet HLS Test: {normalized_id}</h3>
<video id="v" controls playsinline webkit-playsinline></video>
<p><a href="{playlist_url}">playlist</a></p>
<pre id="log">loading...</pre>
<script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
<script>
const video=document.getElementById('v');
const log=document.getElementById('log');
const src={playlist_url!r};
function add(msg){{ log.textContent += '\\n' + msg; console.log(msg); }}
log.textContent='src='+src+'\\nua='+navigator.userAgent;
video.addEventListener('error',()=>add('video error code='+(video.error&&video.error.code)+' message='+(video.error&&video.error.message)));
if (window.Hls && Hls.isSupported()) {{
  add('using hls.js '+Hls.version); const hls=new Hls({{debug:false}});
  hls.on(Hls.Events.ERROR,(ev,data)=>add('hls error '+JSON.stringify(data)));
  hls.on(Hls.Events.MANIFEST_PARSED,()=>add('manifest parsed'));
  hls.on(Hls.Events.FRAG_LOADED,(ev,data)=>add('frag loaded sn='+(data.frag&&data.frag.sn)));
  hls.loadSource(src); hls.attachMedia(video);
}} else if (video.canPlayType('application/vnd.apple.mpegurl')) {{
  add('native HLS supported'); video.src=src; video.play().catch(e=>add('play rejected: '+e));
}} else {{ add('HLS not supported in this browser'); }}
</script></body></html>"""
    return HTMLResponse(html)


@app.get("/api/v1/videos/{video_id}/playback-proxy.m3u8", name="proxy_video_playback_playlist")
async def proxy_video_playback_playlist(video_id: str, request: Request, quality: str | None = None):
    normalized_id = normalize_text(video_id, maximum=128)
    playback = await _ensure_playback(normalized_id)
    if not playback or not playback.get("is_ready") or not playback.get("playback_url"):
        raise HTTPException(status_code=404, detail="playback not ready")
    raw_url = playback["playback_url"]
    referer = playback.get("referer") or playback.get("source_url") or ""
    status, headers, body = await asyncio.to_thread(_fetch_url_bytes, raw_url, referer, None)
    if status >= 400:
        raise HTTPException(status_code=status, detail="upstream playlist fetch failed")

    # Many Android app-side validators are happier with a media playlist (EXTINF segments)
    # than a master playlist (EXT-X-STREAM-INF variants). 51cg already returns media
    # playlists, so flatten MissAV/Surrit master manifests to a concrete variant here.
    playlist_text = body.decode("utf-8", errors="replace")
    if "#EXT-X-STREAM-INF" in playlist_text and "#EXTINF" not in playlist_text:
        variants: list[tuple[int, str]] = []
        last_bandwidth = 0
        for line in playlist_text.splitlines():
            stripped = line.strip()
            if stripped.startswith("#EXT-X-STREAM-INF"):
                match = re.search(r"BANDWIDTH=(\d+)", stripped)
                last_bandwidth = int(match.group(1)) if match else 0
            elif stripped and not stripped.startswith("#") and last_bandwidth >= 0:
                variants.append((last_bandwidth, urllib.parse.urljoin(raw_url, stripped)))
                last_bandwidth = -1
        if variants:
            sorted_variants = sorted(variants, key=lambda item: item[0])
            normalized_quality = (quality or "highest").strip().lower()
            quality_tokens = {
                "1080p": ("1080p", "1920x1080"),
                "720p": ("720p", "1280x720"),
                "480p": ("480p", "842x480", "854x480"),
                "360p": ("360p", "640x360"),
            }.get(normalized_quality, ())
            matched = next(
                (url for _, url in sorted_variants if any(token in url.lower() for token in quality_tokens)),
                None,
            )
            variant_url = matched or sorted_variants[-1][1]
            status, headers, body = await asyncio.to_thread(_fetch_url_bytes, variant_url, referer, None)
            if status >= 400:
                raise HTTPException(status_code=status, detail="upstream variant playlist fetch failed")
            raw_url = variant_url

    body = _rewrite_hls_playlist(body, raw_url, request, normalized_id)
    return Response(content=body, media_type="application/vnd.apple.mpegurl", headers={"Cache-Control": "no-store"})


@app.get("/api/v1/videos/{video_id}/playback-proxy/asset.ts", name="proxy_video_playback_segment")
@app.get("/api/v1/videos/{video_id}/playback-proxy/asset", name="proxy_video_playback_asset")
async def proxy_video_playback_asset(video_id: str, request: Request, url: str):
    normalized_id = normalize_text(video_id, maximum=128)
    playback = db.get_video_playback(normalized_id) or {}
    is_playlist_request = ".m3u8" in url.lower()
    # Do not forward Range for HLS playlists. Some clients probe everything with Range,
    # but a partial .m3u8 is invalid and prevents them from reaching media segments.
    range_header = None if is_playlist_request else (request.headers.get("Range") or request.headers.get("range"))
    status, headers, body = await asyncio.to_thread(_fetch_url_bytes, url, playback.get("referer") or playback.get("source_url") or _missav_source_url_for_id(normalized_id), range_header)
    if status >= 400:
        raise HTTPException(status_code=status, detail="upstream asset fetch failed")
    media_type = headers.get("Content-Type") or ("application/vnd.apple.mpegurl" if is_playlist_request else "video/mp2t")
    if is_playlist_request or "mpegurl" in media_type.lower():
        body = _rewrite_hls_playlist(body, url, request, normalized_id)
        media_type = "application/vnd.apple.mpegurl"
        status = 200
    elif urllib.parse.urlparse(url).path.lower().endswith((".ts", ".jpeg", ".jpg")):
        # Surrit serves HLS transport-stream chunks with .jpeg names and image/jpeg headers.
        # ExoPlayer is more reliable when the proxy exposes the real stream MIME type.
        media_type = "video/mp2t"
    response_headers = {"Cache-Control": "public, max-age=600"}
    for h in ("Content-Range", "Accept-Ranges"):
        if headers.get(h) and "mpegurl" not in media_type.lower():
            response_headers[h] = headers[h]
    return Response(content=body, status_code=status, media_type=media_type, headers=response_headers)


@app.post("/api/v1/videos/{video_id}/refresh-detail")
async def refresh_video_detail(video_id: str, request: Request):
    try:
        body = await request.json()
    except Exception:
        body = {}
    source_url = normalize_text(body.get("source_url") or resolve_detail_source_url(video_id, db.query_video_by_id(video_id)), maximum=500)
    task = schedule_video_detail_refresh(video_id, source_url, force_refresh=bool(body.get("force_refresh", True)))
    if task is None:
        return JSONResponse(content={"status": "skipped", "reason": "already refreshing, cached, or no source_url"})
    result = await wait_for_task_result(task, 4, f"Manual detail refresh {video_id}")
    return JSONResponse(content={"status": "completed" if result else "scheduled", "video": result})


@app.get("/api/v1/videos/{video_id}/recommendations")
async def get_video_recommendations(video_id: str, limit: int = Query(20)):
    return JSONResponse(content={"items": db.recommend_similar_videos(normalize_text(video_id, maximum=128), clamp_limit(limit))})


@app.get("/api/v1/admin/data-quality")
async def admin_data_quality():
    return JSONResponse(content=db.data_quality_summary())


@app.post("/api/v1/admin/maintenance/analyze")
async def admin_maintenance_analyze(request: Request):
    require_admin_token(request)
    return JSONResponse(content=db.maintenance_analyze())


@app.post("/api/v1/admin/maintenance/vacuum")
async def admin_maintenance_vacuum(request: Request):
    require_admin_token(request)
    return JSONResponse(content=db.maintenance_vacuum())
