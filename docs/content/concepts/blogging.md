---
title: Blogging features
summary: Tags, categories, pagination, dates, reading time — the cluster of features that turn a juicer site into a blog.
weight: 40
---

Juicer's docs-site features (sections, `_index.md`, partials, themes) work fine for a blog as-is, but a real blog usually wants four extra things: a way to tag posts, archive pages that group posts by tag, paginated list pages so the front page doesn't drag, and parsed publication dates so list pages can sort by recency. This page covers all of those, plus reading-time estimates and the bundled `juicerblog` theme that exercises every feature.

[= note =]
Every feature on this page is **opt-in**. A docs site that doesn't set `tags` frontmatter, doesn't configure `paginate`, and doesn't supply `date` frontmatter will render exactly the same after these features as before — same files, same URLs, byte-identical output.
[= /note =]

## Tags and categories

Add a `tags` field to a post's frontmatter to mark it. Either a list or a single string is accepted:

```markdown
---
title: Whirlwind tour of Scala 3 enums
date: 2024-03-12
tags: [scala, language]
---
```

```markdown
---
title: A short note
tags: meta
---
```

Two archive pages are emitted for each tag the site uses:

| URL pattern             | What it lists |
|-------------------------|---------------|
| `/tags/`                | Every tag the site uses, with post counts |
| `/tags/<slug>/`         | Every post tagged with that one tag |

Tag names are slugified for URLs — lowercased, ASCII-folded (`café` → `cafe`), and any run of non-alphanumeric characters is collapsed into a single `-`. So a tag named `"Functional Programming"` becomes `/tags/functional-programming/`.

`categories` is parsed identically and treated as a **separate axis**. You can use both or neither — sites that want a single way to group posts pick one and ignore the other:

```markdown
---
title: Setting up the studio
date: 2024-03-12
categories: [behind-the-scenes]
tags: [meta, design]
---
```

That post would be reachable at `/categories/behind-the-scenes/` and at both `/tags/meta/` and `/tags/design/`.

### Templates

Tag and category archives need their own layouts. The `juicerblog` theme ships a default pair; if you're rolling your own theme, drop these two layouts under `layouts/_default/`:

| Layout file       | What it renders |
|-------------------|-----------------|
| `tag-list.html`   | The `/tags/` index — full directory of every tag |
| `tag-page.html`   | A single tag's archive, e.g. `/tags/scala/` |

The same names with `category-list.html` / `category-page.html` cover the categories axis.

The data your tag layouts see:

```
{{ .terms }}                 // List of every term — for tag-list.html
{{ .terms[0].name }}         // "scala"
{{ .terms[0].slug }}         // "scala"
{{ .terms[0].url }}          // "/tags/scala/"
{{ .terms[0].count }}        // 7

{{ .term }}                  // The current term — for tag-page.html
{{ .term.name }}             // "scala"
{{ .term.pages }}            // List of pages tagged with this term
{{ .term.pages[0].title }}   // "Whirlwind tour of Scala 3 enums"
```

[= tip =]
If a layout is missing, the corresponding archive is silently skipped. So a docs site that ships no `tag-list.html` and no `tag-page.html` won't get tag URLs even if you accidentally write `tags: [foo]` somewhere.
[= /tip =]

### Site-wide template access

Templates that aren't tag archives — say, your `topbar.html` — can still iterate over every tag the site uses:

```
{{ .site.tags }}            // List of every tag, sorted by count desc
{{ .site.categories }}      // Same shape, for categories
```

Each entry has `name`, `slug`, `url`, `count`, `pages` — same shape as `.terms`.

## Pagination

A blog with thirty posts shouldn't render every post on the front page. Set `paginate` in `site.toml` to chunk section index pages into multiple pages:

```toml
paginate = 10
sortBy = "date"
```

That tells juicer:

[= steps =]
## Slice section pages

For each section index page (every `_index.md`), take the section's child pages and split them into chunks of 10.

## Render slice 1 to `index.html`

The first chunk lands at the section's natural URL — `/posts/index.html`, `/index.html` for the site root, etc.

