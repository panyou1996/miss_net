from playwright.async_api import async_playwright
from playwright_stealth import Stealth
from supabase import create_client, Client
from dotenv import load_dotenv
import os
import asyncio
import json
import random
import re
from datetime import datetime, timezone
from urllib.parse import parse_qsl, urlencode, urlparse, urlunparse

# Load environment variables from .env file if present
load_dotenv()

# Category Mapping Rules
CATEGORY_MAP = {
    "school": ["校园", "学生", "制服", "女教师", "学校", "女学生", "School", "Student", "女高中生"],
    "office": ["OL", "职场", "公司", "秘书", "同事", "Office", "Business"],
    "mature": ["熟女", "人妻", "妈妈", "姨", "Mature", "Milf", "Married Woman"],
    "subtitled": ["中文字幕", "中文", "Subtitles", "Chinese"],
    "exclusive": ["独家", "Exclusive", "獨家"],
    "nympho": ["痴女", "Nympho", "淫亂", "淫乱"],
    "voyeur": ["偷拍", "盗撮", "Voyeur", "自拍"],
    "sister": ["姐姐", "姐", "Big Sister"],
    "story": ["剧情", "Drama", "Story", "劇情"],
    "amateur": ["素人", "业余", "Amateur"],
    "big_tits": ["巨乳", "大胸", "Big Tits", "美乳"],
    "creampie": ["中出", "Creampie"],
    "single": ["单体", "單體作品", "Single"],
    "beautiful": ["美少女", "Girl"],
    "oral": ["口交", "Oral"],
    "group": ["多人", "多人数", "多人運動"],
}

TAXONOMY_ALIASES = {
    "new": "new",
    "new_release": "new",
    "new_releases": "new",
    "monthly_hot": "monthly_hot",
    "monthly": "monthly_hot",
    "weekly_hot": "weekly_hot",
    "weekly": "weekly_hot",
    "uncensored": "uncensored",
    "subtitled": "subtitled",
    "subtitles": "subtitled",
    "chinese_subtitle": "subtitled",
    "中文字幕": "subtitled",
    "exclusive": "exclusive",
    "creampie": "creampie",
    "single": "single",
    "bigtits": "big_tits",
    "big_tits": "big_tits",
    "mature": "mature",
    "amateur": "amateur",
    "beautiful": "beautiful",
    "oral": "oral",
    "group": "group",
    "nympho": "nympho",
    "school": "school",
    "voyeur": "voyeur",
    "story": "story",
    "sister": "sister",
    "office": "office",
    "pov": "pov",
    "vr": "vr",
    "51cg": "51cg",
    "51mrds": "51mrds",
}

# Target URLs
SOURCES = [
    {"url": "https://missav.ws/new", "tag": "new"},
    {"url": "https://missav.ws/dm263/monthly-hot?sort=monthly_views", "tag": "monthly_hot"},
    {"url": "https://missav.ws/dm169/weekly-hot?sort=weekly_views", "tag": "weekly_hot"},
    {"url": "https://missav.ws/dm628/uncensored-leak", "tag": "uncensored"},
    {"url": "https://missav.ws/chinese-subtitle", "tag": "subtitled"},
    {"url": "https://missav.ws/dm136/genres/%E7%8D%A8%E5%AE%B6", "tag": "exclusive"},
    {"url": "https://missav.ws/dm127/genres/%E4%B8%AD%E5%87%BA", "tag": "creampie"},
    {"url": "https://missav.ws/dm119/genres/%E5%96%AE%E9%AB%94%E4%BD%9C%E5%93%81", "tag": "single"},
    {"url": "https://missav.ws/dm114/genres/%E5%B7%A8%E4%B9%B3", "tag": "big_tits"},
    {"url": "https://missav.ws/dm68/genres/%E4%BA%BA%E5%A6%BB", "tag": "mature"},
    {"url": "https://missav.ws/dm107/genres/%E7%86%9F%E5%A5%B3", "tag": "mature"},
    {"url": "https://missav.ws/dm98/genres/%E7%B4%A0%E4%BA%BA", "tag": "amateur"},
    {"url": "https://missav.ws/dm434/genres/%E7%BE%8E%E5%B0%91%E5%A5%B3", "tag": "beautiful"},
    {"url": "https://missav.ws/dm1295/genres/%E5%8F%A3%E4%BA%A4", "tag": "oral"},
    {"url": "https://missav.ws/dm311/genres/%E5%A4%9A%E4%BA%BA%E9%81%8B%E5%8B%95", "tag": "group"},
    {"url": "https://missav.ws/dm312/genres/%E7%97%B4%E5%A5%B3", "tag": "nympho"},
    {"url": "https://missav.ws/dm4420/genres/%E5%A5%B3%E9%AB%98%E4%B8%AD%E7%94%9F", "tag": "school"},
    {"url": "https://missav.ws/dm483/genres/%E5%81%B7%E6%8B%8D", "tag": "voyeur"},
    {"url": "https://missav.ws/dm94/genres/%E5%8A%87%E6%83%85", "tag": "story"},
    {"url": "https://missav.ws/dm784/genres/%E5%A7%90%E5%A7%90", "tag": "sister"},
    {"url": "https://missav.ws/dm253/genres/%E5%B1%81%E8%82%A1%E5%81%8F%E5%A5%BD", "tag": "sister"},
    {"url": "https://missav.ws/dm546/genres/%E4%B8%BB%E8%A7%80%E8%A6%96%E8%A7%92", "tag": "pov"},
    {"url": "https://missav.ws/dm45/genres/%E6%B7%AB%E8%AA%9E", "tag": "subtitled"},
    {"url": "https://missav.ws/dm467/genres/%E7%B5%B2%E8%A5%AA", "tag": "exclusive"}
]

# Supabase Setup
SUPABASE_URL = os.environ.get("SUPABASE_URL")
SUPABASE_KEY = os.environ.get("SUPABASE_KEY")
USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"


