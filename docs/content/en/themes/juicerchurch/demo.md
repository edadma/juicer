---
title: Demo site
summary: Grace Community Church — a fictional parish with sermon archive, recurring-event calendar grid, ministries section, photo albums, and service-times block.
weight: 20
---

This page is the placeholder for the live juicerchurch demo. When the
docs site is built and deployed, this URL serves the actual demo
rendered with the juicerchurch theme — a separate juicer build from
`docs/demos/juicerchurch/` is dropped on top of the docs render at the
same path. See [`bin/build-demos.sh`](https://github.com/edadma/juicer/blob/dev/bin/build-demos.sh)
for the orchestration.

## What the demo exercises

A fictional parish — "Grace Community Church" — with content
populating every juicerchurch layout:

- **Sermon archive** + per-sermon pages with scripture reference,
  preacher / leader, audio + video URLs with displayed durations,
  transcript body.
- **12-month calendar grid** rendering `.site.calendar` with recurring
  weekly events expanded onto every matching weekday.
- **Events list + per-event pages** sharing the calendar's underlying
  data.
- **Ministries** — each ministry has its own `layout: ministry` page:
  leader, meeting time, contact info, narrative.
- **Photo albums** — per-event and site-wide.
- **Service-times block** in the header driven by `[[services]]` in
  `site.toml`.
- **`/visit/` block** — address, mapURL — surfaced in the footer.
- **Multi-clergy bylines** via the author registry (sermons by
  different preachers).
- **Beliefs page** — a static page that opts out of the post chrome.

## Preview locally

```bash
sbt 'juicerJVM/run serve -s docs/demos/juicerchurch -L'
```

The `-L` flag enables live reload — touch any markdown file under
`docs/demos/juicerchurch/content/` and the open browser tabs reload automatically.

## Use it as a template

Copy `docs/demos/juicerchurch/` into your own project as a starting
point. Replace the service times, the sermon content, the ministries —
the layouts handle the rest. The companion
[Configuration](../configuration/) reference catalogues every
site.toml key, palette token, and per-page frontmatter knob the theme
reads.