## Render slices 2..N to `page/N/index.html`

The second chunk lands at `/posts/page/2/index.html`, the third at `/posts/page/3/`, and so on. Static-host friendly: every URL is a real directory with a real file.
[= /steps =]

If a section has fewer pages than `paginate`, only the first slice is emitted and `total` reads `1` — your template doesn't need to special-case that.

### `sortBy`

| Value     | Order                                                        |
|-----------|--------------------------------------------------------------|
| `"date"`  | `.page.date` descending (newest first); the blog default     |
| `"title"` | `.page.title` ascending (alphabetical)                       |
| `"weight"`| `weight` frontmatter ascending — same as juicer's default    |

A page with `weight` frontmatter overrides whichever sort the section uses, so you can pin a "Welcome" post above date-sorted listings.

### Per-section overrides

Set `paginate` or `sortBy` on a section's `_index.md` frontmatter to override site-wide:

```markdown
---
title: Notes
paginate: 20
sortBy: title
---

A handful of short, alphabetised notes.
```

### Templates

The data layouts see — both `_index` layouts and tag archives:

```
{{ .section.paginator.current }}     // 1-based index of THIS slice
{{ .section.paginator.total }}       // total slice count
{{ .section.paginator.pages }}       // pages on THIS slice (already sliced)
{{ .section.paginator.first }}       // URL of slice 1
{{ .section.paginator.last }}        // URL of last slice
{{ .section.paginator.prevURL }}     // empty string on slice 1
{{ .section.paginator.nextURL }}     // empty string on the last slice
```

A typical pagination footer:

```html
{{ if .section.paginator.prevURL }}
  <a href="{{ .section.paginator.prevURL }}">← Newer</a>
{{ end }}
<span>Page {{ .section.paginator.current }} of {{ .section.paginator.total }}</span>
{{ if .section.paginator.nextURL }}
  <a href="{{ .section.paginator.nextURL }}">Older →</a>
{{ end }}
```

## Dates

Frontmatter `date` is parsed into a real timestamp, not a passthrough string. Three input shapes are recognized:

| Shape                          | Treated as                         |
|--------------------------------|------------------------------------|
| `2024-03-12T10:30:00Z`         | Full ISO-8601 with offset          |
| `2024-03-12T10:30:00`          | Local datetime — assumed UTC       |
| `2024-03-12`                   | Plain date — midnight UTC          |

If `date` is absent, juicer falls back to the source markdown file's filesystem mtime. That means a freshly-written post sorts correctly without you having to set `date` explicitly.

### Rendering

Three pre-formatted helpers ride alongside the parsed value, so templates don't have to call format functions:

| Field             | Example                       | Use for           |
|-------------------|-------------------------------|-------------------|
| `.page.date`      | `OffsetDateTime`              | Sorting, math     |
| `.page.dateISO`   | `2024-03-12T00:00:00Z`        | `<time datetime=...>` attributes |
| `.page.dateLong`  | `March 12, 2024`              | Body copy         |
| `.page.dateShort` | `2024-03-12`                  | Compact list pages|

A standard post-meta line:

```html
<time datetime="{{ .page.dateISO }}">{{ .page.dateLong }}</time>
```

[= note =]
The long-form English month names (`January`, `February`, …) are hand-baked into juicer rather than driven by a locale-aware `DateTimeFormatter`. The reason is purely practical: juicer's Native build ships a minimal locale database and `MMMM` falls back to `M01`/`M02` there. Hand-rolled month names render identically across the JVM, JS, and Native targets. If you need a non-English long format right now, write your own helper using `.page.dateShort` plus your own month table.
[= /note =]

## Reading time and word count

Two more fields automatically computed for every page:

| Field                | Type | What |
|----------------------|------|------|
| `.page.wordCount`    | int  | Word count of the rendered HTML body (after shortcodes, before stripping) |
| `.page.readingTime`  | int  | Minutes — `ceil(wordCount / 200)`, with a floor of 1 for non-empty pages |

