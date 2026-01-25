from playwright.async_api import async_playwright
from playwright_stealth import Stealth
from supabase import create_client, Client
from dotenv import load_dotenv
import os
import asyncio
import random

# Load environment variables from .env file if present
load_dotenv()

# Category Mapping Rules
CATEGORY_MAP = {
    "School": ["校园", "学生", "制服", "女教师", "学校", "女学生", "School", "Student"],
    "Office": ["OL", "职场", "公司", "秘书", "同事", "Office", "Business"],
    "VR": ["VR", "360"],
    "Uncensored": ["无码", "流出", "Uncensored", "Leak"],
    "Mature": ["熟女", "人妻", "妈妈", "姨", "Mature", "Milf"],
    "Subtitled": ["中文字幕", "中文", "Subtitles", "Chinese"],
}

# Target URLs for Production
SOURCES = [
    {"url": "https://missav.ws/new", "tag": "new"},
    {"url": "https://missav.ws/dm263/monthly-hot?sort=monthly_views", "tag": "monthly_hot"},
    {"url": "https://missav.ws/dm169/weekly-hot?sort=weekly_views", "tag": "weekly_hot"},
    {"url": "https://missav.ws/dm628/uncensored-leak", "tag": "uncensored"},
]

# Supabase Setup
SUPABASE_URL = os.environ.get("SUPABASE_URL")
SUPABASE_KEY = os.environ.get("SUPABASE_KEY")
USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

def map_categories(title, tags):
    refined = []
    text_to_check = (title + " " + " ".join(tags)).upper()
    for category, keywords in CATEGORY_MAP.items():
        if any(kw.upper() in text_to_check for kw in keywords):
            refined.append(category)
    return refined

async def get_video_details(page, url):
    """
    Improved extraction with more selectors.
    """
    try:
        await asyncio.sleep(random.uniform(1.0, 2.0))
        await page.goto(url, timeout=60000, wait_until="load")
        await asyncio.sleep(random.uniform(2.5, 4.5))
        
        details = await page.evaluate('''() => {
            const data = { duration: "Unknown", release_date: "Unknown", actors: [], tags: [] };
            
            // Try to find duration and release date in all text nodes if specific selectors fail
            const allDivs = Array.from(document.querySelectorAll('div'));
            for (const div of allDivs) {
                const text = div.innerText;
                if (!text) continue;
                if (text.startsWith('时长:')) data.duration = text.replace('时长:', '').trim();
                if (text.startsWith('发布日期:')) data.release_date = text.replace('发布日期:', '').trim();
            }
            
            // Fallback for duration (sometimes inside a span or specific class)
            if (data.duration === "Unknown") {
                const durationEl = document.querySelector('span.text-secondary') || document.querySelector('.i-bi-clock + span');
                if (durationEl) data.duration = durationEl.innerText.trim();
            }

            data.actors = Array.from(document.querySelectorAll('a[href*="/actors/"]')).map(a => a.innerText.trim()).filter(n => n.length > 0);
            data.tags = Array.from(document.querySelectorAll('a[href*="/tags/"]')).map(a => a.innerText.trim()).filter(n => n.length > 0);
            return data;
        }''')
        return details
    except Exception as e:
        print(f"  Error fetching details: {e}")
        return None

async def sync_video(v, supabase, mode_label):
    if supabase:
        try:
            supabase.table("videos").upsert(v, on_conflict="external_id").execute()
            print(f"  [{mode_label}] Synced: {v['title'][:30]}...")
        except Exception as e:
            print(f"  DB Error: {e}")

async def process_page_batch(videos, source_tag, detail_pages, supabase, semaphore):
    if not videos: return
    external_ids = [v['external_id'] for v in videos]
    metadata_map = {}
    if supabase:
        try:
            res = supabase.table("videos").select("external_id, duration, actors").in_("external_id", external_ids).execute()
            for record in res.data:
                metadata_map[record['external_id']] = record
        except Exception as e:
            print(f"  Batch Check Error: {e}")

    tasks = []
    for v in videos:
        ext_id = v['external_id']
        existing = metadata_map.get(ext_id)
        
        # KEY FIX: If duration exists AND is not empty/null, we skip.
        # If it doesn't exist, we scrape.
        has_metadata = existing and existing.get('duration') and existing.get('duration') != ""
        
        if has_metadata:
            # v['categories'] = map_categories(v['title'], []) # Optional: re-map if needed
            print(f"  [Skip] {v['title'][:30]}... (Already has metadata)")
        else:
            async def scrape_and_sync(vid=v):
                async with semaphore:
                    page = detail_pages.pop()
                    try:
                        details = await get_video_details(page, vid['source_url'])
                        if details:
                            vid.update(details)
                            vid['categories'] = map_categories(vid['title'], vid.get('tags', []))
                            vid['tags'] = list(set([source_tag] + vid.get('tags', [])))
                        await sync_video(vid, supabase, "Deep Scraped")
                    finally:
                        detail_pages.append(page)
            tasks.append(scrape_and_sync())
            
    if tasks:
        await asyncio.gather(*tasks)