def env_positive_int(name: str, default: int) -> int:
    value = os.environ.get(name)
    if value is None:
        return default
    try:
        return max(1, int(value))
    except ValueError:
        print(f"[Config] Invalid {name}={value}, fallback to {default}")
        return default


def env_non_negative_float(name: str, default: float) -> float:
    value = os.environ.get(name)
    if value is None:
        return default
    try:
        return max(0.0, float(value))
    except ValueError:
        print(f"[Config] Invalid {name}={value}, fallback to {default}")
        return default


def env_bool(name: str, default: bool) -> bool:
    value = os.environ.get(name)
    if value is None:
        return default
    return value.strip().lower() in ("1", "true", "yes", "on")


def env_csv(name: str) -> list[str]:
    value = os.environ.get(name, "")
    if not value:
        return []
    return [item.strip() for item in value.split(",") if item.strip()]


MISSAV_MAX_PAGES = env_positive_int("MISSAV_MAX_PAGES", 50)
CG_MAX_PAGES = env_positive_int("CG_MAX_PAGES", 3)
CONCURRENT_DETAIL_PAGES = env_positive_int("CONCURRENT_DETAIL_PAGES", 4)
SUPABASE_UPSERT_CHUNK_SIZE = env_positive_int("SUPABASE_UPSERT_CHUNK_SIZE", 150)
SUPABASE_MAX_RETRIES = env_positive_int("SUPABASE_MAX_RETRIES", 4)
SUPABASE_RETRY_BASE_SECONDS = env_non_negative_float("SUPABASE_RETRY_BASE_SECONDS", 1.2)
DETAIL_PRE_NAV_DELAY_MIN = env_non_negative_float("DETAIL_PRE_NAV_DELAY_MIN", 0.15)
DETAIL_PRE_NAV_DELAY_MAX = env_non_negative_float("DETAIL_PRE_NAV_DELAY_MAX", 0.45)
DETAIL_POST_LOAD_DELAY_MIN = env_non_negative_float("DETAIL_POST_LOAD_DELAY_MIN", 0.15)
DETAIL_POST_LOAD_DELAY_MAX = env_non_negative_float("DETAIL_POST_LOAD_DELAY_MAX", 0.45)
LIST_POST_LOAD_DELAY_MIN = env_non_negative_float("LIST_POST_LOAD_DELAY_MIN", 0.2)
LIST_POST_LOAD_DELAY_MAX = env_non_negative_float("LIST_POST_LOAD_DELAY_MAX", 0.6)
INTER_PAGE_DELAY_MIN = env_non_negative_float("INTER_PAGE_DELAY_MIN", 0.5)
INTER_PAGE_DELAY_MAX = env_non_negative_float("INTER_PAGE_DELAY_MAX", 1.6)
BLOCK_HEAVY_RESOURCES = env_bool("BLOCK_HEAVY_RESOURCES", True)
BLOCKED_RESOURCE_TYPES = {"image", "media", "font"}
EARLY_STOP_STREAK = env_positive_int("EARLY_STOP_STREAK", 3)
EARLY_STOP_MIN_PAGE = env_positive_int("EARLY_STOP_MIN_PAGE", 5)
SCRAPER_RUN_MODE = os.environ.get("SCRAPER_RUN_MODE", "full").strip().lower()
SCRAPER_SOURCE_TAGS = env_csv("SCRAPER_SOURCE_TAGS")
SKIP_51CG = env_bool("SKIP_51CG", False)


def ordered_unique(items):
    seen = set()
    output = []
    for item in items:
        if not item:
            continue
        text = str(item).strip()
        if text and text not in seen:
            seen.add(text)
            output.append(text)
    return output


def canonicalize_taxonomy_value(value: str):
    if value is None:
        return None
    text = str(value).strip()
    if not text:
        return None

    lowered = text.lower()
    snake = re.sub(r"[^a-z0-9]+", "_", lowered).strip("_")

    for key in (text, lowered, snake):
        if key in TAXONOMY_ALIASES:
            return TAXONOMY_ALIASES[key]

    return text


def normalize_taxonomy_values(items):
    normalized = []
    for item in items or []:
        value = canonicalize_taxonomy_value(item)
        if value:
            normalized.append(value)
    return ordered_unique(normalized)


def normalize_duration_text(value):
    if value is None:
        return None
    text = str(value).strip()
    if not text or text in {"0", "00:00", "00:00:00"} or text.lower() == "unknown":
        return None
    return text


def normalize_release_date_text(value):
    if value is None:
        return None
    text = str(value).strip()
    if not text or text.lower() == "unknown":
        return None
    return text


def normalize_cover_url(value):
    if value is None:
        return None
    text = str(value).strip()
    if not text:
        return None
    if text.startswith("//"):
        text = f"https:{text}"
    lowered = text.lower()
    if lowered.startswith("data:image") or lowered.startswith("blob:") or lowered.startswith("about:blank"):
        return None
    if not (lowered.startswith("http://") or lowered.startswith("https://")):
        return None
    if "cover-t.jpg" in text:
        text = text.replace("cover-t.jpg", "cover-n.jpg")
    return text


def looks_like_placeholder_cover(value):
    if value is None:
        return False
    text = str(value).strip().lower()
    return text.startswith("data:image") or text.startswith("blob:") or text.startswith("about:blank")


def make_run_stats():
    return {
        "pages_scanned": 0,
        "discovered_count": 0,
        "new_external_count": 0,
        "existing_complete_count": 0,
        "detail_attempted_count": 0,
        "detail_success_count": 0,
        "detail_fail_count": 0,
        "blocked_count": 0,
        "upserted_count": 0,
        "placeholder_cover_count": 0,
    }


def merge_stats(target: dict, delta: dict | None):
    if not delta:
        return target
    for key in target.keys():
        target[key] += int(delta.get(key, 0))
    return target


