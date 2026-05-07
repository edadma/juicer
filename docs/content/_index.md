---
title: Juicer
summary: A cross-platform Hugo-style static site generator written in Scala 3 — markdown-first, themable, JVM/JS/Native.
---

## Why Juicer?

Juicer is shaped after Hugo: a tree of markdown content, a tree of HTML templates, conventions that bind them together, and a CLI that turns them into a static site. It does what most docs and blog authors actually need — and not much more.

```bash
sbt 'juicerJVM/run serve -s docs -L'
```

That single command builds the docs you're reading right now, watches for changes, and reloads your browser tabs. Try it.

## What's here

The sections above split the docs into the usual three layers — get up and running fast, understand the model, then look up the exact API surface when you need it.
