---
title: Home
---

# Welcome

This is the **minimal** juicer example. One page, one layout. Build it with:

```
sbt 'juicerJVM/run build --source examples/minimal'
```

## Features it shows

- A single content file with YAML frontmatter
- A `folder.html` layout (used for index pages)
- A `file.html` layout (used for individual pages, but unused here since
  there's only an `_index.md`)
- The `{{ .site.title }}`, `{{ .page.title }}`, `{{ .content }}`,
  and `{{ relURL '...' }}` template helpers

## What this isn't

A docs site or a blog. For a slightly bigger example with multiple pages,
a sidebar nav, and partials, see `examples/docs-site`.