def resolve_run_configuration():
    selected_tags = set(SCRAPER_SOURCE_TAGS)

    if SCRAPER_RUN_MODE == "sample":
        if not selected_tags:
            selected_tags = {"new", "weekly_hot", "monthly_hot"}
        missav_pages = min(MISSAV_MAX_PAGES, 5)
        cg_pages = min(CG_MAX_PAGES, 1)
        early_stop_streak = min(EARLY_STOP_STREAK, 2)
        early_stop_min_page = min(EARLY_STOP_MIN_PAGE, 3)
    else:
        missav_pages = MISSAV_MAX_PAGES
        cg_pages = CG_MAX_PAGES
        early_stop_streak = EARLY_STOP_STREAK
        early_stop_min_page = EARLY_STOP_MIN_PAGE

    missav_sources = [source for source in SOURCES if not selected_tags or source["tag"] in selected_tags]
    run_51cg_main = not SKIP_51CG and (not selected_tags or "51cg" in selected_tags)
    run_51cg_mrds = not SKIP_51CG and (not selected_tags or "51mrds" in selected_tags)

    return {
        "mode": SCRAPER_RUN_MODE,
        "selected_tags": sorted(selected_tags),
        "missav_pages": missav_pages,
        "cg_pages": cg_pages,
        "early_stop_streak": early_stop_streak,
        "early_stop_min_page": early_stop_min_page,
        "missav_sources": missav_sources,
        "run_51cg_main": run_51cg_main,
        "run_51cg_mrds": run_51cg_mrds,
    }


def merge_video_record(video: dict, existing: dict | None) -> dict:
    if not existing:
        return video

    merged = dict(video)

    if (not merged.get("title") or len(str(merged.get("title")).strip()) < 2) and existing.get("title"):
        merged["title"] = existing.get("title")

    if not merged.get("source_url") and existing.get("source_url"):
        merged["source_url"] = existing.get("source_url")

    merged["source_site"] = merged.get("source_site") or existing.get("source_site") or infer_source_site(merged.get("source_url"))
    merged["cover_url"] = normalize_cover_url(merged.get("cover_url")) or normalize_cover_url(existing.get("cover_url"))
    merged["duration"] = normalize_duration_text(merged.get("duration")) or normalize_duration_text(existing.get("duration"))
    merged["release_date"] = normalize_release_date_text(merged.get("release_date")) or normalize_release_date_text(existing.get("release_date"))
    merged["actors"] = ordered_unique((existing.get("actors") or []) + (merged.get("actors") or []))
    merged["tags"] = normalize_taxonomy_values((existing.get("tags") or []) + (merged.get("tags") or []))
    merged["categories"] = normalize_taxonomy_values((existing.get("categories") or []) + (merged.get("categories") or []))

    return merged


def should_fetch_details(existing: dict | None) -> bool:
    if not existing:
        return True

    duration = normalize_duration_text(existing.get("duration"))
    release_date = normalize_release_date_text(existing.get("release_date"))
    actors = ordered_unique(existing.get("actors") or [])

    return not (duration and (release_date or actors))


def infer_source_site(source_url: str | None, fallback: str = "missav") -> str:
    url = (source_url or "").lower()
    if "51cg1.com" in url or "51cg" in url:
        return "51cg"
    if "missav" in url or "fourhoi.com" in url:
        return "missav"
    return fallback


DURATION_PATTERNS = [
    re.compile(r"(?:时长|時長|duration|片长|片長)\s*[:：]?\s*([0-9]{1,3}\s*(?:分鐘|分钟|分|min|mins|minutes))", re.IGNORECASE),
    re.compile(r"(?:时长|時長|duration|片长|片長)\s*[:：]?\s*([0-9]{1,2}:\d{2}(?::\d{2})?)", re.IGNORECASE),
]


def extract_duration_from_text(raw_text: str | None):
    if not raw_text:
        return None

    normalized = re.sub(r"\s+", " ", raw_text)
    for pattern in DURATION_PATTERNS:
        match = pattern.search(normalized)
        if match:
            return normalize_duration_text(match.group(1))

    return None


def build_paged_url(base_url: str, page_num: int) -> str:
    parsed = urlparse(base_url)
    query = dict(parse_qsl(parsed.query, keep_blank_values=True))
    query["page"] = str(page_num)
    return urlunparse(parsed._replace(query=urlencode(query)))


def normalize_video_record(video: dict) -> dict:
    record = dict(video)
    record["is_active"] = True
    record["source_site"] = record.get("source_site") or infer_source_site(record.get("source_url"))
    record["tags"] = normalize_taxonomy_values(record.get("tags", []))
    record["categories"] = normalize_taxonomy_values(record.get("categories", []))
    record["actors"] = ordered_unique(record.get("actors", []))
    record["duration"] = normalize_duration_text(record.get("duration"))
    record["release_date"] = normalize_release_date_text(record.get("release_date"))
    record["cover_url"] = normalize_cover_url(record.get("cover_url"))
    return record


def chunked(items, size):
    for i in range(0, len(items), size):
        yield items[i:i + size]


async def jitter_sleep(min_seconds: float, max_seconds: float):
    if max_seconds <= 0:
        return
    if max_seconds < min_seconds:
        min_seconds, max_seconds = max_seconds, min_seconds
    await asyncio.sleep(random.uniform(min_seconds, max_seconds))


def backoff_seconds(attempt: int, base_seconds: float = SUPABASE_RETRY_BASE_SECONDS) -> float:
    return base_seconds * (2 ** max(0, attempt - 1)) + random.uniform(0.0, 0.4)


async def execute_with_retry(label: str, fn):
    for attempt in range(1, SUPABASE_MAX_RETRIES + 1):
        try:
            return fn()
        except Exception as e:
            if attempt >= SUPABASE_MAX_RETRIES:
                raise
            wait = backoff_seconds(attempt)
            print(f"[Retry] {label} failed (attempt {attempt}/{SUPABASE_MAX_RETRIES}): {e}. Sleep {wait:.2f}s")
            await asyncio.sleep(wait)


