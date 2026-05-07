---
title: Markdown
summary: Every markdown extension juicer turns on, with examples.
weight: 50
---

Juicer is built on [`io.github.edadma/markdown`](https://github.com/edadma/markdown) — a full CommonMark 0.31.2 parser. Every page gets the same defaults; this section walks through what's enabled and how each piece looks rendered.

## CommonMark — the basics

The whole CommonMark spec works without any opt-in. Headings, paragraphs, lists, blockquotes, code spans, fenced code blocks, links, images.

```markdown
# Heading 1
## Heading 2
### Heading 3

A *paragraph* with **strong** emphasis and `inline code`.

> A blockquote.
> Lazy continuation works too.

1. Ordered list
2. With items
   1. Nested
3. Continues

[Link text](https://juicer.run/) and ![alt](https://placehold.co/40x40 "title").
```

## GFM extras

The juicer config opts into every GitHub-Flavored-Markdown extension the parser supports:

### Tables

```markdown
| Tier | What's in it |
|------|--------------|
| 1 | site.pages, page URLs, drafts, sitemap, summary |
| 2 | live reload, render hooks, RSS, sections, i18n, themes |
```

renders as

| Tier | What's in it |
|------|--------------|
| 1 | site.pages, page URLs, drafts, sitemap, summary |
| 2 | live reload, render hooks, RSS, sections, i18n, themes |

### Strikethrough

`~~old behavior~~` renders as ~~old behavior~~.

### Task lists

```markdown
- [x] Tier 1 — done
- [x] Section list pages
- [ ] i18n
- [ ] Render hooks
```

renders as

- [x] Tier 1 — done
- [x] Section list pages
- [ ] i18n
- [ ] Render hooks

### Footnotes

You can drop a footnote reference[^numbers] mid-paragraph and define it elsewhere.

[^numbers]: This is the footnote body. Multiple paragraphs supported.

### Auto-linking

Bare URLs like https://juicer.run/ are recognized and turned into clickable links without explicit `[…](…)` syntax.

### Smart punctuation

Straight quotes get curly: "hello" becomes "hello", and `--` / `---` become en/em dashes — like that.

## Callouts (admonitions)

GFM-style callouts — `> [!NOTE]` / `> [!TIP]` / `> [!WARNING]` / `> [!IMPORTANT]` / `> [!CAUTION]` — render as styled blocks. Markdown source:

```markdown
> [!NOTE]
> This is the parser's built-in callout. It produces a styled `<div>` with
> the type baked into a class name.
```

> [!NOTE]
> This is the parser's built-in callout. It produces a styled `<div>` with
> the type baked into a class name.

> [!WARNING]
> The shortcode-based callouts (`\[= note =]…\[= /note =]`) and the markdown-native
> callouts above produce different HTML. The shortcodes are themed by juicerdocs
> directly; the native ones are styled by Tailwind's typography defaults.

## Auto heading IDs

Every `<hN>` gets an `id` derived from its plain-text content. The TOC reads this back; deep links work out of the box.

```markdown
## Hello, world!
```

becomes `<h2 id="hello-world">Hello, world!</h2>` — see your URL bar's hash if you click any heading on this page.

## Definition lists

```markdown
Apple
: A round fruit, typically red or green.

Orange
: A citrus fruit. Also a color.
```

renders as

Apple
: A round fruit, typically red or green.

Orange
: A citrus fruit. Also a color.

## Emoji

`:smile:` becomes :smile:, `:rocket:` becomes :rocket:, `:tada:` becomes :tada: — the unicode codepoints, not images.

## Code blocks with language

```html
<!DOCTYPE html>
<html>
<head><title>{{ .page.title }}</title></head>
<body>
  <main class="prose">{{ .content }}</main>
</body>
</html>
```

```scala
// Squiggly's Go-template-style syntax compiles down to a small AST.
case class TemplateRenderer(
  partials:   TemplateLoader = _ => None,
  data:       Map[String, Any] = Map.empty,
  functions:  Map[String, TemplateFunction] = Map.empty,
)
```

```bash
# A code block tagged `bash` gets a "BASH" badge and copy button.
sbt 'juicerJVM/run serve -s docs -L'
```

The juicerdocs theme adds a copy-to-clipboard button on every `<pre>` — hover to see it.
