# Deploying juicer.build

The site at <https://juicer.build/> is built with juicer (this repo) and the
`juicerdocs` theme (`docs/themes/juicerdocs/`), and deployed via GitHub
Actions.

## Production (push to `stable`)

`.github/workflows/docs.yml` runs on every push to `stable` and:

1. Builds the docs with `sbt 'juicerJVM/run build -s docs -d _site -b https://juicer.build/'`.
2. Builds each per-theme demo with `bash bin/build-demos.sh _site https://juicer.build/` — each demo at `docs/demos/<theme>/` gets built into `_site/themes/<theme>/demo/` and overrides the placeholder `demo.md` content render at the same path. juicerdocs has no demo source (the docs site itself is the demo).
3. Writes a `CNAME` file containing `juicer.build`.
4. Uploads the site to GitHub Pages.

To enable the GitHub Pages deploy:

1. **Settings → Pages →** *Source* = "GitHub Actions".
2. **Custom domain** — set to `juicer.build`. GitHub will create a `_pages-cf` health-check.
3. **DNS** — point `juicer.build` at GitHub Pages with either:
   - A 4× A records: `185.199.108.153`, `185.199.109.153`, `185.199.110.153`, `185.199.111.153`
   - or a single CNAME to `<your-gh-username>.github.io`
4. **HTTPS** — wait for GitHub Pages to issue a Let's Encrypt cert (a few minutes after DNS propagates), then enable "Enforce HTTPS".

## Preview (push to `dev` or PR to `stable`)

The workflow runs on `dev` pushes and PRs against `stable`, but it does **not**
deploy to Pages — it just uploads the rendered site as an artifact named
`docs-site` you can download from the Actions tab.

For automatic preview URLs per branch, swap to **Cloudflare Pages**:

1. Connect the repo to a Cloudflare Pages project.
2. Build command: `sbt 'juicerJVM/run build -s docs -d $CF_PAGES_URL'`.
3. Output directory: `_site`.
4. Cloudflare assigns `<branch>.<project>.pages.dev` for every branch
   automatically; production maps to `juicer.build`.

## Local preview

```bash
sbt 'juicerJVM/run serve -s docs -L'
```

The `-L` flag enables live-reload — edits under `docs/` rebuild automatically
and connected browser tabs reload. Reload is long-polled rather than SSE,
for the reason `serve.scala`'s scaladoc gives. See [Live reload](docs/content/getting-started/quickstart.md).

### Local preview WITH demo sites

`juicer serve` builds only the docs site; the per-theme demos at
`docs/demos/<theme>/` aren't built. So clicking "Demo site" under a
theme during local preview hits the placeholder `demo.md` page, not
the actual rendered demo. To preview a specific demo, run that demo
as its own juicer site:

```bash
sbt 'juicerJVM/run serve -s docs/demos/juicerblog -L'
```

A future enhancement could wire a single `juicer serve` invocation
to build the docs + all demos and serve them under one tree.
