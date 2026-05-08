---
title: Typography 101 for site builders
date: 2024-09-22
tags: [design, web]
summary: A short opinionated guide to picking type, sizing it, and not embarrassing yourself.
---

Most sites get type wrong by default — too cramped, too small, too many faces fighting for attention. The fixes are mostly free.

## Pick two

One serif, one sans. That's it. A monospace for code if there is code. The discipline of "two faces, two roles" eliminates 90% of typographic clutter.

## Set a generous measure

Reading prose at 100 characters per line is exhausting; 60–75 is comfortable. Set `max-width: 38rem` on the column and stop fighting your eyes.

## Line-height is doing more than you think

Body text wants `line-height: 1.6` to `1.75`. Headings want closer to `1.15`. Buttons and labels want `1.0`. The vertical rhythm of a page comes from these ratios more than from any specific font.

## Trust system stacks

```css
font-family: "Iowan Old Style", Charter, Georgia, serif;
```

The web safe stack of 2010 still works in 2024. You don't need to ship a 200KB webfont to look professional.
