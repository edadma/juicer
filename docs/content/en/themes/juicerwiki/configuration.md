---
title: Configuration
summary: site.toml keys juicerwiki reads, per-note frontmatter, and the [juicerwiki] palette table.
weight: 10
---

juicerwiki reads its configuration from three places:

- A small set of **top-level site keys** (the same ones every juicer theme uses, plus `topnav` and `links`).
- A namespaced **`[juicerwiki]` table** for palette + typography overrides.
- **Per-note frontmatter** — flat content tree, one note per file.

## Quick example

```toml
title    = "Field Notes"
tagline  = "A reading wiki on computing"
author   = "ed"
baseURL  = "https://fieldnotes.example"
theme    = "juicerwiki"

[[topnav]]
label = "Tags"
url   = "/tags/"

[[topnav]]
label = "About"
url   = "/about/"
```

A minimal note (`content/zettelkasten.md`):

```markdown
---
title: Zettelkasten
date: 2024-02-10
tags: [method, history]
summary: A note-taking method built around small atomic notes that link to each other.
---

Body in markdown. Link other notes liberally — `[Atomic Notes](/atomic-notes/)`
shows up in the linked note's backlinks panel automatically.
```

And a root index that opts into the home layout (`content/_index.md`):

```markdown
---
title: Field Notes
layout: home
---

Optional intro paragraph shown above the tag cloud.
```

## Top-level keys

| Key                | Type        | Default | Notes |
|--------------------|-------------|---------|-------|
| `title`            | `String`    | required | Brand text in topbar + footer; `<title>` element. |
| `tagline`          | `String`    | unset   | Italic subtitle next to the brand in the topbar. |
| `description`      | `String`    | unset   | Default `<meta name="description">`. |
| `author`           | `String`    | unset   | Footer copyright line. Falls back to `title`. |
| `baseURL`          | `String`    | required | Standard juicer key. |
| `theme`            | `String`    | required | Set to `"juicerwiki"`. |
| `favicon`          | `String`    | unset   | Path to a favicon. |
| `hideJuicerCredit` | `Boolean`   | `false` | Set `true` to remove the "Built with juicer" line. |
| `topnav`           | `[[topnav]]` | unset  | Array of `{label, url}` entries shown in the topbar. |
| `links`            | `[[links]]` | unset   | Array of `{label, url}` entries shown in the footer. |

## Per-note frontmatter

| Key       | Type     | What |
|-----------|----------|------|
| `title`   | `String` | Required — the note's display name. |
| `date`    | `String` | Controls home-page recency ordering. Also drives the Article JSON-LD `datePublished`. |
| `tags`    | `List`   | Single string or list. Each tag generates a `/tags/<slug>/` archive page. |
| `summary` | `String` | Shown on the home list, in `<meta name="description">`, and in *other* notes' backlinks panels. |

## `[juicerwiki]` palette + typography overrides

```toml
[juicerwiki]
brand        = "#3b2c1e"   # walnut ink
brandStrong  = "#1f150c"
accent       = "#7a4a1c"   # umber accent
fontSans     = "'Inter', system-ui, sans-serif"
fontSerif    = "'Crimson Pro', Georgia, serif"

# Dark-mode overrides
brandDark    = "#f1e7d6"
accentDark   = "#d99a52"
```

See [Reference → Theming](/reference/theming/) for the shared theming model.

## Client-side search

juicer auto-generates `/search.json` for every site — see [Reference → Search](/reference/search/) for the field shape. juicerwiki ships a `search` partial that fetches it lazily on first modal open and runs an in-memory substring match. No backend, no API, no third-party service.
