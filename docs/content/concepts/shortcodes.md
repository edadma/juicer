---
title: Shortcodes
summary: Markdown-embeddable templates for callouts, tabs, embeds, and more.
weight: 40
---

Shortcodes let authors invoke HTML templates from inside markdown without writing raw HTML. They use a bracket-equals syntax to avoid collisions with both markdown and squiggly:

```markdown
\[= shortcode-name positional "another arg" key="value" =]
optional body content
\[= /shortcode-name =]
```

The body content is captured raw and exposed to the template as `.content`; positional args become `.args` (a list); named args become `.<name>`.

## What juicerdocs ships

This very theme provides a useful set out of the box. Some examples:

[= note =]
**Note callouts** like this one are written with `\[= note =] ... \[= /note =]`. We have `tip`, `warning`, and `danger` variants for different colors and icons.
[= /note =]

[= tip =]
**Tip:** if you author a lot of structured docs, the `steps`, `tabs`, and `filetree` shortcodes save you a lot of HTML.
[= /tip =]

[= warning =]
**Watch out:** shortcodes are processed *before* markdown is parsed, so you can't put a shortcode inside an indented code block.
[= /warning =]

[= danger =]
**Don't do this:** never expose a shortcode that takes user input straight into HTML. Run it through `markdownify` or escape it explicitly.
[= /danger =]

## Tabs

The `tabs` / `tab` pair gives tabbed content. Useful for showing the same operation across platforms:

[= tabs =]
[= tab "JVM" =]
```bash
sbt 'juicerJVM/run build -s docs'
```
[= /tab =]
[= tab "Native" =]
```bash
juicer build -s docs
```
[= /tab =]
[= tab "Node" =]
```bash
node ./juicer-cli.js build -s docs
```
[= /tab =]
[= /tabs =]

## Steps

Use `\[= steps =]` and write `## Step name` headings inside; they're auto-numbered:

[= steps =]
## Install Juicer

Clone the repo and verify with `sbt 'juicerJVM/run --help'`.

## Make a site directory

Create `site.toml`, a `content/` folder, and `layouts/_default/`.

## Build & serve

Run `sbt 'juicerJVM/run serve -s .'` and open <http://localhost:8080>.
[= /steps =]

## File trees

`\[= filetree =]` is just monospace + indent guides — paste a tree-style listing inside:

[= filetree =]
my-site/
├── content/
│   ├── _index.md
│   └── about.md
├── layouts/
│   └── _default/
│       ├── file.html
│       └── folder.html
└── site.toml
[= /filetree =]

## Inline pieces

Press [= kbd "Ctrl+K" /=] to focus search. Tag your alpha pages with [= badge "alpha" /=] so readers know.

## Collapsible sections

Great for FAQs and "show me the long version":

[= collapse "Why no JSON frontmatter?" =]
The frontmatter format is YAML because YAML covers the use case fluently and supporting three formats (YAML, TOML, JSON) triples the surface area to keep correct. JSON inside markdown is also visually noisy.
[= /collapse =]

[= collapse "What about TOML frontmatter?" =]
Same answer. Site config is TOML; per-page metadata is YAML; it's two formats but they live in different files, and the line is easy to remember.
[= /collapse =]

## Buttons

For landing pages and CTAs:

[= buttons =]
[= button "Get started" href="/getting-started/" primary="true" /=]
[= button "Browse on GitHub" href="https://github.com/edadma/juicer" /=]
[= /buttons =]

## GitHub repo cards

`\[= github "owner/repo" /=]` renders a clickable card:

[= github "edadma/juicer" /=]

## YouTube embeds

`\[= youtube "<video-id>" /=]` embeds a privacy-respecting iframe (`youtube-nocookie.com`).

## Writing your own

Drop a template at `<theme>/shortcodes/<name>.html` (or `<site>/shortcodes/<name>.html`). Inside, you have access to:

| Var       | What |
|-----------|------|
| `.args`   | Positional arguments — a list of strings |
| `.<name>` | Each named argument by its key |
| `.content`| The body, if the shortcode is delimited |

Use the `markdownify` builtin to render the body as markdown:

```html
<div class="my-callout">
  {{ markdownify .content }}
</div>
```
