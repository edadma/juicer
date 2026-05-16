# juicerwiki

Backlink-driven knowledge-base / Zettelkasten theme for [juicer](https://github.com/edadma/juicer). Built around the `.page.backlinks` feature: every page surfaces every other page that links *to* it, so the connective tissue of a personal wiki is always visible.

## Shape

- **Flat URL scheme.** Notes live as `content/<slug>.md` files, rendered at `/<slug>/`. No deep section hierarchy.
- **Backlinks aside on every note.** Lists thin `{title, url, summary}` records of every page that links here, sorted by referrer title.
- **Tag cloud on the home page** scaled by tag count.
- **Live client-side search** over `search.json` — open with `Cmd/Ctrl-K`, filters notes by title + body substring.
- **No tree sidebar.** The wiki is meant to be navigated by following links, not by drilling a folder structure.

## Frontmatter

```yaml
title: Distributed Systems
date: 2024-03-12
tags: [computing, theory]
---

Body in markdown. Drop [internal links](/other-note/) liberally — they
populate the linked note's backlinks panel automatically.
```

Only `title` is required. `date` controls home-page recency ordering. `tags:` (or single string) drives tag-cloud + tag-archive pages.

## site.toml palette overrides

```toml
[juicerwiki]
brand        = "#3b2c1e"   # warm dark ink
accent       = "#7a4a1c"   # umber accent
fontSans     = "'Inter', system-ui, sans-serif"
fontSerif    = "'Crimson Pro', Georgia, serif"
```
