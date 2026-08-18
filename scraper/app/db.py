import os
import re
import sqlite3
import json
import time
from typing import List, Dict, Any, Optional

DB_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB_PATH = os.path.join(DB_DIR, "missnets.db")
MOJIBAKE_MARKERS = ("Ã", "Â", "ã", "ä", "å", "æ", "ç", "è", "é", "ê", "ë", "ï", "ð")
TITLE_ACTOR_BLOCKLIST = (
    "中文字幕",
    "中文",
    "字幕",
    "无码",
    "無碼",
    "破解",
    "高清",
    "完整",
    "合集",
    "版本",
    "流出",
    "生活",
    "描繪",
    "描绘",
    "真實",
    "真实",
    "非常",
    "媽媽",
    "妈妈",
    "射精",
    "性感",
    "翹臀",
    "翘臀",
)
TITLE_ACTOR_SENTENCE_MARKERS = ("的", "了", "被", "把", "和", "與", "与", "在", "到", "是")


def _add_unique(values: List[str], value: Any) -> None:
    if not isinstance(value, str):
        return
    cleaned = repair_mojibake_text(value).strip()
    if cleaned and cleaned not in values:
        values.append(cleaned)


def actor_aliases(actor: Any) -> List[str]:
    """Return stable lookup aliases for actress names with parenthesized kana aliases.

    MissAV directory names may appear as either ``篠田優 (篠田ゆう)`` or
    ``篠田優(篠田ゆう)`` while video rows may store just the kanji name or the
    kana alias. Actor detail lookup should merge all of those spellings.
    """
    if not isinstance(actor, str):
        return []
    normalized = repair_mojibake_text(actor).strip()
    if not normalized:
        return []
    normalized = re.sub(r"[\u3000\t\r\n]+", " ", normalized)
    normalized = re.sub(r"\s+", " ", normalized).strip()
    aliases: List[str] = []
    _add_unique(aliases, normalized)

    match = re.match(r"^(?P<primary>.+?)\s*[（(]\s*(?P<alias>[^）)]+?)\s*[）)]$", normalized)
    if match:
        primary = match.group("primary").strip()
        alias = match.group("alias").strip()
        _add_unique(aliases, f"{primary} ({alias})")
        _add_unique(aliases, f"{primary}({alias})")
        _add_unique(aliases, primary)
        _add_unique(aliases, alias)
    return aliases


def actor_primary_name(actor: Any) -> str:
    aliases = actor_aliases(actor)
    if not aliases:
        return ""
    for alias in aliases:
        if "(" not in alias and "（" not in alias:
            return alias
    return aliases[0]


def _mojibake_score(value: str) -> int:
    marker_hits = sum(value.count(marker) for marker in MOJIBAKE_MARKERS)
    control_hits = sum(1 for ch in value if 0x80 <= ord(ch) <= 0x9F)
    return marker_hits + (control_hits * 2)


def repair_mojibake_text(value: Any) -> Any:
    if not isinstance(value, str) or not value:
        return value
    if _mojibake_score(value) == 0:
        return value
    try:
        repaired = value.encode("latin1").decode("utf-8")
    except (UnicodeEncodeError, UnicodeDecodeError):
        return value
    return repaired if _mojibake_score(repaired) < _mojibake_score(value) else value


def repair_text_list(values: Any) -> List[str]:
    if not isinstance(values, list):
        return []
    return [
        repaired.strip()
        for item in values
        if isinstance((repaired := repair_mojibake_text(item)), str) and repaired.strip()
    ]

def infer_actors_from_title(title: Any) -> List[str]:
    if not isinstance(title, str) or not title.strip():
        return []
    value = repair_mojibake_text(title).strip()
    parts = re.split(r"[。！？!?]|——|—|\||｜", value)
    for part in reversed(parts):
        candidate = re.sub(r"[\s:：,，、.。!！?？~～·・\-]+", "", part.strip())
        candidate = re.sub(r"^(主演|女優|女优|演員|演员)", "", candidate)
        if not (2 <= len(candidate) <= 5):
            continue
        if re.search(r"[0-9０-９「」『』\"“”]", candidate):
            continue
        if any(blocked in candidate for blocked in TITLE_ACTOR_BLOCKLIST):
            continue
        if any(marker in candidate for marker in TITLE_ACTOR_SENTENCE_MARKERS):
            continue
        if re.search(r"[\u3400-\u9fff\u3040-\u30ff]", candidate):
            return [candidate]
    return []

def get_db_connection():
    conn = sqlite3.connect(DB_PATH, timeout=30)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA busy_timeout = 5000")
    return conn

