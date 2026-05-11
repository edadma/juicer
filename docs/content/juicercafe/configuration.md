---
title: Configuration
summary: site.toml keys juicercafe reads, per-page frontmatter for menus / events / albums, and the [juicercafe] palette table.
weight: 10
---

juicercafe reads its configuration from three places:

- A small set of **top-level site keys** (the same ones every juicer theme uses, plus a few café-specific ones for hours / visit info).
- A namespaced **`[juicercafe]` table** for theme-specific palette + sizing overrides.
- **Per-page frontmatter** for menus, events, photo albums, and the "working on it" page.

## Quick example

A minimal `site.toml` that produces a credible café page:

```toml
title    = "Magpie & Pearl"
tagline  = "Coffee, pastry, books — Lawrence Park."
author   = "Magpie & Pearl"
baseURL  = "https://example.com"
theme    = "juicercafe"

[visit]
address = "240 King St E"
city    = "Lawrence Park"
state   = "ON"
zip     = "M4S 1A1"
mapURL  = "https://maps.example.com/magpie"

[[hours]]
day  = "Mon"
open = "07:00"
close = "16:00"

[[hours]]
day  = "Tue"
open = "07:00"
close = "16:00"

# ... (Wed–Fri similar)

[[hours]]
day    = "Sat"
open   = "08:00"
close  = "17:00"

[[hours]]
day    = "Sun"
closed = true

[juicercafe]
brand     = "#9c3022"   # brick red
fontSerif = "'Source Serif 4', Georgia, serif"
```

A typical menu section page (`content/menu/coffee.md`):

```markdown
---
title: Coffee
layout: menu-section
weight: 10
items:
  - { name: "Pour-over", price: "5.00", description: "Single-origin, brewed to order." }
  - { name: "Espresso", price: "3.50", description: "Double shot, in a small cup." }
  - { name: "Cortado", price: "4.50" }
---
```

A typical event page (`content/events/picnic-may.md`):

```markdown
---
title: Spring Picnic
date: 2025-05-15T12:00:00
eventTime: "noon–4pm"
eventLocation: "Lawrence Park bandshell"
summary: Annual neighborhood picnic. Bring a side.
---

The full event writeup goes here.
```

## Top-level keys

Standard juicer keys plus a few café-specific ones.

| Key                | Type        | Default | Notes |
|--------------------|-------------|---------|-------|
| `title`            | `String`    | required | Brand text in topbar + footer; `<title>` element. |
| `tagline`          | `String`    | unset   | Italic subtitle next to the brand in the topbar; appended to `<title>` when the page title equals the site title. |
| `author`           | `String`    | unset   | Footer copyright. Falls back to `title`. |
| `baseURL`          | `String`    | required | Standard juicer key. |
| `theme`            | `String`    | required | Set to `"juicercafe"`. |
| `favicon`          | `String`    | unset   | Path to a favicon. |
| `hideJuicerCredit` | `Boolean`   | `false` | Set `true` to remove the "Built with juicer" line in the footer. |
| `customCSS`        | `[String]`  | unset   | Site stylesheets loaded *after* the theme CSS. See [Theming → Layer 2](/reference/theming/). |
| `visit`            | `Object`    | unset   | `{address, city, state, zip, mapURL}` — the place's physical address, surfaced in the footer on every page. |
| `hours`            | `Array`     | unset   | Day-of-week hours: `[[hours]]` rows of `{day, open?, close?, closed?, note?}`. Drives the topbar "Open now" badge and the footer hours block. |

### Hours-row shape

Each `[[hours]]` row describes one day of the week:

| Field    | Type      | Notes |
|----------|-----------|-------|
| `day`    | `String`  | `Mon`, `Tue`, …, `Sun`. Used both for display and for the "Open now" computation. |
| `open`   | `String`  | `"HH:MM"` 24-hour. Required when not `closed`. |
| `close`  | `String`  | `"HH:MM"` 24-hour. Required when not `closed`. |
| `closed` | `Boolean` | When `true`, the day shows as "closed" and the hours fields are ignored. |
| `note`   | `String`  | Optional small note, e.g. `"holiday hours"`, `"breakfast only"`. |

## Palette + token overrides — `[juicercafe]`