async def create_scrape_run(supabase, source: str):
    if not supabase:
        return None
    try:
        response = await execute_with_retry(
            label=f"scrape-run-start-{source}",
            fn=lambda: supabase.table("scrape_runs").insert({
                "source": source,
                "status": "running",
            }).execute()
        )
        if response.data:
            return response.data[0]["id"]
    except Exception as e:
        print(f"[RunStats] Failed to create scrape_runs row: {e}")
    return None


async def finalize_scrape_run(supabase, run_id: str | None, stats: dict, source_breakdown: dict, status: str, error_message: str | None = None):
    if not supabase or not run_id:
        return

    payload = {
        "status": status,
        "pages_scanned": stats["pages_scanned"],
        "discovered_count": stats["discovered_count"],
        "upserted_count": stats["upserted_count"],
        "detail_success_count": stats["detail_success_count"],
        "detail_fail_count": stats["detail_fail_count"],
        "placeholder_cover_count": stats["placeholder_cover_count"],
        "blocked_count": stats["blocked_count"],
        "finished_at": datetime.now(timezone.utc).isoformat(),
        "error_summary": json.dumps({
            "new_external_count": stats["new_external_count"],
            "existing_complete_count": stats["existing_complete_count"],
            "sources": source_breakdown,
            "error": error_message,
        }, ensure_ascii=False)[:6000],
    }
    try:
        await execute_with_retry(
            label=f"scrape-run-finish-{run_id}",
            fn=lambda: supabase.table("scrape_runs").update(payload).eq("id", run_id).execute()
        )
    except Exception as e:
        print(f"[RunStats] Failed to finalize scrape_runs row {run_id}: {e}")


def write_step_summary(stats: dict, source_breakdown: dict):
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not summary_path:
        return

    lines = [
        "## MissNet scraper summary",
        "",
        f"- Pages scanned: {stats['pages_scanned']}",
        f"- Discovered: {stats['discovered_count']}",
        f"- New external IDs: {stats['new_external_count']}",
        f"- Detail attempts: {stats['detail_attempted_count']}",
        f"- Detail successes: {stats['detail_success_count']}",
        f"- Detail failures: {stats['detail_fail_count']}",
        f"- Blocked: {stats['blocked_count']}",
        f"- Placeholder covers filtered: {stats['placeholder_cover_count']}",
        f"- Upserted: {stats['upserted_count']}",
        "",
        "### Sources",
        "",
    ]
    for source, source_stats in source_breakdown.items():
        lines.append(
            f"- `{source}`: pages={source_stats['pages_scanned']}, discovered={source_stats['discovered_count']}, "
            f"new={source_stats['new_external_count']}, detail_ok={source_stats['detail_success_count']}, "
            f"detail_fail={source_stats['detail_fail_count']}, upserted={source_stats['upserted_count']}"
        )

    with open(summary_path, "a", encoding="utf-8") as fp:
        fp.write("\n".join(lines) + "\n")


async def batch_upsert_videos(records, supabase, mode_label):
    if not records:
        return {"upserted_count": 0, "placeholder_cover_count": 0}

    placeholder_cover_count = sum(1 for record in records if looks_like_placeholder_cover(record.get("cover_url")))
    normalized = [normalize_video_record(v) for v in records]

    # No DB client: keep visible logs for local dry run
    if not supabase:
        for v in normalized[:5]:
            print(f"  [{mode_label}] Prepared: {v.get('title', '')[:30]}... | Dur: {v.get('duration')} | Actors: {v.get('actors')}")
        if len(normalized) > 5:
            print(f"  [{mode_label}] Prepared {len(normalized)} records (dry-run without Supabase)")
        return {"upserted_count": len(normalized), "placeholder_cover_count": placeholder_cover_count}

    synced = 0
    for idx, payload in enumerate(chunked(normalized, SUPABASE_UPSERT_CHUNK_SIZE), start=1):
        await execute_with_retry(
            label=f"{mode_label}-chunk-{idx}",
            fn=lambda payload=payload: supabase.table("videos").upsert(payload, on_conflict="external_id").execute()
        )
        synced += len(payload)
    print(f"  [{mode_label}] Batch upserted {synced} records")
    return {"upserted_count": synced, "placeholder_cover_count": placeholder_cover_count}


def map_categories(title, tags):
    refined = []
    text_to_check = (title + " " + " ".join(tags)).upper()
    for category, keywords in CATEGORY_MAP.items():
        if any(kw.upper() in text_to_check for kw in keywords):
            refined.append(category)
    return refined

