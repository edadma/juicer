---
title: juicerchurch
summary: The bundled church / ministry theme — sermon archive, recurring-event calendar, ministries section, photo albums.
weight: 65
---

`juicerchurch` is the church / ministry sibling to the rest of the
bundled themes. It's at `docs/themes/juicerchurch/` in the juicer
repo, so a clone comes with it out of the box. To use it on your
own site, copy the directory or run:

```bash
juicer theme add https://github.com/edadma/juicer.git -n juicerchurch --subdir docs/themes/juicerchurch
```

…and set `theme = "juicerchurch"` in your `site.toml`. A live demo
lives at `examples/church-site/` in the juicer repo — run it with
`sbt 'juicerJVM/run serve -s examples/church-site -L'` to see
every layout against fictional ministry copy.

## What's in the box

The two units of content are **sermons** and **events**. The home
page is service-times + this-week's-events + most-recent-sermon;
the rest of the site lays out sermon archive, ministries,
calendar, and photo albums in a calm, reverent palette.

- **Calm, reverent aesthetic** — muted blues, slow-eased fades,
  serif headings. Reads more like a parish bulletin than a
  marketing site.
- **Light + dark mode** driven by `[data-theme="dark"]` overrides.
- **Sermon archive** (`layout: sermon-archive` + `layout: sermon`) —
  per-sermon page with scripture reference, preacher / leader,
  audio + video URLs with displayed durations, transcript body.
- **Calendar grid** (`layout: calendar`) — renders `.site.calendar`
  as a 12-month visual grid (default; configurable via
  `calendarMonths`), with recurring weekly events expanded onto
  every matching weekday.
- **Events** (`layout: event-list` + `layout: event`) — same shape
  as juicercafe but with the calendar grid as an extra view.
- **Ministries** (`layout: ministry`) — each ministry has its own
  page: leader, meeting time, contact info, narrative.
- **Photo albums** (`layout: album` + `layout: photo-album`) — per
  event or site-wide.
- **Service-times block** in the header (`[[services]]`) — what
  time the regular weekly services are at.
- **`/visit/` block** — address, mapURL — surfaced in the footer.
- **Author registry** for multi-clergy bylines (sermons by
  different preachers).
- **404 page** that doesn't depend on the rest of the chrome.

## When to use it (and when not to)

Use juicerchurch if you're shipping a parish, ministry, or small
faith-community site where the day-to-day questions are "when is
the next service?", "what's the recent sermon?", "what's on the
calendar this week?", and "where do I park?"

Pick something else if you want:

- **Multi-page docs** — that's [juicerdocs](/themes/juicerdocs/).
- **A blog** with author archives and dated posts — that's
  [juicerblog](/themes/juicerblog/). (Sermons share much of the blog
  shape, but `juicerchurch` is tuned around a sermon as the unit,
  not a post.)
- **A product landing** — that's [juicerlanding](/themes/juicerlanding/).
- **A small-business / café site** with hours and menus — that's
  [juicercafe](/themes/juicercafe/), which shares the events / photos /
  visit conventions but swaps the sermon archive for menu
  sections and the hours widget.
- **A portfolio** — that's [juicerportfolio](/themes/juicerportfolio/).

## What this section covers

The page below documents every site.toml key, every per-page
frontmatter knob (for sermons, events, ministries, albums), and
every `[juicerchurch]` palette token. The shared theming model
(palette / customCSS / file replacement) is in
[Theming](/reference/theming/) and applies identically.
