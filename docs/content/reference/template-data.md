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
| `.site.posts`    | `List[Map]`         | Dated, non-section, non-`static` pages, newest first (see below) |
| `.site.pagesByYear` | `List[Map]`      | Same posts grouped by year, year descending (see below) |
| `.site.root`     | `Map?`              | The root section's `_index` record (or `null`) |
| `.site.tags`     | `List[Term]`        | Every distinct tag used in the site (see below) |
| `.site.categories` | `List[Term]`      | Same shape as `.site.tags`, for the categories axis |
| `.site.authors`  | `List[Map]`         | Every author with at least one referencing page — registry record + `id`, `url`, `count`, `pages` |
| `.site.authorRegistry` | `List[Map]`   | The raw `[[authors]]` table from `site.toml` in declaration order — includes authors with zero referencing pages, useful for staff-directory layouts (see below) |
| `.site.authorsPath` | `String`         | URL prefix under which the team listing and per-author archives are emitted. Configured by `authorsPath` in `site.toml`; defaults to `/authors/`. See below |
| `.site.now`      | `Map`               | Build-time "now" timestamp in four shapes (see below) |
| `.site.events`   | `List[Map]`         | Pages in the configured `eventsSection` with explicit `date:` frontmatter, sorted ascending. Includes future-dated events (see below) |
| `.site.calendar` | `List[Map]`         | Pre-computed N months of calendar grid starting at the current month, with weekly recurring events expanded onto every matching weekday (see below) |
| `.site.photos`   | `List[Map]`         | Aggregated `photos:` frontmatter entries from every page, sorted by parent-page date descending (see below) |
| `.site.data`     | `Map[String, Any]`  | Nested namespace of structured data loaded from `data/` files (see below) |
| `.site.comments` | `Map[String, Any]?` | The `[comments]` config block from `site.toml` (provider + provider-specific keys), or absent if no `[comments]` table is set (see below) |

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

### `.site.posts` and `.site.pagesByYear`

Curated views of the post stream — the same data, sliced two ways.

`.site.posts` is the flat list, newest first. A page is included if it
has an explicit parsed `date:` in frontmatter, is not a section index
(`_index.md`), and is not flagged `static: true`. Filesystem-mtime
fallback dates do **not** count — only authored dates make it in. The
result is exactly the set of pages a blog or news site would call "the
posts."

`.site.pagesByYear` groups the same list by calendar year, with years
descending and pages within each year still newest-first:

| Key     | Type        | What |
|---------|-------------|------|
| `year`  | `Int`       | The 4-digit year |
| `count` | `Int`       | Posts in this year |
| `pages` | `List[Map]` | The posts themselves, each shaped like a `.site.pages` entry |

Useful for chronological archive pages (`<h2>2024</h2>` then a list).

### `.site.authorRegistry`

The raw `[[authors]]` table from `site.toml`, preserved in declaration
order. Each entry is the verbatim TOML table — `id`, `name`, `role`,
`bio`, `avatar`, `email`, plus any custom fields you've added.

Distinct from `.site.authors`, which is filtered to authors who have at
least one referencing page and is augmented with `count`, `url`, and
`pages`. Use `.site.authorRegistry` for staff-directory layouts that
should show every team member regardless of how many sermons or posts
they've authored; use `.site.authors` for "browse by author" archives.

#### When `/authors/` and `/authors/<id>/` get rendered

The two layouts have different gates so themes can ship a "team page"
that shows everyone, even on a fresh site with no posts:

- `layouts/_default/author-list.html` → `/authors/index.html` is
  rendered whenever a registry exists at all (`[[authors]]` non-empty
  in `site.toml`). The layout typically iterates
  `.site.authorRegistry`.
- `layouts/_default/author-page.html` → `/authors/<id>/index.html` is
  only rendered for authors with at least one referencing page. An
  empty per-author archive page would be misleading.

Either layout is opt-in — missing the file means the corresponding
output is silently skipped.

#### Renaming the URL prefix — `authorsPath`

By default both the team listing and the per-author archives live under
`/authors/`. Sites using a non-blog theme often want a different label —
a cafe wants `/team/`, a doc site wants `/people/`. Set `authorsPath` in
`site.toml`:

```toml
authorsPath = "/team/"
```

The engine pivots three things in lockstep: the on-disk output
directory, the `url` field on every `.site.authors` term, and the
`.site.authorsPath` string surfaced for templates. Themes should read
`{{ .site.authorsPath }}` rather than hard-coding `/authors/` so a
site-level override propagates without a layout edit.

