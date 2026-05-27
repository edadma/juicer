---
title: Configuration
summary: site.toml keys juicerportfolio reads, per-page project frontmatter, and the [juicerportfolio] palette table.
weight: 10
---

juicerportfolio reads its configuration from three places:

- A small set of **top-level site keys** (the same ones every juicer theme uses, plus a few portfolio-specific ones for the topbar / footer / filter chips).
- A namespaced **`[juicerportfolio]` table** for theme-specific palette + sizing overrides.
- **Per-project frontmatter** — projects opt into the project layout with `layout: project`, then set hero / meta / gallery fields the layout knows about.

## Quick example

A minimal `site.toml` that produces a credible portfolio:

```toml
title    = "Studio Name"
tagline  = "Industrial design, Lisbon"
author   = "Studio Name"
baseURL  = "https://example.com"
theme    = "juicerportfolio"

[[topnav]]
label = "Work"
url   = "/"

[[topnav]]
label = "About"
url   = "/about/"

[contact]
email    = "studio@example.com"
location = "Lisbon, Portugal"

[[links]]
label = "Instagram"
url   = "https://instagram.com/example"
```

A minimal project page (`content/work/my-project.md`):

```markdown
---
title: Tobias Lounge
layout: project
year: "2024"
tagline: Bent-ash lounge chair
summary: A short subtitle about the piece.
role: Design + production drawings
client: Private commission
tools:
  - European ash
  - 12 oz canvas
  - Brass
hero: /img/work/tobias-lounge.jpg
heroAlt: Tobias lounge chair, three-quarter view
---

The body of the writeup goes here, in regular markdown.
```

That's enough to render the home grid, the project page with its
meta sidebar, and the project body. Add `category`, `gallery`, `link`
to unlock the rest.

## Top-level keys

Standard juicer keys plus a few portfolio-specific ones.

| Key                | Type        | Default | Notes |
|--------------------|-------------|---------|-------|
| `title`            | `String`    | required | Brand text in topbar + footer; `<title>` element. |
| `tagline`          | `String`    | unset   | Italic subtitle next to the brand in the topbar; appended to `<title>` when the page title equals the site title. |
| `description`      | `String`    | unset   | Default `<meta name="description">` for pages that don't set their own `summary`. |
| `author`           | `String`    | unset   | Footer name + copyright line. Falls back to `title`. |
| `baseURL`          | `String`    | required | Standard juicer key. |
| `theme`            | `String`    | required | Set to `"juicerportfolio"`. |
| `favicon`          | `String`    | unset   | Path to a favicon. |
| `hideJuicerCredit` | `Boolean`   | `false` | Set `true` to remove the "Built with juicer" line in the footer. |
| `customCSS`        | `[String]`  | unset   | Site stylesheets loaded *after* the theme CSS. See [Theming → Layer 2](/reference/theming/). |
| `topnav`           | `Array`     | unset   | Topbar nav links: `[[topnav]]` rows of `{label, url}`. **Note:** keyed `topnav` because top-level `nav` is reserved by juicer for table-of-contents config. |
| `workCategories`   | `Array`     | unset   | Filter chips above the work grid: `[[workCategories]]` rows of `{label, value}`. Projects with `category = "<value>"` match the chip. |
| `contact`          | `Object`    | unset   | `{email, location}` — surfaced in the footer brand block. |
| `links`            | `Array`     | unset   | Footer external-link list: `[[links]]` rows of `{label, url}`. |

## Palette + token overrides — `[juicerportfolio]`

All optional. Anything you don't set keeps the warm-paper / rust defaults. The full mapping (which token drives which CSS variable) is documented in [Theming](/reference/theming/) — juicerportfolio follows the same pattern as the other themes.

| Key                | CSS variable     | Default                | Notes |
|--------------------|------------------|------------------------|-------|
| `brand`            | `--brand`        | `#c4633b` (warm rust)  | The single accent — link hover, active filter chip, project meta link, blockquote rule. |
| `brandStrong`      | `--brand-strong` | `#8a3f22`              | Hover-emphasis on brand-coloured elements. |
| `brandSoft`        | `--brand-soft`   | `#f4e6dc`              | Subtle paper-tinted backgrounds. |
| `accent`           | `--accent`       | `#2b2620` (near-black) | Reserved; not used by Stage-1/2 sections. |
| `brandDark`        | `--brand` *(dark)* | `#d6845f`            | Lifted rust for dark backgrounds. |
| `brandStrongDark`  | `--brand-strong` *(dark)* | `#f0a982`     | |
| `brandSoftDark`    | `--brand-soft` *(dark)* | `rgba(214,132,95,0.16)` | |
| `accentDark`       | `--accent` *(dark)* | `#ede7d9`           | |
| `fontSans`         | `--font-sans`    | system-ui stack | Body / UI / nav face. |
| `fontSerif`        | `--font-serif`  | Iowan Old Style stack | Project titles, brand, page titles. |
| `fontMono`         | `--font-mono`    | system mono stack | Materials chips, code in writeups. |
| `measure`          | `--measure`      | `62ch` | Prose column max-width on `file.html` / `folder.html`. |
| `gutter`           | `--gutter`       | `1.5rem` | Outer padding around content blocks. |
| `radiusLg`         | `--radius-lg`    | `0.375rem` | Large border-radius (cards, hero images). |
| `logo`             | (none)           | unset | Path to a small image; rendered next to the brand name in the topbar. |

