from playwright.async_api import async_playwright
from playwright_stealth import Stealth
from supabase import create_client, Client
from dotenv import load_dotenv
import os
import asyncio

# Load environment variables from .env file if present
load_dotenv()

# Target URLs for Diversity
SOURCES = [
    {"url": "https://missav.ws/new", "tag": "new"},
    {"url": "https://missav.ws/dm263/monthly-hot?sort=monthly_views", "tag": "monthly_hot"},
    {"url": "https://missav.ws/dm169/weekly-hot?sort=weekly_views", "tag": "weekly_hot"},
    {"url": "https://missav.ws/dm628/uncensored-leak", "tag": "uncensored"},
    # {"url": "https://missav.ws/dm590/release", "tag": "release"} # Optional
]

# Supabase Setup
SUPABASE_URL = os.environ.get("SUPABASE_URL")
SUPABASE_KEY = os.environ.get("SUPABASE_KEY")

async def scrape_videos():
    supabase: Client = None
    if SUPABASE_URL and SUPABASE_KEY:
        supabase = create_client(SUPABASE_URL, SUPABASE_KEY)
    else:
        print("Warning: Supabase credentials not found. Running in dry-run mode.")

    # Browser Config
    HEADLESS = os.environ.get("HEADLESS", "true").lower() == "true"
    USER_DATA_DIR = os.path.join(os.getcwd(), "user_data") # Store session/cookies here

    async with async_playwright() as p:
        # Args to bypass simple bot detection
        args = [
            "--disable-blink-features=AutomationControlled",
            "--no-sandbox",
            "--disable-setuid-sandbox",
            "--disable-infobars",
            "--window-position=0,0",
            "--ignore-certificate-errors",
            "--ignore-certificate-errors-spki-list",
            "--disable-accelerated-2d-canvas",
            "--disable-gpu",
        ]

        # Use launch_persistent_context to keep cookies/local storage
        # Using channel="chrome" to use the REAL Google Chrome browser reduces detection significantly.
        # ignore_default_args=["--enable-automation"] removes the "controlled by automation" banner.
        try:
            context = await p.chromium.launch_persistent_context(
                user_data_dir=USER_DATA_DIR,
                headless=HEADLESS,
                channel="chrome",
                args=args,
                ignore_default_args=["--enable-automation"], 
                user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                viewport={"width": 1280, "height": 720}
            )
        except Exception:
            print("Google Chrome not found, falling back to bundled Chromium...")
            context = await p.chromium.launch_persistent_context(
                user_data_dir=USER_DATA_DIR,
                headless=HEADLESS,
                args=args,
                ignore_default_args=["--enable-automation"],
                viewport={"width": 1280, "height": 720}
            )
        
        # Apply stealth to the context
        stealth = Stealth()
        page = context.pages[0] if context.pages else await context.new_page()
        await stealth.apply_stealth_async(page)

        import random
        
        # Iterate through different sources
        for source in SOURCES:
            base_url = source["url"]
            tag = source["tag"]
            print(f"\n--- Scraping Category: {tag} ---")

            # Scrape Pages 1 to 2
            for page_num in range(1, 30):
                # Construct URL with pagination
                separator = "&" if "?" in base_url else "?"
                current_url = f"{base_url}{separator}page={page_num}"
                
                print(f"Navigating to {current_url}...")
                
                # Random delay
                await asyncio.sleep(random.uniform(3, 7))

                try:
                    await page.goto(current_url, timeout=60000, wait_until="domcontentloaded")
                    
                    # Human-like behavior
                    try:
                        await page.mouse.move(random.randint(100, 500), random.randint(100, 500))
                    except:
                        pass
                    
                    await asyncio.sleep(random.uniform(1.5, 3.5))
                    
                    # Check for Cloudflare
                    title = await page.title()
                    if "Just a moment" in title or "Cloudflare" in title:
                        print(f"⚠️ Hit Cloudflare challenge on {tag.upper()} Page {page_num}.")
                        
                        if not HEADLESS:
                            print("\n" + "="*60)
                            print("🔴 AUTOMATED SOLVE ATTEMPT: Simulating human behavior...")
                            print("="*60 + "\n")
                            
                            try:
                                # Wait for the Turnstile widget (iframe)
                                # Cloudflare widgets usually have an iframe
                                iframe_element = await page.wait_for_selector("iframe[src*='cloudflare']", timeout=10000)
                                if iframe_element:
                                    box = await iframe_element.bounding_box()
                                    if box:
                                        print("Targeting Cloudflare widget...")
                                        # Random movement strategy
                                        # Start somewhat near the box
                                        start_x = box["x"] - random.randint(50, 150)
                                        start_y = box["y"] - random.randint(50, 150)
                                        await page.mouse.move(start_x, start_y)
                                        
                                        # Move towards the center with noise
                                        target_x = box["x"] + box["width"] / 2
                                        target_y = box["y"] + box["height"] / 2
                                        
                                        steps = 20
                                        for i in range(steps):
                                            # Interpolate with randomness
                                            x = start_x + (target_x - start_x) * (i / steps) + random.randint(-5, 5)
                                            y = start_y + (target_y - start_y) * (i / steps) + random.randint(-5, 5)
                                            await page.mouse.move(x, y)
                                            await asyncio.sleep(random.uniform(0.01, 0.05))
                                        
                                        # Hover over it for a bit
                                        await asyncio.sleep(random.uniform(0.5, 1.0))
                                        
                                        # Click!
                                        print("Clicking widget...")
                                        await page.mouse.click(target_x, target_y)
                                        
                                        # Wait for success
                                        await page.wait_for_selector('div.group', timeout=15000)
                                        print("✅ CAPTCHA solved automatically!")
                                        await asyncio.sleep(2)
                            except Exception as e:
                                print(f"⚠️ Auto-solve failed: {e}")
                                print("Please solve manually if the window is open.")
                                try:
                                    await page.wait_for_selector('div.group', timeout=60000)
                                except:
                                    pass

                        else:
                            print("Waiting 10s to see if it auto-solves (Headless mode)...")
                            await asyncio.sleep(10)
                            title = await page.title()
                            if "Just a moment" in title or "Cloudflare" in title:
                                print("BLOCKED by Cloudflare. Skipping this source.")
                                break

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
                                        // Convert thumbnail URL to High-Res URL (cover-t.jpg -> cover-n.jpg)
                                        let cover = img.src;
                                        if (cover.includes('cover-t.jpg')) {
                                            cover = cover.replace('cover-t.jpg', 'cover-n.jpg');
                                        }

                                        // Add the current category tag
                                        // We pass the python variable 'tag' into the JS context via formatting or args
                                        // But here we are inside a string literal for evaluate.
                                        // Easier approach: Return basic data, add tag in Python.
                                        results.push({
                                            external_id: link.href.split('/').pop(),
                                            title: title.trim(),
                                            cover_url: cover,
                                            source_url: link.href
                                        });
                                    }
                                }
                            }
                        });
                        return results;
                    }''')

                    print(f"[{tag.upper()}] Page {page_num}: Scraped {len(videos)} videos.")
                    
                    if supabase and videos:
                        # Add tag to the video data
                        videos_with_tags = []
                        for v in videos:
                            v['tags'] = [tag] # Overwrite or append? Overwrite is safer for 'source' categorization
                            videos_with_tags.append(v)

                        try:
                            data = supabase.table("videos").upsert(videos_with_tags, on_conflict="external_id").execute()
                            print("Success: Data synced.")
                        except Exception as db_err:
                            print(f"Database Error: {db_err}")
                    elif not supabase:
                        if videos:
                            print(f"[Dry Run] {videos[0]['title']}")
                    
                    if len(videos) == 0:
                        print("No videos found, stopping this source.")
                        break

                except Exception as e:
                    print(f"Error scraping {tag} page {page_num}: {e}")

        await context.close()

if __name__ == "__main__":
    asyncio.run(scrape_videos())
