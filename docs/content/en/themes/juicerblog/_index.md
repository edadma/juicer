---
title: juicerblog
summary: The opinionated blog theme that ships with juicer — exercises every Phase-1 + Phase-2 blog feature, with hand-coded CSS, server-side syntax highlighting, and zero JavaScript framework.
weight: 50
---

`juicerblog` is the blog counterpart to [juicerdocs](/juicerdocs/). It's bundled with the juicer repo at `docs/themes/juicerblog/`, so a clone of juicer comes with it out of the box. To use it on your own site, copy the directory or run:

```bash
juicer theme add https://github.com/edadma/juicer.git -n juicerblog
```

…and set `theme = "juicerblog"` in your `site.toml`. See the [Demo site](./demo/) page for a live demo exercising every feature on this page.

## What's in the box

- Warm-neutral palette (cream paper, deep ink, amber brand, indigo links) tuned for prose readability.
- Light + dark mode driven by `[data-theme="dark"]` overrides on CSS custom properties.
- **Server-side syntax highlighting** via the bundled `highlighter` library — TextMate grammars, no JavaScript highlighter, no FOUC, no dark-mode flicker. Drop `<src>/grammars/<lang>.tmLanguage.json` files in and the colors light up.
- **Author bylines** — avatars (overlapping when multi-author), serial-comma name joining ("By Ed, Alice and Ben"), each name linked to the per-author archive.
- **Series support** — progress badge ("Reading on screens · Part 2 of 3") above the body and an "In this series" sidebar at the bottom.
- **Reading-progress hairline** — 2px brand bar fixed to the top of the viewport, scaled left-to-right as the reader scrolls a single dated post.
- **Code-block enhancements** — small uppercase language chip in the top-left, "Copy" button in the top-right that flashes "Copied" on click.
- **Hover polish** — prose-link underlines grow from 1px → 2px on hover; subtle, WCAG-safe.
- A homepage variant (`layout: home`) distinct from the generic folder layout — hero + curated recent strip + tag cloud, with a link out to a flat archive.
- A site-wide archive page (`layout: archive`) listing every dated post grouped by year.
- Empty-state messaging on every archive layout when a tag/author/month has no posts.
- A static-page convention (`static: true` frontmatter) that opts a page out of post chrome — used for about/colophon/contact pages that should look like clean essays, not posts.

## When to use it (and when not to)

Use juicerblog if you want a Hugo-style blog theme that boots quickly, exercises every blog-shaped engine feature out of the box, and reads as long-form prose. It's tuned for a personal or small-publication blog with one to a handful of authors.

Pick something else if you want:

- A docs theme — juicerblog has no left-rail navigation, no "On this page" right rail, no shortcode set tuned for technical docs. That's [juicerdocs](/themes/juicerdocs/).
- A landing-page theme — the hero treatment is fine for a blog home, not for a marketing page.
- A magazine layout with multi-column post grids — the prose column is fixed at ~38rem for readability.

## What this section covers

The pages below document the configuration knobs juicerblog reads from your `site.toml`, the frontmatter conventions specific to the theme, and the directory layouts that turn engine features (syntax highlighting, author archives, etc.) on. Most of the engine-feature reference for a blog (tags, pagination, dates, series, authors, aliases, OpenGraph) lives at [Blogging features](/concepts/blogging/) — that page is theme-agnostic and is the prerequisite reading. This section assumes you've read it.