## Per-project frontmatter

Projects opt into the project layout by setting `layout: project`. The home grid filters by exactly that flag, so anything else (about, contact, CV) is hidden from the project listing automatically.

| Key            | Type     | What |
|----------------|----------|------|
| `layout`       | `String` | `project` to opt in. Required for the home grid + project layout. |
| `title`        | `String` | The project name. Required. |
| `date`         | `Date`   | Drives `sortBy = "date"` ordering across the grid + the prev/next reading order. |
| `year`         | `String` | Display-only year (e.g. `"2024"` or `"2023–2024"`). Appears on the home card byline and in the meta sidebar. Quote it; TOML reads bare `2024` as an integer. |
| `tagline`      | `String` | One-line subtitle on the home card under the title. |
| `summary`      | `String` | Italic subtitle directly under the title on the project page; also `<meta name="description">`. |
| `category`     | `String` | Matches a `value` in one of the `[[workCategories]]` rows. Drives the home-page filter chips. |
| `role`         | `String` | What you did on the project. Renders as a meta-sidebar row. Optional. |
| `client`       | `String` | Who the project was for. Omit for self-initiated work. |
| `tools`        | `[String]` | Materials / techniques / software list. Renders as small chips in the meta sidebar under "Materials". |
| `link`         | `Object` | `{label, url}` — external link in the meta sidebar (live site, shop, github, etc.). |
| `hero`         | `String` | URL of the hero image at the top of the project page. Also used as the home-card thumbnail. |
| `heroAlt`      | `String` | Alt text for the hero image. Falls back to the title. |
| `caption`      | `String` | Italic caption under the hero image. |
| `gallery`      | `[Object]` | List of `{src, alt, caption}` images rendered as a 2-up figure grid below the body. Section silently omits if absent. |

## Per-page frontmatter (non-project)

Most other pages on a portfolio site use the default `file.html`
layout (about, contact, CV, press) or `folder.html` for section
indexes (`/work/_index.md`, `/press/_index.md`).

| Key       | Type     | What |
|-----------|----------|------|
| `title`   | `String` | Page heading + `<title>`. |
| `summary` | `String` | Italic subtitle under the page title; also `<meta name="description">`. |
| `date`    | `Date`   | Sort key for `folder.html` listings. Optional on prose pages. |
| `lang`    | `String` | Sets `<html lang>` for the page. Defaults to `en`. |

## Prev / next reading order

Project pages get `← Previous` / `Next →` nav at the bottom, sourced from the engine's `.page.prev` / `.page.next` fields. The reading order is a depth-first traversal anchored at the root section, so the project series only links across siblings if `/work/` itself has an `_index.md` to anchor it. Without that anchor, every project's `.page.prev` and `.page.next` are null and the nav block silently omits.

The demo's `content/work/_index.md` is intentionally minimal — it doubles as a text-list archive of all projects via `folder.html`. If you don't want a `/work/` archive page rendered, set `static: true` in the `_index.md` frontmatter (excluded from `.site.posts`) — it still anchors the section for the prev/next math.

## SEO

Every juicer theme ships a shared SEO partial (`partials/seo.html`) that emits the standard meta block: description (with site-level fallback), canonical link, author meta, robots `noindex`, OpenGraph + Twitter cards (via the `ogTags` builtin), Atom feed discovery, and theme-specific JSON-LD. The engine separately writes `sitemap.xml` and `robots.txt`.

### Site-wide keys

```toml
description = "Independent designer in Halifax, working in print + the screen."
ogImage     = "/og/portrait.jpg"

robots   = true
noindex  = false
disallow = ["/scratch/"]

# Optional: Person JSON-LD on the homepage. Drives Google's Knowledge
# Panel for an individual.
[person]
name     = "Ada Hopper"           # defaults to .site.title when omitted
jobTitle = "Graphic designer & illustrator"
image    = "/img/portrait.jpg"
email    = "hello@adahopper.com"
sameAs   = ["https://github.com/adahopper", "https://dribbble.com/adahopper"]
```

### Per-page frontmatter

```yaml
---
title: Field guide cover series
summary: A one-sentence dek for search results and OG cards.
image: /img/projects/field-guide.jpg   # used by ogTags AND CreativeWork JSON-LD
ogTitle: A snappier social headline
ogDescription: Tightened for socials
noindex: true   # excluded from sitemap, JSON-LD suppressed, <meta robots> emitted
---
```

### Structured data emitted

| Page | Schema |
|------|--------|
| Root section | `WebSite` (+ `Person` when `[person]` is set in `site.toml`) |
| Project page with `image` | `CreativeWork` (name, url, image, description, dateCreated) |
| Any page with ancestors | `BreadcrumbList` |

`noindex: true` suppresses ALL JSON-LD on that page.

### Overriding the SEO partial

Drop a file at `<src>/partials/seo.html` (or `seo-jsonld.html` for just the structured-data part). Site overrides win over the theme's copy.
