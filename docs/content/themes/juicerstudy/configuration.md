---
title: Configuration
summary: Every site-config key juicerstudy reads, every palette / typography / sizing token, and the per-page frontmatter conventions.
weight: 10
---

juicerstudy reads its configuration from three places in `site.toml`
plus per-page frontmatter:

- A small set of **top-level site keys** (the same ones every juicer
  theme uses — `title`, `author`, `baseURL`, etc.).
- A namespaced **`[juicerstudy]` table** for theme-specific
  knobs — palette, typography, sizing, the `math` opt-in.
- **Per-page frontmatter** for opt-ins like the right-rail TOC and
  the page's own summary lead.

Anything you don't set keeps the theme's defaults. juicerstudy uses
`[juicerstudy]` so it doesn't collide with the keys other themes
might want.

## Quick example

A complete `site.toml` exercising every juicerstudy-aware feature:

```toml
title    = "Marginalia"
tagline  = "notes from the margins of books"
author   = "a slow reader"
baseURL  = "https://example.com"

theme    = "juicerstudy"
headingShift = 0

[juicerstudy]
# Optional KaTeX rendering. Off by default — sites without math
# don't pay the script + stylesheet cost.
math = true

# Logo for the topbar wordmark + the <link rel=icon> fallback.
# Top-level `favicon` wins over this for the icon tag.
logo = "/site-logo.png"

# Palette — a quiet scholarly grey with amber accent.
brand           = "#475569"   # slate-600 — primary
brandStrong     = "#334155"   # slate-700 — hover
brandSoft       = "#f1f5f9"   # slate-100 — backgrounds
accent          = "#b45309"   # amber-700 — secondary, link-like
leaf            = "#65a30d"   # lime-600  — success / leaf

brandDark       = "#94a3b8"
brandStrongDark = "#cbd5e1"
brandSoftDark   = "rgb(148 163 184 / .12)"
accentDark      = "#fbbf24"
leafDark        = "#a3e635"
```

## Top-level keys (read by juicerstudy)

| Key             | Type    | Default      | What it does |
|-----------------|---------|--------------|--------------|
| `title`         | string  | `"Untitled"` | Wordmark in the topbar; appears in `<title>` tags |
| `author`        | string  | `"Unnamed"`  | Footer copyright line (only when `showCopyright = true`) |
| `baseURL`       | string  | (required)   | Used for absolute permalinks, sitemap, atom feed |
| `theme`         | string  | (required)   | Set this to `"juicerstudy"` |
| `headingShift`  | int     | `2`          | The juicerstudy layout already provides an outer `<h1>{{ .page.title }}</h1>`, so set this to `0` if your markdown bodies start at `##` (the natural prose convention) |
| `favicon`       | string  | (none)       | Path (relative to your site root) of the `<link rel=icon>`. When unset, `[juicerstudy].logo` is used as fallback. |
| `customCSS`     | array   | `[]`         | Site-supplied stylesheets, loaded after `juicerstudy.css` so they can override anything. See [Theming](/reference/theming/). |
| `showCopyright` | bool    | `false`      | When true, the footer renders a `© {{ .site.author }}` line beside the "Built with Juicer" attribution |
| `hideJuicerCredit` | bool | `false`      | When true, suppresses the "Built with Juicer · Theme: juicerstudy" line in the footer |

## `[juicerstudy]` keys

### Optional features

| Key                       | Type   | Default | What |
|---------------------------|--------|---------|------|
| `juicerstudy.math`        | bool   | `false` | When true, loads KaTeX (CSS + JS + auto-render) from CDN and renders `$…$`, `$$…$$`, `\( … \)`, `\[ … \]` math in the body. Pinned to a recent KaTeX major. Sites that don't need math leave it off so the page stays zero-asset. |
| `juicerstudy.logo`        | string | (none)  | Path of the logo image used in the topbar wordmark and (when no top-level `favicon` is set) as the `<link rel=icon>` fallback |

### Palette — light mode

| Key                          | Type   | Default               | What |
|------------------------------|--------|-----------------------|------|
| `juicerstudy.brand`          | color  | `#1e3a8a` (ink blue)  | Primary brand color — body link color, sidebar active border, code-highlight underlines |
| `juicerstudy.brandStrong`    | color  | `#1e40af`             | Hover state for primary links and buttons |
| `juicerstudy.brandSoft`      | color  | `rgba(30,58,138,.10)` | Soft brand tint — sidebar hover, callout backgrounds, selection highlight |
| `juicerstudy.accent`         | color  | `#92400e`             | Secondary accent — decorative highlights, secondary links |
| `juicerstudy.leaf`           | color  | `#166534`             | "Confirmation green" — copy-button "copied" state, success indicators |

