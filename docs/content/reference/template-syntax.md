---
title: Template syntax
summary: Squiggly cheatsheet — the moves you'll use on every page.
weight: 40
---

Squiggly is the template language juicer uses. It's Go-template-like. This page is the practical cheatsheet; the [full squiggly reference](https://github.com/edadma/squiggly) covers everything else.

## Substitution

```
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

## Conditionals

```
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

```
{{ if .page.prev or .page.next }}
  <nav>...</nav>
{{ end }}
```

## Loops

```
{{ for p <- .section.pages }}
  <li><a href="{{ p.url }}">{{ p.title }}</a></li>
{{ end }}

{{ for k, v <- .page }}
  {{ k }}: {{ v }}
{{ end }}
```

## Partials

```
{{ partial 'topbar' . }}              call partials/topbar.html with the current data
{{ partial 'page-toc' .page }}        with a different data context
```

## Defines & blocks

```
{{ define main }}
  <article>{{ .content }}</article>
{{ end }}

{{ block main . }}{{ .content }}{{ end }}    fallback content if main isn't defined
```

`define` is statement-level (no output). `block <name> <data>` looks up the named block, calls it with `<data>` as the context, falls back to its inner template if the block isn't defined.

## Builtin filters

Most squiggly builtins work as either a function or a pipe target:

```
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

## Juicer-specific helpers

| Builtin | What |
|---------|------|
| `relURL '...'` | Site-relative URL (prepends `baseURL.path`) |
| `absURL '...'` | Absolute URL (prepends `baseURL.base + baseURL.path`) |
| `markdownify '...'` | Render a markdown string to HTML |
| `emojify '...'` | `:smile:` → 😄 substitution |

## Comments

```
{{ // squiggly's not too noisy in templates }}
```
