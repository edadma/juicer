---
title: Troubleshooting
summary: Common things that go wrong with a fresh juicer install — and the one-line fix for each.
weight: 30
---

This page is organized by **symptom**, not by feature. If something isn't working, scan the headings for what you're seeing on screen, then read the cause + fix below.

## Build / install

### `sbt 'juicerJVM/run --help'` errors out before printing the help banner

**Cause.** Either sbt itself can't start (Java version too old; sbt couldn't fetch its plugins) or the juicer build is broken (uncommitted edits to `build.sbt`, missing dependency).

**Fix.** Confirm `java -version` reports JDK 17 or newer; confirm `sbt sbtVersion` reports 1.12.x. Then from the repo root:

```bash
sbt clean compile
```

If `compile` succeeds but `run` doesn't, the issue is dependency resolution — try `sbt update` to re-fetch from Maven Central.

### `not a readable directory: <src>`

**Cause.** The `-s <src>` path doesn't exist, isn't a directory, or you don't have read permission. Often a typo or running from the wrong cwd.

**Fix.** Use an absolute path or `pwd && ls <src>` to confirm the directory exists from where you're running sbt. Most juicer commands run relative to the cwd, so `sbt 'juicerJVM/run build -s examples/blog-site'` only works from the repo root.

### `unknown base configuration: <name>`

**Cause.** The `--config` flag references a baseline that doesn't exist. Valid values are `simple`, `standard` (default), and `norme`.

**Fix.** Drop the flag — `standard` is what 99% of sites want. If you need a different shape, see [Configuration](/reference/config/).

### `compile` errors after a `git pull`

**Cause.** A dependency bumped to a version that needs `~/.ivy2/cache` to refresh, or a Scala source file changed in a way sbt's incremental compile didn't catch.

**Fix.** `sbt clean` then re-run. If a dep change is the issue, `sbt update` first picks up the new artifacts before `compile` rebuilds.

## Layouts

### `layout '<X>' not found for rendering '<page>'` in verbose output

**Cause.** Either the theme doesn't ship a layout with that name, or the `layout:` frontmatter on a page references one that isn't there. Most often it's a typo (`layout: hime` instead of `layout: home`).

