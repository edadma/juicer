---
title: Shortcodes
summary: Every shortcode bundled with the juicerdocs theme, with copy-pasteable examples.
weight: 60
---

[Shortcodes](/concepts/shortcodes/) are templates invoked from
inside markdown using `\[= name =]` syntax. The juicerdocs theme
ships 16 of them, covering callouts, structured docs blocks,
embeds, and inline atoms. This page is the catalogue — one entry
per shortcode with the exact syntax, the rendered look, and any
arguments it accepts.

[= note =]
**These are juicerdocs shortcodes.** They live under
`docs/themes/juicerdocs/shortcodes/`. If you're using a different
theme, you won't get this set — bring your own, copy individual
ones into `<src>/shortcodes/`, or include `juicerdocs` in your
theme chain. See [Concepts → Shortcodes](/concepts/shortcodes/)
for the calling conventions and how to author your own.
[= /note =]

[= tip =]
**Escaping in this page.** The code-fence examples below use
`\[=` (backslash-prefixed) so the preprocessor leaves the literal
shortcode syntax in place rather than expanding it. When you
write shortcodes in your own content, drop the backslash.
[= /tip =]

## Callouts

Five admonition flavours, each a paired shortcode that wraps a
markdown body. They share styling: a tinted background, a small
icon, generous padding.

### `\[= note =]`

Neutral information. Use for "by the way" asides that the reader
can skip without missing anything load-bearing.

```markdown
\[= note =]
The frontmatter format is YAML. JSON and TOML frontmatter are
**not** supported.
\[= /note =]
```

[= note =]
The frontmatter format is YAML. JSON and TOML frontmatter are
**not** supported.
[= /note =]

### `\[= tip =]`

A suggestion or shortcut. Use for "you can also …" content where
the alternative path is sometimes the better one.

```markdown
\[= tip =]
The `juicer config` subcommand prints the merged config — it's
the fastest way to debug "why is this key not doing what I expect?"
\[= /tip =]
```

[= tip =]
The `juicer config` subcommand prints the merged config — it's
the fastest way to debug "why is this key not doing what I expect?"
[= /tip =]

### `\[= warning =]`

Something worth slowing down for. Use when ignoring this
information will produce a confusing failure later.

```markdown
\[= warning =]
Permalink templates change both the URL and the on-disk write
location. Juicer doesn't keep both copies.
\[= /warning =]
```

[= warning =]
Permalink templates change both the URL and the on-disk write
location. Juicer doesn't keep both copies.
[= /warning =]

### `\[= danger =]`

A serious caveat. Use for "this will break things" — destructive
operations, security caveats, irreversible flags.

```markdown
\[= danger =]
Never expose a shortcode that takes user input straight into HTML.
Run it through `markdownify` or escape it explicitly.
\[= /danger =]
```

[= danger =]
Never expose a shortcode that takes user input straight into HTML.
Run it through `markdownify` or escape it explicitly.
[= /danger =]

### Picking the right callout

A rule of thumb: `note` for information, `tip` for a better path,
`warning` for a foot-gun, `danger` for something that will
actively break.

## Structured blocks

### `\[= steps =]`

Numbered, vertically-laid-out steps. Headings inside (`## Step
name`) are auto-numbered; the body of each step is regular
markdown.

```markdown
\[= steps =]
## Install Juicer

Clone the repo and verify with `sbt 'juicerJVM/run --help'`.

## Make a site directory

Create `site.toml`, a `content/` folder, and `layouts/_default/`.

## Build & serve

Run `sbt 'juicerJVM/run serve -s .'` and open <http://localhost:8080>.
\[= /steps =]
```

[= steps =]
## Install Juicer

Clone the repo and verify with `sbt 'juicerJVM/run --help'`.

## Make a site directory

Create `site.toml`, a `content/` folder, and `layouts/_default/`.

## Build & serve

Run `sbt 'juicerJVM/run serve -s .'` and open <http://localhost:8080>.
[= /steps =]

### `\[= tabs =]` / `\[= tab "Label" =]`

Tabbed content panes. The outer `tabs` is a container; each `tab`
takes a label as a positional arg and a markdown body.

