---
title: Deploy to GitHub Pages
summary: A copy-pasteable GitHub Actions workflow that builds your juicer site on every push to `stable` and publishes it to GitHub Pages.
weight: 40
---

This recipe is the same flow [juicer.build](https://juicer.build/)
itself uses (and the same flow used by [squiggly's
docs](https://edadma.github.io/squiggly/)). It builds your site
with juicer inside a GitHub Actions runner and pushes the result
to GitHub Pages — no third-party build service, no separate
deploy step, no manual `gh-pages` branch management.

[= note =]
This page assumes you already have a working juicer site building
locally — that `sbt 'juicerJVM/run build -s docs'` produces a tree
of HTML you're happy with. If you haven't gotten there yet, work
through [Quickstart](/getting-started/quickstart/) first.
[= /note =]

## The shape of the deploy

```
push to `stable`  ──▶  build job  ──▶  deploy job  ──▶  https://<owner>.github.io/<repo>/
                       (sbt build)     (actions/deploy-pages)
```

Two GitHub Actions jobs, one workflow file. The `build` job runs
`juicer build` and uploads the rendered site as a Pages artifact;
the `deploy` job, gated on the `stable` branch, takes that
artifact and publishes it to the `github-pages` environment.
Pushes to other branches still run `build` (a sanity check), but
the artifact is uploaded as a downloadable file rather than
published.

## Prerequisites

[= steps =]
## A git repo on GitHub with your site source in it

Anywhere under the repo root is fine — the workflow assumes
`docs/` (so `sbt 'juicerJVM/run build -s docs'` works). Adjust
the `-s` path if your source lives somewhere else.

## A long-lived branch named `stable` for production deploys

The workflow only deploys on pushes to `stable`. Use a separate
`dev` (or `main`) branch for in-progress work, and merge into
`stable` when you want to publish. This stops every `git push`
from triggering a deploy.

If you'd rather deploy from `main` directly, swap `stable` →
`main` in the workflow's `on.push.branches` and the deploy job's
`if:` guard.

## GitHub Pages enabled with "GitHub Actions" as the source

In your repository: **Settings → Pages → Source: "GitHub
Actions"**. Setting this to "Deploy from a branch" instead won't
work — the workflow uses the Actions-based deploy path.
[= /steps =]

## The workflow file

Drop this at `.github/workflows/docs.yml`:

```yaml
# Build & deploy <https://<owner>.github.io/<repo>/>.
#
#   Push to `stable`       → builds and deploys to GitHub Pages.
#   PR targeting `stable`  → builds (sanity check), no deploy.
#   workflow_dispatch      → manual trigger from the Actions tab.

name: Docs

on:
  push:
    branches: ["stable"]
  pull_request:
    branches: ["stable"]
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: "docs-${{ github.ref }}"
  cancel-in-progress: false

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      # actions/setup-java does NOT ship sbt. Install it via the
      # official sbt-maintained action.
      - name: Set up sbt
        uses: sbt/setup-sbt@v1

      - name: Cache sbt
        uses: actions/cache@v4
        with:
          path: |
            ~/.sbt
            ~/.cache/coursier
          key: ${{ runner.os }}-sbt-${{ hashFiles('**/build.sbt', '**/project/**') }}
          restore-keys: ${{ runner.os }}-sbt-

      - name: Build docs site
        run: sbt 'juicerJVM/run build -s docs -d _site -b https://<owner>.github.io/<repo>/'

      - name: Upload site artifact
        uses: actions/upload-artifact@v4
        with:
          name: docs-site
          path: _site

      - name: Upload Pages artifact
        if: github.ref == 'refs/heads/stable'
        uses: actions/upload-pages-artifact@v3
        with:
          path: _site

  deploy:
    if: github.ref == 'refs/heads/stable'
    needs: build
    runs-on: ubuntu-latest
    permissions:
      pages: write
      id-token: write
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    steps:
      - name: Deploy to GitHub Pages
        id: deployment
        uses: actions/deploy-pages@v4
```

Substitute `<owner>` and `<repo>` in the `Build docs site` step's
`-b` flag (e.g. `-b https://edadma.github.io/juicer/`). Getting
this wrong is the single most common deploy bug — every link
juicer emits is computed against `baseURL`, so a stale value
sends visitors to the wrong host.

[= warning =]
**The trailing slash matters.** `baseURL = "https://example.com/repo"`
and `baseURL = "https://example.com/repo/"` produce different
URLs in some edge cases (notably the sitemap and `og:url` tags).
Always include the trailing slash.
[= /warning =]

## What each step does

[= steps =]
## Checkout

Pulls the repo onto the runner. Default checkout depth is fine —
juicer doesn't need git history for anything.

## Set up JDK 17

Juicer is Scala 3 and runs on JVM 17+. `temurin` is the standard
free OpenJDK distribution.

## Set up sbt

`actions/setup-java` only installs the JDK. sbt is a separate tool
and ships via `sbt/setup-sbt@v1`. This step is non-obvious; copy
it verbatim.

## Cache sbt

The slow part of a cold juicer build is sbt fetching its plugins
and dependencies from Maven Central. Caching `~/.sbt` and
`~/.cache/coursier` keyed on `build.sbt` + `project/` files cuts
a typical build from ~3 minutes to ~30 seconds.

## Build docs site

The actual juicer call. `-s docs` points at the source directory;
`-d _site` puts the output where the next step expects it; `-b`
sets the absolute base URL for permalinks, sitemaps, and `og:url`
tags.

## Upload site artifact

Uploads the rendered site as a regular GitHub Actions artifact
named `docs-site`. PR builds — which don't deploy — still produce
this, so you can download it from the Actions tab and inspect the
output before merging.

## Upload Pages artifact

The Pages-specific upload, gated on `stable`. Pages artifacts are
distinct from regular artifacts — they go through a separate
TAR-based pipeline that the `deploy-pages` action consumes.

## Deploy to GitHub Pages

Reads the Pages artifact and publishes it to the `github-pages`
environment, also gated on `stable`. The deployment URL surfaces
in the Actions UI and in the Pages settings page.
[= /steps =]

## Project-pages vs user/org pages

There are two GitHub Pages URL shapes, and the `-b` flag has to
match the one you're using:

| Site type        | Repository naming        | `baseURL`                              |
|------------------|--------------------------|----------------------------------------|
| **Project pages**| any name (e.g. `juicer`) | `https://<owner>.github.io/<repo>/`    |
| **User pages**   | `<owner>.github.io`      | `https://<owner>.github.io/`           |
| **Org pages**    | `<org>.github.io`        | `https://<org>.github.io/`             |

Project pages have a path prefix (`/<repo>/`); user/org pages
don't. Get this wrong and every internal link 404s. Run
`juicer config -s docs -b <your-url>` locally to print the
resolved `baseURL` and double-check before you push.

## Custom domain (`juicer.build`-style)

To deploy to a domain you own — `docs.example.com`,
`example.com`, or anything else — extend the workflow with two
small things:

[= steps =]
## Add a `CNAME` file write to the workflow

After the build step, before the Pages upload, drop a line:

```yaml
- name: Write CNAME
  if: github.ref == 'refs/heads/stable'
  run: echo 'docs.example.com' > _site/CNAME
```

GitHub Pages reads `CNAME` out of the deployed artifact and sets
the custom-domain binding from it. Without this file, GitHub
keeps serving the page at `<owner>.github.io/<repo>/`.

## Point DNS at GitHub Pages

For a **subdomain** (`docs.example.com`), set a CNAME record:

| Host            | Type   | Value                  |
|-----------------|--------|------------------------|
| `docs`          | CNAME  | `<owner>.github.io`    |

For an **apex domain** (`example.com`), set four A records (and
optionally four AAAA for IPv6 — GitHub publishes the v6 set
alongside the v4):

```
185.199.108.153
185.199.109.153
185.199.110.153
185.199.111.153
```

## Enable HTTPS

In the repo: **Settings → Pages → Custom domain**, fill in the
domain, wait for the DNS check to go green (a few seconds to a
few minutes depending on TTL). Then GitHub auto-issues a Let's
Encrypt cert (another few minutes). Finally tick **Enforce
HTTPS**.

## Set `baseURL` to the custom domain

Update the `Build docs site` step's `-b` flag to
`https://docs.example.com/` (or whatever your domain is). On
project repos with a custom domain, the path prefix is gone —
your URL no longer has `/<repo>/` in it.
[= /steps =]

## Pull-request previews

The workflow as written uploads `docs-site` as a regular artifact
on every PR — you can download it from the Actions tab and serve
it locally:

```bash
unzip docs-site.zip -d /tmp/preview
cd /tmp/preview
python3 -m http.server 8000
```

For true per-PR preview URLs (each branch gets its own
`<branch>.<project>.pages.dev`), swap GitHub Pages for
**Cloudflare Pages**: connect the repo to a Cloudflare Pages
project, set the build command to
`sbt 'juicerJVM/run build -s docs -d _site -b $CF_PAGES_URL'`,
and the output directory to `_site`. Cloudflare assigns preview
URLs automatically; production maps to your custom domain.

## Troubleshooting

### Workflow fails on `Upload Pages artifact` with "Pages is not enabled"

You haven't enabled Pages yet. **Settings → Pages → Source:
"GitHub Actions"**. The first deploy after enabling can take a
minute or two for the environment to register.

### Site builds but every link 404s

`baseURL` is wrong. Run
`sbt 'juicerJVM/run config -s docs -b <your-url>'` locally and
inspect the `baseURL` value — it should match what visitors type
in the browser. Common mistakes: missing trailing slash, wrong
`/<repo>/` prefix for user-page deploys.

### CSS and JS load, but images 404

The image paths in your markdown are absolute (`/img/x.png`) but
your site is under a project path prefix (`/<repo>/`). Two
fixes:

- Use the `relURL` template helper on every link/image emitted by
  layouts — `<img src="{{ relURL '/img/x.png' }}">`.
- Inside markdown body content, write paths *without* the leading
  slash (`img/x.png`) and let the markdown renderer compute
  them relative to the page.

### Build succeeds locally but fails in CI

Two usual suspects:

- **sbt version**: ensure your local `project/build.properties`
  pins an sbt version that the runner's `sbt/setup-sbt@v1` knows
  about. Latest 1.x.x is always fine.
- **JDK version**: CI uses `java-version: "17"`. If you've been
  building locally with JDK 21, check the build for any Java
  21-only API usage and pin your local JDK to 17 for parity.

### Deploys hang at "Deploy to GitHub Pages"

Concurrent deploys queue. Check **Actions → recent runs** — an
earlier run is probably still finishing. The `concurrency:` block
prevents stacked deploys from the same ref, but cross-ref deploys
(e.g. `stable` + a tag push at the same time) wait their turn.

## See also

- [DEPLOY.md](https://github.com/edadma/juicer/blob/main/DEPLOY.md) in the
  juicer repo — the file describing juicer.build's own deploy. The shape
  is identical to this page; differences are noted in DEPLOY.md.
- [GitHub Pages docs](https://docs.github.com/en/pages) for the platform-side
  detail (HTTPS, CNAME, custom domains, request limits).
