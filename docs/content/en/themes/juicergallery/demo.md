---
title: Demo site
summary: Hella Studio — a fictional Reykjavík photographer with three small galleries. Showcases the image-variants pipeline, masonry detail pages, and the cover-grid home.
weight: 20
---

This page is the placeholder for the live juicergallery demo. When the docs site is built and deployed, this URL serves the actual demo rendered with the juicergallery theme — a separate juicer build from `docs/demos/juicergallery/` is dropped on top of the docs render at the same path. See [`bin/build-demos.sh`](https://github.com/edadma/juicer/blob/dev/bin/build-demos.sh) for the orchestration.

## What the demo exercises

A fictional Reykjavík photographer — "Hella Studio" — with three small galleries:

- **Iceland 2024** — landscapes, varied aspect ratios to show off the masonry.
- **Studio portraits** — tall portrait crops, packed into the same grid.
- **Local sunsets** — wide horizontals.

Every photo is rendered through the `imageVariants` pipeline, so the build emits webp + JPG variants at three widths and the `<picture>` markup carries a full multi-width `srcset`. View the page source on a gallery detail to see the variant URLs the browser picks from.

The home page uses `layout: home` to opt into the cover-grid; each gallery detail uses the default folder layout (no opt-in needed); the about page is a plain `file.html` prose layout.

## Preview locally

```bash
sbt 'juicerJVM/run serve -s docs/demos/juicergallery -L'
```

The `-L` flag enables live reload — touch any markdown file under `docs/demos/juicergallery/content/` and the open browser tabs reload via SSE.

## Use it as a template

Copy `docs/demos/juicergallery/` into your own project as a starting point. Drop your photos into `static/img/<gallery-name>/`, declare each gallery as a top-level section with a `photos:` list, and retune `[juicergallery]` palette tokens. The companion [Configuration](../configuration/) reference catalogues every site.toml key, palette token, and per-section frontmatter knob the theme reads.
