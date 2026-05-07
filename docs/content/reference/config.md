---
title: Configuration
summary: Every config key juicer understands, what it defaults to, and what it controls.
weight: 20
---

Site config lives in `site.toml` at the source root. Juicer overlays your file on top of one of three baselines (`simple`, `standard`, `norme`) selected by the `-c <name>` CLI flag.

## Baselines

| Name     | What |
|----------|------|
| `simple` | Flat layout — content, layouts, partials, static all at the source root |
| `standard` | Hugo-like nested layout (the default) |
| `norme` | French (Charter-of-the-French-Language compliant) — same as `standard` but with French directory names |

[= tip =]
The `norme` baseline is here because Quebec's Charter of the French Language requires that public-facing software present primarily in French. Same code, French file names. If your content is bilingual, use this baseline alongside the upcoming i18n feature (Tier 2).
[= /tip =]

## Keys

### Identity

| Key      | Default                         | What |
|----------|---------------------------------|------|
| `title`  | `"Untitled"`                    | Site title; available as `.site.title` |
| `author` | `"Unnamed"`                     | Site author; available as `.site.author` |
| `baseURL`| `"http://localhost:8080"`       | Absolute base URL — used for permalinks, sitemap, OpenGraph |

### Theme

| Key        | Default     | What |
|------------|-------------|------|
| `theme`    | (none)      | Theme name (string) or chain (array of strings). Resolved under `themeDir` |
| `themeDir` | `"themes"`  | Directory holding theme subfolders |

### Directory layout

| Key             | `standard` default | What |
|-----------------|--------------------|------|
| `contentDir`    | `"content"`        | Markdown source root |
| `htmlDir`       | `"html"`           | Filesystem-only prefix for nested sections; stripped from URLs |
| `publicDir`     | `"public"`         | Default output directory |
| `staticDir`     | `"static"`         | Verbatim-copied assets |
| `layoutDir`     | `"layouts"`        | Templates root |
| `partialDir`    | `"partials"`       | Partials root |
| `shortcodeDir`  | `"shortcodes"`     | Shortcodes root |

### Layout names

| Key              | Default       | What |
|------------------|---------------|------|
| `defaultLayout`  | `"_default"`  | Fallback layout subfolder under `layoutDir` |
| `baseofLayout`   | `"baseof"`    | Outer-shell layout filename (without extension) |
| `fileLayout`     | `"file"`      | Single-page layout filename |
| `folderLayout`   | `"folder"`    | Section-index layout filename |
| `folderContent`  | `"_index"`    | Filename (without extension) recognized as the section index |

### Behavior

| Key            | Default | What |
|----------------|---------|------|
| `stripPrefix`  | `true`  | Strip leading numeric prefixes from filenames in URLs (`01-foo.md` → `foo`) |
| `headingShift` | `2`     | Add this much to every markdown heading level (1-clamped) |

## Custom keys

Any key you set in `site.toml` is available as `.site.<key>` in templates. Use this for site-wide settings the theme exposes — e.g., `editURL`, `discussionURL`, `social.twitter`. Themes typically document the keys they recognize in their README.
