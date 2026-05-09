---
title: Configuration
summary: Every config key juicer understands, what it defaults to, and what it controls.
weight: 20
---

Site config lives in `site.toml` at the source root. Juicer overlays your file on top of one of three baselines (`simple`, `standard`, `norme`) selected by the `-c <name>` CLI flag.

## Baselines

| Name     | What |
|----------|------|
| `simple` | Flat layout — content, layouts, partials, static all at the source root |
| `standard` | Hugo-like nested layout (the default) |
| `norme` | French (Charter-of-the-French-Language compliant) — same as `standard` but with French directory names |

[= tip =]
The `norme` baseline is here because Quebec's Charter of the French Language requires that public-facing software present primarily in French. Same code, French file names. If your content is bilingual, use this baseline alongside the upcoming i18n feature (Tier 2).
[= /tip =]

## Keys

### Identity

| Key      | Default                         | What |
|----------|---------------------------------|------|
| `title`  | `"Untitled"`                    | Site title; available as `.site.title` |
| `author` | `"Unnamed"`                     | Site author; available as `.site.author` |
| `baseURL`| `"http://localhost:8080"`       | Absolute base URL — used for permalinks, sitemap, OpenGraph |

### Theme

| Key        | Default     | What |
|------------|-------------|------|
| `theme`    | (none)      | Theme name (string) or chain (array of strings). Resolved under `themeDir` |
| `themeDir` | `"themes"`  | Directory holding theme subfolders |

### Directory layout

| Key             | `standard` default | What |
|-----------------|--------------------|------|
| `contentDir`    | `"content"`        | Markdown source root |
| `htmlDir`       | `"html"`           | Filesystem-only prefix for nested sections; stripped from URLs |
| `publicDir`     | `"public"`         | Default output directory |
| `staticDir`     | `"static"`         | Verbatim-copied assets |
| `layoutDir`     | `"layouts"`        | Templates root |
| `partialDir`    | `"partials"`       | Partials root |
| `shortcodeDir`  | `"shortcodes"`     | Shortcodes root |

### Layout names

| Key              | Default       | What |
|------------------|---------------|------|
| `defaultLayout`  | `"_default"`  | Fallback layout subfolder under `layoutDir` |
| `baseofLayout`   | `"baseof"`    | Outer-shell layout filename (without extension) |
| `fileLayout`     | `"file"`      | Single-page layout filename |
| `folderLayout`   | `"folder"`    | Section-index layout filename |
| `folderContent`  | `"_index"`    | Filename (without extension) recognized as the section index |

### Behavior

| Key            | Default | What |
|----------------|---------|------|
| `stripPrefix`  | `true`  | Strip leading numeric prefixes from filenames in URLs (`01-foo.md` → `foo`) |
| `headingShift` | `2`     | Add this much to every markdown heading level (1-clamped) |

### Blog features

These keys turn on the blogging features documented under
[Concepts → Blogging features](../../concepts/blogging/). All four are opt-in;
a docs site that doesn't set them renders unchanged.

