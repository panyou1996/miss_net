# MissNet Project Context

## 1. Project Overview
**MissNet** is a full-stack, serverless video streaming application designed to aggregate and stream content from specific sources (`missav.ws`, `51cg1.com`) without a dedicated backend server. It relies on a "thick client" approach where the mobile app handles video resolution logic, and a cloud-hosted scraper for metadata ingestion.

**Core Philosophy:** "No-Backend" / Serverless.
-   **Data:** Supabase (PostgreSQL) stores metadata.
-   **Ingestion:** Python Scraper runs via GitHub Actions.
-   **Streaming:** Direct-to-client (Mobile) or Redirect (Web). The app resolves streams locally using hidden WebViews to bypass anti-scraping protections.

## 2. Architecture & Components

### A. Mobile/Web Application (`miss_net/`)
-   **Framework:** Flutter (Android, iOS, Web).
-   **Architecture:** Clean Architecture (Presentation, Domain, Data layers).
-   **State Management:** BLoC (Business Logic Component).
-   **Key Libraries:**
    -   `supabase_flutter`: Database interaction.
    -   `flutter_inappwebview`: Headless WebView for intercepting m3u8 streams and headers (`User-Agent`, `Referer`).
    -   `video_player` / `chewie`: Native video playback.
    -   `get_it`: Dependency injection.
-   **Web Limitation:** Due to CORS/browser restrictions, the Web version cannot easily resolve m3u8 streams via the headless method and often falls back to redirecting users to the source.

### B. Data Ingestion (`scraper/`)
-   **Language:** Python 3.10+.
-   **Engine:** Playwright (Async) + `playwright-stealth`.
-   **Logic:**
    -   Scrapes `missav.ws` and `51cg1.com`.
    -   Bypasses Cloudflare ("Just a moment") checks.
    -   Extracts metadata: Title, Cover URL, Source URL, Duration, Actors, Tags.
    -   Upserts data into Supabase `videos` table.
-   **Execution:** Automated via GitHub Actions (`.github/workflows/scraper.yml`) or run locally.

### C. Backend Infrastructure (`supabase/`)
-   **Database:** Supabase (PostgreSQL).
-   **Tables:**
    -   `videos`: Stores video metadata (`external_id`, `source_url`, `is_active`, etc.).
-   **Edge Functions:**
    -   `cleanup-videos` (Deno): Periodically checks 50 oldest active videos; performs `HEAD` requests to verify link validity. Marks 404s as `is_active: false`.

## 3. Key Workflows

### Video Resolution (The "Magic")
1.  **User** clicks a video in the Flutter app.
2.  **App** spawns a hidden `HeadlessInAppWebView` loading the `source_url`.
3.  **App** intercepts network traffic looking for `.m3u8` requests.
4.  **App** captures the m3u8 URL *and* the Request Headers (critical for playback).
5.  **App** passes these details to the video player.

### Data Update Cycle
1.  **GitHub Action** triggers the Python scraper.
2.  **Scraper** navigates target categories (New, Hot, Uncensored, etc.).
3.  **Scraper** parses pages, extracting metadata.
4.  **Scraper** upserts records to Supabase (preventing duplicates via `external_id`).

## 4. Development & commands

### Flutter App (`miss_net/`)
-   **Run:** `flutter run`
-   **Build APK:** `flutter build apk --release`
-   **Build Web:** `flutter build web --release`
-   **Dependencies:** `flutter pub get`

### Scraper (`scraper/`)
-   **Setup:** `pip install -r requirements.txt` (requires `playwright install chromium`)
-   **Run:** `python main.py`
-   **Env:** Requires `.env` with `SUPABASE_URL` and `SUPABASE_KEY`.

### Supabase (`supabase/`)
-   **Deploy Functions:** `supabase functions deploy cleanup-videos`
-   **Run Function Locally:** `supabase functions serve cleanup-videos`

## 5. Directory Structure
-   `/miss_net`: Flutter application source code.
-   `/scraper`: Python scraping scripts and requirements.
-   `/supabase`: Supabase configuration and Edge Functions.
-   `/.github`: CI/CD workflows.