async def get_video_details(page, url):
    try:
        await jitter_sleep(DETAIL_PRE_NAV_DELAY_MIN, DETAIL_PRE_NAV_DELAY_MAX)
        await page.goto(url, timeout=60000, wait_until="domcontentloaded")
        
        # Optimize: Wait for metadata selector instead of hard sleep
        try:
            await page.wait_for_selector('div.text-secondary', timeout=5000)
        except:
            pass 
        await jitter_sleep(DETAIL_POST_LOAD_DELAY_MIN, DETAIL_POST_LOAD_DELAY_MAX)
        
        title = await page.title()
        if "Just a moment" in title:
            print(f"  [Warning] Detail page BLOCKED: {url}")
            return {"_status": "blocked", "duration": None, "release_date": None, "actors": [], "tags": []}

        # Robust DOM-based parsing without complex regex in JS
        details = await page.evaluate('''() => {
            const data = { duration: null, release_date: null, actors: [], tags: [] };
            const rows = document.querySelectorAll('div.text-secondary');
            
            rows.forEach(row => {
                const labelEl = row.querySelector('span');
                if (!labelEl) return;
                
                const label = labelEl.innerText;
                const rowText = row.innerText;
                const normalizedText = rowText.replace(label, '').replace(/^[:：\\s-]+/, '').trim();
                
                if (label.includes('时长') || label.includes('時長') || label.includes('Duration')) {
                    data.duration = normalizedText || data.duration;
                }
                
                if (label.includes('日期') || label.includes('Release')) {
                    const timeEl = row.querySelector('time');
                    data.release_date = timeEl ? timeEl.innerText.trim() : normalizedText;
                }
                
                if (label.includes('女優') || label.includes('Actresses')) {
                    const links = row.querySelectorAll('a');
                    data.actors = Array.from(links).map(a => a.innerText.trim());
                }
                
                if (label.includes('類型') || label.includes('標籤') || label.includes('Genre') || label.includes('Tag')) {
                    const links = row.querySelectorAll('a');
                    const vals = Array.from(links).map(a => a.innerText.trim());
                    data.tags = [...new Set([...data.tags, ...vals])];
                }
            });
            return data;
        }''')
        
        if not details:
            details = {"duration": None, "release_date": None, "actors": [], "tags": []}

        if not details.get('duration'):
            print(f"  [Debug] Missing Duration for {url}. Page Title: {title}")
            body_text = None
            try:
                body_text = await page.locator("body").inner_text(timeout=3000)
            except Exception:
                body_text = None

            details['duration'] = extract_duration_from_text(body_text)

            if not details.get('duration'):
                content = await page.content()
                details['duration'] = extract_duration_from_text(content)

        details["_status"] = "success"
        return details
    except Exception as e:
        print(f"  Detail Fetch Error: {e}")
        return {"_status": "error", "duration": None, "release_date": None, "actors": [], "tags": []}

async def get_51cg_details(page, url):
    try:
        await jitter_sleep(DETAIL_PRE_NAV_DELAY_MIN, DETAIL_PRE_NAV_DELAY_MAX)
        await page.goto(url, timeout=60000, wait_until="domcontentloaded")
        
        try:
            await page.wait_for_selector('h1.post-title', timeout=5000)
        except:
            pass
        await jitter_sleep(DETAIL_POST_LOAD_DELAY_MIN, DETAIL_POST_LOAD_DELAY_MAX)

        details = await page.evaluate(r'''() => {
            const data = { tags: [], actors: [], title: null, release_date: null, videos: [] };
            
            const titleEl = document.querySelector('h1.post-title');
            if (titleEl) data.title = titleEl.innerText.trim();

            const tagLinks = document.querySelectorAll('.tags .keywords a');
            tagLinks.forEach(a => data.tags.push(a.innerText.trim()));

            const dateEl = document.querySelector('.post-meta time');
            if (dateEl) {
                data.release_date = dateEl.innerText.trim();
            } else {
                const metaDate = document.querySelector('meta[itemprop="datePublished"]');
                if (metaDate) data.release_date = metaDate.content;
            }

            // Extract DPlayer videos
            const dplayers = document.querySelectorAll('.dplayer');
            dplayers.forEach((dp, index) => {
                const configStr = dp.getAttribute('data-config');
                if (configStr) {
                    try {
                        const config = JSON.parse(configStr);
                        if (config.video && config.video.url) {
                            let subTitle = "";
                            // Attempt to find subtitle in previous element (e.g. <p>NO1:...</p>)
                            // Structure might be <p>Title</p><p><div class="dplayer"></div></p> or <p>Title</p><div class="dplayer"></div>
                            let container = dp.parentElement;
                            let prev = dp.previousElementSibling;
                            
                            // If dplayer is inside a p tag, look at previous p tag
                            if (container.tagName === 'P') {
                                prev = container.previousElementSibling;
                            }
                            
                            if (prev && (prev.tagName === 'P' || prev.tagName === 'DIV')) {
                                subTitle = prev.innerText.trim();
                            }
                            
                            data.videos.push({
                                url: config.video.url,
                                title_suffix: subTitle
                            });
                        }
                    } catch(e) {}
                }
            });

            return data;
        }''')
        
        # Fallback for single m3u8 in content if no DPlayer found
        if not details['videos']:
            content = await page.content()
            m3u8_match = re.search(r'["\']([^"\']+\.m3u8[^"\']*)["\']', content)
            if m3u8_match:
                 details['videos'].append({
                     'url': m3u8_match.group(1),
                     'title_suffix': ''
                 })
        
        details["_status"] = "success"
        return details
    except Exception as e:
        print(f"  51CG Detail Fetch Error: {e}")
        return {"_status": "error", "tags": [], "actors": [], "title": None, "release_date": None, "videos": []}