The 200-words-per-minute figure is the Medium-popularized average. If you want a different cadence, render `wordCount` directly and divide it yourself in the template:

```html
{{ .page.readingTime }} min read
```

```html
about {{ .page.wordCount }} words
```

Empty pages get `wordCount = 0` and `readingTime = 0` so a stub `_index.md` doesn't say "1 min read" misleadingly.

## Permalinks

By default, the URL juicer emits for a page is one-to-one with its location
in the `content/` tree: `content/posts/hello.md` becomes `/posts/hello/`.
That's the right behaviour for docs sites where the file layout *is* the
information architecture. For a blog, you usually want something different
— most blogs route every post through `/<year>/<month>/<slug>/`,
regardless of whether the post lives in `content/posts/` or
`content/posts/scala/` or `content/drafts/`.

Configure that with the `[permalinks]` table in `site.toml`:

```toml
[permalinks]
posts = ":year/:month/:slug/"
notes = ":slug/"
```

Each key is the **section name** (the first path segment after `htmlDir`
is stripped). Each value is a URL template with substitution tokens.
A post under `content/posts/2024-03-12-hello.md` with
`date: 2024-03-12` in its frontmatter renders to:

| Default URL                   | With `posts = ":year/:month/:slug/"` |
|-------------------------------|--------------------------------------|
| `/posts/2024-03-12-hello/`    | `/2024/03/2024-03-12-hello/`         |

(Tip: when you're using date-prefixed permalinks, drop the date prefix
from the filename and let `stripPrefix` do its job — keep the URL clean.)

### The available tokens

| Token       | Resolves to |
|-------------|-------------|
| `:slug`     | The cleaned filename (numeric prefixes stripped, non-alnum runs collapsed) |
| `:title`    | `slugify(.page.title)` — frontmatter `title` lowercased and ASCII-folded |
| `:year`     | 4-digit year from `.page.date` |
| `:month`    | 2-digit month from `.page.date` |
| `:day`      | 2-digit day from `.page.date` |
| `:section`  | The section name itself — useful in nested patterns like `:year/:section/:slug/` |

`:title` and `:slug` are usually different — the slug comes from the
filename (so `01-getting-started.md` becomes `getting-started`), the
title comes from the `title:` frontmatter (so `"Getting Started"` becomes
`getting-started` too, but `"Café au Lait"` becomes `cafe-au-lait`).
Use `:title` when filenames are opaque or numbered and you want
human-readable URLs anyway:

```toml
[permalinks]
posts = ":year/:title/"
```

### Section index pages stay put

`_index.md` files are **never** routed through a permalink template. Even
when `posts = ":year/:slug/"` is set, the `posts/_index.md` page still
lives at `/posts/`, not at `/2024/posts/`. The reason is structural: the
`_index.md` describes a section, not a piece of content, and a section's
URL has to be predictable so links in your nav and breadcrumbs keep
working.

### Sections you don't list keep their default URL

`[permalinks]` is a **map of overrides**, not an all-or-nothing switch.
Sections that aren't keys in the table render at their default
physical-path URL. So a site with both blog posts (under permalinked
`/posts/`) and prose pages (`about.md`, `contact.md` at the root) just
lists `posts` in `[permalinks]` and leaves the rest alone:

```toml
[permalinks]
posts = ":year/:month/:slug/"
# `about.md` is still at /about/, `contact.md` still at /contact/.
```

[= tip =]
If a permalink template uses `:year` / `:month` / `:day` for a post that
has no `date:` frontmatter, juicer falls back to the source markdown
file's filesystem mtime. That means a freshly written post gets the
current date even when you forgot to set frontmatter — convenient for
drafts, but worth being aware of when you `git mv` files and find the URL
shifted.
[= /tip =]

### What happens on disk

Permalink templates change both the URL and the on-disk write location:
juicer doesn't keep two copies of the page. So with
`posts = ":year/:month/:slug/"`, a post writes only to
`<dst>/2024/03/hello/index.html`; the legacy `<dst>/posts/hello/`
directory is never created. (If you set `htmlDir = "html"` in
`site.toml`, the path is `<dst>/html/2024/03/hello/index.html`, since
`htmlDir` is the filesystem prefix that gets stripped from URLs.)

