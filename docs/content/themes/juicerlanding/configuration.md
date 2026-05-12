---
title: Configuration
summary: site.toml keys juicerlanding reads — top-level chrome keys, the [juicerlanding] palette table, and the nine section blocks that build the home page.
weight: 10
---

juicerlanding reads its configuration from three places:

- A small set of **top-level site keys** (the same ones every juicer theme uses, plus a few landing-specific ones for the topbar / footer).
- A namespaced **`[juicerlanding]` table** for theme-specific palette + sizing overrides.
- A stack of **section blocks** (`[hero]`, `[[features]]`, `[[pricing]]`, etc.) that drive the home page. Each block is independently gated; missing block silently omits the section.

The home layout calls nine section partials in a fixed order: **hero → trust → features → code → showcase → pricing → testimonials → faq → final-cta**. You don't need to think about the order; you just include the blocks for the sections you want.

## Quick example

A minimal `site.toml` that produces a credible landing page:

```toml
title    = "My product"
tagline  = "What it does, in eight words or less."
baseURL  = "https://example.com"
theme    = "juicerlanding"

[hero]
headline    = "The headline."
subheadline = "The subheadline that makes the headline land."
primaryCta   = { label = "Start free", url = "/signup/" }
secondaryCta = { label = "View on GitHub", url = "https://github.com/me/it" }

[[features]]
icon  = "⚡"
title = "Fast"
body  = "Less than a kilobyte."

[[features]]
icon  = "🔒"
title = "Private"
body  = "No cookies, no fingerprinting, no consent banners."

[[features]]
icon  = "💰"
title = "Free"
body  = "Self-hosted is free forever, MIT-licensed."

[finalCta]
title = "Ready to ship?"
body  = "Two minutes to install."
primaryCta = { label = "Start free", url = "/signup/" }
```

Adding `[[pricing]]`, `[[testimonials]]`, `[[faqs]]`, `[showcase]`, `[code]`, and `[[trust]]` blocks fills in the rest of the page. The full demo at `docs/demos/juicerlanding/` exercises every section — see [Demo site](../demo/).

## Top-level keys

Standard juicer keys plus a few landing-specific ones. juicerlanding-specific keys are flagged in the right-most column.