```markdown
\[= tabs =]
\[= tab "JVM" =]
sbt 'juicerJVM/run build -s docs'
\[= /tab =]
\[= tab "Native" =]
juicer build -s docs
\[= /tab =]
\[= /tabs =]
```

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

### `\[= filetree =]`

Renders the inner content in a monospace box. Use it for tree-style
directory listings — box-drawing characters paste in as-is.

```markdown
\[= filetree =]
my-site/
├── content/
│   ├── _index.md
│   └── about.md
└── site.toml
\[= /filetree =]
```

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

### `\[= collapse "Summary" =]`

Native `<details>` / `<summary>` with juicerdocs styling. The
positional argument is the summary text; the body is the
collapsed content (markdown is rendered inside).

```markdown
\[= collapse "Why no JSON frontmatter?" =]
YAML covers the use case fluently, and supporting three formats
triples the surface area to keep correct.
\[= /collapse =]
```

[= collapse "Why no JSON frontmatter?" =]
YAML covers the use case fluently, and supporting three formats
triples the surface area to keep correct.
[= /collapse =]

## Inline atoms

Self-closing shortcodes for short, in-line UI flourishes.

### `\[= kbd "Ctrl+K" /=]`

Renders a `<kbd>` element styled to look like a keycap. One
positional argument: the key combo to display.

```markdown
Press \[= kbd "Ctrl+K" /=] to focus search.
Press \[= kbd "Cmd+Shift+R" /=] to hard-reload.
```

Press [= kbd "Ctrl+K" /=] to focus search.
Press [= kbd "Cmd+Shift+R" /=] to hard-reload.

### `\[= badge "alpha" /=]`

A small pill-shaped tag. One positional argument: the text inside.

```markdown
Tag your alpha pages with \[= badge "alpha" /=] so readers know.
\[= badge "v0.2" /=] \[= badge "beta" /=] \[= badge "experimental" /=]
```

Tag your alpha pages with [= badge "alpha" /=] so readers know.
[= badge "v0.2" /=] [= badge "beta" /=] [= badge "experimental" /=]

## Buttons & calls-to-action

### `\[= button "Label" href="..." /=]` / `\[= buttons =]`

A styled link that looks like a button. The first positional
argument is the label; `href` is the URL; setting `primary="true"`
swaps to the filled primary variant. Wrap one or more in
`\[= buttons =]` for an inline group.

```markdown
\[= buttons =]
\[= button "Get started" href="/getting-started/" primary="true" /=]
\[= button "Browse on GitHub" href="https://github.com/edadma/juicer" /=]
\[= /buttons =]
```

[= buttons =]
[= button "Get started" href="/getting-started/" primary="true" /=]
[= button "Browse on GitHub" href="https://github.com/edadma/juicer" /=]
[= /buttons =]

## Media

### `\[= figure "/img/foo.png" alt="..." caption="..." /=]`

A `<figure>` with an image and optional caption. First positional
argument is the image URL. Named arguments: `alt` (accessibility
text), `caption` (visible caption beneath the image).

```markdown
\[= figure "/img/diagram.svg" alt="Build pipeline diagram" caption="The juicer build pipeline" /=]
```

### `\[= github "owner/repo" /=]`

A clickable GitHub-repo card. One positional argument: the
`owner/repo` slug. Renders the GitHub octocat icon plus the slug
and the subtitle "GitHub repository", linked to the repo.

```markdown
\[= github "edadma/juicer" /=]
```

[= github "edadma/juicer" /=]

### `\[= youtube "<video-id>" /=]`

A privacy-respecting iframe embed (`youtube-nocookie.com`). One
positional argument: the YouTube video ID (the part after `?v=` in
the URL).

```markdown
\[= youtube "dQw4w9WgXcQ" /=]
```

## Reference table

For quick lookup:

