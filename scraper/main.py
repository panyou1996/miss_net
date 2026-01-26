from playwright.async_api import async_playwright
from playwright_stealth import Stealth
from supabase import create_client, Client
from dotenv import load_dotenv
import os
import asyncio
import random
import re

# Load environment variables from .env file if present
load_dotenv()

# Category Mapping Rules
CATEGORY_MAP = {
    "School": ["校园", "学生", "制服", "女教师", "学校", "女学生", "School", "Student", "女高中生"],
    "Office": ["OL", "职场", "公司", "秘书", "同事", "Office", "Business"],
    "Mature": ["熟女", "人妻", "妈妈", "姨", "Mature", "Milf", "Married Woman"],
    "Subtitled": ["中文字幕", "中文", "Subtitles", "Chinese"],
    "Exclusive": ["独家", "Exclusive", "獨家"],
    "Nympho": ["痴女", "Nympho", "淫亂", "淫乱"],
    "Voyeur": ["偷拍", "盗撮", "Voyeur", "自拍"],
    "Sister": ["姐姐", "姐", "Big Sister"],
    "Story": ["剧情", "Drama", "Story", "劇情"],
    "Amateur": ["素人", "业余", "Amateur"],
    "BigTits": ["巨乳", "大胸", "Big Tits", "美乳"],
    "Creampie": ["中出", "Creampie"],
    "Single": ["单体", "單體作品", "Single"],
    "Beautiful": ["美少女", "Girl"],
    "Oral": ["口交", "Oral"],
    "Group": ["多人", "多人数", "多人運動"],
}

