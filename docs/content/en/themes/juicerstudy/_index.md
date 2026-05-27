---
title: juicerstudy
summary: The bundled study / long-form prose theme — serif reading column, sidebar nav, optional KaTeX math, no JavaScript framework. Tuned for essays, notes, course material, and reading journals.
weight: 55
---

`juicerstudy` is the slow-reading sibling to [juicerdocs](/themes/juicerdocs/)
and [juicerblog](/themes/juicerblog/). It's bundled with the juicer
repo at `docs/themes/juicerstudy/`, so a clone of juicer comes with
it out of the box. To use it on your own site, copy the directory
or run:

```bash
juicer theme add https://github.com/edadma/juicer.git -n juicerstudy
```

…and set `theme = "juicerstudy"` in your `site.toml`. See the
[Demo site](./demo/) page for a live demo — a fictional reader's
notebook ("Marginalia") — exercising every treatment on this page.

## What's in the box

The unit of content is a **page of prose** — an essay, a study note,
a chapter, a journal entry. Section landings are author-driven
(no auto-cataloged "child page" list imposed by the layout) so
the section _index.md reads as editorial introduction, not as a
table of contents. The sidebar is the canonical browse surface.

- **Serif reading column** — Source Serif Pro inside the content
  area, sans-serif (Inter) for everything else. ~38rem measure
  (~65ch) for a comfortable, book-feeling line.
- **Three-rail layout** — sidebar nav on the left, body in the
  middle, optional "On this page" rail on the right (only emitted
  when the page sets `toc: true` in its frontmatter).
- **Light + dark mode** driven by `[data-theme="dark"]` overrides
  on CSS custom properties; pre-paint apply script in `<head>`
  prevents the white flash on dark.
- **Root landing page with a hero** — the root `_index.md` opts into
  a frontmatter-driven hero (set `heroTitle`, `heroHighlight`,
  `heroSuffix` to render a gradient-highlighted headline) plus an
  optional hero image (`heroImage:`, rendered responsive via
  `imageVariants`), plus optional Start-reading / GitHub CTA
  buttons, plus a card grid of top-level subsections. Same
  enhancement pattern juicerdocs uses, adapted to the
  reading-journal aesthetic.
- **Author-driven non-root section landings** (`folder.html`) — for
  nested sections, the layout renders the page's frontmatter title
  + summary + body, plus an auto-generated card grid of child
  subsections and a list of pieces. The opt-in
  `\[= section-list =]` shortcode is still available for pages
  that want a fully manual catalog of children.
- **Responsive images via `imageVariants`** — the `figure`
  shortcode now emits a `<picture>` with multi-width `srcset` when
  `[images] enabled = true` is set. Hero images on the root
  landing pull from the same pipeline.
- **Optional KaTeX math** — set `math = true` under `[juicerstudy]`
  and `$inline$` / `$$display$$` math (plus `\( … \)` / `\[ … \]`)
  renders via KaTeX auto-render over CDN. Pinned to a recent major.
- **17 shortcodes** — the full juicerdocs/juicerblog set
  (`note`, `tip`, `warning`, `danger`, `figure`, `kbd`, `badge`,
  `button`, `buttons`, `collapse`, `steps`, `tab`/`tabs`, `filetree`,
  `github`, `youtube`) plus the study-specific `section-list`.
- **Client-side search** — `/search.json` (always emitted by
  juicer) backs a `⌘K` search modal in the topbar.
- **Sticky topbar** with logo, search trigger, sidebar toggle,
  theme toggle.
- **Mobile sidebar overlay** with backdrop, opens from the topbar's
  hamburger.
- **Configurable palette** — five light tokens + five dark
  overrides under `[juicerstudy]`, plus typography knobs
  (`fontSans`, `fontSerif`, `fontMono`) and sizing knobs
  (`contentMax`, `sidebarW`, `railW`, `radiusLg`).

## When to use it (and when not to)

Use juicerstudy if you're shipping a **long-form prose** site
where the reader is doing one thing at a time — an essay
collection, a reading journal, a personal notebook, a set of
lecture notes, a study guide, a slow-cooked devblog where each
post is a chapter rather than a status update. It's tuned for
the reader who arrives to spend twenty minutes with a single
piece, not to graze.

Pick something else if you want:

- **Multi-page reference docs** with `previous / next` chapter
  nav and tight cross-linking — that's
  [juicerdocs](/themes/juicerdocs/). juicerstudy intentionally
  doesn't render a prev/next bar on every page; it isn't trying
  to be a tutorial sequence.
- **A blog** with dated posts, tag archives, year archives, and
  author bylines as a first-class feature — that's
  [juicerblog](/themes/juicerblog/). juicerstudy's pages are
  undated by default and don't have a chronological archive.
- **A landing page** — that's
  [juicerlanding](/themes/juicerlanding/).
- **A community / location site** — that's
  [juicercafe](/themes/juicercafe/) or
  [juicerchurch](/themes/juicerchurch/).
- **A portfolio** — that's
  [juicerportfolio](/themes/juicerportfolio/).

## What this section covers

The page below documents every site.toml key juicerstudy reads,
every `[juicerstudy]` palette / typography / sizing token, and
the per-page frontmatter conventions (`toc: true`, math opt-in,
etc.). The shared theming model — `customCSS`, the
file-replacement overlay pattern — is in [Theming](/reference/theming/)
and applies identically to juicerstudy.
