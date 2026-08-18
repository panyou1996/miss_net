import asyncio
import logging
import json
import os
import re
import urllib.parse
import base64
from typing import List, Dict, Any, Optional
from bs4 import BeautifulSoup
from playwright.async_api import async_playwright
from playwright_stealth import Stealth
import copy

_DEFAULT_PLAYWRIGHT_PATH = os.path.abspath(os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), ".ms-playwright"))
if os.path.exists(_DEFAULT_PLAYWRIGHT_PATH) and "PLAYWRIGHT_BROWSERS_PATH" not in os.environ:
    os.environ["PLAYWRIGHT_BROWSERS_PATH"] = _DEFAULT_PLAYWRIGHT_PATH

from . import db

try:
    from curl_cffi import AsyncSession as CurlAsyncSession
except Exception:  # pragma: no cover - optional runtime fallback
    CurlAsyncSession = None

logger = logging.getLogger("scraper_client")


def _int_env(name: str, default: int, minimum: int, maximum: int) -> int:
    try:
        value = int(os.environ.get(name, str(default)))
    except ValueError:
        value = default
    return max(minimum, min(value, maximum))


FETCH_CONCURRENCY = _int_env("MISSNET_FETCH_CONCURRENCY", 3, 1, 8)
CURL_CONCURRENCY = _int_env("MISSNET_CURL_CONCURRENCY", 3, 1, 8)
PLAYWRIGHT_TIMEOUT_MS = _int_env("MISSNET_PLAYWRIGHT_TIMEOUT_MS", 60000, 5000, 120000)
CURL_TIMEOUT_SECONDS = _int_env("MISSNET_CURL_TIMEOUT_SECONDS", 30, 5, 90)
CG_TOTAL_TIMEOUT_SECONDS = _int_env("MISSNET_51CG_TOTAL_TIMEOUT_SECONDS", 12, 3, 60)
CURL_IMPERSONATE = os.environ.get("MISSNET_CURL_IMPERSONATE", "safari15_5").strip() or None
CURL_ACCEPT_LANGUAGE = os.environ.get(
    "MISSNET_CURL_ACCEPT_LANGUAGE",
    "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7",
)
SOURCE_BLOCK_STATUSES = {403, 429, 503}
SOURCE_BLOCK_MARKERS = (
    "Just a moment",
    "cf-challenge",
    "cf_clearance",
    "/cdn-cgi/challenge-platform",
    "Cloudflare Ray ID",
)
MISSAV_PREFIXES = [
    prefix.strip().strip("/")
    for prefix in os.environ.get("MISSNET_MISSAV_PREFIXES", "dm96,dm437,dm397").split(",")
    if prefix.strip()
]
DIRECTORY_VIDEO_COUNT_RE = re.compile(r"^(?P<name>.+?)\s+(?P<count>[0-9,]+)\s+\S*影片(?:\s+(?P<year>\d{4})\s+\S+)?$")
DIRECTORY_COUNT_ONLY_RE = re.compile(r"^(?P<count>[0-9,]+)\s+\S*影片$")
ACTRESS_DIRECTORY_URLS = [
    "https://missav.ws/en/actresses",
    "https://missav.ws/cn/actresses",
    "https://missav.ai/en/actresses",
    "https://missav.ai/cn/actresses",
]
VIDEO_SORT_VALUES = {"released_at", "views"}
ACTRESS_DIRECTORY_SORT_VALUES = {"videos", "debut"}

CATEGORY_URLS = {
    # MissAV categories & common slugs
    "new": "https://missav.ws/new",
    "release": "https://missav.ws/new",
    "monthly_hot": "https://missav.ws/today-hot?sort=monthly_views",
    "monthly-hot": "https://missav.ws/today-hot?sort=monthly_views",
    "monthly_views": "https://missav.ws/today-hot?sort=monthly_views",
    "weekly_hot": "https://missav.ws/today-hot?sort=weekly_views",
    "weekly-hot": "https://missav.ws/today-hot?sort=weekly_views",
    "weekly_views": "https://missav.ws/today-hot?sort=weekly_views",
    "today_hot": "https://missav.ws/today-hot",
    "today-hot": "https://missav.ws/today-hot",
    "uncensored": "https://missav.ws/uncensored-leak",
    "uncensored-leak": "https://missav.ws/uncensored-leak",
    "chinese_subtitle": "https://missav.ws/chinese-subtitle",
    "chinese-subtitle": "https://missav.ws/chinese-subtitle",
    "subtitled": "https://missav.ws/chinese-subtitle",
    "vr": "https://missav.ws/genres/VR",
    "exclusive": "https://missav.ws/genres/%E7%8D%A8%E5%AE%B6",
    "creampie": "https://missav.ws/genres/%E4%B8%AD%E5%87%BA",
    "single": "https://missav.ws/genres/%E5%96%AE%E9%AB%94%E4%BD%9C%E5%93%81",
    "big_tits": "https://missav.ws/genres/%E5%B7%A8%E4%B9%B3",
    "mature": "https://missav.ws/genres/%E7%86%9F%E5%A5%B3",
    
    # 51CG category
    "51cg": "https://51cgm43.com/"
}


class SourceFetchError(RuntimeError):
    pass


class SourceBlockedError(SourceFetchError):
    pass


def source_blocked_message(url: str, transport: str, detail: str) -> str:
    return f"{transport} source blocked for {url}: {detail}"


def looks_like_source_block(html: str) -> bool:
    sample = html[:8000]
    return any(marker in sample for marker in SOURCE_BLOCK_MARKERS)