# Target URLs
SOURCES = [
    {"url": "https://missav.ws/new", "tag": "new"},
    {"url": "https://missav.ws/dm263/monthly-hot?sort=monthly_views", "tag": "monthly_hot"},
    {"url": "https://missav.ws/dm169/weekly-hot?sort=weekly_views", "tag": "weekly_hot"},
    {"url": "https://missav.ws/dm628/uncensored-leak", "tag": "uncensored"},
    {"url": "https://missav.ws/chinese-subtitle", "tag": "Subtitled"},
    {"url": "https://missav.ws/dm136/genres/%E7%8D%A8%E5%AE%B6", "tag": "Exclusive"},
    {"url": "https://missav.ws/dm127/genres/%E4%B8%AD%E5%87%BA", "tag": "Creampie"},
    {"url": "https://missav.ws/dm119/genres/%E5%96%AE%E9%AB%94%E4%BD%9C%E5%93%81", "tag": "Single"},
    {"url": "https://missav.ws/dm114/genres/%E5%B7%A8%E4%B9%B3", "tag": "BigTits"},
    {"url": "https://missav.ws/dm68/genres/%E4%BA%BA%E5%A6%BB", "tag": "Mature"},
    {"url": "https://missav.ws/dm107/genres/%E7%86%9F%E5%A5%B3", "tag": "Mature"},
    {"url": "https://missav.ws/dm98/genres/%E7%B4%A0%E4%BA%BA", "tag": "Amateur"},
    {"url": "https://missav.ws/dm434/genres/%E7%BE%8E%E5%B0%91%E5%A5%B3", "tag": "Beautiful"},
    {"url": "https://missav.ws/dm1295/genres/%E5%8F%A3%E4%BA%A4", "tag": "Oral"},
    {"url": "https://missav.ws/dm311/genres/%E5%A4%9A%E4%BA%BA%E9%81%8B%E5%8B%95", "tag": "Group"},
    {"url": "https://missav.ws/dm312/genres/%E7%97%B4%E5%A5%B3", "tag": "Nympho"},
    {"url": "https://missav.ws/dm4420/genres/%E5%A5%B3%E9%AB%98%E4%B8%AD%E7%94%9F", "tag": "School"},
    {"url": "https://missav.ws/dm483/genres/%E5%81%B7%E6%8B%8D", "tag": "Voyeur"},
    {"url": "https://missav.ws/dm94/genres/%E5%8A%87%E6%83%85", "tag": "Story"},
    {"url": "https://missav.ws/dm784/genres/%E5%A7%90%E5%A7%90", "tag": "Sister"},
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
    try:
        await asyncio.sleep(random.uniform(1.0, 2.0))
        await page.goto(url, timeout=60000, wait_until="domcontentloaded")
        await asyncio.sleep(random.uniform(4.0, 6.0))
        
        title = await page.title()
        if "Just a moment" in title:
            print(f"  [Warning] Detail page BLOCKED: {url}")
            return None

        # Robust DOM-based parsing without complex regex in JS
        details = await page.evaluate('''() => {
            const data = { duration: null, release_date: null, actors: [], tags: [] };
            const rows = document.querySelectorAll('div.text-secondary');
            
            rows.forEach(row => {
                const labelEl = row.querySelector('span');
                if (!labelEl) return;
                
                const label = labelEl.innerText;
                const rowText = row.innerText;
                
                if (label.includes('时长') || label.includes('時長') || label.includes('Duration')) {
                    data.duration = rowText.replace(label, '').trim();
                }
                
                if (label.includes('日期') || label.includes('Release')) {
                    const timeEl = row.querySelector('time');
                    data.release_date = timeEl ? timeEl.innerText.trim() : rowText.replace(label, '').trim();
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
        
        # Python-side fallback using safe regex
        if not details or not details.get('duration'):
            content = await page.content()
            match = re.search(r'(?:时长|時長|Duration)[:：]\s*(\d+[^<]*)', content)
            if match:
                if not details: details = {}
                details['duration'] = match.group(1).strip()

        return details
    except Exception as e:
        print(f"  Detail Fetch Error: {e}")
        return None

async def sync_video(v, supabase, mode_label):
    if supabase:
        try:
            v['is_active'] = True
            supabase.table("videos").upsert(v, on_conflict="external_id").execute()
            print(f"  [{mode_label}] Synced: {v['title'][:30]}... | Dur: {v.get('duration')} | Actors: {v.get('actors')}")
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
        duration = existing.get('duration') if existing else None
        
        has_real_metadata = duration and duration != "Unknown" and duration != ""
        
        if has_real_metadata:
            print(f"  [Skip] {v['title'][:30]}... (Metadata exists)")
        else:
            async def scrape_and_sync(vid=v):
                async with semaphore:
                    page = detail_pages.pop()
                    try:
                        details = await get_video_details(page, vid['source_url'])
                        if details and (details.get('duration') or details.get('actors')):
                            vid.update(details)
                            vid['categories'] = map_categories(vid['title'], vid.get('tags', []))
                            vid['tags'] = list(set([source_tag] + vid.get('tags', [])))
                            await sync_video(vid, supabase, "Deep Scraped")
                        else:
                            vid['categories'] = map_categories(vid['title'], [])
                            await sync_video(vid, supabase, "Basic Sync")
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
                user_data_dir=USER_DATA_DIR, headless=HEADLESS, channel="chrome", user_agent=USER_AGENT,
                args=args, ignore_default_args=["--enable-automation"], viewport={"width": 1280, "height": 720}
            )
        except Exception:
            context = await p.chromium.launch_persistent_context(
                user_data_dir=USER_DATA_DIR, headless=HEADLESS, user_agent=USER_AGENT,
                args=args, viewport={"width": 1280, "height": 720}
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
                print(f"[{tag.upper()}] Page {page_num}...")
                try:
                    await list_page.goto(current_url, timeout=60000, wait_until="load")
                    await asyncio.sleep(random.uniform(5, 10))
                    
                    if "Just a moment" in await list_page.title():
                        print(f"[{tag.upper()}] Blocked. Skipping.")
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
                                            let c = img.src;
                                            if (c.includes('cover-t.jpg')) c = c.replace('cover-t.jpg', 'cover-n.jpg');
                                            resMap.set(id, { external_id: id, title: t.trim(), cover_url: c, source_url: href });
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

                    await process_page_batch(videos, tag, detail_pages, supabase, semaphore)
                    await asyncio.sleep(random.uniform(4, 8))
                except Exception as e:
                    print(f"Error: {e}")

        await context.close()

if __name__ == "__main__":
    asyncio.run(scrape_videos())