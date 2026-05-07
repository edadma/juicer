---
title: Quickstart
---

# Quickstart

Five steps from zero to a working site.

## 1. Create the layout

```
mysite/
├── site.toml
├── content/
│   └── _index.md
└── layouts/_default/
    ├── folder.html
    └── file.html
```

## 2. Write `site.toml`

```toml
title   = "My Site"
baseURL = "http://localhost:8080"
```

## 3. Write `_index.md`

```markdown
---
title: Home
---

# Hello, world
```

## 4. Write the layouts

`folder.html`:

```html
<title>{{ .page.title }} — {{ .site.title }}</title>
{{ .content }}
```

## 5. Build

```
sbt 'juicerJVM/run build --source mysite'
```

Output lands in `mysite/public/index.html`.
