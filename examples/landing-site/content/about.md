---
title: About Tally
summary: Who's behind the product, what we believe, and why this product exists at all.
date: 2026-04-01
---

Tally is built by a two-person team out of Lisbon. We started it in
late 2024 because we wanted analytics for our own side projects without
re-litigating GDPR every time we shipped a new one.

## What we believe

**Privacy is a default, not a feature.** A consent banner that asks
permission to track is a worse experience than not tracking that data
in the first place. We aggregated everything we actually use — page
views, top sources, referrers, conversion funnels — and built a tool
around that subset, then stopped.

**Open source is the substrate.** The product is MIT licensed; the
self-hosted version is the same code as the cloud version. If we go
away tomorrow, you have the source, the database schema, and a
migration path. We make it easy to leave because we want you to stay.

**Less product is more product.** We've turned down feature requests
for cohort analysis, A/B testing, session replay, and revenue
attribution. There are good tools for all of those things. None of
them belong in a tool whose job is "tell me what's happening on my
site at a glance."

## Who's working on it

Two people, full-time. Both founders. We answer support email, write
the code, ship the releases. There is no sales team because there is
no sales process — pick a tier, click the button, you're billed.

## Where we're going

The roadmap is short on purpose:

- **More integrations.** First-class Cloudflare Worker / Vercel Edge
  setup. A Caddy module for first-party proxying. A Hugo theme.
- **Better self-hosted onboarding.** A one-command installer for
  common VPS providers.
- **A CLI.** For the people (us, mostly) who'd rather curl the API
  than open a dashboard.

That's it. We will keep this short.
