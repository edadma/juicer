---
title: juicerportfolio
summary: The bundled image-led portfolio / case-study theme — project grid + per-project pages with hero, meta sidebar, gallery, and prev/next nav. Warm-paper aesthetic, no JS framework.
weight: 80
---

`juicerportfolio` is the show-the-work sibling to the rest of the
bundled themes. It's at `docs/themes/juicerportfolio/` in the juicer
repo, so a clone comes with it out of the box. To use it on your own
site, copy the directory or run:

```bash
juicer theme add https://github.com/edadma/juicer.git -n juicerportfolio
```

…and set `theme = "juicerportfolio"` in your `site.toml`. A live demo
lives at `examples/portfolio-site/` — run it with `sbt 'juicerJVM/run serve -s examples/portfolio-site -L'` to see every layout against fictional studio
copy (Atelier Hahn, an industrial-design studio in Lisbon).

## What's in the box

The unit of content is a **project**. The home page is a 2-up grid
of project cards, optionally filtered by category. Each project has
its own page: hero image, sticky meta sidebar (year / role / client /
materials / external link), writeup, optional image gallery, and
prev/next nav across the project series.

- **Warm-paper aesthetic** — off-white background, near-black warm
  ink, single muted rust accent. Small radii, generous whitespace.
  Sans-serif body, serif project titles + brand. Inspirations: small
  studio sites, Hay catalog pages, Vitra product pages.
- **Light + dark mode** driven by `[data-theme="dark"]` overrides on
  CSS custom properties; pre-paint apply script in `<head>` prevents
  the white flash on dark.
- **Project grid with filter chips** — `[[workCategories]]` in
  site.toml drives chips above the grid; clicking filters in place
  via a tiny JS that toggles a `data-active-filter` attribute on the
  grid root. No framework, no router, no fetch.
- **Project page** — hero figure, sticky right-side meta sidebar
  (mobile: wraps to a flex row above the body), writeup body,
  optional `[[gallery]]` figure grid, prev/next nav, "All work" back
  link.
- **Materials chips** — `tools: [...]` in frontmatter renders as
  small monospace-tabular chips in the meta sidebar; useful for
  materials, techniques, software, or whatever the project tracks.
- **`/work/_index.md` archive** — folder.html doubles as a complete
  text-list of every project, complementary to the home grid's
  curated image-led view.
- **`file.html`** for prose pages (about, contact, CV, press, etc.)
  with a constrained measure for comfortable reading.
- **404 page** that doesn't depend on the rest of the chrome.
- **Theme toggle** in the topbar with localStorage persistence.

## When to use it (and when not to)

Use juicerportfolio if you're shipping a portfolio for a designer,
illustrator, photographer, indie game dev, freelance developer,
small architecture or design studio, ceramicist, furniture maker —
anywhere the work is best shown image-first and the visitor will
recognise the work before reading the words.

Pick something else if you want:

- **Multi-page reference docs.** That's [juicerdocs](/juicerdocs/).
- **A blog or essay site** with author bylines and dated posts. That's
  [juicerblog](/juicerblog/).
- **A product / SaaS landing** with hero + features + pricing + CTA.
  That's [juicerlanding](/juicerlanding/).
- **A community / location site** (church, cafe, group). That's
  [juicerchurch](/juicerchurch/) or [juicercafe](/juicercafe/).

## What this section covers

The page below documents every site.toml key, every per-page
frontmatter knob, and every `[juicerportfolio]` palette token the
theme reads. The shared theming model (palette / customCSS / file
replacement layers) is in [Theming](/reference/theming/) and applies
identically.
