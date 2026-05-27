---
title: juicergallery
summary: A photo-first gallery / portfolio theme. Flat list of galleries on the home page, masonry detail pages, responsive `<picture>` markup via the image-variants pipeline.
weight: 85
---

`juicergallery` is the photography-first sibling to `juicerportfolio`. The home page is a cover-grid of every gallery section; each gallery detail page is a CSS column-masonry that fills with the gallery's frontmatter `photos:` list. Every photo is delivered through juicer's `imageVariants` pipeline, so the browser pulls the smallest variant that fits the viewport.

It lives at `docs/themes/juicergallery/` in the juicer repo. To use it on your own site, copy the directory or run:

```bash
juicer theme add https://github.com/edadma/juicer.git -n juicergallery
```

…and set `theme = "juicergallery"` in your `site.toml`. The [Demo site](./demo/) is the canonical example — Hella Studio, a fictional Reykjavík photographer with three small galleries.

## What's in the box

The unit of content is a **gallery section** — each gallery is a top-level section with an `_index.md` carrying frontmatter for the cover, the summary, and a `photos:` list. The home page is a cover-grid of every gallery, newest-first. Each gallery's detail page is a CSS column-masonry that holds together at any column-count (one column on mobile, four on a 24" monitor).

- **Cool-warm aesthetic** — warm off-white background, near-black ink, single amber accent on hover. Sans-serif body, serif gallery titles. Quiet chrome that gets out of the way of the photos.
- **Light + dark mode** driven by `[data-theme="dark"]` overrides on CSS custom properties; pre-paint apply script in `<head>` prevents the white flash on dark.
- **CSS column-count masonry** — pure CSS, no JavaScript layout. Photos hold their native aspect ratio inside the column, packed by the browser. Column count steps with viewport width: 1 on mobile → 2 at 40rem → 3 at 64rem → 4 at 96rem.
- **`<picture>` + `srcset` markup** — one `<source>` per format (webp + the original mime) with a full multi-width `srcset`, so the browser picks the right variant instead of being pinned to a single size by a naive one-source-per-width approach.
- **No lightbox JS** — clicking a photo opens the largest variant in a new tab. Reliable, accessible, zero dependencies. Sites that want a lightbox can ship one themselves; the theme leaves the click target intact.
- **`folder.html`** for gallery detail (`_index.md` with a `photos:` list) and **`file.html`** for prose pages (about, contact). A separate **`home.html`** drives the root cover-grid — set `layout: home` in the root `_index.md` to opt in.
- **ImageGallery JSON-LD** on every gallery detail page (Google understands the image set), plus the standard `WebSite` / `BreadcrumbList` / OpenGraph chrome.

## Frontmatter shape

A gallery section's `_index.md`:

```yaml
---
title: Iceland 2024
date: 2024-09-15
cover: /img/iceland/cover.jpg
coverAlt: A dim blue ridge above a waterfall
summary: A two-week loop around the ring road.
photos:
  - src: /img/iceland/skogafoss.jpg
    alt: Skógafoss at golden hour
    caption: Skógafoss, day one
  - /img/iceland/diamond-beach.jpg          # plain string also works
---

Optional prose body — rendered above the masonry. Useful for context, equipment notes, the trip narrative.
```

`photos:` accepts either a plain string (just the image path) or a `{src, alt, caption}` map. The detail layout normalises both forms.

## When to pick juicergallery vs juicerportfolio

The two are siblings, not duplicates. **juicerportfolio** is for selling project-shaped work — each project gets a writeup, a meta sidebar, a hero, a small inline gallery, and prev/next nav. The home page is a 2-up project grid. **juicergallery** drops everything below the masonry: no meta sidebar, no per-project writeup template, no filter chips. Each gallery is just "here are the photos, and a paragraph if I felt like writing one."

Pick juicergallery when the work *is* the photos. Pick juicerportfolio when the work is "things I made, and here's the story of how each came to be."

## Image variants — the showpiece

juicergallery is the canonical showcase for juicer's `imageVariants` pipeline. Turn it on in `site.toml`:

```toml
[images]
enabled = true
widths  = [480, 960, 1600]
formats = ["webp", "original"]
quality = 78
```

…and every photo in every gallery is rendered with webp + JPG variants at all three widths. The build runs `magick` (ImageMagick 7) once per source, caches outputs by content hash, and reuses them across builds. See the [Image variants reference](/reference/image-variants/) for the full configuration surface.
