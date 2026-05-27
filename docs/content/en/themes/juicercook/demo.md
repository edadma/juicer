---
title: Demo site
summary: Elder & Ash — six recipes (sourdough, focaccia, ragù, dal, cookies, tarte tatin) showing the recipe card, hero image, step photos, and Recipe JSON-LD.
weight: 20
---

This page is the placeholder for the live juicercook demo. When the docs site is built and deployed, this URL serves the actual demo rendered with the juicercook theme.

## What the demo exercises

"Elder & Ash" — a small home-cooking site with six recipes split between breads, mains, and desserts:

- **Hero photo + recipe card** on every recipe page.
- **Step photos** on the sourdough recipe (shape, score, crumb cross-section).
- **schema.org Recipe JSON-LD** on every recipe — paste any recipe URL into the [Rich Results Test](https://search.google.com/test/rich-results) to see how Google parses it.
- **Print stylesheet** — File → Print on any recipe page strips chrome and prints clean.
- **Category chips** on the home page.

## Preview locally

```bash
sbt 'juicerJVM/run serve -s docs/demos/juicercook -L'
```

## Use it as a template

Copy `docs/demos/juicercook/` into your own project, drop your hero photos into `static/img/`, write each recipe as a markdown file with the structured frontmatter shape, and the recipe pages render themselves.