**Fix.** Check the theme's `layouts/_default/` directory for the file you're naming. Themes can opt out of certain layouts (juicerblog ships `home.html` and `archive.html`; juicerdocs doesn't). The build doesn't fail — it falls back to `file.html` / `folder.html` — but the page renders with default chrome instead of your intended layout.

### A page renders with the wrong layout

**Cause.** Frontmatter `layout: foo` is being honored when you didn't mean to set it, or vice versa.

**Fix.** Inspect the page's frontmatter — `layout:` is the override; remove it to fall back to the theme default. See [Content files](/concepts/content-files/) for the layout-selection rules.

### A `_index.md` renders as a single post instead of a section

**Cause.** The file isn't named `_index.md` (note the underscore) or sits in the wrong directory. Section pages MUST be named exactly `_index.md` and live at the top of their section directory.

**Fix.** Rename `index.md` → `_index.md` and confirm it's in the section root, not nested in a subdirectory.

## Themes

### Theme renders with no styling at all

**Cause.** The theme's CSS file isn't being copied to the output, OR the `<link rel=stylesheet>` references a path that doesn't resolve.

**Fix.** First check that the theme's `static/<theme>.css` was copied — `ls <dst>/<theme>.css`. If it's not there, your `themeDir` / `theme` keys in `site.toml` aren't pointing at the right directory. Run with `-v` and look for a `theme: …` line; the path it logs is what juicer is actually using.

### `theme add <git-url>` fails with "could not derive theme name"

**Cause.** The git URL doesn't end in something useful (`.git`, `repo-name`). Common with custom git hosts or odd URL forms.

**Fix.** Pass `-n <name>` explicitly: `juicer theme add https://example.com/weird-url -n my-theme`.

### A new theme doesn't show up after `theme add`

**Cause.** You forgot to update `site.toml` — `theme add` only clones the directory; it doesn't change the active theme.

**Fix.** Add `theme = "<name>"` to `site.toml`. Verify with `juicer config -s <src>` — the active `theme` value prints near the top.

### Site assets (logo, favicon) override theme assets but they don't appear

**Cause.** Site files DO win over theme files, but only when the file lives at the SAME relative path. A site `<src>/static/logo.png` overrides a theme `themes/X/static/logo.png` — but `<src>/img/logo.png` doesn't, because the theme expects `static/`.

**Fix.** Mirror the theme's directory structure exactly. See [Themes](/concepts/themes/) for the overlay rules.

## Blog features

### Code blocks aren't highlighted

**Cause.** No `<src>/grammars/` directory exists, or the `*.tmLanguage.json` file inside it doesn't match the language tag on your fence. A block opened with ` ```scala ` looks for `grammars/scala.tmLanguage.json` exactly — case-sensitive.

**Fix.** Create `<src>/grammars/`, drop in the grammar JSON for each language you use. See [juicerblog → Configuration](/themes/juicerblog/configuration/#syntax-highlighting) for the full setup. Run with `-v` and look for the `highlighter: N grammar(s) loaded` line — if you don't see it, the directory is missing or empty.

### Highlighted code has no colors

**Cause.** The grammar loaded successfully but your theme has no CSS for the `.hl-*` classes the highlighter emits.

**Fix.** Themes that ship a palette (juicerblog, juicerdocs) handle this automatically. For a hand-rolled theme, copy the `.hl-keyword` / `.hl-string` / `.hl-comment` / etc. block from `docs/themes/juicerblog/static/juicerblog.css` and adapt the colors.

### Date archives `/2024/` etc. aren't being emitted

**Cause.** Either `dateArchives = true` isn't set in `site.toml`, OR the theme doesn't ship `_default/date-year.html` and `date-month.html` (missing layout = silent skip), OR no posts have parseable explicit `date:` frontmatter.

**Fix.** Confirm all three:

1. `grep dateArchives <src>/site.toml` returns `dateArchives = true`.
2. The theme has the layouts: `ls <theme>/layouts/_default/date-*.html`.
3. At least one post has `date: 2024-MM-DD` (or similar) in its frontmatter — pages whose date came from the filesystem mtime fallback are intentionally excluded.

### Tag pages don't include a post that has the tag

**Cause.** Either the post has `static: true` frontmatter (filtered out of all post listings), or the tag's slug doesn't match what you typed in the URL. Tags are slugified (lowercased, ASCII-folded, runs of non-alphanumeric collapsed to `-`).

**Fix.** Visit `/tags/` to see the canonical slug juicer derived. If your post has `static: true`, that's why — see [juicerblog → Configuration](/themes/juicerblog/configuration/#per-page-frontmatter).

### Author archive page is empty even though the author has posts

**Cause.** The frontmatter `author:` (or `authors:`) value doesn't match any `id` in the `[[authors]]` registry. juicer falls back to a stub `{id: <typo>}` so templates don't NPE, but the archive is empty.

**Fix.** Compare `grep "^author:" content/*.md` output against the `id =` values in your `site.toml` `[[authors]]` blocks — typos are the usual culprit.

### Permalinks `[permalinks]` aren't applying

**Cause.** The section name in `[permalinks]` doesn't match the content directory. `[permalinks] posts = ":year/:slug/"` only applies to pages under `content/posts/` — posts at the root of `content/` are unaffected.

**Fix.** Either move posts under `content/posts/` (recommended for blogs) or list the actual section names you have in `[permalinks]`.

### Aliases redirect to the wrong place

**Cause.** The `aliases:` frontmatter list contains the wrong shape — usually missing the leading `/` or trailing `/`.

**Fix.** Each alias is a URL path with both leading and trailing slash: `aliases: [/old-name/, /even-older/]`. Check the rendered alias HTML at `<dst>/<alias>/index.html` to see what redirect URL got baked in.

## Serve / live reload

### `juicer serve` fails with "address already in use"

**Cause.** Another process (often a previous juicer serve you forgot to stop) holds the port.

**Fix.** Pass a different port: `-p 8089`. juicer's bind-with-retry loop also scans 20 ports upward from the default, so `-p 8088` will find the next free one if 8088 is taken — watch for the actual port in the startup banner.

### Live-reload doesn't fire when I save a markdown file

**Cause.** The `-L` (or `--live-reload`) flag wasn't passed, OR your browser stopped polling the live-reload endpoint, OR the file you saved is outside the watched directory.

**Fix.** Confirm the startup banner says `live reload: enabled`. Open browser devtools → Network and reload the page; you should see a long-lived `fetch` to `/__juicer/wait?since=N` (the live-reload script is injected before `</body>` in every HTML response). Saves are debounced 150ms — don't expect saves faster than that to coalesce, but anything slower should reload within a second. If the page hasn't been opened with the dev server (e.g., a `file://` URL or a stale cached tab), the script never loaded and reloads never fire — open the page through `http://localhost:<port>/`.

### Browser shows old content even after a save

**Cause.** Browser cache holds the page even though juicer rebuilt it. juicer's `serve` sends `Cache-Control: no-cache` headers when `-L` is on, but a stale service worker or aggressive CDN proxy can override.

**Fix.** Hard reload (Cmd-Shift-R / Ctrl-Shift-R). If that doesn't fix it, devtools → Application → Service Workers → unregister.

## Templates

### `unknown variable: X` at build time

**Cause.** A `{{ X }}` reference points at a variable that isn't in scope at that point in rendering.

**Fix.** Most variables come from one of three sources: `.page.<...>` (per-page frontmatter + computed fields), `.site.<...>` (site config + global indexes), or local `:=` assignments. Check [Template data](/reference/template-data/) for the available shapes. A common gotcha inside `for` loops: `.foo` resolves against the current iterated element, not the outer scope — use `$.foo` to escape back to the root data.

### `not a number: <X> (java.lang.Y)` in template comparisons

**Cause.** Squiggly's relational ops (`< > <= >= = !=`) need both sides to be numeric. A boxed Java type can fail to convert if it's an unusual shape.

**Fix.** Bumped to squiggly 0.2.4? That release added boxed-Number coercion. Earlier versions tripped on `for x, i <- coll` indices. `cd <repo>; grep squiggly build.sbt` should show 0.2.4 or newer.

### Markdown body content doesn't render its `{{ for ... }}` loops

**Cause.** **Intentional**, not a bug. Squiggly templates inside a markdown body are HTML-escaped before rendering — they get treated as literal text. Only files in `layouts/` and `partials/` execute templates.

**Fix.** Move the loop into a layout or partial, and have the markdown call it via shortcode or by being short enough to live inside the layout directly.

## When all else fails

Run with `-v` (verbose) to see every step of the build — file walks, layout selection, what's being written where:

```bash
sbt 'juicerJVM/run build -s <src> -v'
```

If something's silently being skipped (missing layout, filtered page, etc.) the verbose output usually shows the line where it bailed. Paste the relevant slice into a [GitHub issue](https://github.com/edadma/juicer/issues) along with the smallest reproducer that surfaces it.