## Date archives

Most blogs eventually want a "browse posts by date" navigation: an archive
page for each year, optionally with a sub-archive for each month. Turn it
on with one line in `site.toml`:

```toml
dateArchives = true
```

Juicer then emits two kinds of pages, gated independently by the
presence of their layouts:

| Layout                        | URL pattern             | What it lists |
|-------------------------------|-------------------------|---------------|
| `_default/date-year.html`     | `/<year>/index.html`    | Every dated post in that year, plus a per-month roll-up |
| `_default/date-month.html`    | `/<year>/<month>/index.html` | Every dated post in that year + month |

Both layouts are optional. Ship the year layout to get year-only
archives; ship both for full month granularity; ship neither and
`dateArchives = true` does nothing visible.

[= note =]
Only pages with **explicit** `date:` frontmatter make it into the
archives. Pages whose `.page.date` came from the filesystem mtime
fallback are excluded — otherwise a docs site that turns the feature
on would suddenly find every reference page in the current year's
archive. The rule is "if you set a date, you're in; if you didn't,
you're not."
[= /note =]

### Year-archive data

The `date-year.html` layout sees:

```
{{ .year }}                // 2024 (BigDecimal)
{{ .pages }}               // every dated post in 2024, newest first
{{ .pages[0].title }}      // most recent post's title
{{ .months }}              // per-month roll-up, ascending by month number
{{ .months[0].month }}     // 1 (BigDecimal)
{{ .months[0].monthName }} // "January"
{{ .months[0].url }}       // "/2024/01/"
{{ .months[0].count }}     // 4 (BigDecimal)
{{ .months[0].pages }}     // pages in that month, newest first
```

A typical year-archive layout:

```html
<h1>{{ .year }}</h1>
<ol>
  {{ for m <- .months }}
  <li>
    <a href="{{ m.url }}">{{ m.monthName }}</a>
    <span>({{ m.count }} posts)</span>
  </li>
  {{ end }}
</ol>
```

### Month-archive data

The `date-month.html` layout sees:

```
{{ .year }}        // 2024
{{ .month }}       // 3 (BigDecimal)
{{ .monthName }}   // "March"
{{ .pages }}       // posts in March 2024, newest first
```

```html
<h1>{{ .monthName }} {{ .year }}</h1>
<ol>
  {{ for p <- .pages }}
  <li>
    <time datetime="{{ p.dateISO }}">{{ p.dateShort }}</time>
    <a href="{{ p.url }}">{{ p.title }}</a>
  </li>
  {{ end }}
</ol>
```

### Combining with permalinks

Date archives compose cleanly with permalink templates: the URL space
remains hierarchical and unique. With:

```toml
dateArchives = true

[permalinks]
posts = ":year/:month/:slug/"
```

a single post with `date: 2024-03-15` lives at `/2024/03/hello/`, and
archive pages cover that same prefix:

| URL                      | What |
|--------------------------|------|
| `/2024/03/hello/`        | The single post |
| `/2024/03/`              | March 2024 archive |
| `/2024/`                 | 2024 year archive |

There's no collision because permalink output paths and archive paths
land in different places — a permalinked post writes to
`/<year>/<month>/<slug>/index.html`, while the year archive writes to
`/<year>/index.html` and the month archive to
`/<year>/<month>/index.html`. The directory structure interleaves
cleanly.

## Series / multi-part posts

Some posts read better as a series — three parts on debugging, four on
setting up a project, twelve on writing your own OS. Juicer joins
related posts into a navigable series via two frontmatter fields:

```markdown
---
title: OS Internals, Part 1 — The Boot Process
series: OS Internals
seriesOrder: 1
---
```

```markdown
---
title: OS Internals, Part 2 — The Memory Subsystem
series: OS Internals
seriesOrder: 2
---
```

Pages with the same `series` value (case-sensitive, exact match) are
linked. Each one sees a `.page.series` block:

