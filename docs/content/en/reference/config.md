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
| `dataDir`       | `"data"`           | Structured data root — `*.toml` / `*.yaml` / `*.yml` files here are exposed to templates as `.site.data` (see [Template data → `.site.data`](../template-data/#sitedata)). Themes can also ship a `data/` directory; site keys win at the file-leaf granularity. |
| `excludeDirs`   | unset              | Extra directories to skip during the site walk. String or array of strings; each entry is a path relative to the source root (`"node_modules"`, `"assets/raw"`). Match is exact-directory only — not a glob pattern. |

The walk already skips the active `themeDir` subfolders, the `themeDir` parent (so inactive themes vendored alongside don't render), the configured `publicDir`, and the build's output `dst`. Use `excludeDirs` for anything else under the source root that isn't part of the site — vendored tooling, scratch folders, generated assets you produce out-of-band, drafts kept outside `content/`:

```toml
excludeDirs = ["node_modules", "scratch", "assets/raw"]
```

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
| `headingShift` | `2`     | Add this much to every markdown heading level. The default `2` exists because layouts typically emit an outer `<h1>{{ .page.title }}</h1>` and most theme CSS expects body markdown to start at `<h2>`. Set to `0` when a theme renders the page heading from the markdown body itself. |
| `feeds`        | `true`  | Emit Atom and RSS feed files alongside the rendered HTML. Set `false` for sites that don't want feeds (single-page landings, internal wikis). |

### Navigation — `nav`

Top-level array that drives `.site.toc` (the sidebar / topbar nav).
When `nav` is **absent**, juicer auto-builds the nav by walking the
content tree. When `nav` is set, juicer walks the array entries:

- A **string** ending in a markdown extension (`.md`, `.markdown`,
  `.mkd`, `.mkdn`, `.mdown`) is a reference to a content file
  (path relative to `contentDir`). The page record at that path is
  pulled into the nav at the array's position.
- Any other **string** is a section **label** — a non-clickable
  group heading.
- A **single-key table** maps an explicit label to a content path —
  useful when the file's frontmatter title isn't the right label
  for the nav.

```toml
nav = [
  "Getting Started",                       # label
  "getting-started/_index.md",             # link, title from frontmatter
  "getting-started/installation.md",
  { "Get help" = "getting-started/troubleshooting.md" },   # link, explicit label
  "Reference",                             # label
  "reference/cli.md",
  "reference/config.md",
]
```

The same array is also walked by themes' "previous / next" partials
when `nav` is set, so the order you write here is the order readers
flip through.

### i18n — `languages`, `defaultLanguage`

Two opt-in keys that turn on multi-language mode. The engine doesn't
duplicate content for you (one markdown file per language is still
the convention) — it surfaces the active language to templates so
themes can render language-aware chrome and pull strings from a
translation table.

| Key               | Default                  | What |
|-------------------|--------------------------|------|
| `languages`       | unset (single-language)  | Array of language codes the site supports — `["en", "fr", "ja"]`. When unset, juicer is in single-language mode and `.page.lang` is the empty string. |
| `defaultLanguage` | first entry / `""`       | Language used when a page's URL prefix doesn't match any of `languages`. Falls back to the first entry of `languages` when not set. |

Translation strings live in `<src>/i18n/<lang>.toml`, one file per
declared language, as flat `key = "value"` tables:

```toml
# i18n/en.toml
home          = "Home"
browse_docs   = "Browse the docs"
read_more     = "Read more"
```

```toml
# i18n/fr.toml
home          = "Accueil"
browse_docs   = "Parcourir la documentation"
read_more     = "Lire la suite"
```

Templates look strings up with the `i18n` helper, passing the page's
language:

```squiggly
<a href="/">{{ i18n .page.lang 'home' }}</a>
<a href="/docs/">{{ i18n .page.lang 'browse_docs' }}</a>
```

Lookups fall back to `defaultLanguage` when a key is missing in the
requested language; if the key is missing in both, the literal key
is returned (so a missing translation is visible during authoring
without crashing the build).

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

## `[comments]` — comments-provider config slot

juicer never ships a comments backend. Sites that want comments declare
the provider and provider-specific keys under `[comments]` in
`site.toml`; theme partials read the config back as `.site.comments.*`
and emit the right embed HTML.

```toml
[comments]
provider = "giscus"
repo     = "edadma/juicer"
repoId   = "R_kgDOXXXXXX"
category = "Announcements"
categoryId = "DIC_kwDOXXXXXX"
mapping  = "pathname"
reactions = true
theme    = "preferred_color_scheme"
```

The only convention the engine cares about is the table name itself —
everything inside is opaque to juicer. Conventional keys for the common
providers:

| Provider     | Conventional keys |
|--------------|-------------------|
| `giscus`     | `repo`, `repoId`, `category`, `categoryId`, `mapping`, `reactions`, `theme` |
| `utterances` | `repo`, `issueTerm`, `label`, `theme` |
| `disqus`     | `shortname` |

Themes typically gate the embed on `{{ if .site.comments }}` and on a
per-page `comments: false` frontmatter override (so individual posts can
opt out of comments without unsetting the site-wide config). See
[Template data → `.site.comments`](../template-data/#sitecomments) for
the template-side contract.

[= note =]
juicer is static-output-only — it doesn't proxy comments, store
moderation state, or call the provider's API. The block above is a
config slot only. If a provider needs server-side state, that belongs in
the provider's own infrastructure, not in juicer.
[= /note =]

## `[images]` — image variant generation

Opt-in build-time generation of resized + reformatted image variants
(`webp`, `avif`, etc.) for responsive `<picture>` / `<img srcset>`
markup. The feature is **disabled by default** — sites that don't set
this table build byte-identically to pre-`[images]` juicer.

```toml
[images]
enabled  = true
widths   = [320, 640, 960, 1280]
formats  = ["webp", "original"]   # most-modern first; "original" passes through
quality  = 80
cacheDir = ".image-cache"          # under dst; variants land here
```

| Key        | Default                            | What |
|------------|------------------------------------|------|
| `enabled`  | `false`                            | Master switch. `false` → templates that call `imageVariants` get a passthrough-only set with no `<source>` rows. |
| `widths`   | `[320, 640, 960, 1280]`            | Target widths in pixels. Widths ≥ the source's own width are dropped (no upscaling); the source's exact width is always included. |
| `formats`  | `["webp", "original"]`             | Output formats in priority order. Known: `webp`, `avif`, `jpeg`, `png`, `original`. Unknown names are dropped. |
| `quality`  | `80`                               | Encoder quality (1–100). Used for lossy formats; `png` ignores it. |
| `cacheDir` | `".image-cache"`                   | Directory under `dst` where generated variants live. Content-hashed filenames mean re-runs on unchanged sources skip the encoder shell-out. |

**Encoder.** Juicer shells out to ImageMagick (`magick`) — install with
`brew install imagemagick` / `apt install imagemagick` /
`dnf install ImageMagick`. When `magick` is not on PATH the build still
succeeds, but `imageVariants` returns a passthrough-only set (a single
advisory line prints to stderr). The Scala Native and Scala.js targets
ship a stub backend; full variant generation is JVM-only today.

Template side: see
[Template syntax → `imageVariants` and `srcset`](../template-syntax/#imagevariants-and-srcset)
for the helpers themes call.

## `[assets]` — Sass / esbuild pipeline

Opt-in build-time compilation of theme assets via two widely-available
CLIs: `sass` for SCSS → CSS and `esbuild` for JS bundling and
minification. Plus an optional fingerprinting flag that appends a
content-hash to output filenames so deploys can ship cache-busting URLs
without giving up long-lived `Cache-Control` headers. **Disabled by
default** — sites that don't set this table build byte-identically to
pre-`[assets]` juicer.

```toml
[assets]
enabled     = true
fingerprint = false

[[assets.sass]]
input  = "src/site.scss"
output = "/css/site.css"
minify = true

[[assets.esbuild]]
input  = "src/main.js"
output = "/js/main.js"
minify = true

[[assets.copy]]
input  = "src/robots.txt"
output = "/robots.txt"
```

| Top-level key | Default | What |
|---------------|---------|------|
| `enabled`     | `false` | Master switch. `false` → no pipeline runs, the `asset` builtin returns its input unchanged. |
| `fingerprint` | `false` | When `true`, juicer inserts the output bytes' content-hash before the extension: `/css/site.css` → `/css/site.<16-hex>.css`. The `asset` builtin resolves to the fingerprinted URL automatically. |

Entry tables (any number of each; use either inline-table shorthand or
`[[assets.kind]]` array form):

| Entry kind | Required keys | Optional keys | What |
|------------|---------------|---------------|------|
| `[[assets.sass]]`    | `input`, `output` | `minify`, `logical` | SCSS / Sass → CSS via `sass --no-source-map [--style=expanded|compressed]`. |
| `[[assets.esbuild]]` | `input`, `output` | `minify`, `logical` | JS bundle via `esbuild <in> --bundle [--minify] --outfile=<out>`. |
| `[[assets.copy]]`    | `input`, `output` | `logical` | Byte-for-byte copy (no tool involved). Useful when fingerprinting a file no compiler needs to touch. |

`input` is resolved under the source root; `output` is a site-rooted
URL path (leading slash optional). `logical` defaults to the basename
of `output` — `/css/site.css` becomes the manifest key `site.css`,
which templates look up as `{{ asset 'site.css' }}`.

**Tool installation.** Juicer shells out to `sass` (the dart-sass /
npm package; the Ruby `sass` gem also works for the small flag subset
juicer uses) and `esbuild`. Install with
`brew install dart-sass esbuild` /
`npm install -g sass esbuild` / your platform's equivalent. When a
tool isn't on PATH the build still succeeds: that entry degrades to a
verbatim copy of its source, the URL still resolves so templates
don't break, and a single advisory line goes to stderr. Native and JS
targets ship stub backends; full pipeline execution is JVM-only today.

Template side: see [Template data → `asset` builtin](../template-data/#asset-builtin).

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
