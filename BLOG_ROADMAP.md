# juicer blog support roadmap

Companion to [ROADMAP.md](ROADMAP.md). The main roadmap deferred
**taxonomies, page bundles, and pagination** under Tier 3 with the
note *"Skip until at least one user blogs with juicer."*

That trigger has fired. This doc is the prioritized plan for turning
juicer into a tool you'd actually pick over Jekyll for a single-author
blog, then a multi-author publication.

## Guiding principles

Inherits everything from [ROADMAP.md §Guiding principles](ROADMAP.md#guiding-principles).
Additional rules specific to blog work:

1. **Docs sites stay first-class.** Nothing here regresses juicerdocs
   or the existing site model. Every blog feature is opt-in: a
   monolingual docs site that doesn't use tags or pagination should
   render byte-identically before and after this work.
2. **Tags first; generic taxonomies maybe later.** Hugo's "any
   taxonomy you declare" model is a year of frontmatter-quoting
   bugs in waiting. Ship `tags` (and `categories` as an alias) as
   first-class fields. Generalize only if a real user files an issue
   wanting a third axis.
3. **A theme that uses every new feature ships in the same PR train.**
   `juicerblog` is the demo that closes the loop on each phase. If a
   feature can't be exercised by `juicerblog`, it isn't done.
4. **Each feature has a `JuicerBuildSpec` case before merge.** Same
   rule as the main roadmap — no "I tested it locally" features.
5. **No comments backend, no analytics backend.** Themes provide
   slots (Giscus container, plausible script tag) populated from
   site config; juicer core stays static-output-only.

## Status snapshot

| Concern | Have | Want |
|---|---|---|
| Tags / categories | none | Phase 1 (`.page.tags`, archives) |
| Pagination | none | Phase 1 (section + tag list pages) |
| Reading time | none | Phase 1 (`.page.readingTime`) |
| `juicerblog` theme | none | Phase 1 (the demo) |
| Series | none | Phase 2 |
| Author registry | implicit `.site.author` only | Phase 2 |
| Date-based archives | none | Phase 2 |
| Aliases / redirects | none | Phase 2 |
| Permalink templates | implicit `path` only | Phase 2 |
| Open Graph / Twitter cards | template-only | Phase 2 (helpers) |
| Future-dated posts | rendered as normal | Phase 2 (`--future` flag) |
| Server-side syntax highlighting | done (0d714ec) | ✓ |
| Image dimensions (`imageDims` builtin) | done | ✓ |
| Image variants (srcset, WebP/AVIF, blurhash) | none | Phase 3 — design call pending |
| Comments slot (`[comments]` config + `.site.comments`) | done | ✓ |
| Asset fingerprinting | none | **deferred indefinitely** |
| Comments / analytics backends | none | **deferred indefinitely (theme slots only)** |

## Phase 1 — the blog MVP

The smallest set that makes "I write posts in markdown and people
can find them by tag" real. Estimated total: ~600–800 LOC of source
plus matching tests, plus the `juicerblog` theme. About a long
weekend of focused work.

### 1.1 Tags

**What.** Frontmatter `tags: [scala, ssg]` (also accepts a single
string). Each tag gets:

- a `/tags/` index page listing every tag with post count,
- a `/tags/<slug>/` archive page listing posts under that tag.

`categories` is parsed identically and treated as a separate axis;
sites that want one axis just pick one and don't use the other.

Templates see `.page.tags` (`List[String]`) and a new
`.site.tags: List[Tag]` where each `Tag` exposes `name`, `slug`,
`url`, `count`, `pages`. Same shape for `.site.categories`.

**Where.** `App.build` gains a tag-collection pass after the existing
content walk: scan all rendered pages for `tags`/`categories`
frontmatter, build inverted indexes, then synthesize virtual
"section" pages for `/tags/` and each `/tags/<slug>/`. The synthesis
reuses the existing section-page rendering code (Tier 2 #9) — a
tag page is just a section whose `.section.pages` is the inverted
index entry.

`tag-list.html` and `tag-page.html` layouts get default
implementations under the `juicerblog` theme; the docs theme can opt
out (no `tags` frontmatter, no archives generated).

**Cost.** ~150 LOC. Slug computation is the only fiddly bit —
ASCII-fold + lowercase + replace non-alphanumeric runs with `-`.

**Test.** Three cases. (a) Build a site with three posts, two
sharing a `scala` tag; assert `/tags/scala/index.html` lists both,
`/tags/ssg/index.html` lists one. (b) Tags index `/tags/index.html`
shows all unique tags with correct counts. (c) Categories work
identically and don't collide with tags.

### 1.2 Pagination

**What.** When a section list page (or tag archive) has more than
`paginate` pages (default 10, configurable per-site and
per-section), juicer emits `index.html`, `page/2/index.html`,
`page/3/index.html`, etc.

Templates see `.section.paginator`:

```
{{ .paginator.current }}     // 1-based int
{{ .paginator.total }}       // page count
{{ .paginator.pages }}       // pages on THIS list page (already sliced)
{{ .paginator.prevURL }}     // '' on page 1
{{ .paginator.nextURL }}     // '' on last page
{{ .paginator.first }}       // URL to page 1
{{ .paginator.last }}        // URL to last page
```

Sort order is configurable: `sortBy = "date"` (default, descending)
or `"title"` (ascending). Frontmatter `weight: <int>` overrides.

**Where.** A new `paginate.scala` in `shared/`. The section render
loop in `App.build` calls it with the full `.section.pages` list;
it returns a `List[(slicedPages, paginator)]` and emits one render
per element with the per-page sliced list.

**Cost.** ~120 LOC. Most of it is URL math (`page/2/` vs `?page=2`
— stick with directory style for clean URLs and static-host
compatibility).

**Test.** Build a site with 25 posts; assert `index.html` lists 10,
`page/2/index.html` lists 10, `page/3/index.html` lists 5; assert
prevURL/nextURL chain correctly; assert `paginate = 5` in
`site.toml` shrinks the slice.

### 1.3 Reading time + word count

**What.** `.page.wordCount` (int) and `.page.readingTime` (int,
minutes; ceiling of `wordCount / 200`).

**Where.** Compute alongside `.page.summary` in `App.build` — both
need the markdown plain-text projection that already exists. ~10
LOC plus test.

**Test.** A post with a known word count asserts both fields.

### 1.4 `.page.date` resolution

**What.** Today juicer treats `date` frontmatter as a passthrough
string. Make it a parsed timestamp with consistent rendering helpers:

- `date` frontmatter parsed as ISO 8601 / RFC 3339 / `YYYY-MM-DD`.
- Falls back to filesystem mtime if absent.
- Exposed as `.page.date` (the parsed instant) plus
  `.page.dateISO` / `.page.dateLong` / `.page.dateShort` formatted
  helpers driven by site `dateFormat` config (with sensible defaults).

This is foundation work for sorting (1.2) and feeds (already
shipped in Tier 2 but currently treats date as opaque).

**Cost.** ~40 LOC. Most of it is the parser-with-fallbacks.

**Test.** Three cases — ISO format, plain `YYYY-MM-DD`, no date
(filesystem mtime).

### 1.5 `juicerblog` theme

**What.** A theme under `docs/themes/juicerblog/` that exercises
1.1–1.4. Hand-rolled CSS (no Tailwind — see
[`feedback_no_tailwind`](../) in the user's preferences). Layouts:

- `home.html` (alias of `folder.html` at site root): hero + recent
  posts + "browse by tag" cluster.
- `post.html`: title, date, reading time, tag pills, body, prev/next.
- `tag-list.html`, `tag-page.html`: see 1.1.
- `archive.html`: "all posts" reverse-chronological list.

Color/type tokens follow the same conventions as juicerdocs (CSS
variables, `[data-theme="dark"]` overrides, `--brand`/`--accent`
palette).

**Cost.** ~300 LOC of CSS, ~150 LOC of HTML across layouts/partials.
Bigger than juicer-side work; the theme is what closes the loop.

**Test.** A `juicerblog` smoke build under
`docs/themes/juicerblog/_demo/` (a tiny standalone content tree)
that we can `juicer build` from CI to catch breakage.

## Phase 2 — "now it's actually nice"

Pick these up one at a time after Phase 1 ships and we've used
`juicerblog` for a real blog for a couple weeks.

### 2.1 Drafts vs. future posts

**What.** Frontmatter `draft: true` is already supported (Tier 1.3).
Add `date: <future>` exclusion: posts with a parsed `date` in the
future are skipped unless `--future` is passed. `--drafts` and
`--future` are independent flags.

**Cost.** ~30 LOC. Reuses 1.4's parsed-date pipeline.

### 2.2 Series / multi-part posts

**What.** Frontmatter `series: "OS Internals"` (and optional
`seriesOrder: 3`). Pages with the same series name are linked into
a navigable series:

```
.page.series.name
.page.series.pages    // all in series, sorted by seriesOrder then date
.page.series.prev
.page.series.next
.page.series.index    // 1-based position
```

A `series-page.html` partial in `juicerblog` renders an "In this
series" sidebar.

**Cost.** ~80 LOC. Same machinery as tags (1.1) — single-value
inverted index.

### 2.3 Author registry

**What.** Site config:

```toml
[[authors]]
id = "ed"
name = "Edward A Maxedon"
email = "..."
bio = "..."
avatar = "/img/ed.jpg"

[[authors.links]]
label = "GitHub"
url = "https://github.com/edadma"
```

Per-page frontmatter: `author: ed` (or `authors: [ed, alice]`).
Templates see `.page.author` (or `.page.authors: List[Author]`)
resolved against the registry. `/authors/<id>/` archive pages list
that author's posts.

**Cost.** ~120 LOC. Archive synthesis reuses 1.1's tag pipeline.

### 2.4 Date archives

**What.** `/YYYY/` and `/YYYY/MM/` index pages, generated from
posts' parsed dates. Optional — controlled by site config
`dateArchives = true`.

**Cost.** ~60 LOC.

### 2.5 Aliases / redirects

**What.** Frontmatter `aliases: [/old-url/, /even-older-url/]`.
Each alias becomes a `<meta http-equiv="refresh">` page at the
listed URL pointing to the canonical page.

**Cost.** ~40 LOC plus a default `alias.html` layout.

### 2.6 Permalink templates

**What.** Per-section permalink format from site config:

```toml
[permalinks]
posts = ":year/:month/:slug/"
notes = ":slug/"
```

Available tokens: `:slug`, `:year`, `:month`, `:day`, `:section`,
`:title` (slugified).

**Cost.** ~80 LOC. Touches every spot that constructs a page URL —
do this **before** 2.1 if possible so future-dated posts and date
archives line up.

### 2.7 Open Graph / Twitter card helpers

**What.** Template builtin `{{ ogTags .page }}` that emits the
canonical block:

```html
<meta property="og:title" content="..."/>
<meta property="og:type" content="article"/>
<meta property="og:url" content="..."/>
<meta property="og:image" content="..."/>
<meta property="og:description" content="..."/>
<meta name="twitter:card" content="summary_large_image"/>
...
```

Resolves `image` from frontmatter `ogImage`, then `image`, then
site default. `description` falls through to `.page.summary`.

**Cost.** ~50 LOC plus a default partial.

## Phase 3 — kill-shot features

Where juicer would actually beat Jekyll. Bigger commitments; only
pick up if a real user is asking.

- **Server-side syntax highlighting.** Currently markdown emits
  `<pre><code class="language-foo">`; client-side highlighters do
  the rest. Server-side coloring removes the FOUC, the JS payload,
  and the dark-mode flicker. Hugo uses Chroma; juicer could call out
  to a Scala port (e.g. `chroma-scala` if one exists by then) or
  shell out to `tree-sitter` highlights. ~200 LOC plus a real
  evaluation of the highlighter library landscape.

- **Image optimization.** Frontmatter or shortcode-driven:
  `srcset`, `sizes`, low-quality placeholders, format conversion
  (WebP/AVIF). Likely a JVM-only feature gated behind a `[images]
  enabled = true` config block; JS/Native targets fall back to
  passthrough. ~300 LOC plus a hard look at whether we want a
  `javax.imageio` dependency in core or a separate `juicer-images`
  module.

  **Foundation shipped:** the `imageDims` template builtin reads
  pixel width/height from PNG/JPEG/GIF/WebP headers via a pure-Scala
  parser (no `javax.imageio`, no FFI). Themes can emit `<img>` with
  proper `width`/`height` attributes today and dodge cumulative
  layout shift. See `reference/template-syntax.md#imagedims` for
  the template contract; tests live in
  `ImageDimensionsSpec` + `ImageDimsBuiltinSpec`.

  **Open design call** for variant generation (the resizing /
  format-conversion piece). Three viable paths, none auto-pickable:

  1. *Pure-JVM via `javax.imageio` + TwelveMonkeys plugins.* New
     dep in `.jvmSettings`, source split in `jvm/src/main/scala`,
     stub no-op in `js/` + `native/`. PNG/JPEG/GIF/WebP reading
     works. WebP/AVIF *writing* needs an extra dep
     (`webp-imageio` is unmaintained, `imageio-avif` is alpha) —
     this option locks us to a stale image stack.
  2. *Shell out to `magick` / `cwebp` / `avifenc`.* Zero JVM
     deps; quality is best-in-class; requires the user to install
     those tools. Cross-target friendly only on JVM + Native
     (JS would need Node's `child_process`, awkward in the SJS
     facade). Cache by content hash + variant config under
     `dst/.image-cache/` so incremental builds skip reprocessing.
  3. *Separate `juicer-images-jvm` published artifact.* Strictest
     module hygiene; users who don't want image processing don't
     pull the deps. Adds another publish step + version coupling.

  Recommendation when a real user files an issue: go with **option 2**
  (shell-out), because it (a) keeps the juicer dep graph clean,
  (b) gives users the freshest WebP/AVIF encoders without juicer
  needing to track upstream releases, and (c) the cache key
  (`hash(srcBytes) + variantParams`) makes it incremental-friendly.
  The `[images]` config block then just declares variant widths
  + which encoders to use, and missing tools degrade gracefully
  to a passthrough.

- **Comments slot.** ✓ Done. `[comments]` table in `site.toml` flows
  through to `.site.comments` automatically via the existing
  `tomlObject` plumbing — no core code change was needed (the
  ~30-LOC estimate was conservative). Documented as a stable
  contract in `reference/config.md` and `reference/template-data.md`;
  `CommentsSlotSpec` pins the surface. Theme partials gate on
  `{{ if .site.comments }}` and branch on `.site.comments.provider`
  (giscus / utterances / disqus conventionally).

- **Native-image binary with sub-second incremental builds.**
  GraalVM `native-image` of `juicer` for instant startup. The
  cross-platform build already targets Native; this is more about
  packaging + benchmarking + watch-mode incremental rebuild than a
  new feature. Multi-week scope.

## Explicitly deferred (don't pick up without a use case)

- **Generic Hugo-style taxonomies.** See principle 2 above.
  `tags` + `categories` + Phase 2.3 author archives + Phase 2.2
  series cover four axes; that's enough.

- **Page bundles (page-as-directory).** Same skip reason as the
  main roadmap's Tier 3. Real value only when there's media.
  Phase 3 image optimization may force this conversation; until
  then, `static/` is enough.

- **Asset pipeline (Hugo Pipes — SCSS, JS bundling, fingerprinting,
  minification).** Same skip reason as the main roadmap. Vite/esbuild
  do this well; users wire that up themselves and `static/` the
  output. The juicerdocs / juicerblog themes ship hand-coded CSS
  precisely so site authors don't need any of this.

- **Comments / analytics backends.** juicer is static-output-only;
  themes provide config-driven slots, never juicer-shipped backends.

## Notes for whoever picks up Phase 1

- The integration test file remains
  `shared/src/test/scala/io/github/edadma/juicer/JuicerBuildSpec.scala`.
  Add cases co-located with the existing 23+.
- Tag/series/author archive pages should reuse the **section-page
  pipeline** (Tier 2 #9). Don't fork a parallel rendering path.
- New page-context fields (`tags`, `wordCount`, `readingTime`,
  `date`, `series`) all go in `pagedata` in `App.build`. New
  site-context fields (`tags`, `categories`, `authors`,
  `paginate`) go in `sitedata`.
- `juicerblog`'s static CSS is hand-rolled; don't reach for
  Tailwind. The juicerdocs rewrite (commit `3733627` on `dev`)
  established the convention — semantic `.juicerblog-*` class
  names + CSS variables + `[data-theme="dark"]` overrides.
- Slug computation should live in **one helper** in `shared/`,
  used by tags, categories, authors, series, and permalink
  templates. Don't reinvent it five times.
- Phase 1 lands as a single PR train on `dev`, then merges to
  `stable` together with a 0.3.0 tag. Phase 2 items can ship
  individually.
