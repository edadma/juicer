---
title: Configuration
summary: site.toml keys juicerchurch reads, per-page frontmatter for sermons / events / ministries, and the [juicerchurch] palette table.
weight: 10
---

juicerchurch reads its configuration from three places:

- A small set of **top-level site keys** (the same ones every juicer theme uses, plus a few church-specific ones for services / visit info).
- A namespaced **`[juicerchurch]` table** for theme-specific palette + sizing overrides.
- **Per-page frontmatter** for sermons, events, ministries, and photo albums.

## Quick example

A minimal `site.toml` that produces a credible parish site:

```toml
title    = "St. Stephen's Parish"
tagline  = "Anglican community, Riverdale."
author   = "St. Stephen's Parish"
baseURL  = "https://example.com"
theme    = "juicerchurch"

calendarMonths = 6
eventsSection  = "events"

[visit]
address = "120 River St"
city    = "Toronto"
state   = "ON"
zip     = "M4M 2P3"
mapURL  = "https://maps.example.com/ststephens"

[[services]]
day   = "Sun"
time  = "8:00 AM"
label = "Said Eucharist (BCP)"

[[services]]
day   = "Sun"
time  = "10:30 AM"
label = "Sung Eucharist"

[[services]]
day   = "Wed"
time  = "7:00 PM"
label = "Evening Prayer"

[juicerchurch]
brand     = "#2c5282"   # muted blue
fontSerif = "'Source Serif 4', Georgia, serif"
```

A typical sermon page (`content/sermons/2024-12-24-christmas-eve.md`):

```markdown
---
title: "On Comfort, and Why It Comes from a Manger"
date: 2024-12-24
author: ann
scripture: "Isaiah 9:2–7; Luke 2:1–14"
audioURL: "/audio/2024-12-24.mp3"
audioDuration: "23:14"
videoURL: "https://youtube.com/watch?v=..."
videoDuration: "24:02"
summary: A meditation on Christmas Eve, on what comfort means and where it comes from.
---

The transcript or summary body goes here.
```

A typical ministry page (`content/ministries/food-bank.md`):

```markdown
---
title: Riverdale Food Bank
layout: ministry
leader: Mara Sanderson
meets: "Saturdays, 10am–noon"
contact: "foodbank@ststephens.example"
---

The ministry's narrative — who, what, why, how to get involved.
```

## Top-level keys

