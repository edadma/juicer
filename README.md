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

> A more thorough docs site, built with juicer itself, is in progress.
> This README is the short version.

## Status

`v0.1.0`. The cross-platform port is just landing; the JVM build is well-shaken
through `examples/`. Scala.js and Scala Native compile from the same sources
but are less battle-tested.

## Install

```scala
libraryDependencies += "io.github.edadma" %%% "juicer" % "0.1.0"
```

Or as a published binary, run via sbt:

```bash
sbt 'juicerJVM/run build -s ./mysite'
```

## Project layout

```
mysite/
├── site.toml                 # site config (overlays a built-in baseline)
├── content/                  # markdown sources, with YAML frontmatter
│   ├── _index.md
│   └── posts/
│       └── hello-world.md
├── layouts/                  # squiggly templates that wrap content
│   ├── _default/
│   │   ├── baseof.html
│   │   ├── file.html
│   │   └── folder.html
├── partials/                 # reusable squiggly fragments
├── shortcodes/               # `[= name … =]` template fragments
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
goes through **markdown** and the result is rendered into the page's layout
via **squiggly**.

## CLI

```
Juicer Site Generator v0.1.0
Usage: juicer [options] [command]

  -b, --baseurl <URL>     base site URL
  -c, --config <name>     base site configuration (default 'standard';
                          others: 'simple', 'norme')
  -h, --help              prints this usage text
  -v, --verbose           verbose output
      --version           prints the version

Commands:
  build  -s <path> -d <path>     build the site (defaults: ./, ./public)
  config -s <path>               show the resolved build configuration
  serve  -s <path> -d <path>     build and serve (not yet wired)
```

## Site config

`site.toml` overlays a built-in baseline (`standard` by default). The
baselines live in [`BaseConfigs.scala`](shared/src/main/scala/io/github/edadma/juicer/BaseConfigs.scala);
the `standard` baseline expects the layout shown above.

```toml
title    = "My Site"
author   = "Ed"
baseURL  = "https://example.com"
```

## Templates

Layouts and partials use **squiggly** syntax: `{{ .field }}`, `{{ for x <- .items }}`,
`{{ if cond }}`, etc. Pages can override blocks defined in a `baseof.html`
layout via `{{ define name }}`.

The page-rendering context exposes:

| key       | what                                                       |
|-----------|------------------------------------------------------------|
| `site`    | the resolved site config + nav data                        |
| `page`    | the page's YAML frontmatter (any-data shape)               |
| `content` | the rendered markdown body (HTML)                          |
| `toc`     | the page's heading tree (auto-generated anchors)           |
| `sub`     | flattened sub-headings (for sidebars)                      |

## Cross-platform

`build.sbt` cross-builds for JVM, Scala.js, and Scala Native. JVM is the
primary target right now; the JS build runs under Node and the Native build
produces a standalone executable. All three exercise the same shared code
through the `path` and `cross_platform` libraries.

## License

[ISC](https://opensource.org/licenses/ISC)
