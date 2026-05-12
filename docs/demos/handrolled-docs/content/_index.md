---
title: Home
---

# Welcome to the docs site example

This is a slightly larger juicer example showing **multiple pages**, a
**sidebar nav**, and **partials**.

Pick a page from the sidebar to read on. Highlights:

- [Installation](guide/installation/) — how to get juicer running
- [Quickstart](guide/quickstart/) — your first site in a minute
- [Cheatsheet](guide/cheatsheet/) — every template helper at a glance

## How this example is laid out

```
docs-site/
├── site.toml              # site metadata + nav
├── content/
│   ├── _index.md          # this page
│   └── guide/             # one folder per docs section
│       ├── installation.md
│       ├── quickstart.md
│       └── cheatsheet.md
├── layouts/_default/
│   ├── baseof.html        # outer chrome (wraps every page)
│   ├── folder.html        # content for `_index.md` pages
│   └── file.html          # content for individual pages
├── partials/
│   ├── nav.html           # sidebar
│   └── footer.html        # site footer
└── static/
    └── style.css          # plain CSS, copied verbatim
```
