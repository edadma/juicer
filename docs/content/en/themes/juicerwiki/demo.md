---
title: Demo site
summary: Field Notes — a small Zettelkasten on computing. Showcases the backlinks panel, tag cloud, and Cmd/Ctrl-K search modal.
weight: 20
---

This page is the placeholder for the live juicerwiki demo. When the docs site is built and deployed, this URL serves the actual demo rendered with the juicerwiki theme — a separate juicer build from `docs/demos/juicerwiki/` is dropped on top of the docs render at the same path.

## What the demo exercises

"Field Notes" — a small wiki of computing notes that link to each other:

- **Backlinks panel** at the bottom of every note. Open [Zettelkasten](https://example.com) and see who points there.
- **Tag cloud** on the home page sized by usage count.
- **Tag archives** at `/tags/<slug>/`.
- **Search modal** — open with `Cmd/Ctrl-K`, type any substring.

## Preview locally

```bash
sbt 'juicerJVM/run serve -s docs/demos/juicerwiki -L'
```

The `-L` flag enables live reload — touch any markdown file under `docs/demos/juicerwiki/content/` and the open browser tabs reload automatically.

## Use it as a template

Copy `docs/demos/juicerwiki/` into your own project as a starting point. Drop notes into `content/` as flat markdown files; link them to each other with regular markdown `[text](/slug/)` syntax; let the backlinks index do the rest.
