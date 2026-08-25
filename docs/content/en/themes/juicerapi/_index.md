---
title: juicerapi
summary: Generated API reference, layered on juicerdocs. Module headers, symbol indexes, dense signature blocks and parameter tables — for pages a doc generator writes rather than a person.
weight: 100
---

`juicerapi` is the one bundled theme that is **not** a look of its own. It
inherits [juicerdocs](/themes/juicerdocs/) and adds furniture for a single page
shape: the API reference a documentation generator emits from a codebase's own
doc comments.

That inheritance is the whole design. An API section and a hand-written guide on
one site have to read as one site, and the reliable way to fail at that is to
build two independent themes and try to keep them matching. juicerapi ships no
palette, no fonts and no spacing scale — every value in it is one of juicerdocs'
tokens, so a `[juicerdocs] brand = …` line in `site.toml` restyles the guides and
the API pages together, and neither can drift.

It lives at `docs/themes/juicerapi/` in the juicer repo. To use it:

```bash
juicer theme add https://github.com/edadma/juicer.git -n juicerapi
```

…and set `theme = "juicerapi"` in `site.toml`. Because it inherits juicerdocs,
naming juicerapi alone pulls in juicerdocs and juicercommon behind it.

## It overrides no default layout

This is worth stating plainly, because it is what makes the theme safe to add to
a site that already exists. juicerapi ships **`api-module.html`** and
**`api-index.html`**, and neither is a default — they are reached by `layout:`
frontmatter on the generated pages:

```yaml
---
title: bristle.text
layout: api-module
module: bristle.text
headingShift: 0
---
```

Every page already on the site keeps rendering through juicerdocs' `file.html`
and `folder.html` exactly as before. Adding this theme changes nothing you did
not ask it to change.

## The generated Markdown is the contract

The theme styles four shapes and nothing else:

| Markdown | Becomes |
|---|---|
| `## Functions` | a kind-group label — the signposts on a long page |
| `### \`push\`` | one symbol entry, separated by a rule |
| a fenced block directly under a symbol | that symbol's signature, given the brand rail |
| a pipe table | the parameter, field or variant table |

Plus `## Index` at the top, whose links become pills.

**None of that needs a wrapper `<div>` or a CSS class, and that is deliberate.**
The point of generating Markdown rather than a self-contained HTML site is that
the same file is readable in the repository it documents — on GitHub, with no
tooling, no hosting and no juicer. A generator that emitted `<div class="…">` to
give this theme something to hook would produce a file that reads *worse* in the
repo than in the browser, which is backwards. So the theme hooks structure and
slugified heading ids, and a generator that emits plain Markdown gets the whole
theme for free.

A generator that emits something else still renders — as ordinary Markdown,
because that is all any of it ever was.

## Two settings that matter

Both are documented in [Configuration](./configuration/), but they are the two
that decide whether the pages are *correct* rather than merely styled:

Both go in each generated page's frontmatter, which is where the demo sets them:

- **`slugStyle: github`**. Generated API headings are snake_case names, and
  juicer's default slug turns `starts_with` into `starts-with` while GitHub keeps
  `starts_with`. The same file is read in both places, so without this one of the
  two renderings has a page of dead links. There is a site key of the same name,
  and on a site that already has hand-written prose it is the wrong one to
  reach for — it would rewrite the anchors those pages were published with.
- **`headingShift: 0`**. The site-wide default assumes a layout supplies the
  `<h1>` and an author's `#` sits beneath it. A generated body's `##` is already
  meant to be an `<h2>` — and has to land at the same level here as it does in
  the repo.

## When to pick something else

If you are hand-writing prose about an API — arguments, worked examples, the
reasoning behind a signature — you want **juicerdocs**, not this. Generated
reference and written explanation are different documents with different jobs,
and the best sites carry both: an API listing the hand-written pages link into,
rather than a wall of signatures standing in for an explanation nobody wrote.

The [Demo site](./demo/) — Bristle, a two-module toy library — is the canonical
example, and doubles as the format specification.