async def scrape_videos():
    supabase: Client = None
    if SUPABASE_URL and SUPABASE_KEY:
        supabase = create_client(SUPABASE_URL, SUPABASE_KEY)
    
    HEADLESS = os.environ.get("HEADLESS", "true").lower() == "true"
    USER_DATA_DIR = os.path.join(os.getcwd(), "user_data")
    
    CONCURRENT_DETAIL_PAGES = 2 
    semaphore = asyncio.Semaphore(CONCURRENT_DETAIL_PAGES)

    async with async_playwright() as p:
        args = ["--disable-blink-features=AutomationControlled", "--no-sandbox"]
        
        try:
            context = await p.chromium.launch_persistent_context(
                user_data_dir=USER_DATA_DIR,
                headless=HEADLESS,
                channel="chrome",
                user_agent=USER_AGENT,
                args=args,
                ignore_default_args=["--enable-automation"], 
                viewport={"width": 1280, "height": 720}
            )
        except Exception:
            context = await p.chromium.launch_persistent_context(
                user_data_dir=USER_DATA_DIR,
                headless=HEADLESS,
                user_agent=USER_AGENT,
                args=args,
                viewport={"width": 1280, "height": 720}
            )

        stealth = Stealth()
        list_page = await context.new_page()
        await stealth.apply_stealth_async(list_page)

        detail_pages = []
        for _ in range(CONCURRENT_DETAIL_PAGES):
            dp = await context.new_page()
            await stealth.apply_stealth_async(dp)
            detail_pages.append(dp)

        for source in SOURCES:
            base_url = source["url"]
            tag = source["tag"]
            print(f"\n>>> Starting Category: {tag} <<<")

            for page_num in range(1, 31):
                current_url = f"{base_url}?page={page_num}"
                print(f"[{tag.upper()}] Navigating to page {page_num}...")
                try:
                    await list_page.goto(current_url, timeout=60000, wait_until="load")
                    await asyncio.sleep(random.uniform(4, 8))
                    
                    if "Just a moment" in await list_page.title():
                        print(f"[{tag.upper()}] Cloudflare detected. Waiting...")
                        await asyncio.sleep(15)
                        continue

                    videos = await list_page.evaluate('''() => {
                        const resultsMap = new Map();
                        const items = document.querySelectorAll('div.grid > div, div.thumbnail, .group');
                        
                        items.forEach(item => {
                            const img = item.querySelector('img');
                            const link = item.querySelector('a');
                            if (img && link) {
                                const href = link.href;
                                const isVideoUrl = /\/[a-z0-9]+-[a-z0-9]+(\/)?$/i.test(href) || href.includes('/dm');
                                
                                if (isVideoUrl) {
                                    // Robustly extract ID: remove trailing slash, then take last part
                                    const pathParts = href.replace(/\/$/, "").split('/');
                                    const external_id = pathParts.pop();
                                    if (external_id && !resultsMap.has(external_id)) {
                                        let title = img.alt || "";
                                        if (title.length < 5) {
                                            const titleEl = item.querySelector('h1, h2, h3, .text-secondary');
                                            if (titleEl) title = titleEl.innerText;
                                        }
                                        if (title.length > 5) {
                                            let cover = img.src;
                                            if (cover.includes('cover-t.jpg')) cover = cover.replace('cover-t.jpg', 'cover-n.jpg');
                                            resultsMap.set(external_id, {
                                                external_id: external_id,
                                                title: title.trim(),
                                                cover_url: cover,
                                                source_url: href
                                            });
                                        }
                                    }
                                }
                            }
                        });
                        return Array.from(resultsMap.values());
                    }''')

                    if not videos:
                        print(f"[{tag.upper()}] No videos found.")
                        break

                    print(f"[{tag.upper()}] Page {page_num}: Found {len(videos)} items. Syncing...")
                    await process_page_batch(videos, tag, detail_pages, supabase, semaphore)
                    await asyncio.sleep(random.uniform(3, 6))
                except Exception as e:
                    print(f"Error: {e}")

        await context.close()

if __name__ == "__main__":
    asyncio.run(scrape_videos())
