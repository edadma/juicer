---
title: juicerdocs
summary: The opinionated docs theme that ships with juicer — what powers this very site.
weight: 40
---

`juicerdocs` is the theme this site uses. It's bundled with the juicer
repo at `docs/themes/juicerdocs/`, so a clone of juicer comes with it
out of the box. To use it on your own site, copy the directory or run:

```bash
juicer theme add https://github.com/edadma/juicer.git -n juicerdocs
```

…and set `theme = "juicerdocs"` in your `site.toml`.

## What's in the box

- A two-rail layout: section navigation on the left, page TOC on the
  right, content in the middle.
- Sticky topbar with logo, search, GitHub link, and theme toggle.
- Light + dark mode driven by CSS custom properties (toggleable from
  the topbar; system preference on first visit).
- Hero treatment for the home page (gradient title, feature-card grid,
  CTA buttons).
- 16 shortcodes — see [Shortcodes](/concepts/shortcodes/) for the full set.
- Client-side search powered by `/search.json` (always emitted by
  juicer; see [search](/getting-started/quickstart/) for the integration).
- "On this page" right rail with auto-highlight on scroll
  (IntersectionObserver-driven).
- Mobile sidebar overlay with backdrop, opens on the topbar's hamburger.

## When to use it (and when not to)

Use juicerdocs if you want a Hugo-style docs theme that boots quickly
and looks reasonable without effort. It's tuned for technical docs:
sidebar nav, code blocks with copy buttons, tables, callouts, search.

Pick something else if you want:

- A blog theme — juicerdocs is structured around hierarchical
  documentation, not a chronological list.
- A landing-page theme — the hero treatment is fine for a docs home,
  but it isn't a marketing page.
- Different colors — actually, it's configurable; see
  [Configuration](/juicerdocs/configuration/).

## What this section covers

The pages below document the configuration knobs juicerdocs reads from
your `site.toml`, plus how to customize anything beyond what's exposed.
