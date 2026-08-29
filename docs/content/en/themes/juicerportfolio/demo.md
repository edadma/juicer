---
title: Demo site
summary: Atelier Hahn — a fictional industrial-design studio in Lisbon. Project grid with category filter chips, per-project pages with hero, sticky meta sidebar, gallery, and prev/next nav.
weight: 20
---

This page is the placeholder for the live juicerportfolio demo. When
the docs site is built and deployed, this URL serves the actual demo
rendered with the juicerportfolio theme — a separate juicer build from
`docs/demos/juicerportfolio/` is dropped on top of the docs render at
the same path. See [`bin/build-demos.sh`](https://github.com/edadma/juicer/blob/dev/bin/build-demos.sh)
for the orchestration.

## What the demo exercises

A fictional studio — "Atelier Hahn", an industrial-design studio run
by Mira Hahn in Lisbon — with content exercising every juicerportfolio
layout:

- **Project grid** on the home page — 2-up cards, each a hero image
  + title + tagline.
- **Filter chips** above the grid driven by `[[workCategories]]` in
  `site.toml`. Click a chip → JS toggles a `data-active-filter`
  attribute on the grid root. No framework, no router, no fetch.
- **Per-project pages** with hero image, sticky right-side meta
  sidebar (year / role / client / materials / external link), writeup
  body, optional `[[gallery]]` figure grid, prev/next nav, "All work"
  back link.
- **Materials chips** — `tools: [...]` in frontmatter renders as small
  monospace-tabular chips in the meta sidebar.
- **`/work/_index.md` archive** — `folder.html` doubles as a full
  text-list of every project, complementary to the home grid's curated
  image-led view.
- **Prose pages** — `/about/`, `/contact/`, `/cv/`, `/press/` — using
  the generic `file.html` layout with a constrained measure for
  comfortable reading.

## Preview locally

```bash
sbt 'juicerJVM/run serve -s docs/demos/juicerportfolio -L'
```

The `-L` flag enables live reload — touch any markdown file under
`docs/demos/juicerportfolio/content/` and the open browser tabs reload automatically.

## Use it as a template

Copy `docs/demos/juicerportfolio/` into your own project as a starting
point. Drop your hero images into `static/img/`, write project pages
in `content/work/`, retune `[[workCategories]]` and the palette under
`[juicerportfolio]`. The companion
[Configuration](../configuration/) reference catalogues every
site.toml key, palette token, and per-project frontmatter knob the
theme reads.