async def process_page_batch(videos, source_tag, detail_pages, supabase, semaphore):
    if not videos:
        return {"stale_page": True, **make_run_stats()}

    page_stats = make_run_stats()
    page_stats["pages_scanned"] = 1
    page_stats["discovered_count"] = len(videos)
    external_ids = [v['external_id'] for v in videos]
    metadata_map = {}
    if supabase:
        try:
            res = await execute_with_retry(
                label=f"{source_tag}-metadata-check",
                fn=lambda: supabase.table("videos").select(
                    "external_id, title, cover_url, source_url, source_site, duration, actors, release_date, tags, categories"
                ).in_("external_id", external_ids).execute()
            )
            for record in res.data:
                metadata_map[record['external_id']] = record
        except Exception as e:
            print(f"  Batch Check Error: {e}")

    rows_to_upsert = []
    tasks = []
    details_needed_count = 0
    for v in videos:
        ext_id = v['external_id']
        existing = metadata_map.get(ext_id)
        if not existing:
            page_stats["new_external_count"] += 1
        if not should_fetch_details(existing):
            print(f"  [Skip] {v['title'][:30]}... (Metadata exists)")
            page_stats["existing_complete_count"] += 1
            rows_to_upsert.append(
                merge_video_record(
                    {
                        **v,
                        "categories": normalize_taxonomy_values([source_tag] + (v.get("categories") or [])),
                        "tags": normalize_taxonomy_values([source_tag] + (v.get("tags") or [])),
                    },
                    existing,
                )
            )
        else:
            details_needed_count += 1
            page_stats["detail_attempted_count"] += 1
            async def scrape_and_prepare(vid=v):
                async with semaphore:
                    page = detail_pages.pop()
                    try:
                        details = await get_video_details(page, vid['source_url'])
                        existing_record = metadata_map.get(vid['external_id'])
                        status = (details or {}).pop("_status", "success") if details else "success"
                        if status == "blocked":
                            page_stats["blocked_count"] += 1
                        elif status == "error":
                            page_stats["detail_fail_count"] += 1

                        if details and (details.get('duration') or details.get('actors') or details.get('release_date') or details.get('tags')):
                            vid.update(details)
                            vid['categories'] = normalize_taxonomy_values([source_tag] + map_categories(vid['title'], vid.get('tags', [])))
                            vid['tags'] = normalize_taxonomy_values([source_tag] + vid.get('tags', []))
                            page_stats["detail_success_count"] += 1
                            rows_to_upsert.append(merge_video_record(vid, existing_record))
                        else:
                            vid['categories'] = normalize_taxonomy_values([source_tag] + map_categories(vid['title'], []))
                            vid['tags'] = normalize_taxonomy_values([source_tag] + vid.get('tags', []))
                            if status not in {"blocked", "error"}:
                                page_stats["detail_fail_count"] += 1
                            rows_to_upsert.append(merge_video_record(vid, existing_record))
                    except Exception as e:
                        page_stats["detail_fail_count"] += 1
                        print(f"  [Detail Error] {vid.get('source_url')}: {e}")
                    finally:
                        detail_pages.append(page)
            tasks.append(scrape_and_prepare())
            
    if tasks:
        await asyncio.gather(*tasks)

    upsert_result = await batch_upsert_videos(rows_to_upsert, supabase, f"{source_tag.upper()} BATCH")
    merge_stats(page_stats, upsert_result)
    page_stats["stale_page"] = page_stats["new_external_count"] == 0 and details_needed_count == 0
    return page_stats

async def process_51cg_batch(videos, detail_pages, supabase, semaphore, source_tag="51cg"):
    if not videos:
        return {"stale_page": True, **make_run_stats()}

    page_stats = make_run_stats()
    page_stats["pages_scanned"] = 1
    page_stats["discovered_count"] = len(videos)
    external_ids = [v['external_id'] for v in videos]
    metadata_map = {}
    if supabase:
        try:
            res = await execute_with_retry(
                label=f"{source_tag}-metadata-check",
                fn=lambda: supabase.table("videos").select(
                    "external_id, title, cover_url, source_url, source_site, duration, actors, release_date, tags, categories"
                ).in_("external_id", external_ids).execute()
            )
            for record in res.data:
                metadata_map[record['external_id']] = record
        except Exception as e:
            print(f"  [51CG Batch Check Error] {e}")

    rows_to_upsert = []
    tasks = []
    for v in videos:
        if not metadata_map.get(v["external_id"]):
            page_stats["new_external_count"] += 1
        page_stats["detail_attempted_count"] += 1
        async def scrape_and_prepare(vid=v):
            async with semaphore:
                page = detail_pages.pop()
                try:
                    details = await get_51cg_details(page, vid['source_url'])
                    status = (details or {}).pop("_status", "success") if details else "success"
                    if status == "error":
                        page_stats["detail_fail_count"] += 1
                    if details:
                        # If list page title is empty/placeholder, use detail page title
                        if (not vid.get('title') or len(vid['title']) < 2) and details.get('title'):
                            vid['title'] = details['title']
                        
                        # Merge tags
                        vid_tags = ordered_unique(vid.get('tags', []) + details.get('tags', []))
                        
                        # Refine Categories based on title and tags
                        refined_cats = map_categories(vid['title'], vid_tags)
                        
                        # Merge with list-page categories
                        final_cats = normalize_taxonomy_values([source_tag] + vid.get('categories', []) + refined_cats + ["51吃瓜"])
                        
                        vid.update({k: v for k, v in details.items() if k not in ['title', 'tags', 'videos']})
                        vid['categories'] = final_cats
                        vid['tags'] = normalize_taxonomy_values(vid_tags + [source_tag])

                        # Handle multiple videos
                        videos_to_sync = []
                        if details.get('videos'):
                            for i, video_info in enumerate(details['videos']):
                                new_vid = vid.copy()
                                new_vid['source_url'] = video_info['url']
                                if video_info['title_suffix']:
                                    new_vid['title'] = f"{vid['title']} {video_info['title_suffix']}"
                                
                                # First video keeps original ID, others get suffix
                                if i > 0:
                                    new_vid['external_id'] = f"{vid['external_id']}_{i+1}"
                                
                                videos_to_sync.append(new_vid)
                        else:
                            videos_to_sync.append(vid)

                        for v_sync in videos_to_sync:
                            rows_to_upsert.append(merge_video_record(v_sync, metadata_map.get(v_sync['external_id'])))
                        page_stats["detail_success_count"] += 1
                    else:
                        vid['categories'] = normalize_taxonomy_values([source_tag] + (vid.get('categories') or []) + ["51吃瓜"])
                        vid['tags'] = normalize_taxonomy_values([source_tag] + (vid.get('tags') or []))
                        rows_to_upsert.append(merge_video_record(vid, metadata_map.get(vid['external_id'])))
                        if status != "error":
                            page_stats["detail_fail_count"] += 1
                except Exception as e:
                    page_stats["detail_fail_count"] += 1
                    print(f"  [51CG Detail Error] {vid.get('source_url')}: {e}")

                finally:
                    detail_pages.append(page)
        tasks.append(scrape_and_prepare())
    
    if tasks:
        await asyncio.gather(*tasks)

    upsert_result = await batch_upsert_videos(rows_to_upsert, supabase, f"51CG ({source_tag})")
    merge_stats(page_stats, upsert_result)
    page_stats["stale_page"] = False
    return page_stats

