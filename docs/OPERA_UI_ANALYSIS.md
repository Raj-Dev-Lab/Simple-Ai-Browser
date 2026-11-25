# Opera Mobile (Opera for Android) — UI & Shell Architecture Analysis

This document captures the deep structural teardown of **Opera for Android (Opera Mobile)** so **Simple AI Browser (SAB)** reproduces its thumb-first ergonomics, visual polish, and modular shell design while swapping the underlying web engine to **GeckoView 159+ (`arm64-v8a`, non-omni)**.

---

## 1. Core Ergonomic Philosophy: Bottom-Heavy Thumb Architecture

Modern smartphones (6.1"–6.8") make top-of-screen URL bars hard to reach one-handed. Opera Mobile solves this by inverting the classic desktop-derived layout:

| Screen Zone | Viewport Share | What Lives Here in Opera Mobile | SAB Equivalent |
| --- | --- | --- | --- |
| **Hard Reach (Top 25–30%)** | Non-interactive or glanceable | Status indicators, wallpaper/header, optional compact URL title bar when scrolling | Compact page title + TLS security indicator + loading progress bar |
| **Stretch Zone (Middle 35–45%)** | Primary scroll canvas | Speed Dial grid, news/discover cards, active webpage viewport | `GeckoView` rendering surface / Speed Dial grid / Tab Gallery |
| **Natural Thumb Zone (Bottom 30–40%)** | All frequent actions | Search trigger, Back/Forward, Central AI button, Tab Switcher, Menu Sheet | Persistent 5-slot Bottom Navigation Bar + Unified Omnibox & Bottom Sheets |

### Sticky Chrome Budget
- Combined height of top indicator bar + bottom navigation bar stays compact (typically `48dp` top compact bar + `56dp`–`64dp` bottom bar) and **auto-collapses on downward scroll** via scroll listener callbacks (`GeckoSession.ScrollDelegate` in SAB).

---

## 2. Screen-by-Screen UI Breakdown

### A. Start Page (Home / New Tab)
1. **Top Header Strip:**
   - Left/Center: Brand mark + subtle greeting or Private Mode badge when incognito.
   - Right: Quick theme/profile/customization icon.
2. **Speed Dial Grid:**
   - 4-column (portrait) or 6-column (landscape/tablet) grid of visual site tiles.
   - Supports **three tile geometry presets** (matching Opera's start-page personalization): **Squircle** (`22%` radius), **Circle** (`50%`), or **Square** (`12%`).
   - Built-in tiles + user-pinned bookmarks + `"+"` Add Speed Dial action.
3. **Unified Search & AI Input Pill:**
   - Anchored cleanly within thumb reach on the start page.
   - Tapping expands the **Omnibox Overlay** with an instant dual-mode switch:
     - **Search / URL Mode:** Autocomplete, history matches, QR scanner, voice input.
     - **Ask SAB AI Mode:** Direct prompt bar with page-context chip.

### B. Persistent 5-Slot Bottom Navigation Bar
Opera Mobile's signature control strip places the five highest-frequency actions along the bottom edge:

1. **Back / Home** (context-sensitive: Back when `canGoBack == true`, or Home/Search when at stack root; long-press shows tab history).
2. **Search / Address Trigger** (focuses the Omnibox or jumps to Speed Dial).
3. **Center SAB AI Orb** (prominent primary accent button in the center—mirrors Opera's central Aria trigger—opens the contextual SAB AI Bottom Sheet).
4. **Tabs Counter Button** (rounded-square tab count badge showing open tab count `1..99` or `:D` above 99; opens the Visual Tab Gallery; swipe left/right on the bottom bar switches adjacent tabs!).
5. **Menu (`≡` / `⋮`)** (opens the Opera-style Bottom Sheet Menu).

### C. Visual Tab Gallery & Tab Islands
- **Tab Cards:** 2-column grid (or stacked carousel) showing live tab thumbnail snapshot (`GeckoSession.capturePixels()`), favicon, page title, and close (`×`) button.
- **Tab Islands:** Related tabs opened from the same origin or task automatically group under a collapsible island header with a shared color strip, preventing 40-tab clutter.
- **Segmented Top Bar in Tab Gallery:**
  - `Normal Tabs` | `Private Tabs` | `Recently Closed`
  - Search open tabs filter + `"Close All"` action in overflow.

### D. Opera-Style Bottom Action Sheet (Menu)
- **Top Quick-Toggle Grid (2×3 or 4-column icon tiles):**
  - **Ad & Tracker Blocker** (shows live blocked request count)
  - **Cookie Dialog Blocker**
  - **Night Mode / Force Dark**
  - **Desktop Site**
  - **Reader Mode**
  - **Translate / Summarize with SAB AI**
- **Vertical Action List:**
  - Add to Speed Dial / Bookmarks
  - Find in Page
  - Downloads
  - History
  - Share
  - Settings

---

## 3. Visual Design Tokens (Opera Mobile Inspired → SAB Branded)

| Token | Value | Usage |
| --- | --- | --- |
| **Canvas (Dark Default)** | `#0B0D12` | App background, Speed Dial backdrop, Tab Gallery |
| **Elevated Surface** | `#141821` | Bottom navigation bar, cards, Speed Dial container |
| **Sheet Surface** | `#1B202C` | Bottom action sheets, Omnibox overlay, AI drawer |
| **Hairline Border** | `rgba(255, 255, 255, 0.08)` | Subtle surface separation without heavy boxes |
| **Primary Accent (SAB Crimson)** | `#FF2E4C` | Central AI trigger, active progress bar, primary CTAs |
| **Secondary Accent (Ice Cyan)** | `#38BDF8` | TLS secure state, Tab Island active markers, links |
| **Private Mode Accent (Violet)** | `#8B5CF6` | Incognito header tint & tab island badge |
| **Text Primary / Muted** | `#F8FAFC` / `#94A3B8` | High-contrast daylight & OLED legibility |

---

## 4. Mapping Opera UI Controls to GeckoView Callbacks

| Opera UI Behavior | Chromium / Blink (Opera) | GeckoView 159+ (SAB) |
| --- | --- | --- |
| Auto-hide bottom bar on scroll | `BrowserControlsStateProvider` | `GeckoSession.ScrollDelegate.onScrollChanged()` |
| Top loading progress line | `TabObserver.onLoadProgressChanged` | `GeckoSession.ProgressDelegate.onProgressChange()` |
| SSL lock & URL updates | `WebContentsObserver.didFinishNavigation` | `GeckoSession.NavigationDelegate.onLocationChange()` & `onSecurityChange()` |
| Tab switcher thumbnail preview | `TabContentManager.cacheTabThumbnail` | `GeckoDisplay.capturePixels()` |
| Long-press text "Ask AI" | `SelectionPopupController` | `GeckoSession.SelectionActionDelegate` |
| Ad/Tracker blocked badge count | `SubresourceFilter` | `GeckoSession.ContentBlocking.Delegate.onContentBlocked()` |
