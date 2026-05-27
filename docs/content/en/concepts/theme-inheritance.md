---
title: Theme inheritance
summary: How a theme pulls in other themes via theme.toml, and how the full lookup chain is resolved.
weight: 35
---

A theme can build on other themes. It declares them in a `theme.toml` file
at its own root:

[= filetree =]
themes/juicerdocs/
├── theme.toml          ← inherits = ["juicercommon"]
├── layouts/
├── partials/
└── static/
[= /filetree =]

```toml
# themes/juicerdocs/theme.toml
inherits = ["juicercommon"]
```

When a site activates `juicerdocs`, juicer doesn't just look in
`juicerdocs/` — it walks the `inherits` list too, splicing each named theme
into the lookup chain right after the theme that named it. Partials,
shortcodes, layouts, and static files all resolve against this expanded
chain.

This is how the bundled themes share code: each one inherits
[`juicercommon`](/concepts/themes/#bundled-themes), the hidden base theme
that holds the `seo` block, the dark-mode init/toggle scripts, and the
common shortcodes (`note`, `tip`, `warning`, `tabs`, `github`, …). No theme
duplicates them.

## The lookup chain

Start from the site's `theme` setting — a single name or an array — and
walk each theme's `inherits` depth-first. The result is one ordered chain.
Resolution is **first-wins**: the earliest place a file is found is the one
that's used.

```toml
# site.toml
theme = "juicerdocs"
```

```toml
# themes/juicerdocs/theme.toml
inherits = ["juicercommon"]
```

resolves to the chain:

```
site  >  juicerdocs  >  juicercommon
```

So a `partials/seo.html` in your site beats `juicerdocs`'s, which beats
`juicercommon`'s. Drop your own `partials/note.html` into `juicerdocs/` and
it overrides the shared shortcode without touching `juicercommon`.

Inheritance composes with the site-level theme **array**. Both are resolved
into the same chain — the array first, then each entry's `inherits`:

```toml
# site.toml
theme = ["my-overrides", "juicerdocs"]
```

```
site  >  my-overrides  >  juicerdocs  >  juicercommon
```

`inherits` is transitive: if `juicercommon` itself declared
`inherits = ["base"]`, `base` would land at the end of the chain.

[= note =]
Precedence never changes: **site files always win**, then themes in chain
order. Inheritance only makes the chain longer — it never lets a theme
override the site.
[= /note =]

## Deduplication

A theme reached more than once — a diamond, where two themes both inherit a
common base — appears in the chain **once**, at its earliest position:

```toml
# site.toml
theme = ["themeA", "themeB"]   # both inherit "shared"
```

```
site  >  themeA  >  shared  >  themeB
```

`shared` resolves once, kept at its first occurrence. A file defined in
`themeA`, `themeB`, and `shared` is won by `themeA` (earliest in the chain).

## Errors

[= warning =]
A theme that lists a non-existent theme in `inherits` fails the build with a
clear message:

```
theme 'juicerdocs' inherits unknown theme 'typo' — no directory at …
```

A **cycle** — `A` inherits `B` inherits `A` — also fails, with the path
traced so you can see where it closes:

```
theme cycle: themeA → themeB → themeA
```
[= /warning =]

A missing theme named at the **site level** (in `site.toml`'s `theme`) is
treated leniently — it's simply skipped — matching juicer's long-standing
behavior. Only `inherits` links are validated strictly, because a theme
author controls its own dependencies.

## No theme.toml?

A theme with no `theme.toml` inherits nothing — it's exactly equivalent to
`inherits = []`. Themes written before this feature existed keep working
byte-for-byte; inheritance is purely additive.

[= tip =]
`theme.toml` currently understands a single key, `inherits`. It's the one
place themes declare their dependencies, so a theme is self-describing — you
can read its `theme.toml` and know the whole chain it expects.
[= /tip =]
