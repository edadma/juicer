---
title: Demo site
summary: Bristle — a two-module toy library documented the way a generator emits it. Doubles as the format specification.
weight: 20
---

This page is the placeholder for the live juicerapi demo. When the docs site is
built and deployed, this URL serves the actual demo rendered with the juicerapi
theme — a separate juicer build from `docs/demos/juicerapi/` dropped on top of
the docs render at the same path.

## What the demo exercises

**Bristle** is a library that does not exist. Its two modules are written the way
a documentation generator emits them, which makes the demo the **format
specification** as much as a showcase — if you are writing a generator that
targets this theme, read `docs/demos/juicerapi/content/` and emit that.

- **`bristle.text`** — a module with no allocator. Functions, a struct, an enum
  and a trait, so all four kind groups appear. Its `Utf8Error` shows a variant
  table; its `trim` shows a `> [!NOTE]` callout.
- **`bristle.buf`** — a module that needs one, so the **Requires** row in the
  metadata strip reads `{ alloc }` rather than `{}`. It also carries the case the
  duplicate-id handling exists for: the constructor `buf()` and the type `Buf`
  share a name, so their anchors are `#buf` and `#buf-1`.
- **A repalette** — `site.toml` sets `[juicerdocs] brand` to teal. Nothing in
  juicerapi's own stylesheet changes, which is the point: the theme reads
  juicerdocs' tokens and has no colours of its own.

## Preview locally

```bash
sbt 'juicerJVM/run serve -s docs/demos/juicerapi -L'
```

The `-L` flag enables live reload — touch any markdown file under
`docs/demos/juicerapi/content/` and open browser tabs reload.

## Read it on GitHub too

Open `docs/demos/juicerapi/content/text.md` in the repository and compare. It is
the same file, with no theme, no CSS and no juicer — and it reads correctly,
including the symbol index at the top, whose links resolve there as well as here.

That comparison *is* the argument for generating Markdown rather than a
self-contained HTML site. A package with fifty repos and no docs hosting still
gets a readable API reference in its own `docs/` folder, and Rust needs an entire
hosted service — docs.rs — to solve the same problem for crates.

## Use it as a template

Copy `docs/demos/juicerapi/` as a starting point, or point your generator at the
shapes in `content/`. The four the theme styles are `## Kind group`,
`### \`symbol\``, a fenced block directly under a symbol, and a pipe table — plus
`## Index` at the top. Emit those and the theme applies itself.