Any CSS color expression works: hex (`#...`), `rgb(...)`, `hsl(...)`, `oklch(...)`, `color-mix(...)`, named colors. The values land in a `<style>` block emitted after `juicerstudy.css`.

### Palette — dark mode overrides

| Key                              | Type   | Default                       |
|----------------------------------|--------|-------------------------------|
| `juicerstudy.brandDark`          | color  | `#93c5fd`                     |
| `juicerstudy.brandStrongDark`    | color  | `#bfdbfe`                     |
| `juicerstudy.brandSoftDark`      | color  | `rgba(147, 197, 253, 0.14)`   |
| `juicerstudy.accentDark`         | color  | `#fdba74`                     |
| `juicerstudy.leafDark`           | color  | `#86efac`                     |

Set just the ones you want to differ — anything you leave out keeps its light-mode value, which is almost certainly wrong in dark mode, so it's worth setting all five.

### Typography

| Key                          | Type   | Default                                          | What |
|------------------------------|--------|--------------------------------------------------|------|
| `juicerstudy.fontSans`       | string | `'Inter', system-sans-stack`                     | Sans-serif face. Chrome (topbar, sidebar, footer) and headings are sans by default. |
| `juicerstudy.fontSerif`      | string | `'Source Serif Pro', 'Iowan Old Style', Charter` | Serif face. The reading column inside `.juicerstudy-content` is serif by default. |
| `juicerstudy.fontMono`       | string | `'JetBrains Mono', ui-monospace, Menlo`          | Monospace face for code blocks + the `kbd` shortcode. |

To swap in a different font, set its `@font-face` declarations in your own `static/site.css`, add the file path to `customCSS`, and set the matching token here.

### Sizing

| Key                          | Type   | Default | What |
|------------------------------|--------|---------|------|
| `juicerstudy.contentMax`     | length | `38rem` | Max width of the reading column. Tuned for ~65ch of serif body. |
| `juicerstudy.sidebarW`       | length | `16rem` | Width of the left sidebar nav. |
| `juicerstudy.railW`          | length | `18rem` | Width of the right "On this page" rail (only emitted when a page sets `toc: true`). |
| `juicerstudy.radiusLg`       | length | `0.5rem` | Border-radius for "large" surfaces — code-block frames, modal, callouts. |

## Per-page frontmatter

Keys juicerstudy reads from each page's frontmatter:

| Key       | Type   | Default | What |
|-----------|--------|---------|------|
| `title`   | string | filename-derived | Rendered as the page's `<h1>` and the `<title>` element |
| `summary` | string | (none)  | Rendered as the `juicerstudy-lead` line under the title (an italic dropcap-ish subtitle) and used as `<meta name=description>` |
| `toc`     | bool   | `false` | When true, the right-side "On this page" rail is emitted for the page (heading IDs are auto-generated from the markdown body). Off by default — most prose doesn't want it, but reference-style notes do. |
| `author`  | string | (none)  | Surfaces in the topbar metadata block (when present) and the atom feed |
| `minutes` | int    | (none)  | Read-time hint, surfaced by the `section-list` shortcode for landing pages that list children |

### Root landing (`_index.md`) extras

Keys juicerstudy reads on the **root** `_index.md` to drive the
landing-page hero:

| Key                | Type   | Default | What |
|--------------------|--------|---------|------|
| `heroTitle`        | string | (none)  | Big hero headline. When set, the title text replaces the plain `<h1>` and you can highlight one word via `heroHighlight`. |
| `heroHighlight`    | string | (none)  | One word from the hero rendered with the brand-gradient `juicerstudy-grad` treatment. |
| `heroSuffix`       | string | `.`     | Appended after the highlight word — typically a period or exclamation point. |
| `heroImage`        | path   | (none)  | Optional hero image, rendered through `imageVariants` for responsive `<picture>` markup. |
| `heroImageAlt`     | string | (none)  | Alt text for the hero image. |
| `heroImageCaption` | string | (none)  | Caption shown under the hero image. |

These are *root-only* — nested section landings render with a simpler
header + subsection-card grid (and ignore the hero keys).

### Site-level landing tokens

The root landing also reads two `[juicerstudy]` keys that control the
hero's CTAs:

| Key                          | Type     | Default | What |
|------------------------------|----------|---------|------|
| `juicerstudy.startReading`   | bool or string | `false` | When `true`, emits a "Start reading" CTA pointing at `/start/`. Set to a path string (e.g. `"/intro/"`) to point elsewhere. |
| `juicerstudy.logo`           | path     | (none)  | Logo image shown in the hero's meta row above the title. |
| `juicerstudy.version`        | string   | (none)  | Optional version chip shown next to the logo. |