class PlaywrightScraper:
    def __init__(self):
        self.playwright = None
        self.browser = None
        self.context = None
        self.lock = asyncio.Lock()
        self.fetch_semaphore = asyncio.Semaphore(FETCH_CONCURRENCY)
        self.curl_semaphore = asyncio.Semaphore(CURL_CONCURRENCY)
        self.actor_directory_lock = asyncio.Lock()
        self.genre_directory_lock = asyncio.Lock()
        self._active_cg_domain = None
        self._active_cg_domains = None

    async def get_active_cg_domain(self) -> str:
        """Dynamically resolve the active 51cg domain from the entry portals.
        Returns a single domain as base, but caches domains internally.
        """
        if self._active_cg_domain:
            return self._active_cg_domain
            
        direct_url = "https://51cg1.com/"
        try:
            test_html = await self.fetch_html_resilient(direct_url)
            if "<article" in test_html:
                logger.info(f"Using direct official 51cg domain: {direct_url}")
                self._active_cg_domain = direct_url
                return direct_url
        except Exception as e:
            logger.warning(f"Direct official domain {direct_url} failed: {e}")
            
        entry_url = "https://51cgm43.com/"
        logger.info(f"Resolving active 51cg domains from entry: {entry_url}")
        try:
            html = await self.fetch_html_resilient(entry_url)
            m = re.search(r'href="([^"]+)"', html)
            if not m:
                self._active_cg_domain = entry_url
                return entry_url
                
            portal_url = m.group(1)
            portal_html = await self.fetch_html_resilient(portal_url)
            
            m2 = re.search(r"Base64\.decode\('([^']+)'\)", portal_html)
            if m2:
                decoded = base64.b64decode(m2.group(1)).decode('utf-8')
                m3 = re.findall(r'\.([a-z0-9\-]+\.cc)', decoded)
                if m3:
                    domains = list(set(m3))
                    # We will save the first successful tested one as the main active domain
                    for domain in domains:
                        for prefix in ("abandon", "ability", "able", "above"):
                            target = f"https://{prefix}.{domain}/"
                            try:
                                test_html = await self.fetch_html_resilient(target)
                                if "<article" in test_html:
                                    logger.info(f"Resolved active 51cg domain: {target}")
                                    self._active_cg_domain = target
                                    return target
                            except Exception as e:
                                pass
            
            self._active_cg_domain = entry_url
            return entry_url
        except Exception as e:
            logger.error(f"Error resolving active 51cg domain: {e}")
            return entry_url

    async def get_active_cg_domains(self) -> List[str]:
        """Resolves all available subdomains from 51cg portals for comprehensive scraping."""
        if self._active_cg_domains:
            return self._active_cg_domains
        entry_url = "https://51cgm43.com/"
        targets = ["https://51cg1.com/"]
        try:
            html = await self.fetch_html_resilient(entry_url)
            m = re.search(r'href="([^"]+)"', html)
            if m:
                portal_url = m.group(1)
                portal_html = await self.fetch_html_resilient(portal_url)
                m2 = re.search(r"Base64\.decode\('([^']+)'\)", portal_html)
                if m2:
                    decoded = base64.b64decode(m2.group(1)).decode('utf-8')
                    m3 = re.findall(r'\.([a-z0-9\-]+\.cc)', decoded)
                    if m3:
                        domains = list(set(m3))
                        for domain in domains:
                            for prefix in ("abandon", "ability", "able", "above"):
                                targets.append(f"https://{prefix}.{domain}/")
            resolved = list(set(targets))
            if len(resolved) > 1:
                self._active_cg_domains = resolved
            return resolved
        except Exception as e:
            logger.warning(f"Error resolving domains in get_active_cg_domains: {e}")
        return list(set(targets))

    async def start(self):
        async with self.lock:
            if self.context:
                return
            logger.info("Starting Playwright browser...")
            if self.playwright is None:
                self.playwright = await async_playwright().start()
            args = ["--disable-blink-features=AutomationControlled", "--no-sandbox"]
            user_agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            try:
                self.context = await self.playwright.chromium.launch_persistent_context(
                    user_data_dir="./user_data_playwright",
                    headless=True,
                    channel="chrome",
                    user_agent=user_agent,
                    args=args,
                    ignore_default_args=["--enable-automation"],
                    viewport={"width": 1280, "height": 720}
                )
            except Exception as e:
                logger.warning(f"Failed to launch with channel='chrome', falling back: {e}")
                self.context = await self.playwright.chromium.launch_persistent_context(
                    user_data_dir="./user_data_playwright",
                    headless=True,
                    user_agent=user_agent,
                    args=args,
                    viewport={"width": 1280, "height": 720}
                )
            self.browser = self.context
            logger.info("Playwright browser started successfully.")

    async def stop(self):
        async with self.lock:
            if self.context:
                await self.context.close()
                self.context = None
            self.browser = None
            if self.playwright:
                await self.playwright.stop()
                self.playwright = None
            logger.info("Playwright browser stopped.")

    async def _get_page(self):
        if not self.context:
            await self.start()
        page = await self.context.new_page()
        stealth = Stealth()
        await stealth.apply_stealth_async(page)
        return page

    async def fetch_html(self, url: str) -> str:
        async with self.fetch_semaphore:
            page = await self._get_page()
            try:
                logger.info(f"Fetching URL: {url}")
                response = await page.goto(url, timeout=PLAYWRIGHT_TIMEOUT_MS, wait_until="domcontentloaded")
                await asyncio.sleep(1.0) # Jitter sleep to let content render

                title = await page.title()
                if "Just a moment" in title:
                    logger.warning(f"Request blocked by Cloudflare: {url}")
                    await asyncio.sleep(4.0)
                    title = await page.title()
                    if "Just a moment" in title:
                        raise SourceBlockedError(source_blocked_message(url, "playwright", "challenge title"))

                html = await page.content()
                status = response.status if response else None
                if status in SOURCE_BLOCK_STATUSES:
                    detail = f"HTTP {status} challenge" if looks_like_source_block(html) else f"HTTP {status}"
                    raise SourceBlockedError(source_blocked_message(url, "playwright", detail))
                if looks_like_source_block(html):
                    raise SourceBlockedError(source_blocked_message(url, "playwright", "challenge html"))
                return html
            finally:
                await page.close()

    async def fetch_html_curl(self, url: str) -> str:
        if CurlAsyncSession is None:
            raise SourceFetchError("curl_cffi is not installed")
        logger.info(f"Fetching URL with curl_cffi transport: {url}")
        request_kwargs: dict[str, Any] = {
            "timeout": CURL_TIMEOUT_SECONDS,
            "headers": {"Accept-Language": CURL_ACCEPT_LANGUAGE},
        }
        if CURL_IMPERSONATE:
            request_kwargs["impersonate"] = CURL_IMPERSONATE

        async with CurlAsyncSession() as session:
            response = await session.get(url, **request_kwargs)

        html = response.text
        if response.status_code in SOURCE_BLOCK_STATUSES:
            detail = f"HTTP {response.status_code} challenge" if looks_like_source_block(html) else f"HTTP {response.status_code}"
            raise SourceBlockedError(source_blocked_message(url, "curl_cffi", detail))
        if response.status_code >= 400:
            raise SourceFetchError(f"curl_cffi returned HTTP {response.status_code}")
        if looks_like_source_block(html):
            raise SourceBlockedError(source_blocked_message(url, "curl_cffi", "challenge html"))
        return html

    async def fetch_html_resilient(self, url: str) -> str:
        """Fetch a URL with automatic mirror fallback.

        Strategy:
          - For MissAV and 51cg URLs: try curl_cffi first, then fall
            back to Playwright only if curl_cffi fails.
          - For all other URLs: use Playwright directly.
        """
        last_error: Exception | None = None
        for candidate_url in self._mirror_urls(url):
            uses_fast_curl = "missav." in candidate_url or "51cg" in candidate_url
            if uses_fast_curl and CurlAsyncSession is not None:
                # --- curl_cffi first (bypasses Cloudflare in <0.5 s) ---
                try:
                    async with self.curl_semaphore:
                        return await self.fetch_html_curl(candidate_url)
                except Exception as curl_error:
                    logger.warning(
                        f"curl_cffi failed for {candidate_url}: {curl_error}; "
                        "falling back to Playwright"
                    )
                    last_error = curl_error
                # --- Playwright fallback ---
                try:
                    return await self.fetch_html(candidate_url)
                except Exception as playwright_error:
                    logger.warning(
                        f"Playwright fallback also failed for {candidate_url}: {playwright_error}"
                    )
                    last_error = playwright_error
            else:
                # Non-missav URL or curl_cffi unavailable: use Playwright directly
                try:
                    return await self.fetch_html(candidate_url)
                except Exception as e:
                    if not uses_fast_curl:
                        raise
                    logger.warning(f"Playwright failed for {candidate_url}: {e}")
                    last_error = e
        if last_error:
            raise last_error
        raise RuntimeError(f"Unable to fetch URL: {url}")

    async def _gather_with_timeout(self, coroutines, timeout_seconds: float, label: str):
        """Collect completed coroutine results and cancel stragglers after a total timeout."""
        tasks = [asyncio.create_task(coro) for coro in coroutines]
        if not tasks:
            return []
        done, pending = await asyncio.wait(tasks, timeout=max(0.1, timeout_seconds))
        if pending:
            logger.warning("%s timed out with %s pending task(s); cancelling stragglers", label, len(pending))
            for task in pending:
                task.cancel()
            await asyncio.gather(*pending, return_exceptions=True)
        results = []
        for task in done:
            if task.cancelled():
                continue
            exc = task.exception()
            if exc:
                logger.warning("%s task failed: %s", label, exc)
                continue
            results.append(task.result())
        return results

    def _normalize_missav_url(self, href: str) -> str:
        if href.startswith("//"):
            return "https:" + href
        if href.startswith("/"):
            return "https://missav.ws" + href
        return href

    def _normalize_asset_url(self, href: str) -> str:
        if href.startswith("//"):
            href = "https:" + href
        parsed = urllib.parse.urlparse(href)
        if not parsed.scheme or not parsed.netloc:
            return href
        normalized_path = "/" + (parsed.path or "").lstrip("/")
        return urllib.parse.urlunparse(parsed._replace(path=normalized_path))

    def _decode_packer_token(self, token: str, base: int) -> int | None:
        alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        value = 0
        for char in token:
            digit = alphabet.find(char)
            if digit < 0 or digit >= base:
                return None
            value = value * base + digit
        return value

    def _unpack_javascript_packer(self, html: str) -> List[str]:
        """Unpack Dean Edwards /packer/ eval blocks used by MissAV for player sources."""
        unpacked: list[str] = []
        packed_pattern = re.compile(
            r"eval\(function\(p,a,c,k,e,d\).*?\}\(\s*'(?P<p>(?:\\'|[^'])*)'\s*,\s*"
            r"(?P<a>\d+)\s*,\s*(?P<c>\d+)\s*,\s*'(?P<k>(?:\\'|[^'])*)'\.split\('\|'\)",
            re.S,
        )
        for match in packed_pattern.finditer(html or ""):
            try:
                payload = match.group("p").encode("utf-8").decode("unicode_escape")
            except Exception:
                payload = match.group("p")
            base = int(match.group("a"))
            count = int(match.group("c"))
            keywords = match.group("k").split("|")

            def replace_token(token_match: re.Match[str]) -> str:
                token = token_match.group(0)
                index = self._decode_packer_token(token, base)
                if index is None or index >= count or index >= len(keywords):
                    return token
                return keywords[index] or token

            unpacked.append(re.sub(r"\b\w+\b", replace_token, payload))
        return unpacked

    def extract_missav_playback_sources(self, html: str) -> Dict[str, str]:
        """Extract protected MissAV HLS sources from packed player JavaScript."""
        sources: dict[str, str] = {}
        chunks = [html or "", *self._unpack_javascript_packer(html or "")]
        assignment_pattern = re.compile(
            r"\b(?P<name>source(?:\d+)?)\s*=\s*['\"](?P<url>https?://[^'\"]+?\.m3u8(?:\?[^'\"]*)?)['\"]",
            re.I,
        )
        direct_pattern = re.compile(r"https?://[^'\"\\\s]+?\.m3u8(?:\?[^'\"\\\s]*)?", re.I)
        for chunk in chunks:
            for match in assignment_pattern.finditer(chunk):
                sources.setdefault(match.group("name"), match.group("url").replace("\\/", "/"))
            for url in direct_pattern.findall(chunk):
                clean_url = url.replace("\\/", "/")
                sources.setdefault("source", clean_url)
        return sources

    def _page_url(self, base_url: str, page: int) -> str:
        if page <= 1:
            return base_url
        parsed = urllib.parse.urlparse(base_url)
        query = urllib.parse.parse_qs(parsed.query)
        query["page"] = [str(page)]
        return urllib.parse.urlunparse(parsed._replace(query=urllib.parse.urlencode(query, doseq=True)))

    def _url_with_query(self, base_url: str, params: dict[str, Any]) -> str:
        parsed = urllib.parse.urlparse(base_url)
        query = urllib.parse.parse_qs(parsed.query)
        for key, value in params.items():
            if value is None:
                query.pop(key, None)
                continue
            text = str(value).strip()
            if text:
                query[key] = [text]
        return urllib.parse.urlunparse(parsed._replace(query=urllib.parse.urlencode(query, doseq=True)))

    def _video_sort(self, sort_by: str | None) -> str:
        normalized = (sort_by or "released_at").strip().lower()
        return normalized if normalized in VIDEO_SORT_VALUES else "released_at"

    def _actress_directory_sort(self, sort_by: str | None) -> str:
        normalized = (sort_by or "videos").strip().lower()
        return normalized if normalized in ACTRESS_DIRECTORY_SORT_VALUES else "videos"

    def _actress_directory_url(self, base_url: str, filters: dict[str, Any] | None, sort_by: str | None) -> str:
        params = {"sort": self._actress_directory_sort(sort_by)}
        for key in ("height", "cup", "age", "debut"):
            value = (filters or {}).get(key)
            if value:
                params[key] = value
        return self._url_with_query(base_url, params)

    def _direct_actor_url(self, actor: str) -> str:
        return f"https://missav.ws/cn/actresses/{urllib.parse.quote(actor.strip())}"

    def _direct_genre_url(self, genre: str) -> str:
        return f"https://missav.ws/cn/genres/{urllib.parse.quote(genre.strip())}"

    def _mirror_urls(self, url: str) -> list[str]:
        parsed = urllib.parse.urlparse(url)
        if parsed.netloc not in {"missav.ws", "missav.ai"}:
            return [url]
        hosts = [parsed.netloc, *[host for host in ("missav.ai", "missav.ws") if host != parsed.netloc]]
        paths = [parsed.path or "/"]
        prefix_match = re.match(r"^/(dm\d+)(/.*)?$", parsed.path or "")
        for prefix in MISSAV_PREFIXES:
            if prefix_match:
                suffix = prefix_match.group(2) or "/"
                candidate_path = f"/{prefix}{suffix}"
            else:
                candidate_path = f"/{prefix}{parsed.path or '/'}"
            if candidate_path not in paths:
                paths.append(candidate_path)

        mirrors: list[str] = []
        seen: set[str] = set()
        for path in paths:
            for host in hosts:
                candidate = urllib.parse.urlunparse(parsed._replace(netloc=host, path=path))
                if candidate not in seen:
                    seen.add(candidate)
                    mirrors.append(candidate)
        return mirrors

    def _is_missav_video_url(self, href: str) -> bool:
        parsed = urllib.parse.urlparse(href)
        path_parts = [part for part in parsed.path.strip("/").split("/") if part]
        if not path_parts:
            return False

        directory_parts = {
            "actresses",
            "genres",
            "labels",
            "makers",
            "series",
            "search",
            "new",
            "today-hot",
            "monthly-hot",
            "weekly-hot",
        }
        if any(part in directory_parts for part in path_parts):
            return False

        slug = path_parts[-1]
        if slug in {"cn", "dm", "uncensored-leak", "chinese-subtitle"}:
            return False

        return len(slug) >= 3 and any(ch.isdigit() for ch in slug)

    def parse_missav_list(
        self,
        html: str,
        source_tag: str,
        actor_name: str | None = None,
        genre_name: str | None = None,
    ) -> List[Dict[str, Any]]:
        soup = BeautifulSoup(html, "html.parser")
        videos = []
        seen = set()
        items = soup.select("div.grid > div, div.thumbnail, .group")
        for item in items:
            img = item.find("img")
            link = item.find("a")
            if img and link and link.get("href"):
                href = link.get("href")
                href = self._normalize_missav_url(href)
                is_video = self._is_missav_video_url(href)
                if is_video:
                    id_val = href.split("?")[0].rstrip("/").split("/")[-1]
                    if id_val and id_val not in seen:
                        seen.add(id_val)
                        title = img.get("alt", "").strip()
                        if len(title) < 5:
                            te = item.select_one("h1, h2, h3, .text-secondary")
                            if te:
                                title = te.get_text().strip()
                        
                        if len(title) > 3:
                            cover_candidates = [
                                img.get("data-src"),
                                img.get("data-original"),
                                img.get("data-lazy-src"),
                                img.get("data-cfsrc"),
                                img.get("src")
                            ]
                            cover_url = ""
                            for candidate in cover_candidates:
                                if candidate and not candidate.startswith("data:image"):
                                    cover_url = candidate
                                    break
                            if "cover-t.jpg" in cover_url:
                                cover_url = cover_url.replace("cover-t.jpg", "cover-n.jpg")

                            item_text = item.get_text(" ", strip=True)
                            duration = None
                            duration_match = re.search(r"\b(?:\d{1,2}:)?\d{1,2}:\d{2}\b", item_text)
                            if duration_match:
                                duration = duration_match.group(0)
                            watched_count = 0
                            views_match = re.search(
                                r"([0-9][0-9,\.]*)([KkMm]?)\s*(?:views|view|觀看|观看)",
                                item_text,
                            )
                            if views_match:
                                base = float(views_match.group(1).replace(",", ""))
                                suffix = views_match.group(2).lower()
                                multiplier = 1_000_000 if suffix == "m" else 1_000 if suffix == "k" else 1
                                watched_count = int(base * multiplier)

                            categories = [genre_name or source_tag]
                            tags = [genre_name or source_tag]
                            actors = [actor_name] if actor_name else []
                            
                            # Standard format
                            videos.append({
                                "external_id": id_val,
                                "title": title,
                                "cover_url": cover_url,
                                "source_url": href,
                                "duration": duration,
                                "watched_count": watched_count,
                                "actors": actors,
                                "categories": categories,
                                "tags": tags
                            })
        return videos

    def parse_actress_directory(self, html: str) -> List[Dict[str, Any]]:
        soup = BeautifulSoup(html, "html.parser")
        grouped: dict[str, dict[str, Any]] = {}
        for link in soup.find_all("a", href=True):
            href = self._normalize_missav_url(link.get("href", ""))
            if "/actresses/" not in href or "/ranking" in href:
                continue
            entry = grouped.setdefault(href, {"source_url": href, "name": "", "cover_url": None, "video_count": 0})
            img = link.find("img")
            if img:
                cover = img.get("data-src") or img.get("data-original") or img.get("src")
                if cover and not cover.startswith("data:image"):
                    entry["cover_url"] = self._normalize_missav_url(cover)
            text = " ".join(link.get_text(" ", strip=True).split())
            if not text:
                continue
            match = DIRECTORY_VIDEO_COUNT_RE.match(text)
            if match:
                entry["name"] = match.group("name").strip()
                entry["video_count"] = int(match.group("count").replace(",", ""))
                entry["latest_release_date"] = match.group("year")
        return [
            entry
            for entry in grouped.values()
            if entry.get("name") and entry.get("source_url") and entry.get("video_count", 0) > 0
        ]

    def parse_genre_directory(self, html: str) -> List[Dict[str, Any]]:
        soup = BeautifulSoup(html, "html.parser")
        grouped: dict[str, dict[str, Any]] = {}
        for link in soup.find_all("a", href=True):
            href = self._normalize_missav_url(link.get("href", ""))
            if "/genres/" not in href:
                continue
            text = " ".join(link.get_text(" ", strip=True).split())
            if not text:
                continue
            entry = grouped.setdefault(href, {"source_url": href, "name": "", "cover_url": None, "video_count": 0})
            match = DIRECTORY_VIDEO_COUNT_RE.match(text)
            if match:
                entry["video_count"] = int(match.group("count").replace(",", ""))
                if not entry.get("name"):
                    entry["name"] = match.group("name").strip()
            elif count_match := DIRECTORY_COUNT_ONLY_RE.match(text):
                entry["video_count"] = int(count_match.group("count").replace(",", ""))
            elif "影片" not in text and len(text) <= 40:
                entry["name"] = text
        return [
            entry
            for entry in grouped.values()
            if entry.get("name") and entry.get("source_url") and entry.get("video_count", 0) > 0
        ]

    async def scrape_actor_directory(
        self,
        limit: int = 100,
        filters: Optional[Dict[str, Any]] = None,
        sort_by: str = "videos",
        persist: bool = True,
    ) -> List[Dict[str, Any]]:
        target = max(24, min(limit, 360))
        has_filters = any((filters or {}).get(key) for key in ("height", "cup", "age", "debut"))
        cache_key = "directory:actor" if not has_filters and self._actress_directory_sort(sort_by) == "videos" else None
        if cache_key and db.is_cache_valid(cache_key, 24 * 3600) and db.count_directory_entries("actor") >= target:
            return db.query_directory_entries("actor", target)
        async with self.actor_directory_lock:
            if cache_key and db.is_cache_valid(cache_key, 24 * 3600) and db.count_directory_entries("actor") >= target:
                return db.query_directory_entries("actor", target)
            max_pages = max(1, min(15, (target + 23) // 24))
            entries: list[dict[str, Any]] = []
            seen = set()
            for page in range(1, max_pages + 1):
                page_entries: list[dict[str, Any]] = []
                last_error: Exception | None = None
                for base_url in ACTRESS_DIRECTORY_URLS:
                    url = self._page_url(self._actress_directory_url(base_url, filters, sort_by), page)
                    try:
                        html = await self.fetch_html_resilient(url)
                        page_entries = self.parse_actress_directory(html)
                        if page_entries:
                            break
                    except Exception as e:
                        last_error = e
                        logger.warning(f"Actor directory URL failed: page={page} url={url} error={e}")
                if not page_entries:
                    if last_error:
                        logger.error(f"Error scraping actress directory page {page}: {last_error}")
                    break
                for entry in page_entries:
                    if entry["name"] in seen:
                        continue
                    seen.add(entry["name"])
                    entries.append(entry)
                if len(entries) >= target:
                    break
            if entries and persist:
                db.save_directory_entries("actor", entries)
                if cache_key:
                    db.update_cache_time(cache_key)
                    logger.info(f"Cached {len(entries)} actress directory entries")
                else:
                    logger.info(f"Indexed {len(entries)} filtered actress directory entries")
            else:
                db.update_cache_time("empty:directory:actor")
            return entries

    async def scrape_genre_directory(self) -> List[Dict[str, Any]]:
        if db.is_cache_valid("directory:genre", 24 * 3600) and db.count_directory_entries("genre") > 0:
            return db.query_directory_entries("genre", 360)
        async with self.genre_directory_lock:
            if db.is_cache_valid("directory:genre", 24 * 3600) and db.count_directory_entries("genre") > 0:
                return db.query_directory_entries("genre", 360)
            return await self._scrape_genre_directory_locked()

    async def _scrape_genre_directory_locked(self) -> List[Dict[str, Any]]:
        try:
            html = await self.fetch_html_resilient("https://missav.ws/cn/genres")
            entries = self.parse_genre_directory(html)
            if entries:
                db.save_directory_entries("genre", entries)
                db.update_cache_time("directory:genre")
                logger.info(f"Cached {len(entries)} genre directory entries")
            else:
                db.update_cache_time("empty:directory:genre")
            return entries
        except Exception as e:
            db.update_cache_time("error:directory:genre")
            logger.error(f"Error scraping genre directory: {e}")
            return []

    def parse_51cg_list(self, html: str, source_tag: str) -> List[Dict[str, Any]]:
        soup = BeautifulSoup(html, "html.parser")
        videos = []
        items = soup.select("#index article")
        for item in items:
            link = item.find("a")
            if link and link.get("href"):
                href = link.get("href")
                if href.startswith("/"):
                    href = "https://51cgm43.com" + href
                    
                match = re.search(r"archives/(\d+)", href)
                id_val = match.group(1) if match else None
                
                if id_val:
                    cover = ""
                    scripts = item.find_all("script")
                    for s in scripts:
                        s_text = s.string or s.text or ""
                        m = re.search(r"loadBannerDirect\('([^']+)'", s_text)
                        if m:
                            cover = m.group(1)
                            break
                    if not cover:
                        img = item.select_one("img[data-xkrkllgl]")
                        if img:
                            cover = img.get("data-xkrkllgl", "")
                    if cover:
                        cover = self._normalize_asset_url(cover)
                    
                    t = ""
                    title_el = item.select_one(".post-card-title")
                    if title_el:
                        title_copy = copy.copy(title_el)
                        for w in title_copy.select(".wrap"):
                            w.decompose()
                        t = title_copy.get_text().strip()
                    
                    cats = []
                    info_div = item.select_one(".post-card-info")
                    if info_div:
                        text = info_div.get_text()
                        parts = text.split("•")
                        if parts:
                            last_part = parts[-1]
                            if last_part:
                                cats = [c.strip() for c in re.split(r"[,，]", last_part) if c.strip()]
                    
                    final_cats = list(set([source_tag] + cats + ["51吃瓜"]))
                    
                    videos.append({
                        "external_id": "51cg_" + id_val,
                        "title": t,
                        "cover_url": cover,
                        "source_url": href,
                        "categories": final_cats,
                        "tags": [source_tag]
                    })
        return videos

    async def scrape_category(self, category: str, page: int = 1, sort_by: str | None = None) -> List[Dict[str, Any]]:
        source_sort = self._video_sort(sort_by) if sort_by is not None else "source_default"
        
        if "51cg" in category:
            domains = await self.get_active_cg_domains()
            if not domains:
                domains = [await self.get_active_cg_domain()]
            
            logger.info(f"Scraping 51cg across domains: {domains}")
            all_videos = []
            seen_ids = set()

            async def scrape_domain(base_url: str):
                if page > 1:
                    url = urllib.parse.urljoin(base_url, f"page/{page}/")
                else:
                    url = base_url
                try:
                    html = await self.fetch_html_resilient(url)
                    videos = self.parse_51cg_list(html, category)
                    return videos
                except Exception as e:
                    logger.warning(f"Error scraping 51cg on domain {base_url}: {e}")
                    return None

            results = await self._gather_with_timeout(
                (scrape_domain(d) for d in domains[:6]),
                timeout_seconds=CG_TOTAL_TIMEOUT_SECONDS,
                label=f"51cg category page {page}",
            )
            successful_sources = 0
            for v_list in results:
                if v_list is None:
                    continue
                successful_sources += 1
                for v in v_list:
                    v_id = v.get("external_id")
                    if v_id and v_id not in seen_ids:
                        seen_ids.add(v_id)
                        all_videos.append(v)

            # Sort gathered videos to maintain consistency (e.g. descending ID or list order)
            if all_videos:
                db.save_videos(all_videos)
                db.update_cache_time(f"category:{category}:{source_sort}:{page}")
                logger.info(f"Successfully scraped and cached {len(all_videos)} 51cg videos for page {page}")
            else:
                status_prefix = "empty" if successful_sources > 0 else "error"
                db.update_cache_time(f"{status_prefix}:category:{category}:{source_sort}:{page}")
            return all_videos

        else:
            base_url = CATEGORY_URLS.get(category)
            if not base_url:
                entry = db.query_directory_entry("genre", category)
                if not entry:
                    await self.scrape_genre_directory()
                    entry = db.query_directory_entry("genre", category)
                base_url = entry.get("source_url") if entry else None
            if not base_url:
                base_url = self._direct_genre_url(category)
                logger.info(f"Using direct genre URL for uncached category={category}: {base_url}")
            
            url = self._page_url(base_url, page)
            if sort_by is not None:
                url = self._url_with_query(url, {"sort": self._video_sort(sort_by)})
                        
            try:
                html = await self.fetch_html_resilient(url)
                videos = self.parse_missav_list(html, category, genre_name=category)
                self._apply_source_view_rank(videos, sort_by)
                if videos:
                    db.save_videos(videos)
                    db.update_cache_time(f"category:{category}:{source_sort}:{page}")
                    logger.info(f"Successfully scraped and cached {len(videos)} videos for {category} page {page} sort={sort_by}")
                else:
                    db.update_cache_time(f"empty:category:{category}:{source_sort}:{page}")
                return videos
            except Exception as e:
                db.update_cache_time(f"error:category:{category}:{source_sort}:{page}")
                logger.error(f"Error scraping category {category} page {page} sort={sort_by}: {e}")
                return []

    async def scrape_actor_videos(self, actor: str, page: int = 1, sort_by: str = "released_at") -> List[Dict[str, Any]]:
        entry = db.query_directory_entry("actor", actor)
        source_url = entry.get("source_url") if entry else None
        using_direct_url = False
        source_actor_name = str((entry.get("name") if entry else None) or db.actor_primary_name(actor) or actor)
        if not source_url:
            source_url = self._direct_actor_url(source_actor_name)
            using_direct_url = True
            logger.info(f"Using direct actor URL for uncached actor={actor} source_actor={source_actor_name}: {source_url}")
        url = self._page_url(self._url_with_query(source_url, {"sort": self._video_sort(sort_by)}), page)
        try:
            html = await self.fetch_html_resilient(url)
            videos = self.parse_missav_list(html, actor, actor_name=actor)
            self._apply_source_view_rank(videos, sort_by)
            if videos:
                db.save_videos(videos)
                if using_direct_url:
                    db.save_directory_entries("actor", [{
                        "name": actor,
                        "source_url": source_url,
                        "cover_url": videos[0].get("cover_url"),
                        "video_count": len(videos),
                    }])
                db.update_cache_time(f"actor:{actor}:{self._video_sort(sort_by)}:{page}")
                logger.info(f"Successfully scraped and cached {len(videos)} videos for actor={actor} page {page} sort={sort_by}")
            else:
                db.update_cache_time(f"empty:actor:{actor}:{self._video_sort(sort_by)}:{page}")
            return videos
        except Exception as e:
            db.update_cache_time(f"error:actor:{actor}:{self._video_sort(sort_by)}:{page}")
            logger.error(f"Error scraping actor {actor} page {page} sort={sort_by}: {e}")
            return []

    def _apply_source_view_rank(self, videos: List[Dict[str, Any]], sort_by: str | None) -> None:
        if self._video_sort(sort_by) != "views" or not videos:
            return
        if any(int(video.get("watched_count") or 0) > 0 for video in videos):
            return
        rank_base = len(videos)
        for index, video in enumerate(videos):
            video["watched_count"] = rank_base - index

    async def scrape_detail(self, video_id: str, source_url: str) -> Optional[Dict[str, Any]]:
        if not source_url:
            return None
        if "51cg" in video_id and source_url:
            active_domain = await self.get_active_cg_domain()
            parsed = urllib.parse.urlparse(source_url)
            parsed_active = urllib.parse.urlparse(active_domain)
            source_url = urllib.parse.urlunparse(parsed._replace(
                scheme=parsed_active.scheme,
                netloc=parsed_active.netloc
            ))
        try:
            html = await self.fetch_html_resilient(source_url)
            soup = BeautifulSoup(html, "html.parser")
            
            if "51cg" in video_id:
                # 51CG detail page
                title = ""
                title_el = soup.select_one("h1.post-title")
                if title_el:
                    title = title_el.get_text().strip()
                    
                tags = []
                tag_links = soup.select(".tags .keywords a")
                for a in tag_links:
                    tags.append(a.get_text().strip())
                    
                release_date = None
                date_el = soup.select_one(".post-meta time")
                if date_el:
                    release_date = date_el.get_text().strip()
                else:
                    meta_date = soup.select_one('meta[itemprop="datePublished"]')
                    if meta_date:
                        release_date = meta_date.get("content")
                        
                # 51cg videos sometimes have actor names in titles/tags
                actors = []
                
                # Extract multiple videos from dplayers or page scripts
                videos_found = []
                dplayers = soup.select('.dplayer')
                for idx, dp in enumerate(dplayers):
                    config_str = dp.get('data-config')
                    if config_str:
                        try:
                            config = json.loads(config_str)
                            if config.get("video") and config["video"].get("url"):
                                # Try to get preceding subtitle tag
                                sub_title = ""
                                container = dp.parent
                                prev = dp.previous_sibling
                                if container and container.name == 'p':
                                    prev = container.previous_sibling
                                while prev and not getattr(prev, 'text', '').strip():
                                    prev = prev.previous_sibling
                                if prev:
                                    sub_title = getattr(prev, 'text', '').strip()
                                
                                videos_found.append({
                                    "url": config["video"]["url"],
                                    "title_suffix": sub_title
                                })
                        except Exception:
                            pass

                if not videos_found:
                    # Fallback to search script blocks for raw m3u8 playlist sources
                    m3u8_matches = re.findall(r'["\'](https?[^"\']+\.m3u8[^"\']*)["\']', html)
                    seen_urls = set()
                    for match in m3u8_matches:
                        clean_url = match.replace("\\/", "/")
                        if clean_url not in seen_urls:
                            seen_urls.add(clean_url)
                            videos_found.append({
                                "url": clean_url,
                                "title_suffix": ""
                            })
                
                # Retrieve existing video to keep categories and cover
                base_video_id = video_id.split('_')[0] + "_" + video_id.split('_')[1] # e.g. 51cg_12345
                existing = db.query_video_by_id(base_video_id)
                base_cats = existing.get("categories", ["51cg", "51吃瓜"]) if existing else ["51cg", "51吃瓜"]
                base_tags = existing.get("tags", ["51cg"]) if existing else ["51cg"]
                base_cover = existing.get("cover_url", "") if existing else ""
                
                # Multi-stage cover extraction fallback
                if not base_cover:
                    banner_match = re.search(r"loadBannerDirect\(['\"]([^'\"]+)", html)
                    if banner_match:
                        base_cover = self._normalize_asset_url(banner_match.group(1))
                if not base_cover:
                    og_img = soup.find("meta", property="og:image")
                    if og_img and og_img.get("content"):
                        base_cover = self._normalize_asset_url(og_img.get("content"))
                if not base_cover:
                    content_img = soup.select_one(".post-content img, .entry-content img, article img")
                    if content_img:
                        cand = content_img.get("data-src") or content_img.get("data-original") or content_img.get("src")
                        if cand and not cand.startswith("data:image"):
                            base_cover = self._normalize_asset_url(cand)
                if not base_cover:
                    cdn_jpgs = re.findall(r"https?://[^\s\"\'\(\)<>]+\.(?:jpe?g|png|webp)", html)
                    for j in cdn_jpgs:
                        if "upload" in j or "pic." in j:
                            base_cover = self._normalize_asset_url(j)
                            break
                
                video_records = []
                for i, video_info in enumerate(videos_found):
                    v_idx = i + 1
                    target_id = base_video_id if v_idx == 1 else f"{base_video_id}_{v_idx}"
                    target_title = title
                    if video_info["title_suffix"]:
                        target_title = f"{title} {video_info['title_suffix']}"
                    elif v_idx > 1:
                        target_title = f"{title} (视频 {v_idx})"
                    
                    target_url = f"{source_url}#video-{v_idx}"
                    
                    detail_data = {
                        "id": target_id,
                        "external_id": target_id,
                        "title": target_title,
                        "cover_url": base_cover,
                        "tags": list(set(tags + base_tags)),
                        "actors": actors,
                        "source_release_date": release_date,
                        "source_url": target_url,
                        "detail_status": "success",
                        "inventory_status": "detail_ready" if v_idx == 1 else "sub_video",
                        "categories": base_cats,
                        "duration": video_info["url"]  # Storing the m3u8 playlist source url directly in the duration field for downstream playback resolution compatibility!
                    }
                    video_records.append(detail_data)
                
                if video_records:
                    db.save_videos(video_records)
                    # Return the record matching the requested video_id, default to first
                    matching_record = next((r for r in video_records if r["id"] == video_id), video_records[0])
                    return matching_record
                else:
                    detail_data = {
                        "id": video_id,
                        "external_id": video_id,
                        "title": title,
                        "tags": tags,
                        "actors": actors,
                        "source_release_date": release_date,
                        "detail_status": "success"
                    }
                    db.save_videos([detail_data])
                    return detail_data
            else:
                # MissAV detail page
                playback_sources = self.extract_missav_playback_sources(html)
                playback_url = (
                    playback_sources.get("source")
                    or playback_sources.get("source1280")
                    or playback_sources.get("source842")
                    or next(iter(playback_sources.values()), None)
                )
                data = {"duration": None, "release_date": None, "actors": [], "tags": [], "cover_url": None}
                rows = soup.select("div.text-secondary")
                for row in rows:
                    span = row.find("span")
                    if not span:
                        continue
                    label = span.get_text()
                    row_text = row.get_text()
                    normalized_text = row_text.replace(label, "").replace(":", "").replace("：", "").strip()
                    
                    if "时长" in label or "時長" in label or "Duration" in label:
                        data["duration"] = normalized_text
                    elif "日期" in label or "Release" in label:
                        time_el = row.find("time")
                        data["release_date"] = time_el.get_text().strip() if time_el else normalized_text
                    elif any(x in label for x in ["女优", "女優", "Actresses", "Artist", "演員", "演员"]):
                        data["actors"] = [a.get_text().strip() for a in row.find_all("a")]
                    elif any(x in label for x in ["类型", "類型", "标签", "標籤", "标籤", "Genre", "Tag", "Tags", "类别", "類別"]):
                        data["tags"] = list(set(data["tags"] + [a.get_text().strip() for a in row.find_all("a")]))
                        
                if not data["actors"]:
                    data["actors"] = [
                        a.get_text().strip()
                        for a in soup.select('a[href*="/actresses/"]')
                        if a.get_text().strip() and "/ranking" not in a.get("href", "") and "排行" not in a.get_text() and "ranking" not in a.get_text().lower()
                    ]

                if not data["tags"]:
                    tag_links = soup.select('a[href*="/genres/"], a[href*="/makers/"], a[href*="/labels/"], a[href*="/series/"]')
                    data["tags"] = list({a.get_text().strip() for a in tag_links if a.get_text().strip()})

                if not data["release_date"]:
                    time_el = soup.find("time")
                    if time_el:
                        data["release_date"] = time_el.get_text().strip()

                og_img = soup.find("meta", property="og:image")
                if og_img and og_img.get("content"):
                    data["cover_url"] = og_img.get("content")
                else:
                    video_el = soup.find("video")
                    if video_el and video_el.get("poster"):
                        data["cover_url"] = video_el.get("poster")
                        
                detail_data = {
                    "external_id": video_id,
                    "cover_url": data["cover_url"],
                    "duration": playback_url or data["duration"],
                    "source_release_date": data["release_date"],
                    "actors": data["actors"],
                    "tags": data["tags"],
                    "detail_status": "success"
                }
                
                # Retrieve existing video to keep categories
                existing = db.query_video_by_id(video_id)
                if existing:
                    merged = {**existing, **{k: v for k, v in detail_data.items() if v is not None}}
                    # Keep original tags and categories
                    merged["categories"] = list(set(existing.get("categories", []) + detail_data.get("categories", [])))
                    merged["tags"] = list(set(existing.get("tags", []) + detail_data.get("tags", [])))
                    db.save_videos([merged])
                    return merged
                else:
                    detail_data["source_url"] = source_url
                    db.save_videos([detail_data])
                    return detail_data
        except Exception as e:
            db.update_cache_time(f"error:detail:{video_id}")
            logger.error(f"Error scraping detail for video {video_id}: {e}")
            return None


    async def scrape_search(self, query: str, page: int = 1) -> List[Dict[str, Any]]:
        # For search, we fetch the search result page from MissAV & 51CG in real-time
        encoded_query = urllib.parse.quote(query.strip())
        if page > 1:
            missav_search_url = f"https://missav.ws/search/{encoded_query}?page={page}"
        else:
            missav_search_url = f"https://missav.ws/search/{encoded_query}"
        
        # 51cg search path: dynamically resolved active domain
        active_domain = await self.get_active_cg_domain()
        if page > 1:
            cg_search_url = urllib.parse.urljoin(active_domain, f"page/{page}/?s={encoded_query}")
        else:
            cg_search_url = urllib.parse.urljoin(active_domain, f"index.php?s={encoded_query}")
        
        logger.info(f"Running dynamic search scraper for query: '{query}', page={page}")
        
        videos = []
        try:
            # Scrape MissAV search page
            html_missav = await self.fetch_html_resilient(missav_search_url)
            v_missav = self.parse_missav_list(html_missav, "search")
            videos.extend(v_missav)
        except Exception as e:
            logger.error(f"Error scraping MissAV search for '{query}' (page {page}): {e}")
            
        try:
            # Scrape 51CG search page
            html_cg = await self.fetch_html_resilient(cg_search_url)
            v_cg = self.parse_51cg_list(html_cg, "search")
            videos.extend(v_cg)
        except Exception as e:
            logger.error(f"Error scraping 51CG search for '{query}' (page {page}): {e}")
            
        if videos:
            db.save_videos(videos)
            db.update_cache_time(f"search:{query.strip().lower()}:{page}")
            logger.info(f"Dynamically discovered {len(videos)} search results for query '{query}' (page {page})")
        else:
            db.update_cache_time(f"empty:search:{query.strip().lower()}:{page}")
            
        return videos

scraper = PlaywrightScraper()
