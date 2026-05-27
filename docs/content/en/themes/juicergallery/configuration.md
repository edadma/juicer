---
title: Configuration
summary: site.toml keys juicergallery reads, per-page gallery frontmatter, and the [juicergallery] palette table.
weight: 10
---

juicergallery reads its configuration from three places:

- A small set of **top-level site keys** (the same ones every juicer theme uses, plus a few gallery-specific ones for the topbar / footer).
- A namespaced **`[juicergallery]` table** for theme-specific palette + sizing overrides.
- **Per-section frontmatter** — galleries are top-level sections whose `_index.md` carries `cover`, `summary`, and `photos:` lists.

## Quick example

```toml
title    = "Hella Studio"
tagline  = "Photography, Reykjavík"
author   = "Hella Carlsen"
baseURL  = "https://hellastudio.example"
theme    = "juicergallery"

[images]
enabled = true
widths  = [480, 960, 1600]
formats = ["webp", "original"]
quality = 78

[[topnav]]
label = "Work"
url   = "/"

[[topnav]]
label = "About"
url   = "/about/"

[contact]
email    = "studio@hellastudio.example"
location = "Reykjavík, Iceland"
```

A minimal gallery (`content/iceland-2024/_index.md`):

```markdown
---
title: Iceland 2024
date: 2024-09-15
cover: /img/iceland/cover.jpg
summary: A two-week loop around the ring road.
photos:
  - src: /img/iceland/skogafoss.jpg
    alt: Skógafoss at golden hour
    caption: Skógafoss, day one
  - /img/iceland/diamond-beach.jpg
---

Optional prose body — context, equipment notes, trip narrative.
```

And a root index that opts into the home cover-grid (`content/_index.md`):

```markdown
---
title: Hella Studio
layout: home
---

Optional intro paragraph shown above the cover grid.
```

That's enough to render the home grid, each gallery detail, and a static prose page or two.

## Top-level keys

Standard juicer keys plus a few gallery-specific ones.

| Key                | Type        | Default | Notes |
|--------------------|-------------|---------|-------|
| `title`            | `String`    | required | Brand text in topbar + footer; `<title>` element. |
| `tagline`          | `String`    | unset   | Italic subtitle next to the brand in the topbar; appended to `<title>` when the page title equals the site title. |
| `description`      | `String`    | unset   | Default `<meta name="description">` for pages that don't set their own `summary`. |
| `author`           | `String`    | unset   | Footer name + copyright line. Falls back to `title`. |
| `baseURL`          | `String`    | required | Standard juicer key. |
| `theme`            | `String`    | required | Set to `"juicergallery"`. |
| `favicon`          | `String`    | unset   | Path to a favicon. |
| `hideJuicerCredit` | `Boolean`   | `false` | Set `true` to remove the "Built with juicer" line in the footer. |
| `topnav`           | `[[topnav]]` | unset  | Array of `{label, url}` entries shown in the topbar. |
| `contact`          | `Table`     | unset   | `{email, location}` — shown in the footer brand block. |
| `links`            | `[[links]]` | unset   | Array of `{label, url}` entries shown in the footer. |
| `[images]`         | `Table`     | unset   | Enables the image-variants pipeline. See the [reference](/reference/image-variants/) for the full surface — required for responsive `<picture>` markup. |

## Per-page frontmatter (gallery sections)

| Key         | Type     | What |
|-------------|----------|------|
| `title`     | `String` | Required — the gallery's display name. |
| `date`      | `String` | Used for sort order on the home cover-grid (newest first). |
| `cover`     | `String` | Path to the cover image. Required for the gallery to appear on the home grid. |
| `coverAlt`  | `String` | Alt text for the cover image. Falls back to the title. |
| `summary`   | `String` | Short description shown on the cover card and as `<meta name="description">`. |
| `photos`    | `List`   | List of photos. Each entry is either a string (path) or a `{src, alt, caption}` map. |

## `[juicergallery]` palette + typography overrides

```toml
[juicergallery]
brand        = "#1f2937"   # ink near-black
brandStrong  = "#0b1220"
accent       = "#b45309"   # amber hover accent
fontSans     = "'Inter', system-ui, sans-serif"
fontSerif    = "'Cormorant Garamond', Georgia, serif"
gutter       = "0.6rem"    # tighter gutter → denser masonry

# Dark-mode overrides (applied under [data-theme="dark"])
brandDark        = "#f3f1ec"
brandStrongDark  = "#ffffff"
accentDark       = "#f59e0b"
```

| Token             | Default                | What |
|-------------------|------------------------|------|
| `brand`           | `#1f2937`              | Primary ink color. |
| `brandStrong`     | `#0b1220`              | Hover / emphasis ink. |
| `brandSoft`       | `#e5e7eb`              | Soft surface tint. |
| `accent`          | `#b45309`              | Hover / focus accent (amber). |
| `fontSans`        | system stack           | Body + UI font. |
| `fontSerif`       | system serif           | Headings + brand. |
| `gutter`          | `1rem`                 | Masonry column gap. Drop to `0.5rem` for a denser feel. |

See [Reference → Theming](/reference/theming/) for the shared theming model — the `[<theme>]` table contract, the `customCSS` chain, file-replacement override pattern.
