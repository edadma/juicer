---
title: Themes
summary: Drop-in skins that ship layouts, partials, shortcodes, and static assets.
weight: 30
---

A juicer theme is a directory under `themes/<name>/` with the same shape as a site:

[= filetree =]
themes/juicerdocs/
├── layouts/
│   └── _default/
│       ├── baseof.html
│       ├── file.html
│       └── folder.html
├── partials/
│   └── ...
├── shortcodes/
│   └── ...
└── static/
    └── ...
[= /filetree =]

Activate it from `site.toml`:

```toml
theme = "juicerdocs"
```

That's it. Juicer scans the theme directory before the site directory and merges the two — site files **win on key collisions**, so you can override any individual layout, partial, shortcode, or static asset without forking the theme.

## Theme chains

The `theme` key also accepts an array, in which case earlier entries override later ones:

```toml
theme = ["my-overrides", "juicerdocs"]
```

Now `my-overrides/` wins over `juicerdocs/`, which wins over the site itself? No — site always wins. Order is **site > my-overrides > juicerdocs**. This is useful when you want to reuse most of `juicerdocs` but customize, say, the topbar with a tiny overlay theme of your own.

[= tip =]
Theme directories live under `themes/` because that's the `themeDir` config key's default. Set `themeDir = ""` if you want themes to live at the site root, or any other path that fits your repo layout.
[= /tip =]

## What a theme can ship

| Subdirectory | What |
|--------------|------|
| `layouts/`   | Templates for the various page kinds (`_default/file.html`, etc.) |
| `partials/`  | Reusable template fragments — called via `{{ partial 'name' . }}` |
| `shortcodes/`| Markdown-callable templates — invoked via `\[= name =]…\[= /name =]` |
| `static/`    | Files copied verbatim into the output tree |

Themes do **not** ship content (no `content/` directory inside a theme is ever read), nor do they get top-level "other templates" rendered (HTML templates outside of those directories are always relative to the user's site).

## Picking the right primitive

[= tabs =]
[= tab "Partial" =]
A *partial* is a reusable HTML fragment called from another template. It receives whatever data context you pass in.

```squiggly
{{ partial 'topbar' . }}
{{ partial 'topbar' .page }}
```

Use partials for: page chrome, repeated UI elements, anything you'd factor out of HTML.
[= /tab =]
[= tab "Shortcode" =]
A *shortcode* is a partial called from **inside markdown content**, via the `\[= name =]` syntax. Authors write content with shortcodes inline:

```markdown
\[= note =]
This is a note callout.
\[= /note =]
```

Use shortcodes for: callouts, tabbed examples, embedded media, anything that's content but needs richer HTML than markdown alone provides.
[= /tab =]
[= tab "Layout" =]
A *layout* is the top-level template for a page kind. Juicer picks the right one by name and folder lookup.

Use layouts for: the actual `<html>` skeleton and the per-page-kind structural rules.
[= /tab =]
[= /tabs =]

## Bundled themes

Seven themes ship in the juicer repo, each tuned for a different shape of
site. Pick the closest match and override individual files from your own
`layouts/` or `static/`:

| Theme              | Where it lives                  | What it's for |
|--------------------|---------------------------------|----------------|
| `juicerdocs`       | `docs/themes/juicerdocs/`       | Documentation sites — sidebar nav, "On this page" rail, 16 callout shortcodes. Powers this site. |
| `juicerblog`       | `docs/themes/juicerblog/`       | Blogs — post lists with pagination, tag/category/year archives, series nav, author bylines, server-side syntax highlighting. |
| `juicerstudy`      | `docs/themes/juicerstudy/`      | Long-form prose — essays, study notes, reading journals, lecture notes. Serif reading column, optional KaTeX math, undated by default. |
| `juicerlanding`    | `docs/themes/juicerlanding/`    | Product / SaaS landing pages — single-page section stack (hero, features, pricing, testimonials, FAQ) driven entirely from `site.toml`. |
| `juicerportfolio`  | `docs/themes/juicerportfolio/`  | Designer / studio portfolios — image-first project grid, per-project hero + meta sidebar + gallery. |
| `juicercafe`       | `docs/themes/juicercafe/`       | Cafés, small businesses, restaurants — hours widget, menu sections, photo albums, events list. |
| `juicerchurch`     | `docs/themes/juicerchurch/`     | Churches, ministries — sermon archive, recurring-event calendar grid, ministries section, photo albums. |

Each theme has its own section under [Themes](/themes/) (linked from the
sidebar) with a "what's in the box" overview and a configuration page
that catalogues every `[juicerXxx]` token, top-level chrome key, and
per-page frontmatter convention.

A working demo site lives under `docs/demos/` for every bundled theme
— for instance, `docs/demos/juicerblog/` mirrors `juicerblog`'s feature
matrix, and `docs/demos/juicerlanding/` exercises every section block in
`juicerlanding`. The deploy pipeline builds each demo into
`/themes/<theme>/demo/` so the sidebar's "Demo site" leaf for each theme
serves it live. Start from the closest demo and edit downward:

```bash
sbt 'juicerJVM/run serve -s docs/demos/juicerblog -L'
sbt 'juicerJVM/run serve -s docs/demos/juicerportfolio -L'
```

## Building your own

Reading through any bundled theme end-to-end is a quick tour of the
conventions. `juicerdocs` is the most complete demo of every theme
primitive (layouts, partials, shortcodes, static); `juicerblog` is the
smallest theme that touches the blog-specific engine features;
`juicerlanding` is the cleanest demo of a frontmatter-driven page that
assembles itself from `site.toml` blocks.

[= github "edadma/juicer" /=]
