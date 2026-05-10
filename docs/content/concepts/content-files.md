---
title: Content files
summary: Markdown files with YAML frontmatter — the source of truth for every page.
weight: 10
---

Every page on a juicer site comes from a markdown file under `content/`. There are two flavors:

- **Section index** — a file named `_index.md` (configurable via `folderContent`). Renders to `index.html` at the section directory. Carries the section's title, summary, and any list-page customizations.
- **Single page** — every other markdown file (`foo.md`). Renders to its own `foo/index.html` for clean URLs.

[= filetree =]
content/
├── _index.md                 → /index.html
├── about.md                  → /about/index.html
└── docs/
    ├── _index.md             → /docs/index.html
    ├── installation.md       → /docs/installation/index.html
    └── api/
        ├── _index.md         → /docs/api/index.html
        └── spec.md           → /docs/api/spec/index.html
[= /filetree =]

## Frontmatter

Every content file may begin with a YAML frontmatter block, delimited by `---`:

```markdown
---
title: My page
summary: One-line description shown in lists and meta tags.
weight: 30
draft: false
---

# Welcome

The body is plain markdown.
```

Recognized frontmatter keys:

| Key       | Type     | What it does |
|-----------|----------|--------------|
| `title`   | string   | Page title; used by templates and search index |
| `summary` | string   | Explicit summary; overrides auto-derived |
| `weight`  | int      | Sort key inside the section (lower = first) |
| `draft`   | bool     | Skip the page unless `--drafts` is passed |
| anything  | any      | Available to templates as `.page.<key>` |

[= note =]
The frontmatter format is YAML. JSON and TOML frontmatter are **not** supported — partly because YAML covers the use case fluently, partly because supporting three formats triples the surface area to keep correct.
[= /note =]

## Pretty URLs

Both flavors of content render to `index.html` so that URLs don't carry file extensions. `about.md` becomes `/about/`, not `/about.html`. The `_index.md` for a section becomes `/docs/`, not `/docs.html`.

## The `htmlDir` quirk

By default (the `standard` baseline config) juicer writes nested sections to `dst/html/<section>/...` rather than `dst/<section>/...`. That `html/` segment is **filesystem-only** — every URL juicer emits has it stripped. The convention exists so that:

- The site root `index.html` lives at the top of `dst/`, alongside `static/` files (`favicon.ico`, `robots.txt`, etc.).
- Nested content stays under a single subtree, so a webserver root pointing at `dst/` doesn't conflate static assets with rendered pages.

If this surprises you, set `htmlDir = ""` in your `site.toml` to disable. Most users don't need to touch it.

## Drafts

A page with `draft: true` is **skipped entirely** during a normal build — invisible to the TOC, sitemap, search index, and section listings. To preview drafts locally, pass `--drafts` (or `-D`):

```bash
sbt 'juicerJVM/run serve -s docs -D'
```

## Scheduling future posts

A page whose parsed `date:` frontmatter is in the future is **skipped** during a normal build, the same way `draft: true` is — invisible to every downstream consumer. The intended workflow is:

[= steps =]
## Set the publication date in frontmatter

```yaml
---
title: Big announcement
date: 2024-12-25T09:00:00Z
---
```

## Push the post to your repo whenever it's done

The post is invisible to a normal `juicer build` until the system clock catches up.

## Re-build (or re-deploy) on or after the publication date

The post starts appearing in section listings, taxonomy archives, feeds, and the sitemap automatically. Most people set up a daily build cron or use a CI scheduler.
[= /steps =]

To preview future-dated posts locally, pass `--future` (or `-F`):

```bash
sbt 'juicerJVM/run serve -s docs -F'
```

`--future` and `--drafts` are independent flags. A post with `draft: true` *and* a future `date` requires both flags to render.

[= note =]
The future-skip rule only applies to pages with **explicit** `date:` frontmatter. Pages relying on the filesystem-mtime fallback are never future-skipped — `mtime` can't be in the future under any normal workflow, but the safer rule is "only authored future-dating counts".
[= /note =]

## Summaries

Every page exposes a `.page.summary`. Three sources are tried, in order:

[= steps =]
## Explicit frontmatter

If `summary: ...` is set in frontmatter, it's used verbatim. Stop.

## `<!--more-->` marker

The body up to (but not including) `<!--more-->` is rendered as HTML and used as the summary. Useful when the natural summary is the first paragraph or two but you don't want to repeat yourself.

## First paragraph (fallback)

The first paragraph's plain text, capped at 30 words and ellipsised. Hugo defaults to 70 words; juicer trims tighter for compact list pages.
[= /steps =]
