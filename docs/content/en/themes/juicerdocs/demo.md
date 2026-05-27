---
title: Demo site
summary: This site (juicer.run) IS the juicerdocs demo — every page you've been reading is rendered with the theme.
weight: 20
---

Unlike the other six bundled themes — each of which has its own
demo site under `docs/demos/<theme>/` that the deploy pipeline builds
into `/themes/<theme>/demo/` — **juicerdocs has no separate demo
source.** This site, [juicer.run](/), is built with juicerdocs. Every
page you've been reading is the live theme.

## What this site demonstrates

The juicerdocs theme rendering against real-world content — over a
hundred markdown pages, deep section trees, all 17 shortcodes in
use, search backed by `/search.json`, the right-rail "On this page"
TOC, mobile sidebar overlay, light + dark mode driven by the topbar
toggle, server-side syntax highlighting in code blocks.

[← Back to the home page](/) and read around the docs to see every
juicerdocs feature in context. Or, if you want a quick tour:

- **[Hero treatment + feature-card grid](/)** — the home page.
- **[Sidebar nav with arbitrary nesting depth](/themes/)** — the
  Themes section ships three levels (Themes → juicerblog →
  Configuration) and the recursive partial supports more.
- **[Right-rail TOC](/concepts/blogging/)** — long pages that opt
  into `toc: true` get the auto-highlighting "On this page" rail.
- **[Code blocks with copy buttons + language chips](/getting-started/quickstart/)** —
  the "Copy" button flashes "Copied" on success.
- **[Shortcodes in use](/reference/shortcodes/)** — the catalogue
  page renders every shortcode against itself, so the styling is
  visible inline.

## Use it as a template

The source for juicer.run lives at `docs/` in the [juicer
repository](https://github.com/edadma/juicer). The shape:

```
docs/
├── site.toml             # site config
├── content/              # this entire docs tree
├── themes/juicerdocs/    # the theme itself
└── demos/                # per-theme demo sources (built separately)
```

Strip the existing `content/` tree, replace it with your own, and
point `[juicerdocs] logo` at your wordmark. The companion
[Configuration](../configuration/) reference catalogues every
site.toml key, palette token, and per-page frontmatter knob the theme
reads.
