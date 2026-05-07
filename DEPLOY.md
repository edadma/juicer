# Deploying juicer.run

The site at <https://juicer.run/> is built with juicer (this repo) and the
`juicerdocs` theme (`docs/themes/juicerdocs/`), and deployed via GitHub
Actions.

## Production (push to `stable`)

`.github/workflows/docs.yml` runs on every push to `stable` and:

1. Builds the docs with `sbt 'juicerJVM/run build -s docs -d _site -b https://juicer.run/'`.
2. Writes a `CNAME` file containing `juicer.run`.
3. Uploads the site to GitHub Pages.

To enable the GitHub Pages deploy:

1. **Settings → Pages →** *Source* = "GitHub Actions".
2. **Custom domain** — set to `juicer.run`. GitHub will create a `_pages-cf` health-check.
3. **DNS** — point `juicer.run` at GitHub Pages with either:
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
   automatically; production maps to `juicer.run`.

## Local preview

```bash
sbt 'juicerJVM/run serve -s docs -L'
```

The `-L` flag enables live-reload — edits under `docs/` rebuild automatically
and connected browser tabs reload via SSE. See [Live reload](docs/content/getting-started/quickstart.md).
