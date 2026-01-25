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

def map_categories(title, tags):
    """
    Automatically maps video to refined categories based on keywords.
    """
    refined = []
    text_to_check = (title + " " + " ".join(tags)).upper()
    
    for category, keywords in CATEGORY_MAP.items():
        if any(kw.upper() in text_to_check for kw in keywords):
            refined.append(category)
    
    return refined

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

async def get_video_details(page, url):
    """
    Scrapes detailed metadata from a single video page.
    """
    try:
        print(f"  Fetching details from: {url}")
        await page.goto(url, timeout=60000, wait_until="domcontentloaded")
        await asyncio.sleep(random.uniform(2, 4))
        
        details = await page.evaluate('''() => {
            const data = {
                duration: null,
                release_date: null,
                actors: [],
                tags: []
            };
            
            const infoItems = document.querySelectorAll('.space-y-2 div, .mt-4 div');
            infoItems.forEach(item => {
                const text = item.innerText;
                if (text.includes('时长:')) data.duration = text.replace('时长:', '').trim();
                if (text.includes('发布日期:')) data.release_date = text.replace('发布日期:', '').trim();
            });
            
            const actorLinks = document.querySelectorAll('a[href*="/actors/"]');
            data.actors = Array.from(actorLinks).map(a => a.innerText.trim()).filter(n => n.length > 0);
            
            const tagLinks = document.querySelectorAll('a[href*="/tags/"]');
            data.tags = Array.from(tagLinks).map(a => a.innerText.trim()).filter(n => n.length > 0);
            
            return data;
        }''')
        return details
    except Exception as e:
        print(f"  Error fetching details: {e}")
        return None

async def scrape_videos():
    supabase: Client = None
    if SUPABASE_URL and SUPABASE_KEY:
        supabase = create_client(SUPABASE_URL, SUPABASE_KEY)
    else:
        print("Warning: Supabase credentials not found. Running in dry-run mode.")

    HEADLESS = os.environ.get("HEADLESS", "true").lower() == "true"
    USER_DATA_DIR = os.path.join(os.getcwd(), "user_data")

    async with async_playwright() as p:
        args = [
            "--disable-blink-features=AutomationControlled",
            "--no-sandbox",
            "--disable-setuid-sandbox",
            "--disable-infobars",
            "--ignore-certificate-errors",
            "--disable-gpu",
        ]

        try:
            context = await p.chromium.launch_persistent_context(
                user_data_dir=USER_DATA_DIR,
                headless=HEADLESS,
                args=args,
                ignore_default_args=["--enable-automation"], 
                user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                viewport={"width": 1280, "height": 720}
            )
        except Exception as e:
            print(f"Launch failed: {e}")
            return

        stealth = Stealth()
        page = context.pages[0] if context.pages else await context.new_page()
        await stealth.apply_stealth_async(page)
        detail_page = await context.new_page()
        await stealth.apply_stealth_async(detail_page)

        for source in SOURCES:
            base_url = source["url"]
            tag = source["tag"]
            print(f"\n--- Category: {tag} ---")

            for page_num in range(1, 31): 
                current_url = f"{base_url}?page={page_num}"
                print(f"Navigating to {current_url}...")
                
                try:
                    await page.goto(current_url, timeout=60000, wait_until="domcontentloaded")
                    await asyncio.sleep(random.uniform(3, 6))
                    
                    if "Just a moment" in await page.title():
                        print("BLOCKED by Cloudflare.")
                        break

                    videos = await page.evaluate('''() => {
                        const results = [];
                        const images = document.querySelectorAll('img');
                        images.forEach(img => {
                            if (img.width > 100 && img.height > 60) {
                                let link = img.closest('a');
                                if (link) {
                                    let title = img.alt || "No Title";
                                    let cover = img.src;
                                    if (cover.includes('cover-t.jpg')) cover = cover.replace('cover-t.jpg', 'cover-n.jpg');
                                    results.push({
                                        external_id: link.href.split('/').pop(),
                                        title: title.trim(),
                                        cover_url: cover,
                                        source_url: link.href
                                    });
                                }
                            }
                        });
                        return results;
                    }''')

                    print(f"Found {len(videos)} videos.")
                    
                    for i, v in enumerate(videos):
                        # Optimization: Deep scrape only if we don't have tags yet (incremental update)
                        # For now, we limit deep scrape to first 5 videos per page to balance speed and data
                        if i < 5:
                            details = await get_video_details(detail_page, v['source_url'])
                            if details:
                                v.update(details)
                                v['categories'] = map_categories(v['title'], v.get('tags', []))
                                v['tags'] = list(set([tag] + v.get('tags', [])))
                        else:
                            # Basic mapping for others
                            v['categories'] = map_categories(v['title'], [])
                        
                        if supabase:
                            try:
                                supabase.table("videos").upsert(v, on_conflict="external_id").execute()
                                print(f"  Synced: {v['title'][:30]}... | Cat: {v.get('categories')}")
                            except Exception as e:
                                print(f"  DB Error: {e}")
                        
                        await asyncio.sleep(random.uniform(1, 2))

                except Exception as e:
                    print(f"Error: {e}")

        await context.close()

if __name__ == "__main__":
    asyncio.run(scrape_videos())