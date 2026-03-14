# Category Hub Discovery Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace feed-page-based MissAV source discovery with category-hub discovery from `https://missav.ws/genres` so index mode can truly broaden coverage.

**Architecture:** Use browser-state scraping to harvest genre links from the MissAV category hub, normalize them into canonical source URLs/tags, then let the existing bounded discovery/filter pipeline consume them. Preserve `discovered_source_limit`, dedupe, and index-mode low-cost behavior.

**Tech Stack:** Python scraper, Playwright, GitHub Actions.
