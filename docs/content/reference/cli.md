---
title: CLI
summary: The juicer command-line tool — build, serve, config, theme.
weight: 10
---

Juicer's CLI has four subcommands: `build`, `serve`, `config`, and
`theme` (with `add` / `upgrade` sub-subcommands). Every command runs
on JVM, Scala.js (Node), and Scala Native — `serve` and its
live-reload watcher use [microserve](https://github.com/edadma/microserve)
for cross-platform HTTP and file-watching.

## Global options

These apply to **every** subcommand. Place them before the subcommand
name on the command line:

| Flag                    | What |
|-------------------------|------|
| `-b`, `--baseurl <URL>` | Override `baseURL` from the config — typically used in CI for environment-specific URLs (`https://staging.example.com/` vs `https://example.com/`). |
| `-c`, `--config <name>` | Pick a baseline config: `simple`, `standard` (default), or `norme`. Your `site.toml` overlays on top of the chosen baseline. |
| `-h`, `--help`          | Print usage and exit. |
| `-v`, `--verbose`       | Print step-by-step build progress — layout selection, file walks, what's being written where. Indispensable when a page is being silently skipped. |
| `--version`             | Print the version and exit. |

```bash
sbt 'juicerJVM/run --baseurl https://staging.example.com/ build -s docs'
sbt 'juicerJVM/run --config simple build -s notes'
sbt 'juicerJVM/run -v build -s docs'
```

## `build`

Render the site to disk. Output goes to `<src>/<publicDir>` (default
`<src>/public/`) unless `-d` overrides.

```bash
sbt 'juicerJVM/run build -s docs'
sbt 'juicerJVM/run build -s docs -d _site -b https://juicer.run/'
sbt 'juicerJVM/run build -s blog -D -F'
```

| Flag                  | Default              | What |
|-----------------------|----------------------|------|
| `-s`, `--source <p>`  | `.`                  | Site source directory. |
| `-d`, `--dest <p>`    | `<src>/<publicDir>`  | Output directory. Overrides the `publicDir` config key. |
| `-D`, `--drafts`      | off                  | Include pages with `draft: true` frontmatter. Drafts are normally invisible to every downstream consumer — TOC, sitemap, search index, section listings. |
| `-F`, `--future`      | off                  | Include pages whose parsed `date:` is past the current build time. Future-skip applies only to pages with **explicit** `date:` frontmatter — mtime-fallback dates are never future-skipped. Pages in the `eventsSection` (default `events`) are also exempt from future-skip. |

[= tip =]
The classic CI build, e.g. for a GitHub Pages workflow:

```bash
sbt 'juicerJVM/run build -s docs -d _site -b https://example.github.io/repo/'
```
[= /tip =]

## `serve`

Build the site once, then serve it on `localhost`. Optionally watch
the source and rebuild on every change.

```bash
sbt 'juicerJVM/run serve -s docs'
sbt 'juicerJVM/run serve -s docs -L'
sbt 'juicerJVM/run serve -s docs --host 0.0.0.0 -p 4000'
```

| Flag                  | Default              | What |
|-----------------------|----------------------|------|
| `-s`, `--source <p>`  | `.`                  | Site source directory. |
| `-d`, `--dest <p>`    | `<src>/<publicDir>`  | Output directory. Excluded from the live-reload watch loop so builds don't trigger themselves. |
| `--host <h>`          | `localhost`          | Bind host. Use `0.0.0.0` to expose the dev server on the LAN. |
| `-p`, `--port <p>`    | `8080`               | Listen port. If the port is already bound, juicer scans up to 20 ports upward (`:8081`, `:8082`, …) and prints the actual port it landed on. |
| `-D`, `--drafts`      | off                  | Include drafts (same semantics as `build -D`). |
| `-F`, `--future`      | off                  | Include future-dated pages (same semantics as `build -F`). |
| `-L`, `--live-reload` | off                  | Watch the source recursively, rebuild on change, and live-reload connected browser tabs. |

### Live reload — how it works

When `-L` is on, every HTML response gets a tiny long-polling client
script injected before `</body>`. The script calls
`GET /__juicer/wait?since=<version>`; the server holds each poll open
until the next rebuild (or 30s, whichever comes first) and responds
with `{reload, version}`. The client calls `location.reload()` on
`reload: true`.

Long-polling — not SSE — was chosen because SSE leaks a connection
during page-unload across browsers, and HTTP/1.1 connection-limit
pooling makes the failure mode look like "live-reload silently
stopped working." A fresh `fetch` per cycle has neither problem.

The watcher is debounced at 150ms, so saving five files in rapid
succession results in one rebuild and one reload. Saves to the
output directory (`-d`) are ignored to prevent build-triggers-itself
loops.

## `config`

Print the resolved configuration after the chosen baseline,
`site.toml` overlay, and CLI overrides are merged. Useful for
debugging "why is `htmlDir` doing that?" — the answer is right
there in the printed config.

```bash
sbt 'juicerJVM/run config -s .'
sbt 'juicerJVM/run --config simple config -s notes'
```

| Flag                  | Default | What |
|-----------------------|---------|------|
| `-s`, `--source <p>`  | `.`     | Site source directory. |

## `theme add`

Install a theme from a git repository into `<src>/<themeDir>/`. After
running it, set `theme = "<name>"` in your `site.toml` to activate.

```bash
sbt 'juicerJVM/run theme add https://github.com/edadma/juicer.git -n juicerdocs'
sbt 'juicerJVM/run theme add https://github.com/me/my-theme.git -r v1.2.0'
sbt 'juicerJVM/run theme add https://github.com/edadma/juicer.git -n juicerblog --subdir docs/themes/juicerblog'
```

| Flag                          | Default                 | What |
|-------------------------------|-------------------------|------|
| `<git-url>` (positional)      | required                | HTTPS or SSH URL of the theme repository. |
| `-s`, `--source <p>`          | `.`                     | Site source directory. The theme is installed under `<src>/<themeDir>/`. |
| `-n`, `--name <name>`         | derived from URL/subdir | Directory name to install under. Required when juicer can't infer a sensible name from the URL. |
| `-r`, `--ref <ref>`           | repo HEAD               | Branch, tag, or commit SHA to check out. Lock to a tag for reproducible builds. |
| `--subdir <path>`             | repo root               | Install only this subdirectory of the cloned repo. Useful when one repo ships several themes (e.g. juicer ships all six under `docs/themes/`). |
| `--force`                     | off                     | Overwrite an existing theme directory. Without this, an existing `<themeDir>/<name>/` aborts the install. |

The metadata for an `upgrade` later (URL, ref, subdir) is recorded in
`<themeDir>/<name>/.juicer-theme.toml`. Don't commit that file is up
to you — it's small, it's plain TOML, and committing it means
collaborators get the same theme version after `git pull` without
re-running `theme add`.

[= note =]
**`theme add` only installs the theme directory.** You still have to
set `theme = "<name>"` in `site.toml` to activate it — juicer doesn't
guess which of your installed themes you want active. Run
`juicer config -s .` after editing `site.toml` to verify the resolved
`theme` value.
[= /note =]

## `theme upgrade`

Re-fetch one or every installed theme from the URL+ref recorded in
its `.juicer-theme.toml`. Use this after the upstream theme has
shipped a fix and you want to pull it in.

```bash
sbt 'juicerJVM/run theme upgrade'
sbt 'juicerJVM/run theme upgrade juicerblog'
sbt 'juicerJVM/run theme upgrade juicerblog -r v1.3.0'
```

| Flag                          | Default        | What |
|-------------------------------|----------------|------|
| `<name>` (positional)         | every theme    | Which theme to upgrade. Omitted = upgrade every theme that has a `.juicer-theme.toml`. |
| `-s`, `--source <p>`          | `.`            | Site source directory. |
| `-r`, `--ref <ref>`           | recorded ref   | Override the recorded ref for this upgrade only. Doesn't rewrite `.juicer-theme.toml` — the next bare `theme upgrade` returns to the previously-recorded value unless you also re-run `theme add`. |

## Exit codes

| Code | Meaning |
|------|---------|
| `0`  | Success. |
| `1`  | Any failure — bad source path, missing required config, parse error in `site.toml`, template render error, git failure in `theme add`/`upgrade`. Run with `-v` for the verbose stack trace. |
