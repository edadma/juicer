---
title: Demo site
summary: Stratus Coffee — a fictional café with hours widget, menu sections, events list with weekly recurrences, photo album, and the working-from-here pitch.
weight: 20
---

This page is the placeholder for the live juicercafe demo. When the
docs site is built and deployed, this URL serves the actual demo
rendered with the juicercafe theme — a separate juicer build from
`docs/demos/juicercafe/` is dropped on top of the docs render at the
same path. See [`bin/build-demos.sh`](https://github.com/edadma/juicer/blob/dev/bin/build-demos.sh)
for the orchestration.

## What the demo exercises

A fictional neighborhood café — "Stratus Coffee" — with content
populating every juicercafe layout:

- **Hours widget** — `[[hours]]` rows for each day, with "Open
  now"/"Closed" pill in the topbar driven by current time.
- **Menu sections** — multiple `layout: menu-section` pages
  composed under a `layout: menu` index.
- **Events list + per-event pages** — including weekly recurring
  events (`recurring: weekly` + `recurringDay:`).
- **Photo album** — site-wide grid pulling from every page's
  `photos:` frontmatter, sorted by date desc.
- **`/visit/` block** — address, city, state, zip, optional map URL
  surfaced in the footer on every page.
- **"We're working on it"** card layout for the pending-projects page.
- **Custom authors path** — `authorsPath = "/team/"` so per-author
  archives live under `/team/` instead of `/authors/`.
- **Multi-baker bylines** via the author registry.

## Preview locally

```bash
sbt 'juicerJVM/run serve -s docs/demos/juicercafe -L'
```

The `-L` flag enables live reload — touch any markdown file under
`docs/demos/juicercafe/content/` and the open browser tabs reload via
SSE.

## Use it as a template

Copy `docs/demos/juicercafe/` into your own project as a starting
point. Replace the hours table, the menu items, the photo URLs, the
address — the layouts handle the rest. The companion
[Configuration](../configuration/) reference catalogues every site.toml
key, palette token, and per-page frontmatter knob the theme reads.