| Key                | Type        | Default | Notes |
|--------------------|-------------|---------|-------|
| `title`            | `String`    | required | Brand text in topbar + footer; `<title>` element. |
| `tagline`          | `String`    | unset   | Italic subtitle next to the brand. |
| `author`           | `String`    | unset   | Footer copyright. Falls back to `title`. |
| `baseURL`          | `String`    | required | Standard juicer key. |
| `theme`            | `String`    | required | Set to `"juicerchurch"`. |
| `favicon`          | `String`    | unset   | Path to a favicon. |
| `hideJuicerCredit` | `Boolean`   | `false` | Set `true` to remove the "Built with juicer" line. |
| `customCSS`        | `[String]`  | unset   | Site stylesheets loaded *after* the theme CSS. |
| `eventsSection`    | `String`    | `"events"` | Section name juicer treats as events; populates `.site.events` and `.site.calendar`. See [Configuration → Calendar / events features](/reference/config/#calendar--events-features). |
| `calendarMonths`   | `Int`       | `12`    | Months of calendar grid `.site.calendar` pre-computes. |
| `visit`            | `Object`    | unset   | `{address, city, state, zip, mapURL}` — surfaced in the footer on every page. |
| `services`         | `Array`     | unset   | Regular weekly services: `[[services]]` rows of `{day, time, label}`. Surfaced in the home page hero and the footer. |

## Palette + token overrides — `[juicerchurch]`

All optional. Anything you don't set keeps the muted-blue defaults.

| Key                | CSS variable     | Notes |
|--------------------|------------------|-------|
| `brand`            | `--brand`        | Primary brand accent. |
| `brandStrong`      | `--brand-strong` | Hover-emphasis. |
| `brandSoft`        | `--brand-soft`   | Subtle paper-tinted backgrounds. |
| `accent`           | `--accent`       | Warm secondary. |
| `leaf`             | `--leaf`         | "Today" indicator on the calendar grid. |
| `brandDark` …      | dark counterparts | One per light token. |
| `fontSans`         | `--font-sans`    | Body / UI / nav face. |
| `fontSerif`        | `--font-serif`   | Headings, sermon titles. |
| `fontMono`         | `--font-mono`    | Scripture references, calendar cells. |
| `measure`          | `--measure`      | Prose column max-width. Default `38rem`. |
| `gutter`           | `--gutter`       | Outer padding around content blocks. |
| `radiusLg`         | `--radius-lg`    | Large border-radius. |

## Per-page frontmatter

### Sermon pages

Sermons are dated, single-author posts that opt into the sermon
layout. They live under `content/sermons/` and surface on the
sermon archive page.

| Key             | Type     | What |
|-----------------|----------|------|
| `layout`        | `String` | `sermon`. The sermon-archive page uses `layout: sermon-archive`. |
| `title`         | `String` | Sermon title. |
| `date`          | ISO      | Required. Drives sort order and archive grouping. |
| `author`        | `String` | Single preacher ID — references `[[authors]]` in `site.toml`. |
| `scripture`     | `String` | Scripture reference text (`"Isaiah 9:2–7; Luke 2:1–14"`). Rendered in a tagged block above the body. |
| `audioURL`      | `String` | Path or absolute URL to an audio file (mp3/m4a). Theme injects an `<audio>` element. |
| `audioDuration` | `String` | Display duration (`"23:14"`). Shown next to the audio control. |
| `videoURL`      | `String` | Path or absolute URL to a video (YouTube / Vimeo / mp4). |
| `videoDuration` | `String` | Display duration. |
| `series`        | `String` | (Engine feature.) Sermons share series support with `juicerblog` — Advent series, Lent series, etc. |
| `summary`       | `String` | Short blurb on the archive page. |

### Event pages

Event pages live in the `eventsSection` (default `events`).

| Key             | Type       | What |
|-----------------|------------|------|
| `date`          | ISO string | Required for an event to surface in `.site.events`. |
| `eventTime`     | `String`   | Display time (`"7 PM"`, `"6:30–8 PM"`). |
| `eventLocation` | `String`   | Address or named location. |
| `recurring`     | `String`   | `"weekly"` for weekly recurrence. Expands onto every matching weekday in `.site.calendar`. |
| `recurringDay`  | `String`   | Day name for recurrence. Defaults to the start date's day of week. |
| `photos`        | `[String]` or `[Object]` | Photos. |

### Ministry pages

| Key       | Type      | What |
|-----------|-----------|------|
| `layout`  | `String`  | `ministry`. |
| `title`   | `String`  | Ministry name. |
| `leader`  | `String`  | Person's name (free text, not a registry ID — ministries often have non-clergy leaders). |
| `meets`   | `String`  | Display string for meeting cadence (`"Saturdays, 10am–noon"`). |
| `contact` | `String`  | Email / phone / freeform contact info. |

### Photo albums

Same as juicercafe — `layout: album` for one event's gallery,
`layout: photo-album` for the site-wide aggregate.

## Going beyond config

Drop a file with the same path under your own site to override anything in the theme — this is the ordinary [theme overlay](/concepts/themes/) pattern:

| You want to change…   | Override at… |
|-----------------------|--------------|
| The topbar            | `<src>/partials/topbar.html` |
| The footer            | `<src>/partials/footer.html` |
| The calendar grid     | `<src>/partials/month-grid.html` |
| The photo grid        | `<src>/partials/photo-grid.html` |
| The 404 page          | `<src>/layouts/_default/404.html` |
| Custom CSS            | `<src>/static/site.css` (and list it under `customCSS` in `site.toml`) |

Site files **always** win over theme files, so overrides are a one-file-at-a-time operation — no forking the theme.

## SEO

Every juicer theme ships a shared SEO partial (`partials/seo.html`) that emits the standard meta block: description (with site-level fallback), canonical link, author meta, robots `noindex`, OpenGraph + Twitter cards (via the `ogTags` builtin), Atom feed discovery, and theme-specific JSON-LD. The engine separately writes `sitemap.xml` and `robots.txt`.

### Site-wide keys

```toml
description = "Trinity Community Church — Sunday services at 9 and 11, Wednesday Bible study at 7."
ogImage     = "/og/sanctuary.jpg"

robots   = true
noindex  = false
disallow = ["/admin/"]

# Optional: Church JSON-LD on the homepage. Subtype of PlaceOfWorship —
# helpful for Google Maps Knowledge Panel.
[church]
phone   = "+1-555-0100"
address = "412 Maple Street, Springfield IL 62701"
image   = "/img/sanctuary.jpg"
```

### Per-page frontmatter

```yaml
---
title: The Sermon on the Mount
summary: A one-sentence dek for search results and OG cards.
date: 2026-04-12
author: pastor-rob
image: /img/sermons/sermon-on-mount.jpg
ogTitle: A snappier social headline
ogDescription: Tightened for socials
noindex: true   # excluded from sitemap, JSON-LD suppressed, <meta robots> emitted
---
```

### Structured data emitted

| Page | Schema |
|------|--------|
| Root section | `WebSite` (+ `Church` when `[church]` is set in `site.toml`) |
| Dated non-section page | `Article` (with author / image / dateISO) |
| Any page with ancestors | `BreadcrumbList` |

`noindex: true` suppresses ALL JSON-LD on that page.

### Overriding the SEO partial

Drop a file at `<src>/partials/seo.html` (or `seo-jsonld.html` for just the structured-data part). Site overrides win over the theme's copy.
