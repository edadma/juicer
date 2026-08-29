---
title: Demo site
summary: The juicerblog demo — 9 dated posts, 3 authors, a 3-post series, dateArchives, permalinks, syntax highlighting. Rendered live with the juicerblog theme.
weight: 20
---

This page is the placeholder for the live juicerblog demo. When the
docs site is built and deployed, this URL serves the actual demo
rendered with the juicerblog theme — a separate juicer build from
`docs/demos/juicerblog/` is dropped on top of the docs render at the
same path. See [`bin/build-demos.sh`](https://github.com/edadma/juicer/blob/dev/bin/build-demos.sh)
for the orchestration.

## What the demo exercises

A short fictional engineering blog populated with realistic content
covering both Phase 1 and Phase 2 blog features:

- **9 dated posts** spanning Jul–Dec 2024 (most recent first).
- **3 authors** (`ed`, `alice`, `ben`) with full author-registry
  records — avatars, bios, external links.
- **Multi-author co-byline** on one post (Ed + Alice).
- **A 3-post series** ("Reading on screens · Part N of 3") with the
  series-progress badge above the body and the "In this series"
  sidebar.
- **Pagination** at 4 posts/page on the home + archive listings.
- **`[permalinks]` with `posts = ":year/:month/:slug/"`** — every
  post lives at `/<year>/<month>/<slug>/` regardless of its source
  filename.
- **Year + month archives** via `dateArchives = true`.
- **Aliases** on one post (old URL redirects to new).
- **Server-side syntax highlighting** (Scala, JS, TOML, Bash) via
  bundled `grammars/`.
- **Atom + RSS feeds** at `/feed.xml` and `/feed.rss`.
- **OpenGraph + Twitter card meta** in `<head>` on every post.

## Preview locally

```bash
sbt 'juicerJVM/run serve -s docs/demos/juicerblog -L'
```

The `-L` flag enables live reload — touch any markdown file under
`docs/demos/juicerblog/content/` and the open browser tabs reload automatically.

## Use it as a template

Copy `docs/demos/juicerblog/` into your own project as a starting
point. Strip the example posts, replace the `[[authors]]` table, drop
your favicon + author avatars into `static/img/`, and you're shipping.
The companion [Configuration](../configuration/) reference catalogues
every site.toml key and palette token the theme reads.