async def scrape_51cg_feed(context, supabase, semaphore, detail_pages, base_url, source_tag="51cg", max_pages=None):
    print(f"\n>>> Starting Source: {source_tag.upper()} ({base_url}) <<<")
    
    list_page = await context.new_page()
    source_stats = make_run_stats()
    total_pages = max_pages or CG_MAX_PAGES
    
    try:
        for page_num in range(1, total_pages + 1):
            url = base_url if page_num == 1 else f"{base_url}page/{page_num}/"
            print(f"[{source_tag.upper()}] Page {page_num}...")
            
            await list_page.goto(url, timeout=60000, wait_until="domcontentloaded")
            
            try:
                await list_page.wait_for_selector('#index article', timeout=10000)
            except:
                pass
            await jitter_sleep(LIST_POST_LOAD_DELAY_MIN, LIST_POST_LOAD_DELAY_MAX)

            videos = await list_page.evaluate(r'''() => {
                const items = document.querySelectorAll('#index article');
                const results = [];
                items.forEach(item => {
                    const link = item.querySelector('a');
                    
                    if (link) {
                        const href = link.href;
                        // Extract ID
                        const idMatch = href.match(/archives\/(\d+)/);
                        const id = idMatch ? idMatch[1] : null;
                        
                        // Extract Image from script
                        let cover = "";
                        const scripts = item.querySelectorAll('script');
                        for (const s of scripts) {
                             const match = s.innerText.match(/loadBannerDirect\('([^']+)'/);
                             if (match) {
                                 cover = match[1];
                                 break;
                             }
                        }
                        
                        // Fallback: look for img with data-xkrkllgl
                        if (!cover) {
                             const img = item.querySelector('img[data-xkrkllgl]');
                             if (img) cover = img.getAttribute('data-xkrkllgl');
                        }

                        // Title
                        let t = "";
                        const titleEl = item.querySelector('.post-card-title');
                        if (titleEl) {
                             // Clone to remove children
                             const clone = titleEl.cloneNode(true);
                             const wraps = clone.querySelectorAll('.wrap');
                             wraps.forEach(w => w.remove());
                             t = clone.innerText.trim();
                        }

                        // Categories
                        let cats = [];
                        const infoDiv = item.querySelector('.post-card-info');
                        if (infoDiv) {
                            const text = infoDiv.innerText;
                            const parts = text.split('•');
                            if (parts.length > 0) {
                                const lastPart = parts[parts.length - 1];
                                if (lastPart) {
                                    cats = lastPart.split(/[,，]/).map(c => c.trim()).filter(c => c);
                                }
                            }
                        }

                        if (id) {
                            results.push({
                                external_id: "51cg_" + id,
                                title: t,
                                cover_url: cover,
                                source_url: href,
                                duration: null,
                                actors: [],
                                categories: cats,
                                tags: []
                            });
                        }
                    }
                });
                return results;
            }''')

            if not videos:
                print(f"[{source_tag.upper()}] No videos found on this page.")
                break

            page_stats = await process_51cg_batch(videos, detail_pages, supabase, semaphore, source_tag)
            merge_stats(source_stats, page_stats)
            await jitter_sleep(INTER_PAGE_DELAY_MIN, INTER_PAGE_DELAY_MAX)

    except Exception as e:
        print(f"[{source_tag.upper()}] Error: {e}")
    finally:
        await list_page.close()
    return source_stats

