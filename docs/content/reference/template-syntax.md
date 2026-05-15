---
title: Template syntax
summary: Squiggly cheatsheet — the moves you'll use on every page.
weight: 40
---

Squiggly is the template language juicer uses. It's Go-template-like. This page is the practical cheatsheet; the [full squiggly reference](https://github.com/edadma/squiggly) covers everything else.

## Substitution

```squiggly
{{ .name }}              value at .name in the data context
{{ .page.title }}        nested
{{ .args[0] }}           list element by index (in shortcodes)
```

## Built-in functions

Juicer adds a few project-specific functions to squiggly's defaults.
Call them as expressions:

| Function                    | What |
|-----------------------------|------|
| `{{ relURL '/path' }}`      | Site-relative URL (`baseURL.path` prefix added if non-trivial) |
| `{{ absURL '/path' }}`      | Absolute URL (`baseURL.base + relURL`) |
| `{{ markdownify s }}`       | Render a markdown string to HTML |
| `{{ emojify s }}`           | Replace `:smile:` etc. with Unicode emoji |
| `{{ i18n lang 'key' }}`     | Look up an i18n string (falls back to default lang then literal key) |
| `{{ ogTags .page }}`        | Emit OpenGraph + Twitter card `<meta>` tags for a page record |
| `{{ imageDims '/path' }}`   | Read pixel dimensions of an on-disk image; returns `{width, height}` or empty map (see below) |

### `imageDims`

Reads the header of an image on disk and returns a map with `width` and
`height` (pixel ints). Useful for emitting `<img width=... height=...>`
so the browser can reserve space before the bytes arrive — eliminates
cumulative layout shift.

```squiggly
{{ d := imageDims '/img/hero.jpg' }}
{{ if d.width }}
  <img src="/img/hero.jpg" width="{{ d.width }}" height="{{ d.height }}" alt="" />
{{ else }}
  <img src="/img/hero.jpg" alt="" />
{{ end }}
```

Resolution order: the path is looked up first under the built output
directory (so theme + site `static/` files and any generated images
work), then under the source root. Paths can be site-absolute
(`/img/x.png`) or relative; absolute URLs (`http://…`) are not fetched.

Returns an empty map when the file is missing, unreadable, or in a
format the header parser does not understand. Recognized formats:
**PNG, JPEG (baseline + progressive), GIF87a/89a, WebP** (VP8 / VP8L /
VP8X). Pure Scala — no `javax.imageio`, no FFI, works on every target.

Per-build cache: each unique path is read once even if a shortcode
fires across hundreds of pages.

## Conditionals

```squiggly
{{ if .page.summary }}
  <p class="lead">{{ .page.summary }}</p>
{{ end }}

{{ if .page.draft }}
  <span class="badge">Draft</span>
{{ else }}
  <span class="badge">Published</span>
{{ end }}
```

`if` is truthy on non-empty strings, non-zero numbers, non-empty lists/maps, and `true`. `null` / empty / `false` / `0` are falsy.

Logical operators: `and`, `or`, prefix `not`.

```squiggly
{{ if .page.prev or .page.next }}
  <nav>...</nav>
{{ end }}
```

## Loops

```squiggly
{{ for p <- .section.pages }}
  <li><a href="{{ p.url }}">{{ p.title }}</a></li>
{{ end }}

{{ for k, v <- .page }}
  {{ k }}: {{ v }}
{{ end }}
```

## Partials

```squiggly
{{ partial 'topbar' . }}              call partials/topbar.html with the current data
{{ partial 'page-toc' .page }}        with a different data context
```

## Defines & blocks

```squiggly
{{ define main }}
  <article>{{ .content }}</article>
{{ end }}

{{ block main . }}{{ .content }}{{ end }}    fallback content if main isn't defined
```

`define` is statement-level (no output). `block <name> <data>` looks up the named block, calls it with `<data>` as the context, falls back to its inner template if the block isn't defined.

## Builtin filters

Most squiggly builtins work as either a function or a pipe target:

```squiggly
{{ trim .page.summary }}
{{ .page.summary | trim }}
{{ .name | upper }}
{{ .x | replace 'foo' 'bar' }}
```

Useful ones:

| Builtin | What |
|---------|------|
| `len`   | Length of a list / string |
| `head`  | First element of a list |
| `tail`  | All but the first |
| `trim`  | Trim whitespace |
| `upper` / `lower` | Case |
| `replace 'a' 'b'` | String substitution |
| `split 'sep'` | String split |
| `join 'sep'` | List join |

## Comments

```squiggly
{{ // squiggly's not too noisy in templates }}
```

## Scope inside loops

Inside a `{{ for x <- coll }}` block, `.foo` resolves against the
**current iterated element**, not the outer page context. Use `$.foo`
to reach back up to the top-level data root:

```squiggly
{{ for p <- .section.pages }}
  <li>
    <a href="{{ p.url }}">{{ p.title }}</a>
    {{ if eq p.url $.page.url }}<span>(this page)</span>{{ end }}
  </li>
{{ end }}
```

A common pitfall: writing `{{ .site.title }}` inside a loop and
getting nothing. Use `{{ $.site.title }}` — `.site` doesn't exist on
the iterated element.
