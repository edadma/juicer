---
title: Quickstart
summary: Build a tiny site with one page in three minutes.
weight: 20
---

This walkthrough builds the smallest possible juicer site — one page, one layout — so you can see the moving parts before adding anything fancy.

## 1. Make a directory

```bash
mkdir my-site
cd my-site
```

## 2. Write `site.toml`

```toml
title   = "My Site"
author  = "You"
baseURL = "http://localhost:8080"
```

That's the minimum. Juicer overlays this on top of the `standard` baseline config (themes go in `themes/`, content in `content/`, etc.) — see [Configuration](/reference/config/) for the full set.

## 3. Write a layout

Juicer needs at least a *file* layout (for individual pages) and a *folder* layout (for section indexes). They live under `layouts/_default/`:

```bash
mkdir -p layouts/_default
```

`layouts/_default/folder.html`:
```squiggly
<!DOCTYPE html>
<html>
<head><title>{{ .page.title }} – {{ .site.title }}</title></head>
<body>
  <h1>{{ .page.title }}</h1>
  {{ .content }}
</body>
</html>
```

`layouts/_default/file.html`:
```squiggly
{{ partial 'folder' . }}
```

(That second template just delegates to `folder.html` — for a one-page site, both layouts can render the same way.)

## 4. Write content

```bash
mkdir content
```

`content/_index.md`:

```markdown
---
title: Hello
---

# It works!

This is **juicer**, rendering markdown.
```

## 5. Build & serve

```bash
sbt 'juicerJVM/run serve -s . -d public'
```

Open <http://localhost:8080>. You should see your page.

## What just happened?

- Juicer walked the `content/` tree, finding markdown files and parsing their YAML frontmatter.
- For each content file it picked a template (`folder.html` for `_index.md`, `file.html` for everything else) and rendered it.
- The output went to `public/` — `_index.md` became `public/index.html`.
- A `sitemap.xml` and `search.json` were also emitted automatically.

Move on to [Concepts](/concepts/) to see what each of those pieces does.