| Shortcode      | Pair / self | Args                                       | Use for |
|----------------|-------------|--------------------------------------------|---------|
| `note`         | pair        | body                                       | Aside, non-critical info |
| `tip`          | pair        | body                                       | Suggestion, shortcut |
| `warning`      | pair        | body                                       | Slow-down, foot-gun |
| `danger`       | pair        | body                                       | Destructive / serious caveat |
| `steps`        | pair        | body w/ `## name` headings                 | Numbered steps |
| `tabs`         | pair        | one or more `tab` children                 | Tabbed content groups |
| `tab`          | pair        | `args[0]` = label                          | One tab inside `tabs` |
| `filetree`     | pair        | opaque body                                | Tree-style listings |
| `collapse`     | pair        | `args[0]` = summary text, body = content   | `<details>` accordion |
| `kbd`          | self-close  | `args[0]` = key text                       | Keycap styling |
| `badge`        | self-close  | `args[0]` = label text                     | Pill / tag |
| `button`       | self-close  | `args[0]` = label, `href`, `primary`, `icon` | Styled link |
| `buttons`      | pair        | one or more `button` children              | Button group |
| `figure`       | self-close  | `args[0]` = src, `alt`, `caption`          | Image with caption |
| `github`       | self-close  | `args[0]` = `owner/repo`                   | GitHub repo card |
| `youtube`      | self-close  | `args[0]` = video ID                       | Privacy-friendly embed |

## Writing your own

Drop a template at `<src>/shortcodes/<name>.html` (or, in a theme,
at `<theme>/shortcodes/<name>.html`). Inside it you have access to:

| Variable    | What |
|-------------|------|
| `.args`     | Positional arguments — a list of strings |
| `.<name>`   | Each named argument by its key |
| `.content`  | The body, if the shortcode is invoked as a pair (else absent) |

A minimal callout — template at `<src>/shortcodes/callout.html`:

```squiggly
<aside class="callout callout-{{ if .kind }}{{ .kind }}{{ else }}note{{ end }}">
  {{ markdownify .content }}
</aside>
```

Invoke it from markdown:

```markdown
\[= callout kind="success" =]
The build passed.
\[= /callout =]
```

See [Concepts → Shortcodes](/concepts/shortcodes/) for the full
syntax (quoting rules, escaping, paired vs self-closing).

## Deferred shortcodes — the `\[~ … ~]` delimiter

The classic `\[= … =]` syntax runs **before** markdown parsing. That's
the right phase for shortcodes that emit markdown — a `note` callout
producing `<aside>…</aside>` flows through the markdown parser
correctly because the parser sees plain HTML in the input.

What that phase **can't** do is reach `.page.pages`, `.page.subsections`,
`.section.*`, or any field of `.site.*` — those records don't exist
yet during the markdown pass. Trying to render a shortcode template
that says `{{ for p <- .page.pages }}` from `\[= … =]` silently produces
an empty list.

Juicer provides a second shortcode delimiter pair, `\[~ … ~]`, that
runs **after** the page + section + site pipeline has finished. Inside
a `\[~` shortcode the template sees:

| Namespace        | What's available |
|------------------|------------------|
| `.page.*`        | Full page record — title, summary, tags, **pages**, **subsections**, permalink, ancestors, prev / next, series, authors, … |
| `.site.*`        | Full sitedata — `.site.posts`, `.site.pages`, `.site.tags`, `.site.authors`, `.site.now`, `.site.data`, … |
| `.args` / `.<key>` / `.content` | Same as the immediate pass |

```markdown
\[~ section-list /~]
```

```squiggly
{{ // shortcodes/section-list.html — must be invoked with [~ ... ~]
   // because it depends on .page.pages and .page.subsections.        }}
{{ if .page.pages }}
  <ul>{{ for p <- .page.pages }}<li><a href="{{ p.url }}">{{ p.title }}</a></li>{{ end }}</ul>
{{ end }}
```

[= warning =]
**Deferred shortcodes emit HTML, not markdown.** They run on the
already-rendered HTML body. If your template wants to render markdown
output, pipe it through `\{\{ markdownify ... \}\}` explicitly.
[= /warning =]

[= note =]
**Same template registry.** Both delimiter pairs look up templates in
`shortcodes/`; the difference is purely *when* the shortcode runs and
*what context it sees*. A template that uses neither `.page` nor
`.site` works identically from either pass.
[= /note =]
