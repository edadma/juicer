---
title: "Reading on screens, part 2: color, contrast, and dark mode"
date: 2024-07-22
author: alice
tags: [design, web, typography]
series: "Reading on screens"
seriesOrder: 2
summary: WCAG ratios are a floor, not a ceiling. Why "pure black on pure white" fatigues your eye, and what to do instead.
---

The default body color of every browser is `#000000` on `#ffffff`. Maximum contrast, right? That's actually the problem.

## Pure black is a glare problem

Sunlight hitting paper bounces back diffusely; sunlight (or backlight) coming *through* a screen punches into your eye directly. When the brightest part of the screen is pure white and the darkest is pure black, the dynamic range your eye is asked to process is roughly the same as standing in a snowfield staring at a wet rock. Reduce both ends and the eye relaxes.

Most production type uses **near-white background** (`#fcfcfa`, `#f8f7f4`, the famous `#fdfcf8` of paperback novels) and **near-black foreground** (`#1f1f1f`, `#1c1917`):

<figure>
  <div style="display:grid;grid-template-columns:1fr 1fr;gap:1rem;font-family:Georgia,serif;line-height:1.5">
    <div style="background:#fff;color:#000;padding:1rem;border:1px solid #ccc">
      <strong>Pure black on pure white</strong><br>
      The default. Notice how your eye flickers around the edges as it tries to settle on the column.
    </div>
    <div style="background:#fdfcf8;color:#1c1917;padding:1rem;border:1px solid #ccc">
      <strong>Warm cream on deep ink</strong><br>
      Same letterforms; the eye lands and stays. The contrast ratio is still 13:1 — well above WCAG AAA.
    </div>
  </div>
  <figcaption>Same words, two palettes. Read each side for thirty seconds and you'll feel the difference.</figcaption>
</figure>

## WCAG ratios are necessary but not sufficient

WCAG AA wants 4.5:1 for body text; AAA wants 7:1. Hit those, and you've cleared the *accessibility* bar. You have not necessarily produced text that's pleasant to read for an hour.

The unspoken floor for "comfortable long-form reading" is closer to **8–13:1** with **off-white** backgrounds:

| Foreground | Background | Ratio | Verdict |
|---|---|---|---|
| `#000` | `#fff` | 21.0 | Glare-prone |
| `#1c1917` | `#fdfcf8` | 16.7 | Cream-paper feel |
| `#44403c` | `#fdfcf8` | 8.9  | Pleasant for body |
| `#78716c` | `#fdfcf8` | 4.7  | Edge of legibility |

The 4.7 row meets WCAG AA. Don't put your body text there.

## Dark mode is its own problem

Inverting the colors does not, in fact, give you a dark mode. You have to redesign:

1. **Reduce, don't invert.** A dark background fights pure white text just as hard as a white background fights black. Pull both toward grey.
2. **Desaturate accent colors.** The `#c2410c` amber that anchors a light theme will burn in a dark one. Use a lighter, slightly cooler tone (`#fb923c`).
3. **Watch your borders.** A 1px hairline that read as "subtle separator" on cream paper will *vanish* on a dark background. Either make it lighter relative to the bg, or replace it with negative space.
4. **Think about your shadows.** They mostly stop working. Borders or elevation tokens replace them.

<figure>
  <div style="display:grid;grid-template-columns:1fr 1fr;gap:1rem">
    <div style="background:#000;color:#fff;padding:1rem;border-radius:4px">
      <strong style="color:#ff6347">Pure white on pure black</strong><br>
      Fonts visibly bleed into the background. The accent color burns.
    </div>
    <div style="background:#1c1917;color:#fafaf9;padding:1rem;border-radius:4px">
      <strong style="color:#fb923c">Off-white on warm ink</strong><br>
      Your eye relaxes. The accent reads as warm, not neon.
    </div>
  </div>
  <figcaption>Same idea, dark side. Notice how the right column doesn't shimmer.</figcaption>
</figure>

## Contrast for code

Code blocks are often where dark-mode design falls apart, because syntax highlighting was designed for light backgrounds. Pull the comment-grey *up*, the keyword color toward warmer tones, and the background slightly darker than the body so the block reads as a distinct surface:

```css
:root[data-theme="dark"] {
  --code-bg:    #161412;          /* darker than body's #1c1917 */
  --code-text:  #e5e5e5;
  --syn-keyword: #fb923c;         /* warm, not neon */
  --syn-string:  #84cc16;         /* lime, not pure green */
  --syn-comment: #78716c;         /* up from #57534e */
}
```

Final part: long-form reading patterns. How people actually move through 2,000 words.
