---
title: About this site
author: ed
static: true
summary: A tiny demo blog rendered with the juicerblog theme — what it is, who writes it, and what it's trying to prove.
---

This site exists for one reason: to be a realistic-looking blog so we can iterate on the **juicerblog** theme without reaching for stock-photo content. Every post here is a real-ish essay about a topic the credited author cares about; nothing is filler, but nothing has to be defended at length either.

## What it exercises

- **Tagging** — most posts carry one or two tags, with `meta` deliberately overloaded (used by three posts) and `life` used exactly once, so the tag cloud has visible weight differences.
- **Series** — the *Reading on screens* posts are a three-part series by Alice. The series sidebar lists all three; the prev/next buttons step through chronological order across all posts, regardless of series.
- **Multiple authors** — Ed, Alice, and Ben each have author records under `[[authors]]` in `site.toml`. One post (*Why static sites still win*) has co-authors.
- **Date archives** — `dateArchives = true` in `site.toml`, so `/2024/`, `/2024/07/`, `/2024/08/`, etc., land as their own pages.
- **Aliases** — *Why static sites still win* declares two old URLs that resolve via meta-refresh.
- **Permalinks** — `[permalinks] posts = ":year/:month/:slug/"` — every post lives under its date, so the URL survives a re-tagging.
- **Atom feeds** — `/feed.xml` aggregates everything; per-section feeds drop in too.

## Why a third demo blog

Two reasons. First, a theme is only as good as the content you can iterate on, and stock-Lorem-ipsum hides exactly the design problems that show up in real text — tag cloud imbalance, series sidebars with awkward title lengths, multi-author bylines that need to wrap, posts with figures vs. without. Second, every Phase-2 engine feature ships with a layout in juicerblog, and a layout that has no demo content is a layout that breaks at the next refactor without anybody noticing.

## How a "page" is different from a "post"

This page declares `static: true` in its frontmatter, which tells the juicerblog theme to drop the post chrome (date line, reading time, tags, series sidebar, prev/next nav) and render the body as a clean long-form essay. The signal is purely a theme convention — the engine still treats this file like any other content — but it means an about / colophon / contact page can live alongside the post archive without looking like part of it.

Source for the demo lives at `examples/blog-site/` in the [juicer repo](https://github.com/edadma/juicer). Pull requests welcome.
