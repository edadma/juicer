<img align="right" src="logo/juicer-200.png" alt="Juicer logo" width="200">

# juicer

![Maven Central](https://img.shields.io/maven-central/v/io.github.edadma/juicer_3)
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

Two ready-to-build examples ship with the source:

```bash
sbt 'juicerJVM/run build --source examples/minimal'
sbt 'juicerJVM/run build --source examples/docs-site'
```

Each builds into `<source>/public/`. For live preview during authoring:

```bash
sbt 'juicerJVM/run serve --source examples/docs-site --port 8080'
```

then open `http://localhost:8080/`.

## Install (use juicer as a library)

```scala
libraryDependencies += "io.github.edadma" %%% "juicer" % "0.1.0"
```

The CLI is the typical way to use it; the library API exposes
`io.github.edadma.juicer.App.build(...)` and `App.run(args)` for callers
that want to embed it in another build pipeline.

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
Juicer Site Generator v0.1.0
Usage: juicer [options] [command]

  -b, --baseurl <URL>     base site URL (overrides site.toml)
  -c, --config <name>     base site configuration (default 'standard';
                          others: 'simple', 'norme')
  -h, --help              prints this usage text
  -v, --verbose           verbose output
      --version           prints the version

Commands:
  build                          build the site
    -s, --source <path>            site source directory (default ./)
    -d, --dest <path>              destination directory (default ./public)

  config                         show the resolved build configuration
    -s, --source <path>            site source directory

  serve                          build the site, then serve it on localhost
    -s, --source <path>            site source directory
    -d, --dest <path>              destination directory
        --host <host>              host to bind (default 'localhost')
    -p, --port <port>              port to listen on (default 8080)
```

`serve` is JVM-only — it uses `com.sun.net.httpserver` under the hood.
`build` and `config` work on JVM, Scala.js, and Scala Native.

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

`build.sbt` cross-builds for JVM, Scala.js, and Scala Native. JVM is the
primary target right now; Scala.js and Scala Native compile from the same
sources but are less battle-tested. The `serve` command is intentionally
JVM-only — a cross-platform server library is on the roadmap (the existing
`microserve` is currently JVM-only).

## Tests

```bash
sbt juicerJVM/test
sbt juicerJS/test
sbt juicerNative/test
```

The integration test suite (`JuicerBuildSpec`) builds small sites under a
temp directory and asserts the rendered HTML structure end-to-end.

## License

[ISC](https://opensource.org/licenses/ISC)