All optional. Anything you don't set keeps the warm-paper / brick-red defaults. The full mapping (which token drives which CSS variable) is documented in [Theming](/reference/theming/) — juicercafe follows the same pattern as the other themes.

| Key                | CSS variable     | Notes |
|--------------------|------------------|-------|
| `brand`            | `--brand`        | Primary brand accent — link hover, "Open now" pill, active topbar entries. |
| `brandStrong`      | `--brand-strong` | Hover-emphasis on brand-coloured elements. |
| `brandSoft`        | `--brand-soft`   | Subtle paper-tinted backgrounds. |
| `accent`           | `--accent`       | Warm secondary — used sparingly on CTAs. |
| `leaf`             | `--leaf`         | "Open now" green; success states. |
| `brandDark` …      | dark counterparts | One per light token. |
| `fontSans`         | `--font-sans`    | Body / UI / nav face. |
| `fontSerif`        | `--font-serif`   | Headings, brand. |
| `fontMono`         | `--font-mono`    | Prices on menu pages. |
| `measure`          | `--measure`      | Prose column max-width. Default `38rem`. |
| `gutter`           | `--gutter`       | Outer padding around content blocks. |
| `radiusLg`         | `--radius-lg`    | Large border-radius (cards, hero images). |

## Per-page frontmatter

### Menu pages

The menu has a two-level structure: a `[layout: menu]` page on
`/menu/_index.md` and one `[layout: menu-section]` page per menu
section (`/menu/coffee.md`, `/menu/pastry.md`, …). Each
menu-section page lists its items inline.

| Key       | Type      | What |
|-----------|-----------|------|
| `layout`  | `String`  | `menu` for the menu landing; `menu-section` for each section. |
| `title`   | `String`  | Section heading on the menu page. |
| `weight`  | `Int`     | Sort order of the section within the full menu. Lower first. |
| `items`   | `[Object]`| Items in this section. Each `{name, price, description?, vegan?, gf?}`. |

### Event pages

Event pages live in the `eventsSection` (default `events`).

| Key             | Type       | What |
|-----------------|------------|------|
| `date`          | ISO string | Required for an event to surface in `.site.events`. |
| `eventTime`     | `String`   | Display time (`"noon–4pm"`, `"7pm–10pm"`). |
| `eventLocation` | `String`   | Address or named location. |
| `recurring`     | `String`   | `"weekly"` for weekly recurrence. Expands the event onto every matching weekday in `.site.calendar`. |
| `recurringDay`  | `String`   | Day name for recurrence. Defaults to the start date's day of week. |
| `photos`        | `[String]` or `[Object]` | Photos for the event — surface on `.site.photos` and on the event page itself. |

### Photo album pages

A `[layout: album]` page renders a 3-up image grid; a
`[layout: photo-album]` page renders the site-wide aggregated
`.site.photos` list. Use the first for one event's gallery, the
second for "every photo on the site, sorted by date."

| Key       | Type      | What |
|-----------|-----------|------|
| `layout`  | `String`  | `album` (one event's photos) or `photo-album` (the site-wide grid). |
| `photos`  | `[Object]`| The album's photos. Each `{src, caption?, alt?}`. |

### "Working on it" pages

A `[layout: working]` page lists pending projects — lease
applications, renovations, hiring. Each page's `working:`
frontmatter is a list.

| Key       | Type      | What |
|-----------|-----------|------|
| `layout`  | `String`  | `working`. |
| `working` | `[Object]`| Projects. Each `{title, status, body}` — `status` is a short label (`"in review"`, `"q2 2025"`, etc.). |

## Going beyond config

Drop a file with the same path under your own site to override anything in the theme — this is the ordinary [theme overlay](/concepts/themes/) pattern:

| You want to change…   | Override at… |
|-----------------------|--------------|
| The topbar            | `<src>/partials/topbar.html` |
| The footer            | `<src>/partials/footer.html` |
| The hours widget      | `<src>/partials/hours-widget.html` |
| The photo grid        | `<src>/partials/photo-grid.html` |
| The 404 page          | `<src>/layouts/_default/404.html` |
| Custom CSS            | `<src>/static/site.css` (and list it under `customCSS` in `site.toml`) |

Site files **always** win over theme files, so overrides are a one-file-at-a-time operation — no forking the theme.
