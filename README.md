<img align="right" src="logo/juicer-200.png" alt="Juicer logo" width="200">

# juicer

![GitHub last commit](https://img.shields.io/github/last-commit/edadma/juicer)
![GitHub](https://img.shields.io/github/license/edadma/juicer)
![Scala Version](https://img.shields.io/badge/Scala-3.8.3-blue.svg)
![Scala.js Version](https://img.shields.io/badge/Scala.js-1.21.0-blue.svg)
![Scala Native Version](https://img.shields.io/badge/Scala_Native-0.5.11-blue.svg)

A small, cross-platform static site generator for Scala 3 — Hugo-style, built
on a stack of small libraries:

- [**markdown**](https://github.com/edadma/markdown) — CommonMark 0.31.2 parser
- [**squiggly**](https://github.com/edadma/squiggly) — Hugo/Liquid-style template engine
- [**toml**](https://github.com/edadma/toml) — site config (`site.toml`)
- [**scala-yaml**](https://github.com/VirtusLab/scala-yaml) — YAML frontmatter
- [**path**](https://github.com/edadma/path) + [**cross_platform**](https://github.com/edadma/cross_platform) — file I/O across JVM / Scala.js / Scala Native
- [**emoji**](https://github.com/edadma/emoji) — `:smile:` → 😄

## Quickstart

Several ready-to-build examples ship with the source, under
`docs/demos/`:

```bash
sbt 'juicerJVM/run build --source docs/demos/minimal'
sbt 'juicerJVM/run build --source docs/demos/handrolled-docs'
```

Each builds into `<source>/public/`. For live preview during authoring:

```bash
sbt 'juicerJVM/run serve --source docs/demos/handrolled-docs --port 8080'
```

then open `http://localhost:8080/`. Each bundled theme also has a
ready-to-preview demo site at `docs/demos/<theme>/` — see the
`themes/<theme>/` section in the docs for the live URL and a
description of what it exercises.

## Install

juicer is a tool, so it installs as one:

```sh
brew tap edadma/tap
brew trust edadma/tap
brew install edadma/tap/juicer
```

macOS on Apple silicon, and Linux on x86_64 and arm64; every release also
attaches the plain binary for each. `libuv` is the only dependency.

The asset pipeline shells out to `sass` and `esbuild` rather than vendoring
either, so a site gets the current version of both — but that pipeline runs
on the JVM only, and what installs here is the Scala Native binary, which
copies SCSS and JS through unchanged. Neither tool is worth installing for
juicer's sake alone.

Other platforms build from source:

```sh
sbt juicerNative/nativeLink        # a standalone binary
sbt 'juicerJVM/run build -s <site>'  # or straight from the build
```

The library API — `io.github.edadma.juicer.App.build(...)` and
`App.run(args)` — is there for embedding juicer in another pipeline, but the
artifact is not published to Maven Central: nobody consumes a site generator
as a dependency, so a source dependency is the way to reach it.

## Project layout

```
mysite/
├── site.toml                 # site config (overlays a built-in baseline)
├── content/                  # markdown sources with YAML frontmatter
│   ├── _index.md
│   └── posts/
│       └── hello-world.md
├── layouts/_default/         # squiggly templates that wrap content
│   ├── file.html             # used for individual pages
│   └── folder.html           # used for *_index.md* pages
├── partials/                 # reusable squiggly fragments
├── shortcodes/               # [= name args =] preprocessor templates
└── static/                   # copied as-is into the output
```

A markdown file looks like:

```markdown
---
title: Hello, World
date: 2026-05-07
tags:
  - intro
---

This is the body.
```

YAML frontmatter (between `---` lines) is parsed by **scala-yaml**; the body
goes through **markdown** with auto-generated heading IDs; the result is
rendered into the page's layout via **squiggly**.

## CLI

```
Juicer Site Generator v0.4.0
Usage: juicer [build|config|theme|serve] [options]

━━━━━ General Options ━━━━━
  -b, --baseurl <URL>      base site URL
  -c, --config <name>      base site configuration (default is 'standard')
  -h, --help               prints this usage text
  -v, --verbose            verbose output
  --version                prints the version

━━━━━ Commands ━━━━━━━━━━━━
Command: build [options]
  Build the site
  -d, --dest <path>        destination directory path
  -s, --source <path>      site sources directory path
  -D, --drafts             include draft pages (frontmatter `draft: true`)
  -F, --future             include future-dated pages (date frontmatter past `now`)
Command: config [options]
  Show build configuration
  -s, --source <path>      site sources directory path
Command: theme [add|upgrade] <args>...
  Theme management
Command: theme add [options] <git-url>
    Install a theme from a git URL into <src>/<themeDir>/
  -s, --source <path>      site sources directory path
  -n, --name <name>        install under this theme name (default: derived from URL or --subdir)
  -r, --ref <branch|tag|sha>
                           branch, tag, or commit to check out (default: repo HEAD)
  --subdir <path>          install only this subdirectory of the cloned repo as the theme
  --force                  overwrite an existing theme directory
  <git-url>                HTTPS or SSH git URL of the theme repo
Command: theme upgrade [options] [<name>]
    Re-fetch installed themes from their recorded source
  -s, --source <path>      site sources directory path
  -r, --ref <branch|tag|sha>
                           override the recorded ref for this upgrade only
  <name>                   theme to upgrade (default: every theme with .juicer-theme.toml metadata)
Command: serve [options]
  Build and serve the site
  -d, --dest <path>        destination directory path
  -s, --source <path>      site sources directory path
  --host <host>            host to bind to (default 'localhost')
  -p, --port <port>        port to listen on (default 8080)
  -D, --drafts             include draft pages (frontmatter `draft: true`)
  -F, --future             include future-dated pages (date frontmatter past `now`)
  -L, --live-reload        rebuild on source changes and reload browser tabs
```

Every command — `build`, `config`, `theme`, `serve` — runs on the JVM and
on Scala Native, which is what the released binary is. The `serve` command
is built on [microserve](https://github.com/edadma/microserve), which
provides a single static-file server / live-reload abstraction across every
runtime; live reload is long-polled rather than SSE, so a multi-page site
can't exhaust the browser's per-host connection pool.

## Site config

`site.toml` overlays one of three built-in baselines (selected by `-c`).
The `standard` baseline (default) expects the layout shown above. The
baselines themselves are in
[`BaseConfigs.scala`](shared/src/main/scala/io/github/edadma/juicer/BaseConfigs.scala).

```toml
title    = "My Site"
author   = "Ed"
baseURL  = "https://example.com"

# Optional: drive the sidebar / nav order. Items ending in a markdown
# extension reference a content file (path relative to content/);
# anything else is a section label.
nav = [
  "Getting Started",
  "_index.md",
  "guide/installation.md",
  "Reference",
  "guide/cheatsheet.md",
]
```

## Templates

Layouts and partials use **squiggly** syntax: `{{ .field }}`,
`{{ for x <- .items }}`, `{{ if cond }}`, `{{ partial 'name' . }}`,
`{{ define name }} … {{ end }}`. The page-rendering context exposes:

| key       | what                                              |
|-----------|---------------------------------------------------|
| `site`    | site config (TOML) plus `toc` and `start` nav data |
| `page`    | the page's YAML frontmatter (any-data shape)      |
| `content` | the rendered markdown body (HTML)                 |
| `toc`     | the page's heading tree                           |
| `sub`     | flattened sub-headings (for an "On this page" list) |

### Helpers juicer adds on top of squiggly's defaults

| name | what |
|------|------|
| `relURL '...'`     | site-relative URL — prepends `baseURL.path` |
| `absURL '...'`     | absolute URL — prepends `baseURL.base` + `baseURL.path` |
| `markdownify '...'`| render a string as markdown into HTML |
| `emojify '...'`    | substitute `:shortcode:` → emoji glyph |

### Markdown extras

- **Auto heading IDs** — every `<h*N*>` gets an `id` attribute slugified from
  its text content, suitable for permalinks.
- **TOC tree** — `{{ for h <- .toc.headings }}` walks each top-level heading;
  each entry has `level`, `contents`, `id`, and a nested `sub` of the same
  shape.

## Shortcodes

Inside markdown content, the bracket-equals preprocessor expands
`[= name args =]` into the template at `shortcodes/name.html`. A self-closing
shortcode is `[= name args / =]`; a paired one wraps content between
`[= name =]` and `[= /name =]`. Inside the template, positional args are
exposed as `args[i]` and named args as `key`.

## Cross-platform

`build.sbt` cross-builds for JVM, Scala.js, and Scala Native from one set of
sources. JVM is where development happens and Scala Native is what ships;
both run the full integration suite. **The Scala.js target does not link at
the moment** — the syntax highlighter's per-block timeout guard uses
`java.lang.Thread`, which Scala.js has no implementation of.

There are no JVM-only *features* — `serve` and its live-reload watcher both
use [microserve](https://github.com/edadma/microserve), which abstracts over
`java.nio` (JVM), Node `net`/`fs.watch` (JS), and libuv (Native) behind a
shared Scala API. What is JVM-only is the shelling-out: the Sass/esbuild
asset pipeline and the image-variant encoder run real commands on the JVM
and degrade to a verbatim copy elsewhere, so a site built by the released
binary still resolves every asset URL.

## Tests

```bash
sbt juicerJVM/test
sbt juicerNative/test
```

`sbt juicerJS/test` is the third of these and cannot run while the JS target
fails to link (see **Cross-platform** above).

Almost every test is an end-to-end build: the suite writes a small site into
a temp directory, runs the real build pipeline over it, and asserts the
rendered HTML. Suites are named for the feature they cover
(`PermalinksSpec`, `TaxonomiesSpec`, `I18nSpec`, ...); `JuicerBuildSpec` is
the original catch-all.

## License

[ISC](https://opensource.org/licenses/ISC)
