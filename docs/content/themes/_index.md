---
title: Themes
summary: The nine themes that ship in the box — what each one is for and which to pick.
weight: 40
---

Juicer bundles nine themes in the `docs/themes/` directory of the
repository. They're all hand-rolled CSS, zero-framework, light/dark
aware, and tuned for a specific shape of site. A clone of juicer
comes with all of them; to use one on your own site, copy the
directory or pull it via `juicer theme add` (each theme's section
shows the exact command).

The shared theming model — palette tokens, `customCSS`, the
file-replacement override pattern — lives in
[Reference → Theming](/reference/theming/) and applies identically to
every bundled theme.

## Which one do I want?

| If your site is mostly…                                  | Use…                                              |
|----------------------------------------------------------|---------------------------------------------------|
| Technical docs — sidebar nav, on-this-page rail, search  | [juicerdocs](/themes/juicerdocs/)                 |
| A blog — dated posts, author bylines, tag/year archives  | [juicerblog](/themes/juicerblog/)                 |
| Long-form prose / study notes — essays, footnotes, math  | [juicerstudy](/themes/juicerstudy/)               |
| A product / SaaS landing page                            | [juicerlanding](/themes/juicerlanding/)           |
| An image-first portfolio or case-study site              | [juicerportfolio](/themes/juicerportfolio/)       |
| A photo-first gallery — the work *is* the photos         | [juicergallery](/themes/juicergallery/)           |
| A backlink-driven wiki / Zettelkasten                    | [juicerwiki](/themes/juicerwiki/)                 |
| A café, restaurant, or small-business site               | [juicercafe](/themes/juicercafe/)                 |
| A church / ministry / faith-community site               | [juicerchurch](/themes/juicerchurch/)             |

## What each section covers

Every theme section in this group documents the same three layers:

- **Overview** — what the theme ships with, when to pick it, when to
  pick something else.
- **Configuration** — every `site.toml` key the theme reads, every
  `[<theme>]` palette token, every per-page frontmatter knob.
- **Demo site** — a live demo rendered with the theme itself. The
  deploy pipeline builds each demo (sources live under
  `docs/demos/<theme>/`) into `/themes/<theme>/demo/`, so the leaf
  serves the rendered demo at that URL. The exception is juicerdocs
  — this docs site is built with juicerdocs, so the juicerdocs Demo
  site leaf points back at `/`.
