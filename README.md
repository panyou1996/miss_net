Project: MissNet-Flutter (Serverless Streaming App)

1. Project Overview

We are building a Full-Stack Video Streaming Application similar to Netflix.

Target Platform: Flutter (Android/iOS) and Web.

Content Source: missav.ws (Japanese Video Content).

Architecture: Serverless. No dedicated backend server.

Infrastructure:

Database: Supabase (PostgreSQL).

Scraper: Python + Playwright (Hosted on GitHub Actions).

Hosting: Vercel (for Flutter Web).

2. Technology Stack

Frontend: Flutter (Latest Stable).

Architecture: Clean Architecture (Domain, Data, Presentation layers).

State Management: BLoC or Riverpod.

Networking: Supabase Flutter SDK.

Video Strategy: flutter_inappwebview (Headless) + chewie/video_player.

Backend (Data Ingestion):

Language: Python 3.10+.

Libs: playwright (stealth mode), supabase.

Automation: GitHub Actions (Cron Job).

3. Core Architecture & Constraints (CRITICAL)

A. The "No-Backend" Policy

We do not have a server to run FFmpeg or proxies.

Do NOT try to stream video bytes through a middleman.

Do NOT store .m3u8 links in the database (they expire/rotate).

DO store static metadata (Title, Cover URL, Source Page URL, ID).

B. The Playback Strategy (Bypass Cloudflare)

Mobile App:

Use a Headless InAppWebView to load the Source Page URL in the background.

Intercept the network traffic to find the .m3u8 request.

Extract the URL and HTTP Headers (User-Agent, Referer).

Pass these to the native video player.

Web Version:

Due to CORS, we cannot intercept m3u8 easily.

Action: Show "Open in Original Site" button or try to embed via Iframe (if allowed), otherwise redirect.

C. Data Pipeline

GitHub Action runs daily.

Python Script uses Playwright (Stealth) to scrape missav.ws.

Script upserts data into Supabase videos table.

Flutter App queries Supabase directly.

4. Database Schema (Supabase)

Table: videos

id (uuid, primary key)

external_id (string, unique) - The ID from the website (e.g., 'dm221').

title (text)

cover_url (text)

source_url (text) - The page URL to scrape m3u8 from later.

created_at (timestamp)

tags (array of strings)