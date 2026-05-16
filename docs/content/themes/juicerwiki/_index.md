---
title: juicerwiki
summary: Backlink-driven knowledge-base / Zettelkasten theme. Flat URL shape, prominent backlinks panel on every note, tag cloud + client-side search.
weight: 90
---

`juicerwiki` is built around `.page.backlinks` — juicer's inverted link index. Every note shows you every other note that links *to* it, so the connective tissue of a personal wiki is always visible. Combined with a flat URL scheme (`content/<slug>.md` → `/<slug>/`) and a client-side search modal, the theme gives a small Zettelkasten the same network-shaped navigation you'd get from Roam or Obsidian, with none of the JavaScript framework on top.

It lives at `docs/themes/juicerwiki/` in the juicer repo. To use it on your own site:

```bash
juicer theme add https://github.com/edadma/juicer.git -n juicerwiki
```

…and set `theme = "juicerwiki"` in `site.toml`. The [Demo site](./demo/) — Field Notes, a small set of computing notes that link to each other — is the canonical example.

## What's in the box

The unit of content is a **note** — a single markdown file under `content/`, named after its slug. Notes are short and atomic; they link to each other freely; the link graph is what holds the wiki together. The home page is a tag cloud plus a list of recent notes. Tag-archive pages (`/tags/<slug>/`) ship as a free bonus.

- **Backlinks panel** on every note (and section-index page) — thin `{title, url, summary}` records sorted by referrer title. Renders nothing when no other page links here.
- **Tag cloud** on the home page sized by usage count via CSS calc on a `data-count` attribute.
- **Client-side search modal** (`Cmd/Ctrl-K`) over `search.json` — fetches lazily on first open, substring-matches title + summary + body.
- **Light + dark mode** — cream paper / walnut ink vs deep walnut / cream ink. Pre-paint apply script in `<head>`.
- **Flat URL scheme by default**, but the folder layout still works for the rare nested section.
- **Article JSON-LD** on notes with a date, plus the standard `WebSite` / `BreadcrumbList` / OpenGraph chrome.

## Frontmatter shape

```yaml
---
title: Zettelkasten
date: 2024-02-10
tags: [method, history]
summary: A note-taking method built around small atomic notes that link to each other.
---

Body in markdown. Link liberally — [other notes](/atomic-notes/) end up
in your backlinks panel automatically.
```

Only `title` is required. `date` controls home-page recency ordering. `tags:` (or single string) drives the tag cloud and tag-archive pages. `summary` is shown in the home list, in `<meta name="description">`, and in *other notes'* backlinks panels.

## When to pick juicerwiki vs juicerstudy

**juicerstudy** is for long-form prose — essays, study notes, manuscript-shaped writing. It has footnotes, on-this-page rails, and a generous serif body for reading.

**juicerwiki** is for a *network* of short prose — atomic notes that point at each other. It has backlinks and search but no big reading rail. Pick it when the link between notes is as important as the note itself.