| Key          | Default   | What |
|--------------|-----------|------|
| `paginate`     | (none)    | Default slice size for section index pages. When unset, sections render in a single page no matter how many children they have. |
| `sortBy`       | `weight`  | Order section pages: `"date"` (newest first), `"title"` (alphabetical), or `"weight"` (juicer's default — `weight` ascending) |
| `dateArchives` | `false`   | Emit `/<year>/` and `/<year>/<month>/` archive pages from posts' parsed dates. Requires matching `date-year.html` / `date-month.html` layouts; missing layouts are silent skips. Only pages with **explicit** `date:` frontmatter are included — mtime-fallback dates don't pollute the archive. |
| `dateFormat`   | (none)    | Reserved for future per-site date-format overrides. Not yet wired up; templates use the built-in `dateLong` / `dateShort` / `dateISO` helpers for now. |

[= note =]
Both `paginate` and `sortBy` can be overridden per-section by setting the same
key on the section's `_index.md` frontmatter. So a site that wants 10 posts
per page on `/posts/` but 30 short notes per page on `/notes/` puts
`paginate = 30` in `content/notes/_index.md` and leaves the site-wide value at
10.
[= /note =]

### Calendar / events features

Juicer surfaces a curated events list (`.site.events`) and a 12-month
calendar grid (`.site.calendar`) for any site that has a section of
event pages. See
[Template data → `.site.events`](../template-data/#siteevents) and
[`.site.calendar`](../template-data/#sitecalendar).

| Key              | Default     | What |
|------------------|-------------|------|
| `eventsSection`  | `"events"`  | Name of the content section juicer treats as events. Pages in this section with explicit `date:` frontmatter populate `.site.events` and `.site.calendar`. The site-wide future-post filter is also exempted for pages in this section so future-dated event detail pages still render to disk. |
| `calendarMonths` | `12`        | How many months `.site.calendar` pre-computes, starting at the current month. Higher values cost build time and HTML size; lower values mean the calendar runs out sooner. |

Recurring events are theme-and-template territory — the engine
recognizes `recurring: weekly` plus an optional `recurringDay:`
frontmatter on event pages and expands the event onto every matching
weekday in `.site.calendar`. Without `recurringDay:`, the recurrence
defaults to the start date's day of the week.

## `[permalinks]` — URL templates per section

A TOML sub-table that overrides a section's URL pattern. Each key is a
section name (the first path segment after `htmlDir` is stripped); each
value is a template string with substitution tokens. Tokens are resolved
against the page's frontmatter and parsed date.

```toml
[permalinks]
posts = ":year/:month/:slug/"
notes = ":slug/"
articles = ":year/:section/:title/"
```

Recognized tokens:

| Token       | Resolves to |
|-------------|-------------|
| `:slug`     | The cleaned filename (`01-foo.md` → `foo` when `stripPrefix = true`) |
| `:title`    | `slugify(.page.title)` — frontmatter title, lowercased and ASCII-folded |
| `:year`     | 4-digit year from `.page.date` |
| `:month`    | 2-digit month from `.page.date` |
| `:day`      | 2-digit day from `.page.date` |
| `:section`  | The section name itself |

Sections **without** a `[permalinks]` entry keep juicer's default
physical-path-derived URL (the file tree determines the URL one-to-one).
Section index pages (`_index.md`) are never routed through permalink
templates — they always live at the section root.

[= note =]
Permalink templates change both the URL and the on-disk write location of
each affected page. Juicer doesn't keep both copies — only the
permalinked path exists in the output tree. So a `posts/foo.md` with
`posts = ":year/:slug/"` writes only to `<dst>/2024/foo/index.html`,
never to `<dst>/posts/foo/index.html`.
[= /note =]

See [Concepts → Blogging features → Permalinks](../../concepts/blogging/#permalinks)
for the narrative version.

## `[[authors]]` — author registry

An array of tables describing the people who write posts on the site.
Each entry needs at least an `id`; everything else is optional and
flows directly into `.page.author` / `.page.authors` records.

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
```

| Field    | Required | What |
|----------|----------|------|
| `id`     | yes      | Stable url-safe identifier; archive lives at `/authors/<id>/` |
| `name`   | no       | Display name |
| `email`  | no       | Author email; useful in feed templates |
| `bio`    | no       | Short bio used in bylines and author archive headers |
| `avatar` | no       | URL of avatar image — site-relative or absolute |
| `links[]`| no       | Each entry has `label` and `url`; renders as a list of external links |

Pages reference an author via `author: <id>` or `authors: [<id>, ...]`
in their frontmatter. See
[Concepts → Blogging features → Author registry](../../concepts/blogging/#author-registry)
for the full narrative.

## Custom keys

Any key you set in `site.toml` is available as `.site.<key>` in templates. Use this for site-wide settings the theme exposes — e.g., `editURL`, `discussionURL`, `social.twitter`. Themes typically document the keys they recognize in their README.
