---
title: Template data
summary: The full data context exposed to templates — site, page, section, content, toc.
weight: 30
---

Every template renders against a single map. The top-level keys are documented below; nested fields follow.

## `.site`

The merged site config (`site.toml` overlaid on the baseline) plus a few computed extras:

| Key              | Type                | What |
|------------------|---------------------|------|
| `.site.<config>` | varies              | Every key from `site.toml` |
| `.site.toc`      | `List[TOCItem]`     | Site-wide auto-nav (only if no `nav` is set) |
| `.site.start`    | `String?`           | URL of the conventional landing page |
| `.site.pages`    | `List[Map]`         | Every page's enriched record |
| `.site.pagesByPath` | `Map[String, Map]`  | Same records, keyed by `relPermalink` |
| `.site.root`     | `Map?`              | The root section's `_index` record (or `null`) |

## `.page`

The current page's enriched record:

| Key                    | Type      | What |
|------------------------|-----------|------|
| `.page.title`          | `String`  | Frontmatter title |
| `.page.summary`        | `String`  | Resolved summary (frontmatter / `<!--more-->` / fallback) |
| `.page.url`            | `String`  | Site-relative URL |
| `.page.relPermalink`   | `String`  | Same as `url`, named for Hugo parity |
| `.page.permalink`      | `String`  | Absolute URL (baseURL + url) |
| `.page.isSection`      | `Boolean` | `true` for `_index.md` pages |
| `.page.parent`         | `Map?`    | Enclosing section's `_index` record |
| `.page.ancestors`      | `List[Map]` | Root → parent chain (excluding self) |
| `.page.next`           | `Map?`    | Next page in section by `pageOrder` |
| `.page.prev`           | `Map?`    | Previous page in section |
| `.page.<custom>`       | varies    | Any frontmatter key |

For section index pages (where `.page.isSection` is `true`), additionally:

| Key                  | Type        | What |
|----------------------|-------------|------|
| `.page.pages`        | `List[Map]` | Non-`_index` siblings, sorted |
| `.page.subsections`  | `List[Map]` | Direct child sections, sorted |

## `.section`

Always available (for non-`_index` pages it describes the enclosing section):

| Key                     | Type        | What |
|-------------------------|-------------|------|
| `.section.pages`        | `List[Map]` | Non-`_index` siblings, sorted |
| `.section.subsections`  | `List[Map]` | Direct child sections, sorted |
| `.section.index`        | `Map?`      | Section's `_index` record |

## Other top-level

| Key         | Type      | What |
|-------------|-----------|------|
| `.content`  | `String`  | Rendered markdown body, HTML |
| `.toc`      | `Map`     | `{ headings: [TocEntry] }` — full heading tree |
| `.sub`      | `List`    | Children of the first heading, flattened |

## Page ordering

`pageOrder` is **weight ascending, then name ascending**. Pages with no `weight` frontmatter sort after weighted pages but before any sentinel value.

[= note =]
You'll almost always want to set explicit `weight` values on pages that need a particular order — installation before quickstart, etc. Anything that ships a `weight` lower than the default (`9223372036854775807` = `Long.MaxValue / 2`) wins.
[= /note =]
