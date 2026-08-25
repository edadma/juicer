---
title: Configuration
summary: The site keys and page frontmatter juicerapi reads, and the two settings that decide whether the generated anchors resolve.
weight: 10
---

juicerapi inherits juicerdocs, so **every** juicerdocs setting applies unchanged
— the palette overrides under `[juicerdocs]`, `customCSS`, `logo`, `math`,
`topnav`, the search modal. See
[juicerdocs → Configuration](/themes/juicerdocs/configuration/) for those. This
page covers only what is specific to generated API pages.

## Site keys

### `slugStyle`

```toml
slugStyle = "github"
```

Selects the auto heading id algorithm. Two values:

| Value | `### starts_with` becomes | `## Buf[T]` becomes |
|---|---|---|
| `"juicer"` (default) | `#starts-with` | `#buf-t` |
| `"github"` | `#starts_with` | `#buft` |

**Generated API reference wants `"github"`.** Its pages are read twice — as a
page on this site, and as a file in the repository the code lives in — and the
per-page symbol index links to its own headings. If the two renderings disagree
about what `### starts_with` is called, one of them has a page of dead links, and
it is the repository copy that has no sidebar or search to fall back on.

It is not the default because changing an existing site's slugs breaks every
in-page link anyone has ever shared. Opt in per site — or, where the site is
already published and those links are already out in the world, **per page**;
see below.

A repeated heading gets GitHub's numeric suffix under **both** styles — the first
`### buf` keeps `#buf`, a later `### Buf` becomes `#buf-1`. That is not optional
behaviour; two elements sharing an `id` is invalid HTML and the second is
unreachable. It matters here because a type and the function that constructs it
conventionally share a name.

## Page frontmatter

### `headingShift`

```yaml
headingShift: 0
```

Overrides the site-wide `headingShift` for one page. The site default is `2`,
which assumes the layout supplies the `<h1>` and an author's `#` is a subsection
of the page title. A generated body does not fit that assumption: its `##` groups
are already meant to be `<h2>`, and they must land at the same levels here as
they do when the file is read in the repository.

Set it on every generated page. A cascade cannot set it — it is a property of how
one file was authored, not of where it sits.

### `slugStyle`

```yaml
slugStyle: github
```

Overrides the site-wide `slugStyle` for one page, and is the answer when the
site key above cannot be thrown. That is the ordinary case for an established
documentation site: its hand-written headings were published with juicer's
anchors, turning `"github"` on rewrites every one of them that carries an
apostrophe, an em dash or a backticked snake_case name, and those are links
other people have saved.

Anchors are per page — nothing outside a page's own headings is named by them,
and its table of contents is built from those same headings — so a generated
section can carry GitHub's algorithm while the prose beside it keeps the anchors
it was published with.

Set it on every generated page, beside `headingShift`. A value that is not a
string falls back to the site setting, and an unrecognised style falls back to
`"juicer"`; neither fails the build.

### The `api-module` layout

```yaml
---
title: bristle.text
layout: api-module
headingShift: 0
module: bristle.text
summary: Searching, splitting and trimming over UTF-8 strings.
package: bristle
coordinate: github.com/example/bristle
requires: "{}"
since: "0.1.0"
---
```

| Key | Required | What it does |
|---|---|---|
| `layout` | yes | must be `api-module` |
| `title` | yes | the browser title and the sidebar entry |
| `module` | no | the dotted module path — sets the big mono heading and the copyable **Import** line |
| `summary` | no | the lead paragraph, run through markdown |
| `package` | no | the package a module outside the standard library came from |
| `coordinate` | no | that package's dependency coordinate |
| `requires` | no | the capability clause, shown in the metadata strip |
| `since` | no | the version the module first appeared in |

With no `module`, the heading falls back to `title` and the import line is
omitted. The metadata strip renders only the rows that are present, and disappears
entirely when none of the four is.

### The `api-index` layout

```yaml
---
title: Bristle
layout: api-index
headingShift: 0
summary: A small library for text and byte buffers.
version: 0.4.0
---
```

`version` renders a "Generated from …" line under the lead. The module list is the
page **body**, not something the template walks out of the section graph — the
generated Markdown has to stand on its own when GitHub renders it with no theme,
so a list only this template could produce would be a list missing from the
artifact people actually read in the repo.

## Syntax highlighting

Signatures highlight if the site carries a grammar for the language. Drop a
`<lang>.tmLanguage.json` into `grammars/` at the site root and fence the
signature blocks with that language name; the demo ships sysl's. With no grammar
the signature renders as plain preformatted text, correctly styled — highlighting
is the one thing here that degrades rather than breaks.
