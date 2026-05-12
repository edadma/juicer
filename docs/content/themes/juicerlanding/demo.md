---
title: Demo site
summary: Tally — a fictional privacy-first web-analytics product. Every juicerlanding section (hero, features, pricing, testimonials, FAQ, showcase, code, trust, footerColumns) is exercised against realistic SaaS copy.
weight: 20
---

This page is the placeholder for the live juicerlanding demo. When
the docs site is built and deployed, this URL serves the actual demo
rendered with the juicerlanding theme — a separate juicer build from
`docs/demos/juicerlanding/` is dropped on top of the docs render at
the same path. See [`bin/build-demos.sh`](https://github.com/edadma/juicer/blob/dev/bin/build-demos.sh)
for the orchestration.

## What the demo exercises

A fictional SaaS product — "Tally", privacy-first web analytics —
with `site.toml` populated so every section in the juicerlanding
stack renders:

- **Hero** — title, subtitle, primary + secondary CTAs, install
  one-liner code snippet.
- **Features grid** with icons.
- **`[[pricing]]` tiers** — three plans, with one row marked
  `featured = true` to get the badge + ring + scale-up treatment.
- **`[[testimonials]]`** — multi-customer quotes with avatars.
- **`[[faqs]]`** — native `<details>` accordion, no JS.
- **`[showcase]`** — featured-customer logos block.
- **`[code]`** — standalone code-snippet section.
- **`[[trust]]`** — security / compliance badges row.
- **`[[footerColumns]]`** — four-column footer with brand blurb
  auto-generated from `title` + `tagline`.
- **Secondary pages** — `/changelog/`, `/privacy/`, `/about/` — using
  the generic `file.html` prose layout.
- **404 page** that survives even if the topbar partial errors out.

## Preview locally

```bash
sbt 'juicerJVM/run serve -s docs/demos/juicerlanding -L'
```

The `-L` flag enables live reload — touch any markdown file under
`docs/demos/juicerlanding/content/` (or change `site.toml`) and the
open browser tabs reload via SSE.

## Use it as a template

Copy `docs/demos/juicerlanding/` into your own project as a starting
point. The home page assembles entirely from `site.toml` blocks, so
most of your edits land there — change the hero copy, the pricing
table, the FAQ entries. The companion
[Configuration](../configuration/) reference catalogues every section
block, frontmatter knob, and palette token the theme reads.
