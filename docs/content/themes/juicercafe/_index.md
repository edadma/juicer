---
title: juicercafe
summary: The bundled small-business theme — café, restaurant, neighborhood spot. Hours widget, menu sections, photo albums, events, "we're working on it" cards.
weight: 60
---

`juicercafe` is the small-business sibling to the rest of the
bundled themes. It's at `docs/themes/juicercafe/` in the juicer
repo, so a clone comes with it out of the box. To use it on your
own site, copy the directory or run:

```bash
juicer theme add https://github.com/edadma/juicer.git -n juicercafe --subdir docs/themes/juicercafe
```

…and set `theme = "juicercafe"` in your `site.toml`. A live demo
lives at `examples/cafe-site/` in the juicer repo — run it with
`sbt 'juicerJVM/run serve -s examples/cafe-site -L'` to see every
section on this page rendered against a fictional café.

## What's in the box

The unit of content is a **place** — a café, a small restaurant,
a roastery, a wine bar, a neighborhood shop. The home page is a
hero image + hours widget + featured menu + photo strip; the rest
of the site (menu, events, "we're working on it") branches off from
there.

- **Warm-paper aesthetic** with a brick-red brand accent and a
  serif heading face. Reads more like a neighborhood newsletter
  than a marketing site.
- **Light + dark mode** driven by `[data-theme="dark"]` overrides
  on CSS custom properties; pre-paint apply script in `<head>`
  prevents the dark-mode flash.
- **Hours widget** in the topbar (when `[[hours]]` is defined) —
  reads the day-of-week schedule from `site.toml` and badges the
  topbar with an "Open now" / "Closed" pill based on the current
  time.
- **Menu sections** (`layout: menu` + `layout: menu-section`) —
  each section is a markdown page with frontmatter `items: [...]`;
  the menu layout assembles them into the full menu.
- **Events list** (`layout: event-list` + `layout: event`) —
  reuses the engine's `.site.events` curated list, including
  weekly recurring events.
- **"We're working on it"** cards (`layout: working`) — a
  designated page lists pending projects, lease applications,
  upcoming changes; useful for places mid-renovation.
- **Photo album** (`layout: photo-album`) — site-wide photo grid
  pulling from every page's `photos:` frontmatter (sorted by date
  desc).
- **`/visit/` block** — address, city, state, zip, and an optional
  map URL — surfaced in the footer on every page.
- **Author registry** support — multi-baker, multi-barista bylines
  work the same way as juicerblog.
- **404 page** that doesn't depend on the rest of the chrome.

## When to use it (and when not to)

Use juicercafe if you're shipping a small-business site where the
day-to-day questions are "are you open right now?", "what's on the
menu?", "where do I park?", and "what's the next event?"

Pick something else if you want:

- **Multi-page docs** — that's [juicerdocs](/themes/juicerdocs/).
- **A blog** with author archives and dated posts — that's
  [juicerblog](/themes/juicerblog/).
- **A product landing** with hero + features + pricing CTA — that's
  [juicerlanding](/themes/juicerlanding/).
- **A church or community site** with sermons, ministries, and a
  calendar grid — that's [juicerchurch](/themes/juicerchurch/), which
  shares the events / photos / hours conventions but adds
  sermon-archive specific layouts.
- **A portfolio** — that's [juicerportfolio](/themes/juicerportfolio/).

## What this section covers

The page below documents every site.toml key, every per-page
frontmatter knob, and every `[juicercafe]` palette token the
theme reads. The shared theming model (palette / customCSS / file
replacement layers) is in [Theming](/reference/theming/) and applies
identically to juicercafe.