Forgiving normalization: `team`, `/team`, `team/`, and `/team/` all
become `/team/`. Path traversal segments (`.`, `..`) are rejected with a
warning and the default is used.

### `.site.now`

Build-time timestamp captured once per build, in four shapes so the
right one is always at hand:

| Key    | Type     | What |
|--------|----------|------|
| `iso`  | `String` | Full ISO offset — `2026-05-09T14:00:00Z` |
| `date` | `String` | `YYYY-MM-DD`, lex-comparable with `.page.dateShort` for past/future filters |
| `long` | `String` | `May 9, 2026`, same formatter as `.page.dateLong` |
| `year` | `Int`    | Useful for footer copyright |

The static-site model means "now" is legitimately frozen at build time.
Live-reload re-runs the build on every change, so the value stays fresh
during local preview.

### `.site.events`

Pages in the configured events section (`eventsSection` in `site.toml`,
default `"events"`) with an explicit `date:` frontmatter, sorted
ascending. Each entry is shaped like a `.site.pages` record — `title`,
`url`, `date`, `dateShort`, `summary`, plus any frontmatter the event
page declares (`eventTime`, `eventLocation`, `recurring`, etc.).

Includes future-dated events: events are announced *before* they happen,
and the engine's site-wide future-post filter is exempted for pages in
the events section so both `.site.events` entries and the corresponding
event detail pages survive the build. Future-dated *posts* (anything
outside the events section) continue to be future-skipped — see the
`--future` CLI flag to override per build.

Recurring events appear once each (the canonical page record); use
`.site.calendar` for occurrence-by-occurrence expansion.

### `.site.calendar`

Pre-computed N months (default 12, override via `calendarMonths` in
`site.toml`) starting at the current month. Each month is:

| Key         | Type            | What |
|-------------|-----------------|------|
| `year`      | `Int`           | 4-digit year |
| `month`     | `Int`           | 1–12 |
| `monthName` | `String`        | `"May"` |
| `label`     | `String`        | `"May 2026"` |
| `weeks`     | `List[List[Cell]]` | Always six 7-day rows, Sunday-first |

Each cell:

| Key       | Type        | What |
|-----------|-------------|------|
| `day`     | `Int?`      | 1–31, or `null` for out-of-month padding cells |
| `isToday` | `Boolean`   | `true` for the cell whose year/month/day matches `.site.now` |
| `events`  | `List[Map]` | Basic page records for events on this day (empty list when none) |

Recurring weekly events (`recurring: weekly` + optional `recurringDay:`)
are expanded onto every matching weekday from their start date forward.
A recurring event with no `recurringDay:` defaults to its start-date's
day of the week. Non-recurring events appear once on their `date`.

The grid is always 6×7 = 42 cells per month so calendar templates don't
have to special-case month boundaries; out-of-month cells render as
`day=null` padding.

### `.site.photos`

Aggregated `photos:` frontmatter entries from every page on the site,
sorted by the parent page's date descending. Each entry:

| Key       | Type     | What |
|-----------|----------|------|
| `src`     | `String` | Image URL (absolute or site-relative) |
| `caption` | `String` | Display caption — empty string when not provided |
| `alt`     | `String` | Accessibility text — falls back to `caption` when absent |
| `page`    | `Map`    | Basic page record of the parent page (lets templates link the photo back to its event/album/sermon) |

Frontmatter shape — either a list of strings (URLs only) or a list of
maps with `src` / `caption` / `alt` keys:

```yaml
photos:
  - "/img/picnic-01.jpg"
  - { src: "/img/picnic-02.jpg", caption: "The pie table" }
```

Pages without a `photos:` frontmatter contribute nothing.

### `.site.data`

Structured data the theme or site author wants templates to reach for
without forcing it into every page's frontmatter. juicer scans the
`dataDir` (default `data/`) under the site root and under each active
theme:

```
data/team.toml              → .site.data.team
data/menu/lunch.yaml        → .site.data.menu.lunch
data/menu/dinner.toml       → .site.data.menu.dinner
```

The file's basename (sans extension) becomes the leaf key; each
directory under `data/` becomes a nesting level. Both `.toml` and
`.yaml` / `.yml` are accepted — pick whichever shape fits the data
better. JSON is not parsed natively, but since it's a strict subset of
YAML you can rename `.json` → `.yaml` and it works.

