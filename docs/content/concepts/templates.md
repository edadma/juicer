---
title: Templates
summary: How squiggly templates render content into HTML.
weight: 20
---

Templates live under `layouts/` and are written in [squiggly](https://github.com/edadma/squiggly), a Go-template-style language. Every template is plain HTML with `{{ ... }}` interpolation.

## Template kinds

| Kind          | Filename pattern             | When it's used |
|---------------|------------------------------|----------------|
| `baseof`      | `_default/baseof.html`       | Outer shell — wraps every page |
| `file`        | `_default/file.html`         | Single-page template |
| `folder`      | `_default/folder.html`       | Section-index template |
| Partial       | `partials/<name>.html`       | Called via `{{ partial 'name' . }}` |
| Shortcode     | `shortcodes/<name>.html`     | Called from markdown via `\[= name =]` |

The `_default/` folder name comes from the `defaultLayout` config key. You can also place per-section overrides in `layouts/<section>/` — juicer falls back to `_default/` when a section-specific layout is missing.

## The two-pass render

When both a particular template (`file.html` or `folder.html`) AND a `baseof.html` exist:

[= steps =]
## First pass — the particular template runs

Its output is **discarded**. What matters are the `{{ define <name> }}…{{ end }}` blocks it populates.

## Second pass — `baseof.html` runs

Its `{{ block <name> . }}…{{ end }}` calls pull the previously-defined content into place.
[= /steps =]

This pattern lets a single `baseof.html` provide the entire page chrome (head, header, sidebar, footer) while `file.html` and `folder.html` only describe what goes in the main column.

[= tabs =]
[= tab "baseof.html" =]
```html
<!DOCTYPE html>
<html>
<head><title>{{ .page.title }}</title></head>
<body>
  {{ partial 'topbar' . }}
  <main>
    {{ block main . }}{{ .content }}{{ end }}
  </main>
</body>
</html>
```
[= /tab =]
[= tab "file.html" =]
```html
{{ define main }}
  <article class="prose">
    <h1>{{ .page.title }}</h1>
    {{ .content }}
  </article>
{{ end }}
```
[= /tab =]
[= tab "folder.html" =]
```html
{{ define main }}
  <article class="prose">
    <h1>{{ .page.title }}</h1>
    {{ .content }}
  </article>

  <ul>
    {{ for p <- .section.pages }}
      <li><a href="{{ p.url }}">{{ p.title }}</a></li>
    {{ end }}
  </ul>
{{ end }}
```
[= /tab =]
[= /tabs =]

## The data context

Every page renders against a fixed data context:

| Variable      | What it holds |
|---------------|---------------|
| `.site`       | Site config + `pages`, `pagesByPath`, `root` |
| `.page`       | The current page record (frontmatter + url + summary + nav fields) |
| `.section`    | Enclosing section's pages + subsections + index |
| `.content`    | Rendered markdown body |
| `.toc`        | Heading tree |
| `.sub`        | First heading's children, flattened |

Inside `.page`:

- `.page.title`, `.page.summary` — from frontmatter (summary is auto-derived if absent)
- `.page.url`, `.page.relPermalink`, `.page.permalink` — three URL flavors
- `.page.parent`, `.page.ancestors` — section navigation
- `.page.next`, `.page.prev` — sibling navigation
- `.page.isSection` — `true` for `_index.md` pages
- Plus any custom frontmatter key

See [Reference / Template data](/reference/template-data/) for the full set.
