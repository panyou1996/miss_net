from playwright.async_api import async_playwright
from playwright_stealth import Stealth
from supabase import create_client, Client
from dotenv import load_dotenv
import os
import asyncio

# Load environment variables from .env file if present
load_dotenv()

# Target URL
BASE_URL = "https://missav.ws"
TARGET_URL = f"{BASE_URL}/new"

# Supabase Setup
SUPABASE_URL = os.environ.get("SUPABASE_URL")
SUPABASE_KEY = os.environ.get("SUPABASE_KEY")

async def scrape_videos():
    supabase: Client = None
    if SUPABASE_URL and SUPABASE_KEY:
        supabase = create_client(SUPABASE_URL, SUPABASE_KEY)
    else:
        print("Warning: Supabase credentials not found. Running in dry-run mode.")

    async with async_playwright() as p:
        # Launch browser (Headless=True for CI)
        browser = await p.chromium.launch(headless=True) 
        context = await browser.new_context(
            user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
        
        # Apply stealth
        stealth = Stealth()
        page = await context.new_page()
        await stealth.apply_stealth_async(page)

        print(f"Navigating to {TARGET_URL}...")
        try:
            await page.goto(TARGET_URL, timeout=60000, wait_until="domcontentloaded")
            
            # extract complete video objects
            videos = await page.evaluate('''() => {
                const results = [];
                const images = document.querySelectorAll('img');
                
                images.forEach(img => {
                    if (img.width > 100 && img.height > 60) {
                        let link = img.closest('a');
                        if (link) {
                            let title = img.alt;
                            if (!title || title.length < 3) {
                                const card = link.closest('.group') || link.parentElement.parentElement;
                                if (card) {
                                    const titleEl = card.querySelector('.text-secondary') || card.querySelector('h1, h2, h3, h4, div.my-2');
                                    if (titleEl) title = titleEl.innerText;
                                }
                            }

                            if (title) {
                                results.push({
                                    external_id: link.href.split('/').pop(),
                                    title: title.trim(),
                                    cover_url: img.src,
                                    source_url: link.href
                                });
                            }
                        }
                    }
                });
                return results;
            }''')

            print(f"Scraped {len(videos)} videos.")
            
            if supabase and videos:
                print("Upserting to Supabase...")
                # Chunking could be added here if list is huge, but 20-50 items is fine
                try:
                    data = supabase.table("videos").upsert(videos, on_conflict="external_id").execute()
                    print("Success: Data synced.")
                except Exception as db_err:
                    print(f"Database Error: {db_err}")
            else:
                for v in videos[:3]:
                    print(f"[Dry Run] {v['title']}")

        except Exception as e:
            print(f"Error during scraping: {e}")
        finally:
            await browser.close()

if __name__ == "__main__":
    asyncio.run(scrape_videos())