A top-level `repoURL` (the standard juicer key) adds a "View on GitHub" CTA button when set.

## Image variants

`[images] enabled = true` in `site.toml` turns on responsive image
delivery for both the `heroImage` and any use of the `figure`
shortcode. The build runs `magick` (ImageMagick 7) once per source,
caches by content hash, and reuses across builds. See [Reference →
Image variants](/reference/image-variants/) for the full configuration
surface.

```toml
[images]
enabled = true
widths  = [480, 960, 1600]
formats = ["webp", "original"]
quality = 78
```

All other juicer engine frontmatter (`date`, `weight`, `aliases`, `permalinks`, etc.) works as documented in [Reference → Frontmatter](/reference/frontmatter/) — juicerstudy doesn't override or hide any of it.

## Shortcodes

juicerstudy ships the standard juicer shortcode set plus one
study-specific one:

- **Callouts** — `note`, `tip`, `warning`, `danger`
- **Layout helpers** — `figure`, `collapse`, `tabs` / `tab`, `steps`,
  `buttons` / `button`
- **Inline** — `kbd`, `badge`, `github`
- **Embeds** — `youtube`
- **Reference** — `filetree`
- **juicerstudy-specific** — `section-list` (renders a section's
  direct children as a titled list with summaries; useful on
  authored section landings that want an explicit catalog beside
  the editorial prose). See the [shortcodes reference](/reference/shortcodes/) for the
  generic shortcode usage and call-site conventions.

[= note =]
The `section-list` shortcode is wired up but currently silently
renders nothing — see the comment in
`docs/themes/juicerstudy/shortcodes/section-list.html` for the
engine-side gap. It will start working when juicer threads page
context into shortcode rendering; the template and CSS are ready.
[= /note =]

## Where the values land

The `<head>` partial emits a `<style>` block immediately after
`juicerstudy.css`, redefining only the variables you've set:

```css
:root {
  --brand:        #475569;   /* from juicerstudy.brand */
  --brand-strong: #334155;   /* from juicerstudy.brandStrong */
  /* ... */
}
[data-theme="dark"] {
  --brand:        #94a3b8;   /* from juicerstudy.brandDark */
  /* ... */
}
```

Anything not set falls through to the defaults declared at the top
of `juicerstudy.css`. The theme always toggles between light and
dark via the `data-theme` attribute on `<html>`, set by the
pre-paint script in `<head>`.

## Going beyond config

Drop a file with the same path under your own site to override
anything in the theme — the ordinary
[theme overlay](/concepts/themes/) pattern:

| You want to change… | Override at… |
|---------------------|--------------|
| The topbar          | `<src>/partials/topbar.html` |
| The footer markup   | `<src>/partials/footer.html` |
| The 404 page        | `<src>/layouts/_default/404.html` |
| The section landing | `<src>/layouts/_default/folder.html` |
| Custom CSS          | `<src>/static/site.css` (and add `"site.css"` to `customCSS` in `site.toml`) |

Site files always win over theme files, so overrides are a
one-file-at-a-time operation — no forking the theme.

## SEO

Every juicer theme ships a shared SEO partial (`partials/seo.html`) that emits the standard meta block: description (with site-level fallback), canonical link, author meta, robots `noindex`, OpenGraph + Twitter cards (via the `ogTags` builtin), Atom feed discovery, and theme-specific JSON-LD. The engine separately writes `sitemap.xml` and `robots.txt`.

### Site-wide keys

```toml
description = "Notes and short courses on linear algebra, written for the curious."
ogImage     = "/og/default.png"

robots   = true
noindex  = false                  # set true for staging/preview domains
disallow = ["/scratch/"]
```

### Per-page frontmatter

```yaml
---
title: Eigenvalues, intuitively
summary: A one-sentence dek for search results and OG cards.
date: 2026-04-12                  # surfaces in Article JSON-LD
author: ed
image: /img/eigenvalues-card.png
ogTitle: A snappier social headline
ogDescription: Tightened for socials
noindex: true   # excluded from sitemap, JSON-LD suppressed, <meta robots> emitted
---
```

### Structured data emitted

| Page | Schema |
|------|--------|
| Root section | `WebSite` |
| Every other page | `Article` (with author / image / dateISO when present) |
| Any page with ancestors | `BreadcrumbList` |

`noindex: true` suppresses ALL JSON-LD on that page.

### Overriding the SEO partial

Drop a file at `<src>/partials/seo.html` (or `seo-jsonld.html` for just the structured-data part). Site overrides win over the theme's copy.