| Field                    | What |
|--------------------------|------|
| `.page.series.name`      | The series name as it appears in frontmatter |
| `.page.series.pages`     | List of every page in the series, ordered |
| `.page.series.prev`      | Previous page's record, or `null` on the first |
| `.page.series.next`      | Next page's record, or `null` on the last |
| `.page.series.index`     | 1-based position of the current page |
| `.page.series.total`     | Number of pages in the series |

A typical "in this series" sidebar:

```html
{{ if .page.series }}
<aside aria-label="In this series">
  <h2>{{ .page.series.name }}</h2>
  <p>Part {{ .page.series.index }} of {{ .page.series.total }}.</p>
  <ol>
    {{ for s <- .page.series.pages }}
    <li><a href="{{ s.url }}">{{ s.title }}</a></li>
    {{ end }}
  </ol>
</aside>
{{ end }}
```

The `juicerblog` theme ships exactly this widget as the
`partials/series-nav.html` partial; `_default/file.html` calls it after
the post body. Override it from your site's `partials/` if you want
custom copy.

### Ordering rules

Within a series, pages sort by:

[= steps =]
## `seriesOrder` ascending

Pages with explicit `seriesOrder` come first in the order they declare.
A page with `seriesOrder: 2` sorts before one with `seriesOrder: 5`.

## Date ascending (oldest first)

Pages WITHOUT a `seriesOrder` sort by `.page.date` ascending. Series
usually read chronologically — older posts before newer ones — and the
ascending direction matches that.

## Filename, as a stable tiebreaker

Two unordered same-day posts fall back to filename order, so the result
is deterministic regardless of filesystem walk order.
[= /steps =]

[= note =]
A page can be in **at most one** series — `series:` is a string, not a
list. If you need a multi-membership relationship, that's exactly what
`tags` is for. Series is for narratively-linked posts that share a
single arc.
[= /note =]

## Author registry

A single-author blog can lean on `.site.author` from `site.toml` and
call it a day. A multi-author site needs more: per-author bio, avatar,
external links, and an archive page that lists each author's posts.

Declare authors as an array of tables:

```toml
[[authors]]
id     = "ed"
name   = "Edward A Maxedon"
email  = "ed@example.com"
bio    = "Writes a lot of code."
avatar = "/img/ed.jpg"

[[authors.links]]
label = "GitHub"
url   = "https://github.com/edadma"

[[authors.links]]
label = "Mastodon"
url   = "https://hachyderm.io/@edadma"

[[authors]]
id     = "alice"
name   = "Alice Smith"
bio    = "Does a lot of design."
avatar = "/img/alice.jpg"
```

Then point a post at one (or more) of the registered ids:

```markdown
---
title: A solo post
author: ed
---
```

```markdown
---
title: A co-authored post
authors: [ed, alice]
---
```

`author:` is the singular shorthand; `authors:` is the multi-author
form. Either works. Templates see both fields, normalised:

| Field             | Type        | What |
|-------------------|-------------|------|
| `.page.author`    | `Map?`      | First (or only) resolved author record, or `null` |
| `.page.authors`   | `List[Map]` | All resolved author records — empty list when none |

Each record carries every key from the registry — `id`, `name`, `bio`,
`avatar`, `links[]` — so a per-post byline can render the avatar, name,
and external links directly:

```html
{{ if .page.author }}
<div class="byline">
  {{ if .page.author.avatar }}
  <img src="{{ .page.author.avatar }}" alt="" />
  {{ end }}
  <div>
    <p class="name">{{ .page.author.name }}</p>
    {{ if .page.author.bio }}<p class="bio">{{ .page.author.bio }}</p>{{ end }}
  </div>
</div>
{{ end }}
```

[= note =]
A frontmatter `author:` value that doesn't match any registry id falls
back to a stub record `{id: "<typo>"}`. Templates that read
`.page.author.name` get an empty string rather than a hard failure —
the build doesn't break on a typo, but the author archive is empty.
Audit `.site.authors` if a name disappears unexpectedly.
[= /note =]

### Author archives

