---
title: Template data
summary: The full data context exposed to templates — site, page, section, content, toc.
weight: 30
---

Every template renders against a single map. The top-level keys are documented below; nested fields follow.

## `.site`

The merged site config (`site.toml` overlaid on the baseline) plus a few computed extras:

| Key              | Type                | What |
|------------------|---------------------|------|
| `.site.<config>` | varies              | Every key from `site.toml` |
| `.site.toc`      | `List[TOCItem]`     | Site-wide auto-nav (only if no `nav` is set) |
| `.site.start`    | `String?`           | URL of the conventional landing page |
| `.site.pages`    | `List[Map]`         | Every page's enriched record |
| `.site.pagesByPath` | `Map[String, Map]`  | Same records, keyed by `relPermalink` |
| `.site.root`     | `Map?`              | The root section's `_index` record (or `null`) |
| `.site.tags`     | `List[Term]`        | Every distinct tag used in the site (see below) |
| `.site.categories` | `List[Term]`      | Same shape as `.site.tags`, for the categories axis |
| `.site.authors`  | `List[Map]`         | Every author with at least one referencing post — registry record + `id`, `url`, `count`, `pages` |

### `Term` shape

Every entry in `.site.tags` and `.site.categories` (and on tag-archive
templates, the elements of `.terms`) has these fields:

| Key       | Type        | What |
|-----------|-------------|------|
| `name`    | `String`    | The original tag name as it appeared in frontmatter |
| `slug`    | `String`    | URL-safe form (lowercased, ASCII-folded, non-alnum runs collapsed to `-`) |
| `url`     | `String`    | Site-relative archive URL — `/tags/<slug>/` or `/categories/<slug>/` |
| `count`   | `Int`       | How many pages reference this term |
| `pages`   | `List[Map]` | The pages themselves, each in the same shape as a `.site.pages` entry |

## Alias pages

Frontmatter `aliases: [...]` makes juicer emit a redirect page at each
listed URL. The alias layout (or built-in fallback) sees a separate
data context:

| Key            | Type     | What |
|----------------|----------|------|
| `.target`      | `String` | Site-relative canonical URL |
| `.absTarget`   | `String` | Absolute canonical URL (`baseURL` + `.target`) |
| `.page.<...>`  | varies   | Every field of the canonical page's record |
| `.site.<...>`  | varies   | The full site context |

If `layouts/_default/alias.html` exists, it's rendered with the data
above. Otherwise juicer writes a minimal built-in HTML page with a
`<meta http-equiv="refresh">` to `.target`.

## `.page`

The current page's enriched record:

| Key                    | Type      | What |
|------------------------|-----------|------|
| `.page.title`          | `String`  | Frontmatter title |
| `.page.summary`        | `String`  | Resolved summary (frontmatter / `<!--more-->` / fallback) |
| `.page.url`            | `String`  | Site-relative URL |
| `.page.relPermalink`   | `String`  | Same as `url`, named for Hugo parity |
| `.page.permalink`      | `String`  | Absolute URL (baseURL + url) |
| `.page.isSection`      | `Boolean` | `true` for `_index.md` pages |
| `.page.parent`         | `Map?`    | Enclosing section's `_index` record |
| `.page.ancestors`      | `List[Map]` | Root → parent chain (excluding self) |
| `.page.next`           | `Map?`    | Next page in section by `pageOrder` |
| `.page.prev`           | `Map?`    | Previous page in section |
| `.page.tags`           | `List[String]` | Frontmatter `tags` (always a list, even when authored as a single string) |
| `.page.categories`     | `List[String]` | Frontmatter `categories`, normalized the same way |
| `.page.date`           | `OffsetDateTime` | Parsed publication date (frontmatter `date` or filesystem mtime fallback) |
| `.page.dateISO`        | `String`  | `2024-03-12T00:00:00Z` — for `<time datetime=...>` |
| `.page.dateLong`       | `String`  | `March 12, 2024` — for body copy |
| `.page.dateShort`      | `String`  | `2024-03-12` — for compact lists |
| `.page.wordCount`      | `Int`     | Word count of the rendered HTML body |
| `.page.readingTime`    | `Int`     | Estimated minutes (`ceil(wordCount / 200)`, floor 1 for non-empty pages) |
| `.page.series`         | `Map?`    | Series block — `null` when the page is not in a series (see below) |
| `.page.author`         | `Map?`    | First (or only) resolved author registry record, or `null` |
| `.page.authors`        | `List[Map]` | All resolved author records — empty list when none |
| `.page.<custom>`       | varies    | Any frontmatter key |

### `.page.series` shape

Set when the page declares `series:` in frontmatter:

| Key       | Type        | What |
|-----------|-------------|------|
| `name`    | `String`    | Series name as it appeared in frontmatter |
| `pages`   | `List[Map]` | Every page in the series, ordered (see ordering rules below) |
| `prev`    | `Map?`      | Previous page's record, or `null` on the first |
| `next`    | `Map?`      | Next page's record, or `null` on the last |
| `index`   | `Int`       | 1-based position of this page within the series |
| `total`   | `Int`       | Number of pages in the series |

Ordering: explicit `seriesOrder` ascending first, then `.page.date`
ascending, then filename as a stable tiebreaker.

For section index pages (where `.page.isSection` is `true`), additionally:

| Key                  | Type        | What |
|----------------------|-------------|------|
| `.page.pages`        | `List[Map]` | Non-`_index` siblings, sorted |
| `.page.subsections`  | `List[Map]` | Direct child sections, sorted |

## `.section`

Always available (for non-`_index` pages it describes the enclosing section):

| Key                     | Type        | What |
|-------------------------|-------------|------|
| `.section.pages`        | `List[Map]` | Non-`_index` siblings, sorted |
| `.section.subsections`  | `List[Map]` | Direct child sections, sorted |
| `.section.index`        | `Map?`      | Section's `_index` record |
| `.section.paginator`    | `Map`       | Pagination state for the current slice (always present — see below) |

### `.section.paginator`

Section index pages render once per slice when `paginate` is set. Non-section
pages get a paginator with `total = 1` so the same template can read it
unconditionally.

| Key       | Type     | What |
|-----------|----------|------|
| `current` | `Int`    | 1-based index of the current slice |
| `total`   | `Int`    | Slice count |
| `pages`   | `List[Map]` | The pages on **this** slice (already sliced — don't slice again) |
| `first`   | `String` | URL of slice 1 |
| `last`    | `String` | URL of the last slice |
| `prevURL` | `String` | URL of the previous slice; empty string on slice 1 |
| `nextURL` | `String` | URL of the next slice; empty string on the last slice |

Slice 2+ lives at `<section>/page/<N>/index.html` — directory-style URLs that
work on any static host without rewrite rules.

## Other top-level

| Key         | Type      | What |
|-------------|-----------|------|
| `.content`  | `String`  | Rendered markdown body, HTML |
| `.toc`      | `Map`     | `{ headings: [TocEntry] }` — full heading tree |
| `.sub`      | `List`    | Children of the first heading, flattened |

## Page ordering

`pageOrder` is **weight ascending, then name ascending**. Pages with no `weight` frontmatter sort after weighted pages but before any sentinel value.

[= note =]
You'll almost always want to set explicit `weight` values on pages that need a particular order — installation before quickstart, etc. Anything that ships a `weight` lower than the default (`9223372036854775807` = `Long.MaxValue / 2`) wins.
[= /note =]
