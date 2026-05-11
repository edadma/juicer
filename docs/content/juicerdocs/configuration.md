---
title: Configuration
summary: Every site-config key the juicerdocs theme reads, with defaults.
weight: 10
---

juicerdocs reads its configuration from two places in `site.toml`:

- A small set of **top-level site keys** (the same ones juicer itself uses for things like the page title and the `showCopyright` flag).
- A namespaced **`[juicerdocs]` table** for theme-specific knobs — logo, brand colors, dark-mode overrides.

Anything you don't set keeps the theme's defaults. Themes can read from any TOML namespace; juicerdocs uses `[juicerdocs]` so it doesn't collide with the keys other themes might want.

## Quick example

```toml
title           = "My Project"
author          = "Acme Inc."
baseURL         = "https://docs.acme.example/"

theme           = "juicerdocs"
headingShift    = 0
showCopyright   = true

[juicerdocs]
logo            = "/acme-logo.png"
brand           = "#3b82f6"        # blue-500
brandStrong     = "#2563eb"        # blue-600
brandSoft       = "#dbeafe"        # blue-100
accent          = "#0891b2"        # cyan-600
leaf            = "#10b981"        # emerald-500

# Dark-mode overrides (optional; sensible defaults derived from the lights)
brandDark       = "#60a5fa"
brandStrongDark = "#93c5fd"
brandSoftDark   = "rgb(59 130 246 / .12)"
accentDark      = "#22d3ee"
leafDark        = "#34d399"
```

## Top-level keys (read by juicerdocs)

| Key             | Type    | Default      | What it does |
|-----------------|---------|--------------|--------------|
| `title`         | string  | `"Untitled"` | Wordmark in the topbar; appears in `<title>` tags and the footer |
| `author`        | string  | `"Unnamed"`  | Footer copyright (only when `showCopyright = true`) |
| `baseURL`       | string  | (required)   | Used for absolute permalinks, sitemap, OpenGraph |
| `theme`         | string  | (required)   | Set this to `"juicerdocs"` to activate the theme |
| `headingShift`  | int     | `2`          | The juicerdocs layout already provides an outer `<h1>{{ .page.title }}</h1>`, so set this to `0` for `## Foo` markdown to render as `<h2>` (the natural docs-author convention) |
| `showCopyright` | bool    | `false`      | When true, the footer renders a `© {{ .site.author }}` line beside the "Built with Juicer" attribution |

## `[juicerdocs]` keys

### Logo

| Key                | Type   | Default            | What |
|--------------------|--------|--------------------|------|
| `juicerdocs.logo`  | string | `"/juicer-logo.png"` | Path (relative to your site root) of the logo image. Drop your file into `<src>/static/` and point this at it. Used in the topbar, the hero on the home page, the 404 page, and as the favicon. |

The default value points at the bundled `juicer-logo.png` shipped with the theme — fine if you're previewing juicerdocs on its own, but you'll want your own logo for any real deployment.

### Hero call-to-action

| Key                       | Type | Default | What |
|---------------------------|------|---------|------|
| `juicerdocs.getStarted`   | bool | `false` | When true, the homepage hero shows a primary "Get started" button linking to `/getting-started/`. Leave unset for sites that don't have a getting-started section. |

The hero also shows a secondary "Star on GitHub" button when the top-level `repoURL` is set (see [Theming reference → Site-wide chrome keys](/reference/theming/#site-wide-chrome-keys)). When **both** are off — `getStarted` unset and no `repoURL` — the action row collapses entirely and the hero ends at its summary paragraph (no reserved empty space).

### Brand colors (light mode)

| Key                          | Type   | Default     | What |
|------------------------------|--------|-------------|------|
| `juicerdocs.brand`           | color  | `#d61472`   | Primary brand color — links, button fills, sidebar active borders, hero gradient start |
| `juicerdocs.brandStrong`     | color  | `#a30d57`   | Hover state for primary buttons + emphasized links |
| `juicerdocs.brandSoft`       | color  | `#fce7f3`   | Soft brand tint — sidebar hover, brand-soft callout backgrounds, badges |
| `juicerdocs.accent`          | color  | `#6f1f9e`   | Secondary accent — hero gradient end, decorative highlights |
| `juicerdocs.leaf`            | color  | `#65a30d`   | "Confirmation green" — copy-button "copied" state, success indicators |

Any CSS color expression works: hex (`#...`), `rgb(...)`, `hsl(...)`, `oklch(...)`, `color-mix(...)`, named colors. The values are dropped into `<style>` declarations, so anything CSS would accept here works.

### Brand colors (dark mode overrides)

| Key                              | Type   | Default                       |
|----------------------------------|--------|-------------------------------|
| `juicerdocs.brandDark`           | color  | `#ec4899`                     |
| `juicerdocs.brandStrongDark`     | color  | `#f472b6`                     |
| `juicerdocs.brandSoftDark`       | color  | `rgb(236 72 153 / .12)`       |
| `juicerdocs.accentDark`          | color  | `#c084fc`                     |
| `juicerdocs.leafDark`            | color  | `#84cc16`                     |

These override the light-mode values when the user toggles dark mode (or the page loads with the `prefers-color-scheme: dark` system preference). Set just the ones you want to differ — anything you leave out keeps its light-mode value, which is *almost certainly wrong* in dark mode for the brand pinks/blues, so it's worth setting all five.

[= tip =]
**Picking a palette.** A handy starting point: pick a brand hue, then derive `brandStrong` (≈10% darker), `brandSoft` (very light tint at ~10% lightness), `brandDark` (≈10% lighter — dark mode wants more luminance), `brandStrongDark` (slightly lighter still), `brandSoftDark` (transparent overlay at ~12% alpha). Tools like [oklch.com](https://oklch.com/) and [coolors.co](https://coolors.co/) make this fast.
[= /tip =]

## Where the values land

The `<head>` partial emits a `<style>` block immediately after `juicerdocs.css`, redefining only the variables you've set:

```css
:root {
  --jd-brand:        #3b82f6;   /* from juicerdocs.brand */
  --jd-brand-strong: #2563eb;   /* from juicerdocs.brandStrong */
  /* ... */
}
[data-theme="dark"] {
  --jd-brand:        #60a5fa;   /* from juicerdocs.brandDark */
  /* ... */
}
```

Anything not set falls through to the defaults declared at the top of `juicerdocs.css`. The theme always toggles between light and dark via the `data-theme` attribute on `<html>`, set by the pre-paint script in `<head>`.

## Going beyond config

Drop a file with the same path under your own site to override anything in the theme — this is the ordinary [theme overlay](/concepts/themes/) pattern:

| You want to change… | Override at… |
|---------------------|--------------|
| The topbar          | `<src>/partials/topbar.html` |
| The footer markup   | `<src>/partials/footer.html` |
| The "Note" callout  | `<src>/shortcodes/note.html` |
| The 404 page        | `<src>/layouts/_default/404.html` |
| Custom CSS          | `<src>/static/site.css` (and link it from your overridden `head.html`) |

Site files **always** win over theme files, so overrides are a one-file-at-a-time operation — no forking the theme.