| Key                    | Type        | Default | Notes |
|------------------------|-------------|---------|-------|
| `title`                | `String`    | required | Brand text in topbar + footer; used as `<title>` element. |
| `tagline`              | `String`    | unset   | Appears under the brand in the footer; appended to `<title>` when the page title equals the site title. |
| `description`          | `String`    | unset   | Default `<meta name="description">` for pages that don't set their own `summary`. |
| `author`               | `String`    | unset   | Footer copyright line. Falls back to `title`. |
| `baseURL`              | `String`    | required | Standard juicer key. |
| `theme`                | `String`    | required | Set to `"juicerlanding"`. |
| `repoURL`              | `String`    | unset   | When set, juicerlanding shows a GitHub icon link in the topbar. |
| `favicon`              | `String`    | unset   | Path to a favicon; emitted as `<link rel="icon">` in `<head>`. |
| `hideJuicerCredit`     | `Boolean`   | `false` | Set `true` to remove the "Built with juicer" line in the footer. |
| `customCSS`            | `[String]`  | unset   | Site stylesheets loaded *after* the theme CSS. See [Theming → Layer 2](/reference/theming/#layer-2-customcss-drop-your-own-stylesheet-alongside-the-theme). |
| `topbarCta`            | `Object`    | unset   | `{label, url}` — adds a CTA button to the topbar. Surfaced on every page so the conversion path is one click from anywhere. |
| `topnav`               | `Array`     | unset   | Topbar nav links: `[[topnav]]` rows of `{label, url}`. **Note:** keyed `topnav` because top-level `nav` is reserved by juicer for table-of-contents config. |
| `footerColumns`        | `Array`     | unset   | Footer link columns: `[[footerColumns]]` rows of `{title, links: [{label, url}]}`. The brand-blurb column is auto-generated. |
| `trustLabel`           | `String`    | `"Trusted by teams at"` | Header line above the trust-bar logos. |

## Palette + token overrides — `[juicerlanding]`

All optional. Anything you don't set keeps the slate / indigo / amber defaults. The full mapping (which token drives which CSS variable) is documented in [Theming](/reference/theming/) — juicerlanding follows the same pattern as the other themes.

| Key                | CSS variable     | Default               | Notes |
|--------------------|------------------|-----------------------|-------|
| `brand`            | `--brand`        | `#4f46e5` (indigo-600) | Primary brand colour — primary buttons, focus rings, link hover. |
| `brandStrong`      | `--brand-strong` | `#3730a3` (indigo-800) | Heading emphasis on light backgrounds; primary-button hover. |
| `brandSoft`        | `--brand-soft`   | `#eef2ff` (indigo-50)  | Eyebrow chip background, hero gradient, feature-card icon background. |
| `accent`           | `--accent`       | `#f59e0b` (amber-500)  | Reserved for "warm" highlights; not used by Stage-1 sections. |
| `leaf`             | `--leaf`         | `#10b981` (emerald-500) | Checkmark colour in the pricing + code-section bullet lists. |
| `brandDark`        | `--brand` *(dark)* | `#818cf8` (indigo-400) | Lifted indigo for dark-mode backgrounds. |
| `brandStrongDark`  | `--brand-strong` *(dark)* | `#c7d2fe` (indigo-200) | |
| `brandSoftDark`    | `--brand-soft` *(dark)* | `rgba(129,140,248,0.12)` | |
| `accentDark`       | `--accent` *(dark)* | `#fbbf24` (amber-400) | |
| `leafDark`         | `--leaf` *(dark)* | `#34d399` (emerald-400) | |
| `fontSans`         | `--font-sans`    | Inter stack | Body / UI / heading face. |
| `fontMono`         | `--font-mono`    | JetBrains Mono stack | Code blocks. |
| `measure`          | `--measure`      | `65ch` | Prose column max-width on `file.html` / `folder.html`. |
| `gutter`           | `--gutter`       | `1.5rem` | Outer padding around content blocks. |
| `radiusLg`         | `--radius-lg`    | `1rem` | Large border-radius (cards, hero panels, code frame). |
| `logo`             | (none)           | unset | Path to a small image; rendered next to the brand name in the topbar + footer. |

## Section blocks

Each section partial reads its own block. **Missing block = silently omitted**, so an early-stage site can ship with just `[hero]` + `[[features]]` + `[finalCta]`, and add the rest later without changing the layout.

### `[hero]` — the main pitch

```toml
[hero]
eyebrow      = "v1.0 · privacy-first"
headline     = "The headline."
subheadline  = "Subheadline."
codeLang     = "html"
codeSnippet  = "<script src=...></script>"
screenshot   = "/img/screenshots/hero.png"   # alternative to codeSnippet
screenshotAlt = "Dashboard screenshot"

primaryCta   = { label = "Start free", url = "/signup/" }
secondaryCta = { label = "View on GitHub", url = "https://github.com/..." }
```

`codeSnippet` and `screenshot` are mutually exclusive — `codeSnippet` wins if both are set. If neither is set, the hero renders as a single-column copy block.

### `[[trust]]` — the social-proof strip

```toml
trustLabel = "Trusted by teams at"     # optional; default shown

[[trust]]
logo = "/img/logos/acme.svg"
alt  = "Acme Corp"
url  = "https://acme.example/"          # optional — wraps the logo in <a>
```

Logos are rendered greyscale with a hover that lifts the saturation back. Aim for monochrome wordmarks at ~120×40.

### `[featuresHead]` + `[[features]]` — the feature grid

```toml
[featuresHead]
title = "Everything you need, nothing you don't"
body  = "Optional intro paragraph."

[[features]]
icon  = "⚡"            # any emoji, OR a path to an SVG
title = "Title"
body  = "Body copy."
```

3-up at desktop, 2-up at tablet, 1-up at mobile. The icon is rendered inline; emoji-as-icon is the recommended path because it ships zero asset weight.

### `[code]` — code showcase with bullet list

```toml
[code]
title   = "Track what matters"
body    = "Optional intro paragraph above the bullets."
lang    = "javascript"
snippet = "tally('signup', { plan: 'pro' })"
bullets = [
  "First-party only",
  "Properties aggregated server-side",
  "Same API everywhere",
]
```

Two-column layout — code on one side, copy + checklist on the other.

### `[showcase]` — the screenshot section

```toml
[showcase]
title   = "A dashboard you can read at a glance"
body    = "Body copy."
image   = "/img/screenshots/dashboard.svg"
alt     = "Tally dashboard"
caption = "Optional figcaption under the image."
reverse = true     # flip image to the left, copy to the right
```

`reverse = false` (default) puts copy on the left and image on the right. Set `true` when you've already used the same orientation in `[code]` immediately above and want visual variety.

### `[pricingHead]` + `[[pricing]]` — pricing tiers

```toml
[pricingHead]
title = "Simple pricing"
body  = "Optional intro."

[[pricing]]
tier     = "Free"
price    = "$0"
cadence  = "self-hosted"
tagline  = "One-line subtitle."
features = ["Feature one", "Feature two", "Feature three"]
featured = false
cta      = { label = "Get the source", url = "/docs/install/" }
```

The row marked `featured = true` gets the highlighted column treatment (border, glow, scale-up, "Most popular" badge). Order in the file is left-to-right on the page.

### `[testimonialsHead]` + `[[testimonials]]` — quote cards

```toml
[testimonialsHead]
title = "What devs are saying"
body  = "Optional intro."

[[testimonials]]
quote   = "..."
name    = "..."
role    = "..."
company = "..."         # optional
avatar  = "/img/avatars/jane.svg"   # optional
```

3-up at desktop, 1-up at mobile. The avatar is rendered as a 2.5rem circle; SVG initial-badges work well as placeholders before you have real photos.

### `[faqHead]` + `[[faqs]]` — accordion

```toml
[faqHead]
title = "Frequently asked"
body  = "Optional intro."

[[faqs]]
q    = "Question?"
a    = "Answer."
open = true   # optional — render this item open by default
```

Native `<details>`/`<summary>`. No JS. The `<a>` tag inside the answer string works as expected (it's emitted into the page as plain HTML).

### `[finalCta]` — the close

```toml
[finalCta]
title        = "Ready to ship?"
body         = "Two minutes to install."
primaryCta   = { label = "Start free", url = "/signup/" }
secondaryCta = { label = "Read the docs", url = "/docs/" }
```

Indigo-gradient panel with white copy and a primary + ghost button pair. Always render this if you can — it's the last conversion surface before the footer.

## Per-page frontmatter

Most pages on a juicerlanding site are the home page (`layout: home`) and a handful of secondary pages (`layout: file` is the default). The frontmatter knobs:

| Key       | Type     | What |
|-----------|----------|------|
| `layout`  | `String` | `home` for the section-driven landing page, omitted (or `file`) for prose pages, `folder` for section indexes (e.g. `/blog/_index.md`). |
| `title`   | `String` | Heading at the top of the page; also `<title>`. The home `_index.md` should omit this so the page title falls through to `.site.title`. |
| `summary` | `String` | Subtitle under the page title; also `<meta name="description">` for that page. |
| `date`    | `Date`   | Sort key for `folder.html` listings. Optional. |
| `lang`    | `String` | Sets `<html lang>` for the page. Defaults to `en`. |

## SEO

Every juicer theme ships a shared SEO partial (`partials/seo.html`) that emits the standard meta block: description (with site-level fallback), canonical link, author meta, robots `noindex`, OpenGraph + Twitter cards (via the `ogTags` builtin), Atom feed discovery, and theme-specific JSON-LD. The engine separately writes `sitemap.xml` and `robots.txt`.

### Site-wide keys

```toml
description = "The fastest way to ship docs that look like you wrote them yourself."
ogImage     = "/og/hero.png"

robots   = true
noindex  = false                  # set true for staging/preview domains
disallow = ["/private/"]

# Optional: Organization JSON-LD on the homepage. Drives Google's
# Knowledge Panel for the brand.
[organization]
name   = "Juicer Labs"            # defaults to .site.title when omitted
logo   = "/img/logo-square.png"
email  = "hello@juicerlabs.io"
sameAs = ["https://github.com/edadma/juicer", "https://twitter.com/juicerlabs"]
```

### Per-page frontmatter

```yaml
---
title: Pricing
summary: A one-sentence dek for search results and OG cards.
image: /img/og/pricing.png
ogTitle: Snappier headline for socials
ogDescription: Tightened for socials
noindex: true   # excluded from sitemap, JSON-LD suppressed, <meta robots> emitted
---
```

### Structured data emitted

| Page | Schema |
|------|--------|
| Root section | `WebSite` (+ `Organization` when `[organization]` is set in `site.toml`) |
| Any page with ancestors | `BreadcrumbList` |

Landing-page sites usually don't have a "post" surface, so juicerlanding doesn't emit `Article` JSON-LD by default. Add your own via a `<src>/partials/seo-jsonld.html` override if you need it.

`noindex: true` suppresses ALL JSON-LD on that page.

### Overriding the SEO partial

Drop a file at `<src>/partials/seo.html` (or `seo-jsonld.html` for just the structured-data part). Site overrides win over the theme's copy.
