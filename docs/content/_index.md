---
title: Juicer
summary: A cross-platform Hugo-style static site generator written in Scala 3.
---

Juicer is a cross-platform static site generator inspired by Hugo. It runs on the JVM, Scala.js (Node), and Scala Native, so you can pick the runtime that fits your build pipeline.

This site — the one you're reading — is built with juicer, using the **juicerdocs** theme.

```bash
# Get started
sbt 'juicerJVM/run build -s docs'
sbt 'juicerJVM/run serve -s docs'
```

## Why juicer?

- **Familiar** — content/, layouts/, partials/, shortcodes/, themes/ — Hugo conventions, no new vocabulary to learn.
- **Markdown-first** — full CommonMark 0.31.2 plus optional GFM extensions, math, callouts, and emoji.
- **Themable** — drop a directory under `themes/<name>/`, set `theme = "<name>"` in `site.toml`, done.
- **Cross-platform** — JVM for everyday use; Scala.js for Node-driven CI; Scala Native for static binaries.
- **Search-ready** — every build emits a `search.json` you can wire up with three lines of JS.
- **Zero JavaScript by default** — themes ship JS only when they need it, and you choose.

## What's here

The sections below cover everything from your first build to the full template reference.
