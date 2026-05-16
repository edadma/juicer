# juicergallery

Photo-first gallery / portfolio theme for [juicer](https://github.com/edadma/juicer). Showcases the new image-variants pipeline that landed in juicer 0.x — every photo is rendered through `imageVariants`, so the browser pulls the smallest variant that fits the viewport.

## Shape

- Galleries are **top-level sections** (`content/iceland-2024/_index.md`). The home page is a grid of every gallery cover, newest first.
- Each gallery's `_index.md` carries a `photos:` list in frontmatter (list of strings or `{src, caption, alt}` maps).
- The detail page renders a CSS column-masonry grid; clicking a photo opens the largest variant in a new tab. No JavaScript lightbox.
- Static "about" / "contact" / "press" pages use a plain prose layout.

## Section index (`_index.md`) frontmatter

```yaml
title: Iceland 2024
date: 2024-09-15
cover: /img/iceland/cover.jpg
description: A two-week loop around the ring road.
photos:
  - src: /img/iceland/skogafoss.jpg
    alt: Skogafoss at golden hour
    caption: Skogafoss, day one
  - /img/iceland/diamond-beach.jpg          # plain string also works
```

## site.toml palette overrides

```toml
[juicergallery]
brand        = "#1f2937"   # ink near-black
brandStrong  = "#0b1220"
accent       = "#b45309"   # amber accent for hover / focus
fontSans     = "'Inter', system-ui, sans-serif"
fontSerif    = "'Cormorant Garamond', Georgia, serif"
gutter       = "0.6rem"    # tighter gutter → more masonry density
```

Image variants are produced automatically when `[images] enabled = true` is set in `site.toml` and `magick` (ImageMagick 7) is on `PATH`. The theme degrades cleanly to a plain `<img>` when variants aren't generated.