Two archive pages per author registry are emitted, each gated by an
optional layout:

| Layout                              | URL pattern              | What it lists |
|-------------------------------------|--------------------------|---------------|
| `_default/author-list.html`         | `/authors/index.html`    | Every author with at least one referencing post |
| `_default/author-page.html`         | `/authors/<id>/index.html` | One author's posts, newest first |

The `author-list.html` layout sees:

```
{{ .authors }}                  // List of records, in registry order
{{ .authors[0].name }}
{{ .authors[0].url }}           // "/authors/ed/"
{{ .authors[0].count }}         // 12
{{ .authors[0].pages }}         // their posts (already date-desc)
```

The `author-page.html` layout sees:

```
{{ .author }}                   // single record
{{ .author.name }}
{{ .author.bio }}
{{ .author.count }}
{{ .author.pages }}             // posts, newest first
```

Authors with **zero** referencing posts are omitted from both archives —
keeps a partially-populated `[[authors]]` registry from emitting empty
"see all posts by Bob" pages.

### `.site.authors`

Like `.site.tags` and `.site.categories`, the same author records are
exposed site-wide for use in any template:

```html
{{ for a <- .site.authors }}
<a href="{{ a.url }}">{{ a.name }} ({{ a.count }})</a>
{{ end }}
```

Useful for a "contributors" list in a footer or about page.

## Aliases / redirects

When you change a post's URL — by editing its filename, adding a
`[permalinks]` template, or renaming a section — every link to the old
URL breaks. Aliases stop the bleeding without server-side rewrites.

Add the old URL(s) to the new page's frontmatter:

```markdown
---
title: Setting up a Scala 3 project
date: 2024-03-12
aliases:
  - /old-blog-name/setting-up-scala/
  - /2023/setting-up/
---
```

Juicer emits a small static HTML page at each listed URL. The page sets
a `<meta http-equiv="refresh">` to the canonical URL, plus a visible
fallback link in case the meta refresh is blocked:

```html
<!doctype html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta http-equiv="refresh" content="0; url=/posts/setting-up-scala-3/">
  <link rel="canonical" href="https://example.com/posts/setting-up-scala-3/">
  <title>Redirecting…</title>
</head>
<body>
  <p>This page has moved. Redirecting to <a href="/posts/setting-up-scala-3/">/posts/setting-up-scala-3/</a>.</p>
</body>
</html>
```

That's the **built-in default**. It works without any theme. If you
want different markup — branded styling, custom copy, JavaScript
fallback — drop a layout at `layouts/_default/alias.html`:

```html
<!doctype html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta http-equiv="refresh" content="0; url={{ .target }}">
  <link rel="canonical" href="{{ .absTarget }}">
  <title>{{ .page.title }} — moved</title>
</head>
<body>
  <h1>This page has moved</h1>
  <p><a href="{{ .target }}">Continue to {{ .page.title }}</a></p>
</body>
</html>
```

The custom layout has access to:

| Field          | What |
|----------------|------|
| `.target`      | Site-relative canonical URL |
| `.absTarget`   | Absolute canonical URL (`baseURL` + `.target`) |
| `.page.<...>`  | Every field of the canonical page's record |
| `.site.<...>`  | The full site context |

Aliases accept a single string or a list:

```markdown
aliases: /old-url/
```

```markdown
aliases:
  - /old-url/
  - /even-older-url/
```

[= note =]
Aliases are not the same as URL rewriting. Each alias is a real static
HTML file in the output tree — `<dst>/old-url/index.html`,
`<dst>/even-older-url/index.html`, etc. That's the point: it works on
any static host (GitHub Pages, Cloudflare Pages, S3, Netlify…) without
edge-rule configuration. Cost is one tiny HTML file per alias.
[= /note =]

## OpenGraph / Twitter cards

When a post is shared on social media, the platform renders a "card"
preview built from a small block of `<meta>` tags in the page's
`<head>`. Juicer ships a `{{ ogTags .page }}` template builtin that
emits the canonical block in one line:

```html
<head>
  ...
  {{ ogTags .page }}
</head>
```