async def scrape_videos():
    supabase: Client = None
    if SUPABASE_URL and SUPABASE_KEY:
        supabase = create_client(SUPABASE_URL, SUPABASE_KEY)
    run_config = resolve_run_configuration()
    
    HEADLESS = os.environ.get("HEADLESS", "true").lower() == "true"
    USER_DATA_DIR = os.environ.get("USER_DATA_DIR", os.path.join(os.getcwd(), "user_data"))
    print(
        f"[Config] HEADLESS={HEADLESS} | RUN_MODE={run_config['mode']} | "
        f"MISSAV_MAX_PAGES={run_config['missav_pages']} | "
        f"CG_MAX_PAGES={run_config['cg_pages']} | CONCURRENT_DETAIL_PAGES={CONCURRENT_DETAIL_PAGES} | "
        f"UPSERT_CHUNK={SUPABASE_UPSERT_CHUNK_SIZE} | RETRIES={SUPABASE_MAX_RETRIES} | "
        f"EARLY_STOP_STREAK={run_config['early_stop_streak']} | EARLY_STOP_MIN_PAGE={run_config['early_stop_min_page']} | "
        f"SOURCE_TAGS={run_config['selected_tags'] or 'ALL'} | SKIP_51CG={SKIP_51CG} | "
        f"BLOCK_HEAVY_RESOURCES={BLOCK_HEAVY_RESOURCES}"
    )
    if not run_config["missav_sources"] and not run_config["run_51cg_main"] and not run_config["run_51cg_mrds"]:
        print("[Config] No sources selected. Exiting without work.")
        return
    semaphore = asyncio.Semaphore(CONCURRENT_DETAIL_PAGES)
    run_stats = make_run_stats()
    source_breakdown = {}
    run_id = await create_scrape_run(supabase, "daily_scraper")
    run_error = None

    try:
        async with async_playwright() as p:
            args = ["--disable-blink-features=AutomationControlled", "--no-sandbox"]
            try:
                context = await p.chromium.launch_persistent_context(
                    user_data_dir=USER_DATA_DIR, headless=HEADLESS, channel="chrome", user_agent=USER_AGENT,
                    args=args, ignore_default_args=["--enable-automation"], viewport={"width": 1280, "height": 720}
                )
            except Exception:
                context = await p.chromium.launch_persistent_context(
                    user_data_dir=USER_DATA_DIR, headless=HEADLESS, user_agent=USER_AGENT,
                    args=args, viewport={"width": 1280, "height": 720}
                )

            if BLOCK_HEAVY_RESOURCES:
                async def route_handler(route, request):
                    if request.resource_type in BLOCKED_RESOURCE_TYPES:
                        await route.abort()
                    else:
                        await route.continue_()

                await context.route("**/*", route_handler)

            stealth = Stealth()
            # Create pages for main scraping
            list_page = await context.new_page()
            await stealth.apply_stealth_async(list_page)

            detail_pages = []
            for _ in range(CONCURRENT_DETAIL_PAGES):
                dp = await context.new_page()
                await stealth.apply_stealth_async(dp)
                detail_pages.append(dp)

            if run_config["run_51cg_main"]:
                source_stats = await scrape_51cg_feed(
                    context,
                    supabase,
                    semaphore,
                    detail_pages,
                    "https://51cg1.com/",
                    "51cg",
                    max_pages=run_config["cg_pages"],
                )
                source_breakdown["51cg"] = source_stats
                merge_stats(run_stats, source_stats)

            if run_config["run_51cg_mrds"]:
                source_stats = await scrape_51cg_feed(
                    context,
                    supabase,
                    semaphore,
                    detail_pages,
                    "https://51cg1.com/category/mrds/",
                    "51mrds",
                    max_pages=run_config["cg_pages"],
                )
                source_breakdown["51mrds"] = source_stats
                merge_stats(run_stats, source_stats)

            for source in run_config["missav_sources"]:
                base_url = source["url"]
                tag = source["tag"]
                source_stats = make_run_stats()
                stale_streak = 0
                print(f"\n>>> Starting Category: {tag} <<<")

                for page_num in range(1, run_config["missav_pages"] + 1):
                    current_url = build_paged_url(base_url, page_num)
                    print(f"[{tag.upper()}] Page {page_num}...")
                    try:
                        await list_page.goto(current_url, timeout=60000, wait_until="domcontentloaded")
                        
                        try:
                            await list_page.wait_for_selector('div.grid > div, div.thumbnail, .group', timeout=10000)
                        except:
                            pass
                        await jitter_sleep(LIST_POST_LOAD_DELAY_MIN, LIST_POST_LOAD_DELAY_MAX)
                        
                        if "Just a moment" in await list_page.title():
                            print(f"[{tag.upper()}] Blocked. Skipping.")
                            source_stats["blocked_count"] += 1
                            continue

                        videos = await list_page.evaluate('''() => {
                            const resMap = new Map();
                            const items = document.querySelectorAll('div.grid > div, div.thumbnail, .group');
                            items.forEach(item => {
                                const img = item.querySelector('img');
                                const link = item.querySelector('a');
                                if (img && link && link.href) {
                                    const href = link.href;
                                    // Simple check for video ID pattern or /dm
                                    const isVideo = href.includes('/dm') || href.split('/').pop().includes('-');
                                    if (isVideo) {
                                        const id = href.split('?')[0].split('/').pop();
                                        if (id && !resMap.has(id)) {
                                            let t = img.alt || "";
                                            if (t.length < 5) {
                                                const te = item.querySelector('h1, h2, h3, .text-secondary');
                                                if (te) t = te.innerText;
                                            }
                                            if (t.length > 5) {
                                                const candidates = [
                                                    img.getAttribute('data-src'),
                                                    img.getAttribute('data-original'),
                                                    img.getAttribute('data-lazy-src'),
                                                    img.getAttribute('data-cfsrc'),
                                                    img.getAttribute('data-xkrkllgl'),
                                                    img.currentSrc,
                                                    img.src
                                                ].filter(Boolean);

                                                let c = "";
                                                for (const candidate of candidates) {
                                                    if (!candidate.startsWith('data:image')) {
                                                        c = candidate;
                                                        break;
                                                    }
                                                }
                                                if (!c && candidates.length > 0) {
                                                    c = candidates[0];
                                                }
                                                if (c.includes('cover-t.jpg')) c = c.replace('cover-t.jpg', 'cover-n.jpg');
                                                resMap.set(id, {
                                                    external_id: id,
                                                    title: t.trim(),
                                                    cover_url: c,
                                                    source_url: href
                                                });
                                            }
                                        }
                                    }
                                }
                            });
                            return Array.from(resMap.values());
                        }''')

                        if not videos:
                            print(f"[{tag.upper()}] No videos found.")
                            break

                        page_stats = await process_page_batch(videos, tag, detail_pages, supabase, semaphore)
                        merge_stats(source_stats, page_stats)
                        if page_stats.get("stale_page"):
                            stale_streak += 1
                        else:
                            stale_streak = 0

                        if page_num >= run_config["early_stop_min_page"] and stale_streak >= run_config["early_stop_streak"]:
                            print(f"[{tag.upper()}] Early stop after {stale_streak} stale pages (page {page_num}).")
                            break

                        await jitter_sleep(INTER_PAGE_DELAY_MIN, INTER_PAGE_DELAY_MAX)
                    except Exception as e:
                        source_stats["detail_fail_count"] += 1
                        print(f"Error: {e}")

                source_breakdown[tag] = source_stats
                merge_stats(run_stats, source_stats)

            await context.close()
    except Exception as e:
        run_error = str(e)
        raise
    finally:
        write_step_summary(run_stats, source_breakdown)
        await finalize_scrape_run(
            supabase=supabase,
            run_id=run_id,
            stats=run_stats,
            source_breakdown=source_breakdown,
            status="failed" if run_error else "success",
            error_message=run_error,
        )

if __name__ == "__main__":
    asyncio.run(scrape_videos())
