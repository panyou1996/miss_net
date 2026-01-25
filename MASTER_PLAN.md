# Master Execution Plan: MissNet (Flutter + Supabase + Python)

## Phase 1: Infrastructure & Data Ingestion (Python & Supabase)

**Goal:** Establish the database and automate content scraping.

### 1.1 Database Schema (Supabase)
**Task:** Create the database schema in Supabase SQL Editor.
**SQL:**
```sql
-- Enable UUID extension
create extension if not exists "uuid-ossp";

-- Create Videos Table
create table videos (
  id uuid primary key default uuid_generate_v4(),
  external_id text unique not null,
  title text not null,
  cover_url text,
  source_url text not null,
  duration text,
  tags text[],
  created_at timestamp with time zone default timezone('utc'::text, now()) not null
);

-- Enable Row Level Security (RLS)
alter table videos enable row level security;

-- Policy: Allow Public Read Access
create policy "Public videos are viewable by everyone"
  on videos for select
  using ( true );

-- Policy: Allow Service Role (Scraper) Insert/Update
-- (Service role bypasses RLS by default, but explicit is good)
```

### 1.2 Python Scraper (Local Development)
**Task:** Develop the scraper engine.
**Path:** `scraper/`
**Tech:** Python 3.10+, `playwright`, `supabase`.
**Steps:**
1.  **Setup:** `pip install playwright supabase python-dotenv pytest`.
2.  **Browser:** `playwright install chromium`.
3.  **Logic (`scraper/main.py`):**
    *   Initialize Supabase Client.
    *   Launch Playwright (Headless=True, but use `stealth` plugins if possible).
    *   **Target:** Iterate through pagination of `missav.ws`.
    *   **Extraction:**
        *   `external_id`: Extract from URL or DOM.
        *   `title`: Text content of title tag.
        *   `cover_url`: Image source.
        *   `source_url`: The link to the video page.
    *   **Storage:** Use `supabase.table('videos').upsert(data, on_conflict='external_id').execute()`.
    *   **Error Handling:** Implement retry logic for network timeouts.

### 1.3 GitHub Actions Automation (CI/CD)
**Task:** Schedule the scraper.
**File:** `.github/workflows/scraper.yml`
**Risks:** GitHub IPs might be blocked.
**Mitigation:**
    *   Use `playwright-python` action.
    *   Set user-agent to a common desktop UA.
    *   Run at random intervals if possible (cron is fixed, but sleep random seconds at start).

## Phase 2: Flutter Foundation & Architecture

**Goal:** Initialize a scalable Flutter app using Clean Architecture.

### 2.1 Project Setup
**Task:** Scaffold the application.
**Commands:**
```bash
flutter create miss_net
cd miss_net
flutter pub add flutter_bloc equatable get_it supabase_flutter cached_network_image flutter_inappwebview chewie video_player url_launcher google_fonts
```

### 2.2 Layered Structure (Clean Architecture)
**Structure:**
```text
lib/
├── core/
│   ├── constants/
│   ├── error/
│   ├── services/       <-- Service Locator, Resolver logic
│   └── theme/
├── data/
│   ├── datasources/    <-- SupabaseVideoDataSource
│   ├── models/         <-- VideoModel (fromJson)
│   └── repositories/   <-- VideoRepositoryImpl
├── domain/
│   ├── entities/       <-- Video (Pure Dart class)
│   ├── repositories/   <-- IVideoRepository (Interface)
│   └── usecases/       <-- GetRecentVideos, SearchVideos
├── presentation/
│   ├── blocs/          <-- HomeBloc, PlayerBloc
│   ├── pages/          <-- HomePage, PlayerPage
│   └── widgets/        <-- VideoCard, LoadingSpinner
└── main.dart
```

### 2.3 Data Layer Implementation
**Task:** Connect to Supabase.
*   **DataSource:** `SupabaseVideoDataSource` methods: `getVideos({int limit, int offset})`, `searchVideos(String query)`.
*   **Repository:** Map `VideoModel` to `Video` entity.

## Phase 3: The "Magic" Resolver (Video Playback)

**Goal:** Extract the HLS stream on the user's device.

### 3.1 Video Resolver Service
**Path:** `lib/core/services/video_resolver.dart`
**Logic:**
1.  **Input:** `source_url` (e.g., `https://missav.ws/dm123`).
2.  **Mechanism:** Spawn a `HeadlessInAppWebView`.
3.  **Interception:**
    *   Listen to `onLoadResource` or `shouldInterceptRequest`.
    *   Filter for `*.m3u8`.
4.  **Extraction:**
    *   Capture the full URL.
    *   **CRITICAL:** Capture `User-Agent` and `Referer` headers from the request. Some streams fail without them.
5.  **Output:** Return a custom object `StreamInfo(url, headers)`.

### 3.2 Native Player Integration
**Path:** `lib/presentation/pages/player/video_player_screen.dart`
**Logic:**
1.  Show `CircularProgressIndicator` while `VideoResolver` runs.
2.  On success, initialize `VideoPlayerController.networkUrl`.
    *   Pass `httpHeaders` map from `StreamInfo`.
3.  Wrap in `Chewie` for UI controls (fullscreen, progress bar).
4.  **Lifecycle:** Dispose controllers properly to avoid memory leaks.

## Phase 4: UI/UX Implementation

**Goal:** Create a polished, "Netflix-like" interface.

### 4.1 Home Screen
**Widgets:**
*   `SliverAppBar`: Collapsing header.
*   `FeaturedVideo`: Large banner at top (random or latest).
*   `VideoGrid` / `VideoList`: Horizontal lists for "New Releases", "Trending".
*   `VideoCard`: Rounded corners, `CachedNetworkImage`, fade-in effect.

### 4.2 Search Functionality
**Task:** Add search capability.
*   **UI:** Search icon in AppBar -> SearchDelegate or dedicated Page.
*   **Logic:** Call `Supabase.from('videos').select().textSearch('title', query)`.

### 4.3 Web Adaptation
**Task:** Handle platform differences.
*   **Check:** `kIsWeb` (from `flutter/foundation.dart`).
*   **Logic:**
    *   **Mobile:** Open `PlayerPage`.
    *   **Web:** Show Dialog: "Playback not supported on Web due to browser restrictions. Open source?" -> `launchUrl(source_url)`.

## Phase 5: Testing & Deployment

### 5.1 Quality Assurance
*   **Unit Tests:** Test Bloc states and Repository mapping.
*   **Integration Test:** Test the Scraper flow locally before pushing.

### 5.2 Deployment
*   **Android:** Build APK (`flutter build apk --release`).
*   **Web:** Build Web (`flutter build web --release`). Deploy to Vercel/Netlify.
*   **CI:** Ensure GitHub Action for scraper is enabled and secrets (`SUPABASE_URL`, `SUPABASE_KEY`) are set in Repo Settings.