That call expands to:

```html
<meta property="og:type" content="article" />
<meta property="og:title" content="The post title" />
<meta property="og:url" content="https://example.com/posts/the-post/" />
<meta property="og:description" content="The post summary." />
<meta property="og:image" content="https://example.com/img/hero.jpg" />
<meta property="og:site_name" content="My blog" />
<meta name="twitter:card" content="summary_large_image" />
<meta name="twitter:title" content="The post title" />
<meta name="twitter:description" content="The post summary." />
<meta name="twitter:image" content="https://example.com/img/hero.jpg" />
```

Tags whose source field is empty are omitted (you don't get an empty
`<meta property="og:image" content="" />` cluttering the output).

### Resolution rules

For each value the builtin needs, it tries fields in priority order:

| Tag                            | Resolution chain |
|--------------------------------|------------------|
| `og:title`, `twitter:title`    | `.page.ogTitle` → `.page.title` |
| `og:description`, `twitter:description` | `.page.ogDescription` → `.page.description` → `.page.summary` |
| `og:image`, `twitter:image`    | `.page.ogImage` → `.page.image` → `.site.ogImage` → `.site.image` |
| `og:url`                       | `.page.permalink` (always present — the absolute URL juicer computed) |
| `og:site_name`                 | `.site.title` |
| `twitter:card`                 | `summary_large_image` if an image was resolved, otherwise `summary` |

Image URLs are promoted to absolute via the configured `baseURL` — a
site-relative `/img/hero.jpg` becomes
`https://example.com/img/hero.jpg`. Crawlers typically reject relative
URLs in `og:image`, so this matters.

### Per-page overrides

To set a different card title or summary than the page's own title and
summary, add `ogTitle` / `ogDescription` / `ogImage` to the post's
frontmatter:

```markdown
---
title: A long, descriptive, SEO-targeted post title
summary: A long summary that's good for the page's own list-page meta…
ogTitle: A short, punchy title for social cards
ogDescription: A different summary that fits in a card preview.
ogImage: /img/posts/short-title-card.png
---
```

[= note =]
The `juicerblog` theme calls `{{ ogTags .page }}` from its
`partials/head.html`, so every page gets the meta block automatically
without you doing anything in the post itself. Sites that want to
control the call site (or skip it for some pages) can override the
partial.
[= /note =]

## The `juicerblog` theme

Juicer ships a default blog theme under `themes/juicerblog/` that exercises every feature on this page — plus server-side syntax highlighting, author bylines, series progress badges, a reading-progress hairline, code-block copy buttons, and a homepage / archive layout pair. The full theme reference lives in its own section: see [juicerblog](/juicerblog/) for the overview and [juicerblog · Configuration](/juicerblog/configuration/) for the full config knobs, frontmatter conventions, syntax-highlighting setup, and override patterns.

The fastest way to see every blog feature working together is the bundled demo:

```bash
sbt 'juicerJVM/run serve -s examples/blog-site -L'
```

That spins up a live preview of `examples/blog-site/` — 9 dated posts spanning Jul–Dec 2024, three authors with a multi-author co-byline, a 3-post series, dateArchives + permalinks + aliases, syntax highlighting, the works. Touch any markdown file in the source and the page reloads in under a second.

## Where each feature lives

When a feature breaks, here's where to start looking:

| Feature        | Engine                                                                 | Theme                                  |
|----------------|------------------------------------------------------------------------|----------------------------------------|
| Tags / categories | `App.scala` `collectTaxonomy`, `renderTaxonomyArchives`             | `tag-list.html`, `tag-page.html`       |
| Pagination     | `paginate.scala`, render-loop slicing in `App.scala`                  | `partials/pagination.html`             |
| Dates          | `App.scala` `parseDateString`, `formatDateLong`                       | `partials/post-meta.html`              |
| Reading time   | `App.scala` `wordsOf`, `readingTimeOf`                                | Anywhere `.page.readingTime` is used   |
| Slug helper    | `package.scala` `slugify`, `asciiFold`                                | (used internally for tag URLs)         |
