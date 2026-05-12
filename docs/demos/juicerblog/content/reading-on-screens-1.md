---
title: "Reading on screens, part 1: display fundamentals"
date: 2024-07-08
author: alice
tags: [design, web, typography]
series: "Reading on screens"
seriesOrder: 1
summary: Subpixel rendering, anti-aliasing, and hinting — the substrate every screen-typography decision rests on.
---

Before we can reason about line-height or letter-spacing, we need to be honest about what we're rendering *into*. A "pixel" is a triplet of red-green-blue subpixels arranged in a known geometry, and most operating systems exploit that geometry to triple horizontal resolution at the cost of color fringing.

## RGB stripe vs. PenTile vs. nothing

Desktop monitors are overwhelmingly **RGB stripe** — three vertical bars per logical pixel:

```
[R][G][B]   [R][G][B]   [R][G][B]
[R][G][B]   [R][G][B]   [R][G][B]
```

OLED phones often use **PenTile** layouts where greens outnumber reds and blues. The "300 PPI iPhone display" and the "300 PPI laptop display" do not have the same effective resolution for thin vertical strokes, and your fonts will look subtly different on each.

When the OS draws a glyph, it can target subpixel positions:

```
fn draw_stem(x: f32, y: f32, height: f32) {
    // x is fractional in physical pixels — 0.33 lights the green
    // sub-pixel only; 0.67 lights blue. Whole numbers light all three.
    rasterize(x, y, /*width=*/1.0, height);
}
```

The catch: subpixel rendering only works when you know the subpixel layout. Rotate the display 90° and the technique inverts; on a phone in landscape it usually disables itself.

## Anti-aliasing

A glyph's edge rarely lands on a pixel boundary. Anti-aliasing samples the glyph's coverage of each pixel and renders accordingly:

| Coverage | Pixel value |
|---|---|
| 0%  | background |
| 25% | 0.75 background + 0.25 ink |
| 50% | 0.50 / 0.50 |
| 75% | 0.25 / 0.75 |
| 100%| ink |

The "fuzziness" people complain about with non-Retina displays is anti-aliased coverage doing exactly what it's supposed to do, just at a resolution where 25% coverage is half a millimetre wide and your eye notices.

## Hinting

Hinting is the font's attempt to round its outlines to the pixel grid so that a stem ends *exactly* at a pixel boundary. Done well, hinting makes 12px Verdana look like a deliberate design choice instead of a bitmap accident. Done badly — or skipped entirely on a Mac, which doesn't hint — it's the difference between text that "snaps" into shape at small sizes and text that visibly blurs.

Most modern setups have given up on hinting because Retina-class densities (>200 PPI) make pixel-level snapping invisible. Outside that regime — every CRT, every old laptop, every projector — hinting is still load-bearing, and your designer-mac-rendered comp will not match.

## What it means for the CSS you write

You don't get to control any of this. You can decide whether to hide it or design with it in mind:

- Choose a body font that ships well-hinted (Charter, Source Serif, Inter) over one that doesn't (most decorative serifs).
- Avoid 1px-wide vertical lines next to text — they alias differently than the glyph stems do.
- Test on a non-Retina monitor at least once. The rendering you see is what 30% of your audience sees.

Next part: color, contrast, and the dark-mode rabbit hole.
