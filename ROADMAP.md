# juicer roadmap

This is the stop-thinking-and-pick-up-from-here doc. As of 0.1.0 juicer is
a working static site generator with the core feature set listed in the
[README](README.md). What follows is the prioritized list of additions —
written after a Hugo-vs-juicer audit aimed at the documentation-site use
case.

## Guiding principles

1. **Documentation sites first.** Every Tier 1 item is something the
   docs-site for `markdown` / `squiggly` / juicer itself would actually use.
2. **No scope creep into asset pipelines or module registries.** The
   JS ecosystem already does asset bundling well; juicer's job is content
   to HTML, not webpack-replacement. See [Tier 3](#tier-3--explicitly-deferred)
   for the full skip list.
3. **Cross-platform stays cross-platform.** Anything new in `shared/`
   has to keep compiling on JVM, JS, and Native. JVM-only features
   (currently just `serve`) live under `jvm/src/main/scala/...` with
   stub equivalents in `js/` and `native/` that print a clear message.
4. **Each item ships with a test in `JuicerBuildSpec`.** No item is
   considered done until the integration suite covers it.
5. **Avoid one-shot `if (frontmatter.contains("xyz"))` plumbing.** When a
   feature requires a new shape on the page-rendering context, make it
   regular and uniform — `.page.foo` for every page, not "only set if X."

## Status snapshot (post 0.1.0)

| Concern | Have | Want |
|---|---|---|
| Markdown + YAML frontmatter | ✓ | — |
| Templates (squiggly) + partials + blocks | ✓ | — |
| Shortcodes (`[= name =]`) | ✓ | — |
| Auto heading IDs / TOC | ✓ | — |
| Static file pass-through | ✓ | — |
| `serve` command | ✓ JVM-only | + live reload |
| `site.toc` nav data | ✓ | + `.site.pages` |
| `.page.permalink` etc. | implicit only | explicit |
| Drafts | none | `draft: true` |
| Sitemap / 404 / RSS | none | sitemap + 404 (Tier 1); RSS (Tier 2) |
| Sections with auto list pages | none | Tier 2 |
| Themes (`themes/<name>/` overlay) | none | Tier 2 |
| i18n (per-language directory tree) | none | Tier 2 |
| Module registries, asset pipeline, page bundles, taxonomies | none | **deferred indefinitely** |

## Tier 1 — small wins, do these first

Estimated total: ~150–200 LOC of source plus matching tests. About a
half-day of focused work.

**Status: complete (commits `1b291da`, `32252fd`, `96f82b9`, `b354d1c`
on `dev`).** All five items shipped with 12 new `JuicerBuildSpec`
cases (11 → 23 tests); JVM, JS, and Native all compile. Implementation
notes worth carrying into Tier 2:

- The render loop now drives from a `List[(ContentFile, Map[String, Any])]`
  pair list rather than `Map[ContentFile, Map]`. Case-class equality on
  a `var` field is a footgun even when it doesn't bite immediately.
- `htmlDir` (when set) is *stripped* from URLs; it's a filesystem-only
  convenience for keeping static assets alongside rendered content.
- `<!--more-->` is parsed against the source pre-preprocess; the
  preprocessor only touches `[= ... =]` shortcodes, so the marker
  passes through unaffected.
- Auto-summary skips leading headings via
  `doc.children.collectFirst { case p: Paragraph => p }`. This is the
  shape Hugo authors are used to (h1 page title above the lead).

### 1. `site.pages` page list and map

**What.** Add `pages: List[Page]` and `pagesByPath: Map[String, Page]` to
the site-rendering context, where `Page` is a small record exposing the
fields templates actually need (`title`, `path`, `permalink`, `summary`,
plus the existing `frontmatter` map and `toc`). Templates can do
`{{ for p <- .site.pages }}` to build "all pages" lists, search
indexes, related-page sections, etc.

**Where.** The data already exists in `App.build` as `site.content` /
`site.map` (see `Process.scala`). Wire it through into `sitedata` after
the existing `+ ("toc" -> sitetoc.toList)` line in
`App.scala`.

**Test.** New `JuicerBuildSpec` case: a site with two content files;
template iterates `.site.pages` and asserts both titles appear.

### 2. `.page.permalink` / `.page.relPermalink` / `.page.url`

**What.** Each page in the rendering context exposes its own URLs as
proper fields, not a thing templates compute by hand. `permalink` is
the absolute URL (`baseURL.base + path`); `relPermalink` is the
site-relative path (`baseURL.path + page.path`); `url` is `relPermalink`
without the baseURL prefix.

**Where.** Compute once in `App.build` per-page (alongside `pagedata`
construction). Keep `relURL`/`absURL` template helpers around — they're
useful for arbitrary URLs, not just pages.

**Test.** Two cases: at site root, and in a subdirectory; assert each
of the three fields.

### 3. `draft: true` skip

**What.** Frontmatter flag. Drafts are skipped at build time unless
the CLI passes `--drafts`.

**Where.** `Args` adds `drafts: Boolean = false`. `Main.scala` opt.
`Process.scala` filters: when scanning `content/`, skip files whose
parsed frontmatter has `draft: true` and `drafts == false`.

**Test.** Three cases: draft skipped by default; draft included with
`--drafts`; non-draft never skipped.

### 4. Sitemap.xml + 404.html

**Sitemap.** A new pass after the main render walks `site.pages`,
emits a standard `<urlset>` XML to `dst1 / "sitemap.xml"`. Format is
known and short. ~25 lines.

**404.** Look for a layout named `404.html` in `layouts/`; if present,
render it once with site context only (no page) and write to
`dst1 / "404.html"`. Static hosts (GitHub Pages, Netlify) pick it up
automatically. ~15 lines.

**Tests.** Sitemap: assert `<loc>` entries for each non-draft page.
404: assert the rendered file exists when the layout is provided,
absent when not.

### 5. `.page.summary`

**What.** Extracted in this priority order:

1. `summary` field in frontmatter (explicit caller wins).
2. Content up to the first `<!--more-->` HTML comment in the markdown
   source (Hugo convention).
3. Plain text of the first paragraph, capped at ~30 words.

Exposed as `.page.summary` on the rendering context.

**Where.** Compute alongside `pagedata` in `App.build`. Use the new
public `markdown.plainText` helper for option 3.

**Test.** Three cases, one per option.

## Tier 2 — bigger but bounded; one at a time

### 6. Live reload in `serve` (JVM-only)

**What.** When `serve` is running, watch the source tree; on file
changes, rebuild incrementally and inject a tiny WebSocket script into
the served HTML so connected browsers refresh.

**Cost.** ~150–200 LOC across `jvm/src/main/scala/.../serve.scala` plus
a new `LiveReloadHandler`. Uses `java.nio.file.WatchService` (JVM-only,
which is fine — `serve` already is) and `com.sun.net.httpserver` for
the WebSocket-or-SSE channel.

**Where.** New file in jvm/. The shared `App.run` doesn't change; the
JVM `serve(...)` overload picks up a new `liveReload: Boolean = false`
argument bound to a CLI flag.

**Test.** Hard to integration-test cleanly without a browser; cover by
unit-testing the watcher abstraction and the HTML-injection helper.
Manual smoke-test in the README.

### 7. Render hooks for links / images / code blocks

**What.** Today juicer rewrites link destinations via an AST pass in
`App.scala` (`transformLinks`). Promote that to a template-callable
hook: a layout can declare `{{ define linkRenderHook }} ... {{ end }}`
and juicer will walk the AST, calling that template per `Link` /
`Image` / heading. Hugo style.

**Cost.** Some surgery in `App.build` and a new entry in
`squiggly.TemplateRenderer.blocks`. ~80 LOC.

**Test.** A custom link hook that wraps every link in `<span class="link">`,
verified against rendered output.

### 8. RSS / Atom feed

**What.** Per-section `feed.xml`. The default home `_index.md` plus
each section's `_index.md` get a sibling `feed.xml` listing recent
pages of that section.

**Cost.** A `feed.xml` layout (templated by site author or shipped as
a built-in default), plus another pass in `App.build`. ~50 LOC plus
a default layout file.

**Test.** Build a site with three pages, assert the feed has three
`<entry>` items in date-descending order.

### 9. Section list pages

**What.** A "section" is a directory under `content/` with an
`_index.md`. Currently those pages render their own content but have
no idea about the pages under them. Expose `.section.pages` (the
direct-child pages) on the section's `_index.md` rendering context,
so the section page can render an auto list of children.

**Cost.** ~50 LOC in `App.build` to compute the parent/child
relationship. The relationship was already implicit in `site.content`
ordering; this just surfaces it.

**Test.** Build a site with a section + 3 child pages; assert the
section index renders all three.

### 10. i18n / multi-language sites

**What.** A site can ship the same content in multiple languages, with
language-aware URLs and a per-language navigation. Default Hugo-style
layout:

```
content/
├── en/
│   ├── _index.md
│   └── guide/installation.md
└── fr/
    ├── _index.md
    └── guide/installation.md
```

Site config declares the language list:

```toml
defaultLanguage = "en"
languages       = ["en", "fr"]
```

URLs prepend the language code (`/en/...`, `/fr/...`); the default
language can optionally be served without a prefix (`languageInRootURL = false`).
The page-rendering context exposes `.page.lang` and `.page.translations`
(siblings in other languages, for a language-switcher widget). Plus a
`.site.lang` for the current render pass. UI strings (button labels,
"Read more", etc.) live in `i18n/<lang>.toml` and are looked up via a
new `{{ i18n 'key' }}` template helper.

**Why this matters here specifically.** Quebec's Charter of the French
Language requires French versions of a lot of public-facing content.
A juicer-hosted docs / blog / business site for a Quebec entity needs
to be able to ship both EN and FR side-by-side without forcing every
user globally into multi-language complexity (single-language sites
stay zero-config).

**Cost.** Bigger than the other Tier 2 items — ~250–350 LOC across
`Process.scala` (a content-tree walk per language), `App.scala`
(per-language render passes + cross-link generation in
`.page.translations`), and URL handling (the URL math throughout
needs to know about the language prefix). Plus a small `i18n.scala`
for translation lookup, plus sitemap updates (language-prefixed URLs
+ `<xhtml:link rel="alternate" hreflang="...">` per page).

**Where.** Process: detect language directories under `content/` if
`languages` is configured; otherwise treat content as monolingual
(no behavior change for existing sites). App: outer loop over
languages, inner loops as today. URLs: a `LangPrefix` derived once
from `Args` + `confdata` and threaded through everywhere paths are
formed. Translation lookup: `i18nLookup: (String, String) => Option[String]`
plus a registered template builtin.

**Test.** Build a bilingual site (EN + FR, same `_index.md` in each);
assert both `<dst>/en/index.html` and `<dst>/fr/index.html` exist;
assert the FR page's `.page.translations` includes the EN sibling and
vice versa; assert the sitemap has `<loc>` entries for both.

### 11. Themes

**What.** A theme is a juicer-shaped directory (`layouts/`, `partials/`,
`shortcodes/`, `static/`, optionally `assets/`) that's layered *underneath*
the site's own files of the same name. Site config selects a theme:

```toml
theme = "minty"     # → look under themes/minty/
```

Lookup order during the build:

1. `site.layouts/_default/file.html` (site override)
2. `site.themes/<theme>/layouts/_default/file.html` (theme default)

Same precedence for partials, shortcodes, and static. Multiple themes
can chain: `theme = ["minty", "base"]` — earlier entries override later.

**How users install a theme.** Initially, the simplest approach: a
theme is a directory under `themes/<name>/` in the site repo. Users
add it as a git submodule, an sbt-style dep (when juicer is a library
target someone is consuming), or a plain directory copy. Anything
fancier (a `themes` registry, `juicer mod get`, etc.) is Tier 3.

**Where.** `Process.scala` already classifies files by directory
(`content/`, `layouts/`, etc.). Add a second pass over `themes/<name>/`
that contributes the same maps with **site entries already populated
winning by key** — `layoutTemplates.getOrElseUpdate(key, themeFile)`.
Static files: copy the theme's first, then the site's overwrites.
~100 LOC.

**Test.** Build a site that overrides one layout from a theme
(theme provides `file.html` and `folder.html`; site provides a custom
`folder.html`). Assert the site's `folder.html` wins; assert the
theme's `file.html` ships through unchanged.

## Tier 3 — explicitly deferred

These are good ideas. They are not Real Soon Now ideas. Don't pick
them up unless a concrete consumer needs them.

- **Page bundles** (page-as-directory + colocated assets). Hugo's
  killer feature for media-rich content. Real value only when there
  *is* media; current users can use `static/` and live.
- **Taxonomies** (tags / categories with their own auto-generated list
  pages and term pages). Whole new content type. Skip until at least
  one user blogs with juicer.
- **Multiple output formats** (HTML + JSON + AMP from the same content).
  Hugo's `outputs` config.
- **Asset pipeline (Hugo Pipes)** — SCSS, JS bundling, fingerprinting,
  minification. The JS ecosystem (Vite, esbuild) already does this
  well; recommend users build assets there and `static/` the output.
- **Modules / theme registries** — formal distribution / discovery
  mechanism for shared themes (Hugo Modules style). Tier 2's plain
  `themes/<name>/` directory is enough to cover the share-a-theme
  use case for now. Revisit if multiple users start exchanging themes.

## Notes for whoever picks up Tier 1

- The integration test file (`shared/src/test/scala/io/github/edadma/juicer/JuicerBuildSpec.scala`)
  has the `writeAt` / `out` / `build` helpers ready; new tests follow
  the same pattern as the 11 existing ones.
- The **page-rendering context** is constructed in
  `App.build` near the end (search for `val pagedata = Map(`). Most
  Tier 1 items add fields here.
- The **site-rendering context** (`sitedata`) is constructed slightly
  earlier (search for `val sitedata = confdata + …`). `site.pages`
  goes there.
- juicer's runtime data uses **squiggly's any-data shape**: `Map[String, Any]`
  / `List[Any]` / `String` / `BigDecimal` / `Boolean` / `null`. Page
  records get serialized into this shape, not exposed as case classes.
- `page.summary` and `.site.pages` should both be **lazy** so unused
  templates don't pay for them.
- Don't add fields to `ContentFile` willy-nilly — add a `Page`
  record that the rendering layer materializes from `ContentFile`,
  keeping the parsing layer minimal.
