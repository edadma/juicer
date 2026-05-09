---
title: Configuration
summary: site.toml keys juicerblog reads, the per-page frontmatter conventions, and the directories that turn engine features on.
weight: 10
---

juicerblog reads its configuration from three places:

- A small set of **top-level site keys** (the same ones every juicer theme uses).
- A namespaced **`[juicerblog]` table** for theme-specific palette overrides.
- **Per-page frontmatter** for opt-in conventions like the static-page flag and the homepage layout selector.

Anything you don't set keeps the theme's defaults. juicerblog uses `[juicerblog]` so it doesn't collide with keys other themes might want.

## Quick example

A complete `site.toml` exercising every juicerblog-aware feature:

```toml
title    = "My blog"
tagline  = "a place for prose"
author   = "Ed Maxedon"
baseURL  = "https://example.com"

theme    = "juicerblog"
paginate = 5
sortBy   = "date"

# Every post lives at /<year>/<month>/<slug>/.
[permalinks]
posts = ":year/:month/:slug/"

# Year + month archive pages.
dateArchives = true

# Author registry — see [Blogging features → Author registry] for the
# full data shape. Each id is referenced from per-post `author:` /
# `authors:` frontmatter.
[[authors]]
id     = "ed"
name   = "Ed Maxedon"
bio    = "Writes about Scala, languages, and the craft of small tools."
avatar = "/img/avatar-ed.png"

[[authors.links]]
label = "GitHub"
url   = "https://github.com/edadma"

# Optional palette overrides. Defaults are warm-neutral (amber + indigo).
[juicerblog]
brand  = "#0f766e"  # teal-700
accent = "#7c3aed"  # violet-600
```

## Top-level keys

Standard juicer keys, with a note on how juicerblog uses each:

| Key            | Type    | Default      | What |
|----------------|---------|--------------|------|
| `title`        | string  | `"Untitled"` | Wordmark in the topbar; appears in `<title>` tags and the footer |
| `tagline`      | string  | (none)       | Italic line beside the wordmark; rendered as the hero subtitle on the homepage |
| `author`       | string  | `"Unnamed"`  | Footer copyright line; also drives the site-wide Atom feed `<author>` element |
| `baseURL`      | string  | (required)   | Used for absolute permalinks, sitemap, OpenGraph, atom feeds |
| `theme`        | string  | (required)   | Set this to `"juicerblog"` |
| `paginate`     | int     | `10`         | Posts per page on the home + archive listings. juicerblog renders 4–5 well; 10+ starts feeling long |
| `sortBy`       | string  | `"weight"`   | Set to `"date"` for a blog. juicerblog's home + archive listings respect this |

The `[permalinks]`, `dateArchives`, and `[[authors]]` tables are engine-level features documented at [Blogging features](/concepts/blogging/). juicerblog renders the layouts that surface them; the engine's job is unchanged.

## `[juicerblog]` keys

A small table for palette overrides. Defaults are:

| Key                  | Type   | Default     | What |
|----------------------|--------|-------------|------|
| `juicerblog.brand`   | color  | `#c2410c`   | Primary brand color — links on hover, brand pill backgrounds, reading-progress bar |
| `juicerblog.accent`  | color  | `#4338ca`   | Default link color in prose (indigo-700 — readable against cream paper) |
| `juicerblog.leaf`    | color  | `#16a34a`   | "Confirmation green" — the copy-button "Copied" flash, success badges |

Any CSS color expression works: hex, `rgb(...)`, `hsl(...)`, `oklch(...)`, named colors. Values are dropped into a `<style>` block in `<head>` immediately after `juicerblog.css`, redefining the named CSS variables.

[= note =]
Dark-mode variants of these tokens are derived automatically by the theme — the dark palette tunes the same hues lighter and slightly cooler so they don't burn against the dark background. The current build doesn't expose dark-mode overrides as separate config keys; if you need radically different colors per theme, override `static/juicerblog.css` from your site's `static/` directory.
[= /note =]

## Per-page frontmatter

