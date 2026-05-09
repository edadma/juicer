---
title: Changelog
summary: Releases, in reverse chronological order. Major versions get a write-up; minor versions get a bullet.
date: 2026-05-09
---

## v1.0.0 — 2026-05-09

The first stable release. We've been running this in production for the
last six months across ~40 sites; the feature set hasn't moved in three.
Time to drop the beta tag.

- **Stable API.** No more breaking changes between minor versions. The
  `tally()` function signature, the `data-site` attribute, the dashboard
  REST API — all locked.
- **Self-hosted parity.** The Docker image, the SQLite default, the
  optional Postgres mode, the migration story — fully exercised by the
  cloud product. Same image, same code path.
- **Single-page-app autopilot.** History API navigations are detected
  without configuration in React Router, SvelteKit, Next.js App Router,
  Vue Router, and Astro view transitions.
- **CSV export.** Raw-events export is now available from the dashboard
  on every plan.

## v0.9.4 — 2026-04-22

- Fixed a regression where the daily salt rotation could fire twice on
  servers with a clock that jittered backward at midnight UTC.
- Added a `--dry-run` flag to the migration CLI.
- Documentation for the first-party-hosting proxy setup.

## v0.9.3 — 2026-04-08

- Real-time dashboard now updates via SSE instead of long-polling.
  Dropped the upstream NGINX timeout requirement.
- Funnel definitions can now reference custom-event properties.

## v0.9.0 — 2026-03-15

- Custom events API. `tally('signup', { plan: 'pro' })` is now a thing.
- Funnels (closed beta).
- Breaking: the `t.js` script is now under `/t.js` instead of
  `/track.js`. Old path serves a 301 redirect; will be removed in v1.1.

## v0.8.0 — 2026-02-04

- New, smaller tracker script (was 1.4 KB, now 0.9 KB gzipped).
- Initial Postgres backend support.
- Breaking: dashboard moved from `/admin` to `/dashboard`.
