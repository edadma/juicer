---
title: Cheatsheet
---

# Template helper cheatsheet

The page-rendering context exposes:

| key       | what                                               |
|-----------|----------------------------------------------------|
| `site`    | site config (from `site.toml`) + nav / start data  |
| `page`    | the page's YAML frontmatter, any-data shape        |
| `content` | the rendered markdown body, HTML                   |
| `toc`     | the page's heading tree                            |
| `sub`     | flattened sub-headings (handy for sidebars)        |

## Helpers juicer adds on top of squiggly

- `relURL '...'` — site-relative URL (prepends baseURL.path)
- `absURL '...'` — absolute URL (prepends baseURL.base + baseURL.path)
- `markdownify '...'` — render a string as markdown into HTML
- `emojify '...'` — substitute `:smile:` → `😄`

## Squiggly built-in templating

```
{{ .field }}                     value substitution
{{ if cond }}…{{ else }}…{{ end }}
{{ for x <- .xs }}…{{ end }}
{{ partial 'name' . }}           call a partial template
{{ define name }}…{{ end }}      define a template block
{{ // a comment }}
```

Inside markdown content you can also call **shortcodes** — bracket-equals
delimiters, with the shortcode name and any args, optionally paired with a
closing tag. Each shortcode resolves to a template in `shortcodes/`. See
the juicer README for the exact syntax (it's not shown here because this
page would itself try to expand the example).

## Markdown extras

- Auto heading IDs — every `<hN>` gets `id="..."` from its text
- TOC tree available as `{{ .toc.headings }}`

## Some emoji 🎉

The literal `:rocket:` becomes a rocket; `:sparkles:` becomes sparkles;
and `:smile:` becomes a smiley.