Three frontmatter conventions are juicerblog-specific (the engine doesn't read them; the theme branches on them):

| Field      | Type   | What |
|------------|--------|------|
| `static`   | bool   | When `true`, the page renders without post chrome — no date/reading-time line, no series badge, no prev/next nav. Use it for about / colophon / contact pages so they look like clean essays, not posts. Pages with `static: true` are also filtered out of the home post list, the per-tag archives, the per-author archives, and `.site.posts` / `.site.pagesByYear`. |
| `layout`   | string | When set, juicer renders the page through `_default/<layout>.html` instead of the default `file.html` / `folder.html`. juicerblog ships two non-default layouts: `home` and `archive`. |
| `series`   | string | (Engine feature.) When set, the post is part of a series — juicerblog adds a progress badge ("Reading on screens · Part 2 of 3") above the body and an "In this series" sidebar after the body. See [Blogging features → Series](/concepts/blogging/#series--multi-part-posts) for the full data shape. |

A typical post:

```markdown
---
title: A whirlwind tour of Scala 3
date: 2024-11-18
author: ed
tags: [scala, language]
summary: Five things Scala 3 gets right.
---

Scala 3 cleans up a decade of accreted complexity…
```

A static page:

```markdown
---
title: About this site
author: ed
static: true
summary: Who runs this place and why.
---

This blog exists for one reason…
```

A homepage:

```markdown
---
title: My blog
layout: home
---

A short paragraph that renders inside the hero, under the title and tagline.
```

An archive landing:

```markdown
---
title: Archive
layout: archive
static: true
summary: Every post on this site, grouped by year.
---

A short intro paragraph that renders above the year-grouped list.
```

## Directory layout

The directories juicerblog cares about under your site root:

```
my-blog/
├── site.toml
├── content/
│   ├── _index.md            # the homepage (often layout: home)
│   ├── archive.md           # the archive page (layout: archive, static: true)
│   ├── about.md             # static: true
│   └── *.md                 # posts (with date, author, tags, etc.)
├── grammars/
│   ├── scala.tmLanguage.json
│   ├── javascript.tmLanguage.json
│   └── ...
└── static/                  # site-side asset overrides (avatars, og images)
    ├── img/avatar-ed.png
    └── og-default.png
```

`grammars/` is the opt-in for syntax highlighting — covered in its own section below.

## Syntax highlighting

juicerblog renders code blocks through juicer's [highlighter integration](/concepts/blogging/) — TextMate grammars at build time, no JavaScript runtime, no FOUC. The integration is engine-level (any juicer theme benefits); juicerblog ships the `.hl-*` CSS palette that turns the spans into colors.

To enable highlighting, drop a `<lang>.tmLanguage.json` file into `<src>/grammars/` for each language you want colored:

```
my-blog/grammars/
├── scala.tmLanguage.json
├── javascript.tmLanguage.json
├── python.tmLanguage.json
└── rust.tmLanguage.json
```

The `<lang>` part of the filename matches the fence's language tag — a fenced block opened with ` ```scala ` looks for `grammars/scala.tmLanguage.json`. Languages with no matching grammar render as plain `<pre><code>` (no error).

[= tip =]
**Where to get grammars.** Almost every VS Code language extension ships a TextMate grammar file. Grab them from the language's official VS Code extension repository — for example, [scala-lang/vscode-scala-syntax](https://github.com/scala/vscode-scala-syntax) for Scala. Drop the `.tmLanguage.json` file directly into your `grammars/` directory.
[= /tip =]

[= note =]
**Sites with no `grammars/` directory pay zero cost.** The build doesn't try to load any highlighter at all. Code blocks render as plain `<pre><code>`. The theme's CSS for the `.hl-*` classes is inert in that case (no `.hl-*` spans get emitted), so there's no wasted bytes either.
[= /note =]

The classes the theme styles, with their default light-mode colors:

| Class             | Color (light) | Color (dark)  | What kinds of tokens |
|-------------------|---------------|---------------|----------------------|
| `.hl-keyword`     | `#9a3412`     | `#fb923c`     | `class`, `def`, `if`, `for`, `match`, `import`, etc. |
| `.hl-string`      | `#166534`     | `#86efac`     | String literals (single, double, triple) |
| `.hl-comment`     | `#78716c`     | `#a8a29e`     | Line + block comments (italic) |
| `.hl-number`      | `#b45309`     | `#fdba74`     | Numeric literals |
| `.hl-type`        | `#1d4ed8`     | `#93c5fd`     | Type names (capitalized identifiers in most grammars) |
| `.hl-function`    | `#6d28d9`     | `#c4b5fd`     | Function / method names — when the grammar tags them |
| `.hl-variable`    | inherit       | inherit       | Variable references |
| `.hl-operator`    | `#44403c`     | `#d6d3d1`     | `=>`, `::`, etc. — when the grammar tags them |
| `.hl-punctuation` | `#78716c`     | `#a8a29e`     | Brackets, commas, semicolons, dots |

Override any of these from your site's `static/site.css` (loaded after the theme's stylesheet) if the defaults don't match your site's palette.

## Going beyond config

Drop a file with the same path under your own site to override anything in the theme — this is the ordinary [theme overlay](/concepts/themes/) pattern:

| You want to change… | Override at… |
|---------------------|--------------|
| The topbar nav      | `<src>/partials/topbar.html` |
| The footer markup   | `<src>/partials/footer.html` |
| The byline          | `<src>/partials/post-byline.html` |
| The series sidebar  | `<src>/partials/series-nav.html` |
| The 404 page        | `<src>/layouts/_default/404.html` |
| Custom CSS          | `<src>/static/site.css` (and link it from your overridden `head.html`) |

Site files **always** win over theme files, so overrides are a one-file-at-a-time operation — no forking the theme.
