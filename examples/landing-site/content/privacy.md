---
title: Privacy policy
summary: A short, plain-language description of what data Tally processes and what we do with it. The lawyer-friendly version is at /privacy/full/.
date: 2026-04-01
---

This is the version of the privacy policy written for humans. The
formal document lives at [`/privacy/full/`](/privacy/full/) and
governs in case of conflict.

## What Tally collects

For each page view on a site that uses Tally, we receive:

- The **URL path** of the page (without query strings, unless the site
  owner explicitly opts in).
- The **referrer** (the page the visitor came from, if any), normalised
  to the domain.
- A coarse **country code** derived from the visitor's IP address. The
  IP itself is **not stored**.
- The **device class** (desktop / tablet / mobile) and the **browser
  family**, derived from the User-Agent string. The full UA string is
  **not stored**.
- A **daily visitor ID** that is the SHA-256 of `(IP, UA, today's
  rotating salt)`. The salt is regenerated every 24 hours and the
  previous salt is discarded. This means we cannot link a visitor's
  activity across calendar days.

That's everything. We do not set cookies. We do not fingerprint. We
do not retain anything that could be used to re-identify a specific
person.

## What we do with it

We aggregate it into the dashboard the site owner sees. That's the
entire purpose of the product.

We do **not**:

- Sell data to third parties.
- Share data with advertisers.
- Use the data to train any model, ours or anyone else's.
- Combine data across sites in any way that would let us build a
  profile of an individual.

## Where the data lives

For Cloud customers: in our database in Frankfurt (Hetzner). Daily
backups go to a second region; both regions are inside the EU.

For Agency customers: same, with optional region selection at
checkout.

For Self-hosted: on your server. We never see it.

## Your rights

If you are an end user of a site that uses Tally:

- We don't have any data that could let us find your activity in our
  database, because we explicitly designed it that way.
- The site owner who runs Tally on their own infrastructure may have
  data subject to their privacy policy. Contact them.

If you are a Tally customer:

- Export, delete, or modify your account data at any time from the
  dashboard.
- Email `privacy@tally.dev` for anything the dashboard doesn't cover.
