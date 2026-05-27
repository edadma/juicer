---
title: Demo site
summary: Marginalia — a slow reader's notebook. Two studies ("On Reading", "On Time"), nested sections, KaTeX math, footnotes, scholarly slate-and-amber palette.
weight: 20
---

This page is the placeholder for the live juicerstudy demo. When the
docs site is built and deployed, this URL serves the actual demo
rendered with the juicerstudy theme — a separate juicer build from
`docs/demos/juicerstudy/` is dropped on top of the docs render at the
same path. See [`bin/build-demos.sh`](https://github.com/edadma/juicer/blob/dev/bin/build-demos.sh)
for the orchestration.

## What the demo exercises

A fictional reader's notebook ("Marginalia") populated with two short
studies and the conventions a long-form prose site actually needs:

- **Two authored sections** — *On Reading* and *On Time* — each with
  an `_index.md` that reads as editorial introduction, not as an
  auto-cataloged TOC.
- **Nested subsections** — `on-reading/practices/` to exercise the
  arbitrarily-deep section tree.
- **KaTeX math** — both inline (`$10^9$`) and display (`$$ … $$`)
  forms; `juicerstudy.math = true` in `site.toml` opts in.
- **Right-rail "On this page" TOC** on the pages that set
  `toc: true` in frontmatter.
- **Heading-shift = 0** — page `<h1>` comes from frontmatter title,
  markdown bodies start at `##`.
- **Slate-and-amber palette** retuned via `[juicerstudy]` overrides —
  intentionally distinct from juicerdocs's defaults so the two themes
  don't read as the same site.
- **Read-time hints** — `minutes:` frontmatter on each page.

## Preview locally

```bash
sbt 'juicerJVM/run serve -s docs/demos/juicerstudy -L'
```

The `-L` flag enables live reload — touch any markdown file under
`docs/demos/juicerstudy/content/` and the open browser tabs reload
via SSE.

## Use it as a template

Copy `docs/demos/juicerstudy/` into your own project as a starting
point. Replace the prose, retune the palette under `[juicerstudy]`,
turn `math` off if you don't need it. The companion
[Configuration](../configuration/) reference catalogues every
site.toml key, palette / typography / sizing token, and per-page
frontmatter knob the theme reads.
