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

async def block_aggressively(route):
    bad_resource_types = ["image", "font", "media", "manifest", "other"]
    if route.request.resource_type in bad_resource_types:
        await route.abort()
    else:
        await route.continue_()

def map_categories(title, tags):
    refined = []
    text_to_check = (title + " " + " ".join(tags)).upper()
    for category, keywords in CATEGORY_MAP.items():
        if any(kw.upper() in text_to_check for kw in keywords):
            refined.append(category)
    return refined

async def get_video_details(page, url):
    try:
        await asyncio.sleep(random.uniform(1.0, 2.5))
        await page.goto(url, timeout=60000, wait_until="domcontentloaded")
        await asyncio.sleep(random.uniform(2.0, 4.0))
        details = await page.evaluate('''() => {
            const data = { duration: null, release_date: null, actors: [], tags: [] };
            const infoItems = document.querySelectorAll('.space-y-2 div, .mt-4 div');
            infoItems.forEach(item => {
                const text = item.innerText;
                if (text.includes('时长:')) data.duration = text.replace('时长:', '').trim();
                if (text.includes('发布日期:')) data.release_date = text.replace('发布日期:', '').trim();
            });
            data.actors = Array.from(document.querySelectorAll('a[href*="/actors/"]')).map(a => a.innerText.trim()).filter(n => n.length > 0);
            data.tags = Array.from(document.querySelectorAll('a[href*="/tags/"]')).map(a => a.innerText.trim()).filter(n => n.length > 0);
            return data;
        }''')
        return details
    except Exception as e:
        print(f"  Error fetching details for {url}: {e}")
        return None

async def sync_video(v, supabase, mode_label):
    if supabase:
        try:
            supabase.table("videos").upsert(v, on_conflict="external_id").execute()
            print(f"  [{mode_label}] Synced: {v['title'][:30]}...")
        except Exception as e:
            print(f"  DB Error for {v['title'][:20]}: {e}")
    else:
        print(f"  [Dry Run] {v['title'][:30]}")

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
        if existing and existing.get('duration') and existing.get('actors'):
            v['categories'] = map_categories(v['title'], [])
            tasks.append(sync_video(v, supabase, "Incremental"))
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
        args = ["--disable-blink-features=AutomationControlled", "--no-sandbox", "--disable-gpu"]
        try:
            context = await p.chromium.launch_persistent_context(
                user_data_dir=USER_DATA_DIR,
                headless=HEADLESS,
                args=args,
                ignore_default_args=["--enable-automation"], 
                viewport={"width": 1280, "height": 720}
            )
        except Exception as e:
            print(f"Launch failed: {e}")
            return

        stealth = Stealth()
        list_page = await context.new_page()
        await stealth.apply_stealth_async(list_page)
        await list_page.route("**/*", block_aggressively)

        detail_pages = []
        for _ in range(CONCURRENT_DETAIL_PAGES):
            dp = await context.new_page()
            await stealth.apply_stealth_async(dp)
            await dp.route("**/*", block_aggressively)
            detail_pages.append(dp)

        for source in SOURCES:
            base_url = source["url"]
            tag = source["tag"]
            print(f"\n>>> Starting Category: {tag} <<<")

            for page_num in range(1, 31):
                current_url = f"{base_url}?page={page_num}"
                print(f"[{tag.upper()}] Navigating to page {page_num}...")
                try:
                    await list_page.goto(current_url, timeout=60000, wait_until="domcontentloaded")
                    await asyncio.sleep(random.uniform(4, 7))
                    
                    if "Just a moment" in await list_page.title():
                        print(f"[{tag.upper()}] BLOCKED by Cloudflare. Waiting 15s...")
                        await asyncio.sleep(15)
                        continue

                    videos = await list_page.evaluate('''() => {
                        const results = [];
                        const images = document.querySelectorAll('img');
                        images.forEach(img => {
                            let link = img.closest('a');
                            if (link && link.href.includes('/')) {
                                let title = img.alt || "No Title";
                                let cover = img.src;
                                results.push({
                                    external_id: link.href.split('/').pop(),
                                    title: title.trim(),
                                    cover_url: cover,
                                    source_url: link.href
                                });
                            }
                        });
                        return results;
                    }''')

                    if not videos:
                        print(f"[{tag.upper()}] No videos found.")
                        break

                    print(f"[{tag.upper()}] Page {page_num}: Processing {len(videos)} videos...")
                    await process_page_batch(videos, tag, detail_pages, supabase, semaphore)
                    await asyncio.sleep(random.uniform(3, 6))
                except Exception as e:
                    print(f"Error: {e}")

        await context.close()

if __name__ == "__main__":
    asyncio.run(scrape_videos())