A typical use:

```toml
# data/team.toml
[[members]]
name = "Alice"
role = "Lead"

[[members]]
name = "Bob"
role = "Eng"
```

```
{{ for m <- .site.data.team.members }}
  <li>{{ m.name }} — {{ m.role }}</li>
{{ end }}
```

**Theme overlay.** Themes can ship their own `data/` directory; site
entries win at the file-leaf granularity. If a theme provides
`data/site.toml` and the site provides `data/site.toml`, the site's
file replaces the theme's at the `.site.data.site` key path. The site
does **not** deep-merge fields — to support partial overrides as a
theme author, namespace into a subdirectory
(`data/colors/default.toml` + `data/colors/dark.toml`).

**Format notes.** TOML at a file root always parses to a map; YAML can
parse to a list (`data/sponsors.yaml` starting with `- name: ...`) or a
scalar, but a map is by far the most useful shape because nested-field
access (`.site.data.sponsors[0].name`) is more readable than positional
indexing in most templates.

### `.site.comments`

A pass-through of the `[comments]` table from `site.toml` (see
[Config → `[comments]`](../config/#comments--comments-provider-config-slot)).
juicer never ships a comments backend — the config block is opaque to
the engine, and theme partials are responsible for reading it and
emitting the embed HTML.

`provider` is the only convention; everything else is provider-specific
and theme-defined. A theme that supports several providers typically
branches on it:

```squiggly
{{ if .site.comments }}
  {{ if eq .site.comments.provider 'giscus' }}
    {{ partial 'comments/giscus.html' . }}
  {{ end }}
  {{ if eq .site.comments.provider 'utterances' }}
    {{ partial 'comments/utterances.html' . }}
  {{ end }}
  {{ if eq .site.comments.provider 'disqus' }}
    {{ partial 'comments/disqus.html' . }}
  {{ end }}
{{ end }}
```

The outer `{{ if .site.comments }}` is the right gate for "comments on
or off site-wide" — when no `[comments]` table is set in `site.toml`,
`.site.comments` is absent and the conditional evaluates falsy. Themes
that allow per-page opt-out also check `{{ if .page.comments }}` (or
similar) so individual posts can override the site-wide setting.

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
| `.page.slug`           | `String`  | URL stem (last path segment, no slashes) — useful for in-page anchors when a layout walks `.section.pages` and wants a stable per-section HTML id. `""` for the root index. |
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
| `.page.backlinks`      | `List[Map]` | Thin `{title, url, summary}` records for every page that links *to* this one — empty list when nothing points here (see below) |
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

### `.page.backlinks` shape

A list of thin records — one per page that contains an internal link
pointing at the current page's permalink. Sorted by referrer title for
deterministic rendering.

| Key       | Type     | What |
|-----------|----------|------|
| `title`   | `String` | Referrer's frontmatter title (or URL if title absent) |
| `url`     | `String` | Referrer's site-relative URL |
| `summary` | `String` | Referrer's resolved summary |

Templates that need a richer referrer record (tags, date, author, etc.)
look it up via `.site.pagesByPath[bl.url]` — keeping the embedded
records thin avoids cycles when two pages link each other.

The inverted index is built during the markdown pass from each page's
AST link destinations. Filtered out at collection time: absolute URLs
(`https://…`, `mailto:`, `tel:`), fragment-only anchors (`#section`),
and self-links. Query strings and fragments are stripped before
matching, so `[X](/target/#section)` and `[X](/target/?q=1)` both count
as a link to `/target/`. Lists, tables, definition lists, footnote
definitions, blockquotes, and list items are all walked — anywhere an
author can drop a `[text](url)` in markdown, it counts.

Wiki-style "Linked from" footer in a layout:

```squiggly
{{ if .page.backlinks }}
  <aside class="backlinks">
    <h2>Linked from</h2>
    <ul>
      {{ for bl <- .page.backlinks }}
        <li><a href="{{ bl.url }}">{{ bl.title }}</a>{{ if bl.summary }} — {{ bl.summary }}{{ end }}</li>
      {{ end }}
    </ul>
  </aside>
{{ end }}
```

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

## Site-wide chrome keys

Top-level `site.toml` keys that the bundled themes read from `.site.*` to
drive shared chrome — favicon, footer attribution, repo link, custom
stylesheets — are catalogued in [Theming](/reference/theming/) along with
the per-theme `[juicerXxx]` palette / typography / sizing tables.

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
