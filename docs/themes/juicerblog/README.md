# juicerblog

A hand-coded blog theme for [juicer](https://juicer.build). Sibling to
[juicerdocs](../juicerdocs/) but tuned for prose: serif body, generous
measure, warm-neutral palette by default.

## Features it exercises

- `.section.paginator` — the post list paginates at the configured
  `paginate` size (`paginate = 10` by default; the demo overrides to 3).
- `.page.tags` + `/tags/<slug>/` archive pages.
- `.page.dateLong` / `.page.readingTime` rendered into post meta.
- `[juicerblog]` table in `site.toml` lets a consuming site override
  `brand`, `accent`, `leaf` without forking the CSS.

## Smoke build

A working demo site that exercises this theme end-to-end lives at
`examples/blog-site/`. From the juicer repo root:

```
sbt 'juicerJVM/run build -s examples/blog-site -d /tmp/blog-out'
```

Then point a browser at `/tmp/blog-out/index.html`. To live-preview:

```
sbt 'juicerJVM/run serve -s examples/blog-site -L'
```

The example resolves the theme via `themeDir = "../../docs/themes"` —
keep this layout when copying for your own blog.

## Layout
```
juicerblog/
├── layouts/_default/
│   ├── baseof.html       (HTML envelope)
│   ├── folder.html       (home / archive — paginated post list)
│   ├── file.html         (single post)
│   ├── tag-list.html     (/tags/index.html)
│   ├── tag-page.html     (per-tag archive)
│   └── 404.html
├── partials/
│   ├── head.html         (<head> + per-site palette overrides)
│   ├── topbar.html
│   ├── footer.html
│   ├── post-meta.html
│   └── pagination.html
└── static/
    └── juicerblog.css
```

## Per-site palette overrides

Default tokens (cream + amber + indigo) are tuned to be pleasant out of
the box. Override per-site in `site.toml`:

```toml
[juicerblog]
brand  = "#0ea5e9"   # cyan
accent = "#0284c7"   # darker cyan for body links
leaf   = "#10b981"   # emerald
```

The override block emits CSS-variable overrides into `<head>` and beats
the theme defaults at runtime.