def init_db():
    os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("PRAGMA journal_mode = WAL")
    cursor.execute("PRAGMA synchronous = NORMAL")

    # videos table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS videos (
            id TEXT PRIMARY KEY,
            external_id TEXT,
            title TEXT,
            cover_url TEXT,
            source_url TEXT,
            duration TEXT,
            source_release_date TEXT,
            watched_count INTEGER DEFAULT 0,
            created_at TEXT,
            actors TEXT, -- JSON array
            tags TEXT,   -- JSON array
            categories TEXT, -- JSON array
            is_active INTEGER DEFAULT 1,
            inventory_status TEXT DEFAULT 'detail_ready',
            detail_status TEXT DEFAULT 'pending',
            detail_fetched_at TEXT
        )
    """)

    # cache_metadata table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS cache_metadata (
            key TEXT PRIMARY KEY,
            last_updated INTEGER
        )
    """)

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS directory_entries (
            kind TEXT NOT NULL,
            name TEXT NOT NULL,
            source_url TEXT,
            cover_url TEXT,
            video_count INTEGER DEFAULT 0,
            latest_release_date TEXT,
            created_at TEXT,
            updated_at INTEGER,
            PRIMARY KEY (kind, name)
        )
    """)

    cursor.execute("PRAGMA table_info(videos)")
    columns = {row["name"] for row in cursor.fetchall()}
    if "watched_count" not in columns:
        cursor.execute("ALTER TABLE videos ADD COLUMN watched_count INTEGER DEFAULT 0")

    cursor.execute("""
        CREATE INDEX IF NOT EXISTS idx_videos_active_release
        ON videos (is_active, source_release_date DESC, created_at DESC, id DESC)
    """)
    cursor.execute("""
        CREATE INDEX IF NOT EXISTS idx_videos_active_created
        ON videos (is_active, created_at DESC, id DESC)
    """)
    cursor.execute("""
        CREATE INDEX IF NOT EXISTS idx_videos_active_watched
        ON videos (is_active, watched_count DESC, source_release_date DESC, created_at DESC, id DESC)
    """)
    cursor.execute("""
        CREATE INDEX IF NOT EXISTS idx_directory_kind_count
        ON directory_entries (kind, video_count DESC, updated_at DESC, name ASC)
    """)

    conn.commit()
    conn.close()
    ensure_schema()

def ensure_schema():
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("PRAGMA table_info(videos)")
    columns = {row["name"] for row in cursor.fetchall()}
    if "watched_count" not in columns:
        cursor.execute("ALTER TABLE videos ADD COLUMN watched_count INTEGER DEFAULT 0")
    cursor.execute("""
        CREATE INDEX IF NOT EXISTS idx_videos_active_watched
        ON videos (is_active, watched_count DESC, source_release_date DESC, created_at DESC, id DESC)
    """)
    conn.commit()
    conn.close()

def save_videos(videos: List[Dict[str, Any]]):
    if not videos:
        return
    ensure_schema()
    conn = get_db_connection()
    cursor = conn.cursor()
    now_str = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())

    # Pre-fetch existing metadata to intelligently merge actors/tags/categories
    raw_ids = [v.get("id") or v.get("external_id", "") for v in videos]
    valid_ids = [vid for vid in raw_ids if vid]
    existing_map = {}
    if valid_ids:
        placeholders = ",".join(["?"] * len(valid_ids))
        cursor.execute(f"SELECT id, actors, tags, categories FROM videos WHERE id IN ({placeholders})", valid_ids)
        for r in cursor.fetchall():
            try:
                ex_a = json.loads(r["actors"]) if r["actors"] else []
            except Exception:
                ex_a = []
            try:
                ex_t = json.loads(r["tags"]) if r["tags"] else []
            except Exception:
                ex_t = []
            try:
                ex_c = json.loads(r["categories"]) if r["categories"] else []
            except Exception:
                ex_c = []
            existing_map[r["id"]] = {"actors": ex_a, "tags": ex_t, "categories": ex_c}

    for v in videos:
        # Support either id or external_id as the primary key
        vid_id = v.get("id") or v.get("external_id", "")
        if not vid_id:
            continue
        watched_count = int(v.get("watched_count") or v.get("watchedCount") or 0)

        existing = existing_map.get(vid_id, {})
        ex_actors = existing.get("actors", [])
        ex_tags = existing.get("tags", [])
        ex_categories = existing.get("categories", [])

        # Normalize actors/tags/categories
        new_actors = repair_text_list(v.get("actors", []))
        new_tags = repair_text_list(v.get("tags", []))
        new_categories = repair_text_list(v.get("categories", []))

        # Smart merge: combine lists, deduplicate, filter out purely 'search' placeholder if real categories exist
        merged_actors = list(dict.fromkeys(ex_actors + new_actors))
        merged_tags = [t for t in dict.fromkeys(ex_tags + new_tags) if t != "search" or not (ex_tags or new_tags)]
        merged_categories = [c for c in dict.fromkeys(ex_categories + new_categories) if c != "search" or not (ex_categories or new_categories)]
        if not merged_categories and ("search" in new_categories or "search" in ex_categories):
            merged_categories = ["search"]
        if not merged_tags and ("search" in new_tags or "search" in ex_tags):
            merged_tags = ["search"]

        actors_str = json.dumps(merged_actors, ensure_ascii=False)
        tags_str = json.dumps(merged_tags, ensure_ascii=False)
        categories_str = json.dumps(merged_categories, ensure_ascii=False)

        has_detail_status = "detail_status" in v
        has_detail_fetched_at = "detail_fetched_at" in v
        has_detail_payload = has_detail_status or has_detail_fetched_at

        cursor.execute("""
            INSERT INTO videos (
                id, external_id, title, cover_url, source_url, duration,
                source_release_date, watched_count, created_at, actors, tags, categories,
                is_active, inventory_status, detail_status, detail_fetched_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                title = CASE WHEN excluded.title IS NOT NULL AND excluded.title != '' THEN excluded.title ELSE title END,
                cover_url = CASE WHEN excluded.cover_url IS NOT NULL AND excluded.cover_url != '' THEN excluded.cover_url ELSE cover_url END,
                source_url = CASE WHEN excluded.source_url IS NOT NULL AND excluded.source_url != '' THEN excluded.source_url ELSE source_url END,
                duration = CASE WHEN excluded.duration IS NOT NULL AND excluded.duration != '' THEN excluded.duration ELSE duration END,
                source_release_date = CASE WHEN excluded.source_release_date IS NOT NULL THEN excluded.source_release_date ELSE source_release_date END,
                watched_count = CASE WHEN excluded.watched_count > 0 THEN excluded.watched_count ELSE watched_count END,
                created_at = CASE WHEN excluded.created_at IS NOT NULL AND excluded.created_at != '' THEN excluded.created_at ELSE created_at END,
                actors = excluded.actors,
                tags = excluded.tags,
                categories = excluded.categories,
                is_active = COALESCE(excluded.is_active, is_active),
                inventory_status = COALESCE(excluded.inventory_status, inventory_status),
                detail_status = CASE WHEN ? THEN COALESCE(excluded.detail_status, detail_status) ELSE detail_status END,
                detail_fetched_at = CASE WHEN ? THEN excluded.detail_fetched_at ELSE detail_fetched_at END
        """, (
            vid_id,
            vid_id,
            repair_mojibake_text(v.get("title", "")),
            v.get("cover_url"),
            v.get("source_url", ""),
            v.get("duration"),
            v.get("source_release_date"),
            watched_count,
            v.get("created_at") or now_str,
            actors_str,
            tags_str,
            categories_str,
            1 if v.get("is_active", True) else 0,
            v.get("inventory_status", "detail_ready"),
            v.get("detail_status") if has_detail_status else "pending",
            v.get("detail_fetched_at") if has_detail_fetched_at else (now_str if has_detail_status else None),
            1 if has_detail_payload else 0,
            1 if has_detail_payload else 0,
        ))

    conn.commit()
    conn.close()

def get_cache_time(key: str) -> int:
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT last_updated FROM cache_metadata WHERE key = ?", (key,))
    row = cursor.fetchone()
    conn.close()
    return row["last_updated"] if row else 0

def update_cache_time(key: str):
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("""
        INSERT INTO cache_metadata (key, last_updated) VALUES (?, ?)
        ON CONFLICT(key) DO UPDATE SET last_updated = excluded.last_updated
    """, (key, int(time.time())))
    conn.commit()
    conn.close()

def is_cache_valid(key: str, ttl_seconds: int) -> bool:
    last_updated = get_cache_time(key)
    return (time.time() - last_updated) < ttl_seconds

def save_directory_entries(kind: str, entries: List[Dict[str, Any]]):
    if not entries:
        return
    conn = get_db_connection()
    cursor = conn.cursor()
    now_str = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    now_int = int(time.time())
    for entry in entries:
        name = repair_mojibake_text(entry.get("name", "")).strip()
        if not name:
            continue
        cursor.execute("""
            INSERT INTO directory_entries (
                kind, name, source_url, cover_url, video_count, latest_release_date, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(kind, name) DO UPDATE SET
                source_url = CASE WHEN excluded.source_url IS NOT NULL AND excluded.source_url != '' THEN excluded.source_url ELSE source_url END,
                cover_url = CASE WHEN excluded.cover_url IS NOT NULL AND excluded.cover_url != '' THEN excluded.cover_url ELSE cover_url END,
                video_count = CASE WHEN excluded.video_count > 0 THEN excluded.video_count ELSE video_count END,
                latest_release_date = COALESCE(excluded.latest_release_date, latest_release_date),
                updated_at = excluded.updated_at
        """, (
            kind,
            name,
            entry.get("source_url", ""),
            entry.get("cover_url"),
            int(entry.get("video_count") or 0),
            entry.get("latest_release_date"),
            entry.get("created_at") or now_str,
            now_int,
        ))
    conn.commit()
    conn.close()

def parse_directory_row(row: sqlite3.Row) -> Dict[str, Any]:
    d = dict(row)
    d["name"] = repair_mojibake_text(d.get("name", ""))
    d["video_count"] = int(d.get("video_count") or 0)
    return d

def query_directory_entries(kind: str, limit: int = 100) -> List[Dict[str, Any]]:
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("""
        SELECT * FROM directory_entries
        WHERE kind = ?
        ORDER BY video_count DESC, updated_at DESC, name ASC
        LIMIT ?
    """, (kind, limit))
    rows = cursor.fetchall()
    conn.close()
    return [parse_directory_row(r) for r in rows]

def query_directory_entry(kind: str, name: str) -> Optional[Dict[str, Any]]:
    conn = get_db_connection()
    cursor = conn.cursor()
    names = actor_aliases(name) if kind == "actor" else [name]
    placeholders = ",".join("?" for _ in names)
    cursor.execute(f"""
        SELECT * FROM directory_entries
        WHERE kind = ? AND name IN ({placeholders})
        ORDER BY video_count DESC, updated_at DESC, name ASC
        LIMIT 1
    """, (kind, *names))
    row = cursor.fetchone()
    conn.close()
    return parse_directory_row(row) if row else None

def count_directory_entries(kind: str) -> int:
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) AS count FROM directory_entries WHERE kind = ?", (kind,))
    row = cursor.fetchone()
    conn.close()
    return int(row["count"]) if row else 0

def parse_video_row(row: sqlite3.Row) -> Dict[str, Any]:
    d = dict(row)
    d["title"] = repair_mojibake_text(d.get("title", ""))
    try:
        d["actors"] = repair_text_list(json.loads(d["actors"]) if d["actors"] else [])
    except Exception:
        d["actors"] = []
    try:
        d["tags"] = repair_text_list(json.loads(d["tags"]) if d["tags"] else [])
    except Exception:
        d["tags"] = []
    try:
        d["categories"] = repair_text_list(json.loads(d["categories"]) if d["categories"] else [])
    except Exception:
        d["categories"] = []
    d["is_active"] = bool(d["is_active"])
    d["watched_count"] = int(d.get("watched_count") or 0)
    return d


def _video_order_clause(sort_by: str = "released_at") -> str:
    normalized = (sort_by or "released_at").strip().lower()
    if normalized in {"views", "watched", "watched_count"}:
        return "watched_count DESC, COALESCE(source_release_date, created_at) DESC, created_at DESC, id DESC"
    return "COALESCE(source_release_date, created_at) DESC, created_at DESC, id DESC"


def _dedupe_fetch_limit(limit: int) -> int:
    if limit <= 0:
        return 0
    return min(max(limit * 4, limit + 20), max(limit, 240))


def _dedupe_url(value: Any) -> str:
    if not isinstance(value, str):
        return ""
    return value.split("#", 1)[0].split("?", 1)[0].strip().rstrip("/").casefold()


def _dedupe_text(value: Any) -> str:
    if not isinstance(value, str):
        return ""
    repaired = repair_mojibake_text(value)
    return repaired.strip().casefold() if isinstance(repaired, str) else ""


def _is_low_information_title(title: str) -> bool:
    return title in {"unknown", "untitled", "未知标题"}


def video_dedupe_key(video: Dict[str, Any], prefer_title: bool = False) -> str:
    vid = str(video.get("id") or video.get("external_id") or "")
    if vid.startswith("51cg_"):
        parts = vid.split("_")
        if len(parts) >= 2:
            return f"51cg_post:{parts[0]}_{parts[1]}"

    source_url = _dedupe_url(video.get("source_url"))
    if source_url and "51cg" in source_url:
        return f"51cg_source:{source_url}"

    title = _dedupe_text(video.get("title"))
    if prefer_title and title and not _is_low_information_title(title):
        return f"title:{title}"

    cover_url = _dedupe_url(video.get("cover_url"))
    if title and cover_url:
        return f"title-cover:{title}|{cover_url}"

    if source_url:
        return f"source:{source_url}"

    video_id = _dedupe_text(video.get("id")) or _dedupe_text(video.get("external_id"))
    return f"id:{video_id}" if video_id else ""


def dedupe_video_results(
    videos: List[Dict[str, Any]],
    limit: Optional[int] = None,
    prefer_title: bool = False
) -> List[Dict[str, Any]]:
    seen = set()
    deduped = []
    for video in videos:
        key = video_dedupe_key(video, prefer_title=prefer_title)
        if key and key in seen:
            continue
        if key:
            seen.add(key)
        deduped.append(video)
        if limit is not None and len(deduped) >= limit:
            break
    return deduped


def query_recent_videos(category: str = "new", limit: int = 20, offset: int = 0, sort_by: str = "released_at") -> List[Dict[str, Any]]:
    conn = get_db_connection()
    cursor = conn.cursor()
    fetch_limit = _dedupe_fetch_limit(limit + offset)
    order_clause = _video_order_clause(sort_by)
    sub_filter = "AND (inventory_status IS NULL OR inventory_status != 'sub_video') AND id NOT GLOB '51cg_*_*'"

    if category == "new" or not category:
        cursor.execute(f"""
            SELECT * FROM videos
            WHERE is_active = 1 {sub_filter} AND (categories LIKE ? OR tags LIKE ?)
            ORDER BY {order_clause}
            LIMIT ?
        """, (f'%"new"%', f'%"new"%', fetch_limit))
        rows = cursor.fetchall()
        # Backward-compatible cold-start fallback: if the database was seeded before
        # source-list categories existed, still return something rather than an
        # empty homepage. Once /new has been scraped, prefer only source-tagged rows.
        if not rows:
            cursor.execute(f"""
                SELECT * FROM videos
                WHERE is_active = 1 {sub_filter}
                ORDER BY {order_clause}
                LIMIT ?
            """, (fetch_limit,))
            rows = cursor.fetchall()
    else:
        # Category queries categories column (JSON array, e.g. ["new", "subtitled"])
        cursor.execute(f"""
            SELECT * FROM videos
            WHERE is_active = 1 {sub_filter} AND (categories LIKE ? OR tags LIKE ?)
            ORDER BY {order_clause}
            LIMIT ?
        """, (f'%"{category}"%', f'%"{category}"%', fetch_limit))
        rows = cursor.fetchall()

    conn.close()
    return dedupe_video_results([parse_video_row(r) for r in rows], limit + offset, prefer_title=True)[offset:offset + limit]

def query_videos_by_actor(actor: str, limit: int = 20, offset: int = 0, sort_by: str = "released_at") -> List[Dict[str, Any]]:
    aliases = actor_aliases(actor)
    if not aliases:
        return []
    conn = get_db_connection()
    cursor = conn.cursor()
    fetch_limit = _dedupe_fetch_limit(limit + offset)
    order_clause = _video_order_clause(sort_by)
    clauses = []
    params: list[Any] = []
    for alias in aliases:
        clauses.append("actors LIKE ?")
        params.append(f'%"{alias}"%')
        clauses.append("title LIKE ?")
        params.append(f"%{alias}%")
    cursor.execute(f"""
        SELECT * FROM videos
        WHERE is_active = 1 AND ({' OR '.join(clauses)})
        ORDER BY {order_clause}
        LIMIT ?
    """, (*params, fetch_limit))
    rows = cursor.fetchall()
    conn.close()
    return dedupe_video_results([parse_video_row(r) for r in rows], limit + offset, prefer_title=True)[offset:offset + limit]

def query_video_by_id(video_id: str) -> Optional[Dict[str, Any]]:
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM videos WHERE id = ?", (video_id,))
    row = cursor.fetchone()
    conn.close()
    return parse_video_row(row) if row else None

def query_videos_missing_actors(limit: int = 10) -> List[Dict[str, Any]]:
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("""
        SELECT * FROM videos
        WHERE is_active = 1
          AND source_url IS NOT NULL
          AND source_url != ''
          AND (actors IS NULL OR actors = '' OR actors = '[]')
        ORDER BY source_release_date DESC, created_at DESC, id DESC
        LIMIT ?
    """, (limit,))
    rows = cursor.fetchall()
    conn.close()
    return [parse_video_row(r) for r in rows]

def search_videos(query: str, limit: int = 20, offset: int = 0) -> List[Dict[str, Any]]:
    conn = get_db_connection()
    cursor = conn.cursor()
    q = f"%{query}%"
    fetch_limit = _dedupe_fetch_limit(limit + offset)
    sub_filter = "AND (inventory_status IS NULL OR inventory_status != 'sub_video') AND id NOT GLOB '51cg_*_*'"
    cursor.execute(f"""
        SELECT * FROM videos
        WHERE is_active = 1 {sub_filter} AND (
            id LIKE ? OR external_id LIKE ? OR source_url LIKE ?
            OR title LIKE ? OR actors LIKE ? OR tags LIKE ?
        )
        ORDER BY source_release_date DESC, created_at DESC, id DESC
        LIMIT ?
    """, (q, q, q, q, q, q, fetch_limit))
    rows = cursor.fetchall()
    conn.close()
    return dedupe_video_results([parse_video_row(r) for r in rows], limit + offset, prefer_title=True)[offset:offset + limit]

def query_actor_aggregates(limit: int = 20) -> List[Dict[str, Any]]:
    directory_rows = query_directory_entries("actor", limit)
    if directory_rows:
        return [
            {
                "actor": row["name"],
                "cover_url": row.get("cover_url"),
                "video_count": row.get("video_count", 0),
                "latest_release_date": row.get("latest_release_date"),
                "source_url": row.get("source_url"),
            }
            for row in directory_rows
        ]

    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT actors, title, cover_url, source_release_date FROM videos WHERE is_active = 1")
    rows = cursor.fetchall()
    conn.close()

    actor_stats = {}
    for r in rows:
        try:
            actors = json.loads(r["actors"]) if r["actors"] else []
        except Exception:
            continue
        if not actors:
            actors = infer_actors_from_title(r["title"])
        cover = r["cover_url"]
        date = r["source_release_date"]

        for actor in actors:
            actor = repair_mojibake_text(actor).strip()
            if not actor or len(actor) <= 1:
                continue
            if actor not in actor_stats:
                actor_stats[actor] = {
                    "actor": actor,
                    "cover_url": cover,
                    "video_count": 0,
                    "latest_release_date": date
                }
            stats = actor_stats[actor]
            stats["video_count"] += 1
            if cover and (not stats["cover_url"] or stats["cover_url"].startswith("data:image")):
                stats["cover_url"] = cover
            if date and (not stats["latest_release_date"] or date > stats["latest_release_date"]):
                stats["latest_release_date"] = date

    sorted_actors = sorted(
        actor_stats.values(),
        key=lambda x: (-x["video_count"], -(1 if x["latest_release_date"] else 0), x["latest_release_date"] or "", x["actor"])
    )
    return sorted_actors[:limit]

def query_tag_aggregates(limit: int = 30) -> List[Dict[str, Any]]:
    directory_rows = query_directory_entries("genre", limit)
    if directory_rows:
        return [
            {
                "tag": row["name"],
                "video_count": row.get("video_count", 0),
                "latest_release_date": row.get("latest_release_date"),
                "source_url": row.get("source_url"),
            }
            for row in directory_rows
        ]

    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT tags, source_release_date FROM videos WHERE is_active = 1")
    rows = cursor.fetchall()
    conn.close()

    tag_stats = {}
    for r in rows:
        try:
            tags = json.loads(r["tags"]) if r["tags"] else []
        except Exception:
            continue
        date = r["source_release_date"]

        for tag in tags:
            tag = repair_mojibake_text(tag).strip()
            if not tag or len(tag) <= 1:
                continue
            if tag not in tag_stats:
                tag_stats[tag] = {
                    "tag": tag,
                    "video_count": 0,
                    "latest_release_date": date
                }
            stats = tag_stats[tag]
            stats["video_count"] += 1
            if date and (not stats["latest_release_date"] or date > stats["latest_release_date"]):
                stats["latest_release_date"] = date

    sorted_tags = sorted(
        tag_stats.values(),
        key=lambda x: (-x["video_count"], -(1 if x["latest_release_date"] else 0), x["latest_release_date"] or "", x["tag"])
    )
    return sorted_tags[:limit]


# --- Enhancement schema and services ---
def ensure_enhancement_schema():
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS refresh_jobs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            kind TEXT NOT NULL,
            job_key TEXT NOT NULL,
            status TEXT NOT NULL DEFAULT 'queued',
            attempt_count INTEGER DEFAULT 0,
            payload_count INTEGER DEFAULT 0,
            source_url TEXT,
            last_error TEXT,
            created_at INTEGER NOT NULL,
            started_at INTEGER,
            finished_at INTEGER,
            duration_ms INTEGER
        )
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_refresh_jobs_kind_key ON refresh_jobs(kind, job_key, created_at DESC)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_refresh_jobs_status ON refresh_jobs(status, created_at DESC)")
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS user_video_actions (
            user_id TEXT NOT NULL,
            video_id TEXT NOT NULL,
            action TEXT NOT NULL,
            value INTEGER NOT NULL DEFAULT 1,
            updated_at INTEGER NOT NULL,
            PRIMARY KEY (user_id, video_id, action)
        )
    """)
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS watch_history (
            user_id TEXT NOT NULL,
            video_id TEXT NOT NULL,
            position_seconds INTEGER DEFAULT 0,
            duration_seconds INTEGER DEFAULT 0,
            updated_at INTEGER NOT NULL,
            PRIMARY KEY (user_id, video_id)
        )
    """)
    cursor.execute("""
        CREATE VIRTUAL TABLE IF NOT EXISTS videos_fts USING fts5(
            video_id UNINDEXED,
            title,
            actors,
            tags,
            categories,
            content=''
        )
    """)
    conn.commit()
    conn.close()


def _now_int() -> int:
    return int(time.time())


def _row_to_dict(row):
    return dict(row) if row is not None else None


def create_refresh_job(kind: str, job_key: str, source_url: str | None = None) -> int:
    ensure_enhancement_schema()
    now = _now_int()
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("""
        INSERT INTO refresh_jobs(kind, job_key, status, source_url, created_at)
        VALUES (?, ?, 'queued', ?, ?)
    """, (kind, job_key, source_url, now))
    job_id = int(cursor.lastrowid)
    conn.commit(); conn.close()
    return job_id


def start_refresh_job(job_id: int):
    ensure_enhancement_schema()
    now = _now_int()
    conn = get_db_connection(); cursor = conn.cursor()
    cursor.execute("""
        UPDATE refresh_jobs
        SET status='running', started_at=COALESCE(started_at, ?), attempt_count=attempt_count + 1
        WHERE id=?
    """, (now, job_id))
    cursor.execute("SELECT * FROM refresh_jobs WHERE id=?", (job_id,))
    row = cursor.fetchone(); conn.commit(); conn.close()
    return _row_to_dict(row)


def finish_refresh_job(job_id: int, status: str, payload_count: int = 0, error: str | None = None):
    ensure_enhancement_schema()
    status = status if status in {"success", "empty", "error", "cancelled"} else "error"
    now = _now_int()
    conn = get_db_connection(); cursor = conn.cursor()
    cursor.execute("SELECT started_at FROM refresh_jobs WHERE id=?", (job_id,))
    row = cursor.fetchone()
    started_at = int(row["started_at"] or now) if row else now
    duration_ms = max(0, int((now - started_at) * 1000))
    cursor.execute("""
        UPDATE refresh_jobs
        SET status=?, payload_count=?, last_error=?, finished_at=?, duration_ms=?
        WHERE id=?
    """, (status, int(payload_count or 0), error, now, duration_ms, job_id))
    cursor.execute("SELECT * FROM refresh_jobs WHERE id=?", (job_id,))
    out = cursor.fetchone(); conn.commit(); conn.close()
    return _row_to_dict(out)


def list_refresh_jobs(limit: int = 50, status: str | None = None, kind: str | None = None):
    ensure_enhancement_schema()
    limit = max(1, min(int(limit or 50), 500))
    clauses=[]; params=[]
    if status:
        clauses.append("status=?"); params.append(status)
    if kind:
        clauses.append("kind=?"); params.append(kind)
    where = " WHERE " + " AND ".join(clauses) if clauses else ""
    conn=get_db_connection(); cursor=conn.cursor()
    cursor.execute(f"SELECT * FROM refresh_jobs{where} ORDER BY created_at DESC, id DESC LIMIT ?", (*params, limit))
    rows=[dict(r) for r in cursor.fetchall()]
    conn.close(); return rows


def refresh_job_metrics():
    ensure_enhancement_schema()
    conn=get_db_connection(); cursor=conn.cursor()
    cursor.execute("SELECT count(*) AS n FROM refresh_jobs")
    total=cursor.fetchone()["n"]
    cursor.execute("SELECT status, count(*) AS n FROM refresh_jobs GROUP BY status")
    by_status={r["status"]: r["n"] for r in cursor.fetchall()}
    cursor.execute("SELECT kind, count(*) AS n FROM refresh_jobs GROUP BY kind")
    by_kind={r["kind"]: r["n"] for r in cursor.fetchall()}
    cursor.execute("SELECT kind, avg(duration_ms) AS avg_ms FROM refresh_jobs WHERE duration_ms IS NOT NULL GROUP BY kind")
    avg_duration_ms={r["kind"]: int(r["avg_ms"] or 0) for r in cursor.fetchall()}
    conn.close()
    return {"total": total, "by_status": by_status, "by_kind": by_kind, "avg_duration_ms": avg_duration_ms}


def db_counts():
    ensure_enhancement_schema()
    conn=get_db_connection(); cursor=conn.cursor(); out={}
    for table in ["videos", "directory_entries", "cache_metadata", "refresh_jobs", "user_video_actions", "watch_history"]:
        cursor.execute(f"SELECT count(*) AS n FROM {table}")
        out[table] = cursor.fetchone()["n"]
    conn.close(); return out


def _contains_all(values, required):
    values = set(values or [])
    return all(item in values for item in (required or []))


def _match_reason(video, query: str, actor: str | None, tags: list[str], categories: list[str]) -> str:
    q=(query or '').lower()
    if actor and actor in video.get('actors', []): return 'actor'
    if tags and any(t in video.get('tags', []) for t in tags): return 'tag'
    if categories and any(c in video.get('categories', []) for c in categories): return 'category'
    if q and q in str(video.get('title','')).lower(): return 'title'
    if q and q in str(video.get('id','')).lower(): return 'id'
    return 'fulltext' if q else 'filter'


def search_videos_advanced(query: str = '', actor: str | None = None, tags: list[str] | None = None, categories: list[str] | None = None, limit: int = 20, offset: int = 0, sort_by: str = 'released_at', include_facets: bool = False):
    limit=max(1,min(int(limit or 20),200)); offset=max(0,int(offset or 0))
    tags=tags or []; categories=categories or []
    q=(query or '').strip()
    fetch_limit=min(MAX_OFFSET if 'MAX_OFFSET' in globals() else 5000, offset + limit * 10 + 100)
    if actor:
        candidates=query_videos_by_actor(actor, fetch_limit, 0, sort_by=sort_by)
    elif q:
        candidates=search_videos(q, fetch_limit, 0)
    elif categories:
        candidates=query_recent_videos(categories[0], fetch_limit, 0, sort_by=sort_by)
    else:
        candidates=query_recent_videos('new', fetch_limit, 0, sort_by=sort_by)
    items=[]
    q_lower=q.lower()
    for video in candidates:
        if actor and actor not in video.get('actors', []) and actor not in video.get('title',''):
            continue
        if tags and not _contains_all(video.get('tags', []), tags):
            continue
        if categories and not _contains_all(video.get('categories', []), categories):
            continue
        if q and not any(q_lower in str(video.get(field,'')).lower() for field in ['id','title','source_url']) and not any(q_lower in str(x).lower() for x in video.get('actors', []) + video.get('tags', []) + video.get('categories', [])):
            continue
        video=dict(video)
        video['match_reason']=_match_reason(video, q, actor, tags, categories)
        items.append(video)
    total=len(items)
    page_items=items[offset:offset+limit]
    result={"items": page_items, "count": len(page_items), "total": total, "limit": limit, "offset": offset}
    if include_facets:
        facets={"actors":{}, "tags":{}, "categories":{}}
        for video in items:
            for key in facets:
                for value in video.get(key, []):
                    facets[key][value]=facets[key].get(value,0)+1
        result['facets']=facets
    return result


def set_user_video_action(user_id: str, video_id: str, action: str, value: bool = True):
    ensure_enhancement_schema(); now=_now_int()
    user_id=(user_id or 'default').strip()[:64]; action=(action or '').strip()[:32]
    conn=get_db_connection(); cursor=conn.cursor()
    cursor.execute("""
        INSERT INTO user_video_actions(user_id, video_id, action, value, updated_at)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT(user_id, video_id, action) DO UPDATE SET value=excluded.value, updated_at=excluded.updated_at
    """, (user_id, video_id, action, 1 if value else 0, now))
    conn.commit(); conn.close()
    return {"user_id": user_id, "video_id": video_id, "action": action, "value": bool(value), "updated_at": now}


def list_user_video_actions(user_id: str = 'default', action: str | None = None, limit: int = 100):
    ensure_enhancement_schema(); limit=max(1,min(int(limit or 100),500))
    params=[user_id or 'default']; where="user_id=? AND value=1"
    if action:
        where += " AND action=?"; params.append(action)
    conn=get_db_connection(); cursor=conn.cursor()
    cursor.execute(f"SELECT * FROM user_video_actions WHERE {where} ORDER BY updated_at DESC LIMIT ?", (*params, limit))
    rows=[dict(r) for r in cursor.fetchall()]; conn.close(); return rows


def record_watch_progress(user_id: str, video_id: str, position_seconds: int = 0, duration_seconds: int = 0):
    ensure_enhancement_schema(); now=_now_int()
    conn=get_db_connection(); cursor=conn.cursor()
    cursor.execute("""
        INSERT INTO watch_history(user_id, video_id, position_seconds, duration_seconds, updated_at)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT(user_id, video_id) DO UPDATE SET position_seconds=excluded.position_seconds, duration_seconds=excluded.duration_seconds, updated_at=excluded.updated_at
    """, (user_id or 'default', video_id, int(position_seconds or 0), int(duration_seconds or 0), now))
    conn.commit(); conn.close()
    return {"user_id": user_id or 'default', "video_id": video_id, "position_seconds": int(position_seconds or 0), "duration_seconds": int(duration_seconds or 0), "updated_at": now}


def get_video_playback(video_id: str, episode_index: int | None = None):
    # If episode_index is given (> 1), map to sub-video id if applicable
    target_id = video_id
    if episode_index and episode_index > 1:
        if "51cg_" in video_id and not video_id.endswith(f"_{episode_index}"):
            parts = video_id.split("_")
            if len(parts) >= 2:
                base_id = f"{parts[0]}_{parts[1]}"
                target_id = f"{base_id}_{episode_index}"

    video = query_video_by_id(target_id)
    if not video:
        # Fallback to main video if sub-video not found
        video = query_video_by_id(video_id)
        if not video:
            return None

    duration = video.get('duration') or ''
    is_url = str(duration).startswith(('http://','https://'))
    playback_headers = {
        "Referer": video.get('source_url') or "",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    }

    # Find all sub-videos for this base post if 51cg
    sub_videos = []
    if "51cg_" in video_id:
        parts = video_id.split("_")
        if len(parts) >= 2:
            base_id = f"{parts[0]}_{parts[1]}"
            conn = get_db_connection()
            c = conn.cursor()
            c.execute("SELECT id, title, duration FROM videos WHERE (id = ? OR id GLOB ?) ORDER BY id ASC", (base_id, f"{base_id}_*"))
            rows = c.fetchall()
            conn.close()
            for idx, r in enumerate(rows):
                r_dur = r["duration"] or ""
                r_url = r_dur if str(r_dur).startswith(('http://','https://')) else None
                sub_videos.append({
                    "index": idx + 1,
                    "id": r["id"],
                    "title": r["title"] or f"视频 {idx+1}",
                    "raw_playback_url": r_url,
                    "is_ready": bool(r_url),
                })

    return {
        "video_id": target_id,
        "base_video_id": f"{video_id.split('_')[0]}_{video_id.split('_')[1]}" if "51cg_" in video_id else video_id,
        "playback_url": duration if is_url else None,
        "source_url": video.get('source_url'),
        "referer": video.get('source_url'),
        "headers": playback_headers if is_url else {},
        "source_type": "hls" if '.m3u8' in str(duration).lower() else ("url" if is_url else "unknown"),
        "detail_status": video.get('detail_status'),
        "is_ready": bool(is_url),
        "video_count": len(sub_videos) if sub_videos else 1,
        "videos": sub_videos if sub_videos else [],
    }


def recommend_similar_videos(video_id: str, limit: int = 20):
    base=query_video_by_id(video_id)
    if not base: return []
    limit=max(1,min(int(limit or 20),100))
    seeds=list(dict.fromkeys((base.get('actors') or []) + (base.get('tags') or []) + (base.get('categories') or [])))
    candidates=[]; seen={video_id}
    for seed in seeds:
        for video in search_videos(seed, 50, 0):
            if video.get('id') in seen: continue
            score=len(set(base.get('actors',[])) & set(video.get('actors',[]))) * 5 + len(set(base.get('tags',[])) & set(video.get('tags',[]))) * 2 + len(set(base.get('categories',[])) & set(video.get('categories',[])))
            if score<=0: continue
            item=dict(video); item['recommendation_score']=score; item['recommendation_reason']=seed
            candidates.append(item); seen.add(video.get('id'))
    candidates.sort(key=lambda v: (v.get('recommendation_score',0), v.get('source_release_date') or '', v.get('created_at') or ''), reverse=True)
    return candidates[:limit]


def data_quality_summary():
    ensure_enhancement_schema(); conn=get_db_connection(); cursor=conn.cursor()
    queries={
        'total_videos': "SELECT count(*) AS n FROM videos WHERE is_active=1",
        'missing_cover': "SELECT count(*) AS n FROM videos WHERE is_active=1 AND (cover_url IS NULL OR cover_url='')",
        'missing_actors': "SELECT count(*) AS n FROM videos WHERE is_active=1 AND (actors IS NULL OR actors='' OR actors='[]')",
        'pending_details': "SELECT count(*) AS n FROM videos WHERE is_active=1 AND coalesce(detail_status,'pending')!='success'",
        'missing_playback': "SELECT count(*) AS n FROM videos WHERE is_active=1 AND (duration IS NULL OR duration='' OR duration NOT LIKE 'http%')",
    }
    out={}
    for key, q in queries.items():
        cursor.execute(q); out[key]=cursor.fetchone()['n']
    cursor.execute("SELECT title, count(*) AS n FROM videos WHERE is_active=1 AND title IS NOT NULL AND title!='' GROUP BY title HAVING count(*)>1")
    out['duplicate_titles']=len(cursor.fetchall())
    conn.close(); return out


def source_status_summary():
    ensure_enhancement_schema(); conn=get_db_connection(); cursor=conn.cursor()
    cursor.execute("""
        SELECT kind, status, count(*) AS n, max(finished_at) AS last_finished_at
        FROM refresh_jobs GROUP BY kind, status
    """)
    jobs=[dict(r) for r in cursor.fetchall()]
    conn.close()
    return {"jobs": jobs, "cache": refresh_job_metrics()}


def maintenance_analyze():
    conn=get_db_connection(); conn.execute('ANALYZE'); conn.commit(); conn.close(); return {"status":"ok", "operation":"analyze"}


def maintenance_vacuum():
    conn=get_db_connection(); conn.execute('VACUUM'); conn.close(); return {"status":"ok", "operation":"vacuum"}
