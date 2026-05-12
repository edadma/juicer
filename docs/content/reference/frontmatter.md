---
title: Frontmatter
summary: Every YAML key juicer (and the bundled themes) read from a page's frontmatter.
weight: 25
---

The YAML block at the top of every content file. Juicer reads a
fixed set of keys; everything else passes through to templates as
`.page.<key>`. This page catalogues the **engine-level** keys (read
by juicer itself) and points at the theme-specific keys (read by
one bundled theme each, documented under that theme's section).

```markdown
---
title: Hello, World
date: 2024-03-12
tags: [intro, meta]
summary: A short description used in list pages and OpenGraph tags.
draft: false
---

The body of the page goes here.
```

## Engine-level keys

These are read by juicer itself. Every bundled theme can rely on
them being present (or normalized to a sensible default) on every
page record in templates.

### Page identity

| Key      | Type     | What |
|----------|----------|------|
| `title`  | `String` | Page title. Falls back to the first heading in the body, then to the filename when both are absent. |
| `summary`| `String` | Explicit page summary. Auto-derived from a `<!--more-->` marker or the first paragraph (capped at 30 words) when absent. |
| `layout` | `String` | Override the default layout. For a single page, `file` is the default; for `_index.md`, `folder`. Setting `layout: home` (etc.) renders the page through `_default/home.html` instead. |

### Publishing & visibility

| Key      | Type                          | What |
|----------|-------------------------------|------|
| `date`   | ISO-8601 string or YAML date  | Publication date. Three input shapes recognized: `2024-03-12T10:30:00Z` (with offset), `2024-03-12T10:30:00` (local, assumed UTC), `2024-03-12` (date only, midnight UTC). |
| `draft`  | `Boolean`                     | When `true`, the page is excluded from the build unless `--drafts` is passed. Drafts are invisible to TOC, sitemap, search index, and section listings. |
| `static` | `Boolean`                     | Mark a page as **non-post** even though it lives in a posts-style section. Excluded from `.site.posts`, `.site.pagesByYear`, and tag/author archive listings. Used for about/colophon/contact pages on a blog. |

```yaml
---
title: Big announcement
date: 2024-12-25T09:00:00Z
draft: false
---
```

```yaml
---
title: About this site
static: true
---
```

[= note =]
**The `date` rule:** only pages with **explicit** `date:`
frontmatter are included in `.site.posts`, `.site.pagesByYear`,
and `dateArchives` output. When `date:` is absent, juicer falls
back to the source file's filesystem mtime for *sort order* —
it's a deliberate split: silently sorting by mtime is convenient;
silently treating every page as a dated post is not.
[= /note =]

### Taxonomy

| Key          | Type                       | What |
|--------------|----------------------------|------|
| `tags`       | `String` or `[String]`     | Tags for the post. A single string is auto-wrapped to a one-element list. Generates `/tags/<slug>/` archive pages (gated on a `tag-page.html` layout existing). |
| `categories` | `String` or `[String]`     | A second taxonomy axis. Same slugification and archive pattern but at `/categories/<slug>/`. Use one, the other, or both. |

```yaml
---
title: Functional programming in Scala 3
tags: [scala, functional, language]
categories: tutorials
---
```

Slugification: lowercase → ASCII-fold (`café` → `cafe`) → runs of
non-alphanumerics → `-`. So `"Functional Programming"` becomes
`functional-programming`.

### Ordering

| Key            | Type     | What |
|----------------|----------|------|
| `weight`       | `Int`    | Explicit sort position within a section. Lower weights sort first. Pages without a weight come after weighted pages. |
| `sortBy`       | `String` | (on section `_index.md`) Override the site-wide `sortBy`. One of `"date"`, `"title"`, `"weight"`. |
| `paginate`     | `Int`    | (on section `_index.md`) Override the site-wide `paginate` slice size. |

### Authors

| Key       | Type           | What |
|-----------|----------------|------|
| `author`  | `String`       | Single author ID — references an `id` in `[[authors]]` in `site.toml`. Singular shorthand for `authors: [<id>]`. |
| `authors` | `[String]`     | Multiple author IDs. When both `author:` and `authors:` are absent, `.page.author` is `null` and `.page.authors` is the empty list. |

```yaml
---
title: A co-authored post
authors: [ed, alice]
---
```

Author IDs that don't match any `[[authors]].id` fall through to a
stub `{id: "<typo>"}` record so templates don't fail; the per-author
archive is empty in that case.

### Series

| Key            | Type     | What |
|----------------|----------|------|
| `series`       | `String` | Series name. Pages with the same `series:` value (case-sensitive) form an ordered group. |
| `seriesOrder`  | `Int`    | Position within the series. Pages without `seriesOrder` sort by `date` ascending after the ordered pages. |

```yaml
---
title: OS Internals, Part 1
series: OS Internals
seriesOrder: 1
---
```

### Aliases (redirects)

| Key       | Type                  | What |
|-----------|-----------------------|------|
| `aliases` | `String` or `[String]`| Old URLs that should redirect to this page. Each generates a static HTML stub at the old URL with a `<meta http-equiv="refresh">` to the canonical URL. |

```yaml
---
title: Setting up a Scala 3 project
aliases:
  - /old-blog-name/setting-up-scala/
  - /2023/setting-up/
---
```

Each alias must include both leading and trailing slashes:
`/old-url/`, not `old-url` or `/old-url`.

### Events

These keys are read on pages in the `eventsSection` (default
`events`) — the section juicer treats as the source of calendar
events.

| Key            | Type     | What |
|----------------|----------|------|
| `recurring`    | `String` | `"weekly"` for a weekly recurring event. The event expands onto every matching weekday in `.site.calendar`. |
| `recurringDay` | `String` | Day name (`"Monday"`, …, `"Sunday"`) for the recurrence. Defaults to the start date's day of week when absent. |

```yaml
---
title: Tuesday Coffee
date: 2024-03-12T09:00:00
recurring: weekly
recurringDay: Tuesday
---
```

### Photos

| Key      | Type                                | What |
|----------|-------------------------------------|------|
| `photos` | `[String]` or `[{src, caption?, alt?}]` | Photos for the page. Aggregated site-wide into `.site.photos` (sorted by page date desc). |

```yaml
---
title: Picnic in the park
date: 2024-06-12
photos:
  - "/img/picnic-01.jpg"
  - { src: "/img/picnic-02.jpg", caption: "The pie table", alt: "A long folding table loaded with summer pies" }
---
```

### OpenGraph / social cards

These override defaults that the `ogTags` template helper otherwise
computes from `.page.title`, `.page.summary`, `.page.image`, etc.

| Key             | Type     | What |
|-----------------|----------|------|
| `description`   | `String` | Fallback for the OpenGraph description when `summary` isn't appropriate. |
| `image`         | `String` | Default page image — used by `og:image` and as a card thumbnail by themes that show one. |
| `ogTitle`       | `String` | Override the page title in OpenGraph and Twitter card tags. |
| `ogDescription` | `String` | Override the description in OpenGraph and Twitter card tags. |
| `ogImage`       | `String` | Override the image in OpenGraph and Twitter card tags. |

```yaml
---
title: A long, descriptive, SEO-targeted post title
summary: A long summary tuned for the page's own list rendering.
ogTitle: A short, punchy title for social cards
ogDescription: A different summary that fits in a card preview.
ogImage: /img/posts/short-title-card.png
---
```

### Computed (not set by you)

These fields appear on `.page.<key>` in templates but are not read
from frontmatter — they're set by juicer during build:

| Key                  | What |
|----------------------|------|
| `permalink`          | Canonical site-relative URL — `/posts/the-post/`. |
| `relPermalink`       | Same as `permalink` (Hugo parity). |
| `url`                | Same as `permalink`. |
| `dateISO`            | `2024-03-12T00:00:00Z` (only when `date:` is set). |
| `dateLong`           | `March 12, 2024` (only when `date:` is set). |
| `dateShort`          | `2024-03-12` (only when `date:` is set). |
| `slug`               | URL stem (last path segment). |
| `wordCount`          | Rendered-body word count. |
| `readingTime`        | `ceil(wordCount / 200)` minutes, floor 1 for non-empty pages. |
| `isSection`          | `true` for `_index.md` pages. |
| `parent`, `ancestors`| Section's `_index` record, then root → parent chain. |
| `next`, `prev`       | Sibling navigation by section page order. |
| `pages`, `subsections`| (`_index.md` only) Children of the section. |
| `series` (block)     | `{name, pages, prev, next, index, total}` — populated when the page declares a `series:`. |

The full `.page` reference, including these, lives in
[Template data → `.page`](/reference/template-data/#page).

## Theme-specific frontmatter

The bundled themes recognize their own frontmatter keys on top of
the engine surface. Those are documented per theme:

| Theme                | What it reads on top |
|----------------------|----------------------|
| [juicerblog](/themes/juicerblog/configuration/#per-page-frontmatter) | `static`, `layout: home`/`archive`, `series` |
| [juicerstudy](/themes/juicerstudy/configuration/#per-page-frontmatter) | `toc`, `summary` (as lead), `minutes` |
| [juicerlanding](/themes/juicerlanding/configuration/#per-page-frontmatter) | `layout: home`, `summary` (as subtitle), `lang` |
| [juicerportfolio](/themes/juicerportfolio/configuration/#per-project-frontmatter) | `layout: project`, `year`, `tagline`, `role`, `client`, `tools`, `category`, `hero`, `heroAlt`, `caption`, `gallery`, `link` |
| [juicerdocs](/themes/juicerdocs/configuration/) | Engine-level keys only — no theme-specific frontmatter. |

## Any other key

Anything you write in frontmatter that juicer doesn't read is
exposed on `.page.<key>` for templates to use. So adding a custom
field is just a matter of writing it:

```yaml
---
title: Cherry pie
yield: 8 servings
prepTime: 45 min
cookTime: 1 h 10 min
---
```

```squiggly
<dl>
  <dt>Yield</dt><dd>{{ .page.yield }}</dd>
  <dt>Prep time</dt><dd>{{ .page.prepTime }}</dd>
  <dt>Cook time</dt><dd>{{ .page.cookTime }}</dd>
</dl>
```
