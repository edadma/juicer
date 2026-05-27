---
title: Configuration
summary: site.toml keys juicercook reads, recipe frontmatter, the [juicercook] palette table, and the categories strip.
weight: 10
---

juicercook reads its configuration from three places:

- A small set of **top-level site keys** (the same ones every juicer theme uses).
- A namespaced **`[juicercook]` table** for palette overrides and the home-page category strip.
- **Per-recipe frontmatter** — structured fields that drive both the rendered card and the Recipe JSON-LD.

## Quick example

```toml
title    = "Elder & Ash"
tagline  = "A small home cooking website"
author   = "Hanna Petrescu"
baseURL  = "https://elderandash.example"
theme    = "juicercook"

# Image variants — responsive <picture> markup on every recipe hero
# and card.
[images]
enabled = true
widths  = [480, 960, 1600]
formats = ["webp", "original"]
quality = 78

[[topnav]]
label = "Recipes"
url   = "/"

[[juicercook.categories]]
label = "All"
url   = "/"

[[juicercook.categories]]
label = "Breads"
url   = "/breads/"

[[juicercook.categories]]
label = "Desserts"
url   = "/desserts/"
```

A recipe (`content/breads/sourdough.md`):

```markdown
---
title: Sourdough loaf
date: 2024-03-12
servings: 1 loaf
prepTime: PT30M
cookTime: PT45M
image: /img/sourdough.jpg
imageAlt: Crusty boule with an open crumb
summary: A reliable everyday loaf — 25 % whole wheat, 70 % hydration.
ingredients:
  - 500 g bread flour
  - 350 g water
  - 100 g active starter
  - 10 g sea salt
instructions:
  - Mix flour and water; rest 30 minutes (autolyse).
  - Add starter and salt; mix.
  - Bulk ferment 4–6 hours.
tags: [bread, sourdough]
---

Optional body content goes here.
```

## Top-level keys

| Key                | Type        | Default | Notes |
|--------------------|-------------|---------|-------|
| `title`            | `String`    | required | Brand text in topbar + footer; `<title>` element. |
| `tagline`          | `String`    | unset   | Italic subtitle in the topbar. |
| `description`      | `String`    | unset   | Default `<meta name="description">`. |
| `author`           | `String`    | unset   | Footer + JSON-LD author. |
| `baseURL`          | `String`    | required | Standard juicer key. |
| `theme`            | `String`    | required | Set to `"juicercook"`. |
| `favicon`          | `String`    | unset   | Path to a favicon. |
| `hideJuicerCredit` | `Boolean`   | `false` | Set `true` to remove the "Built with juicer" line. |
| `topnav`           | `[[topnav]]` | unset  | Topbar nav entries. |
| `links`            | `[[links]]` | unset   | Footer link entries. |
| `[images]`         | `Table`     | unset   | Image-variants pipeline. Strongly recommended — the hero and recipe-card images use it. |

## Per-recipe frontmatter

| Key            | Type     | What |
|----------------|----------|------|
| `title`        | `String` | Required. |
| `date`         | `String` | Home-page recency ordering. Also `datePublished` in JSON-LD. |
| `servings`     | `String` | "1 loaf", "4–6", etc. Maps to `recipeYield`. |
| `prepTime`     | `String` | **ISO 8601 duration** — `PT30M`, `PT2H`, `P1D`. |
| `cookTime`     | `String` | ISO 8601 duration. |
| `totalTime`    | `String` | ISO 8601 duration. Optional. |
| `image`        | `String` | Hero photo path. |
| `imageAlt`     | `String` | Alt text. Falls back to the title. |
| `summary`      | `String` | Card summary, `<meta name="description">`, JSON-LD description. |
| `category`     | `String` | `recipeCategory` — `Dessert`, `Main`, `Side`, etc. |
| `cuisine`      | `String` | `recipeCuisine` — `Italian`, `Indian`, etc. |
| `ingredients`  | `List`   | One string per ingredient line. |
| `instructions` | `List`   | One string per numbered step. |
| `photos`       | `List`   | Optional process photos. Each entry is a string (path) or `{src, alt, caption}` map. Renders below the recipe card. |
| `tags`         | `List`   | Drives `/tags/<slug>/` archives. |

## `[juicercook]` palette overrides

```toml
[juicercook]
brand        = "#7a2e10"   # terracotta
brandStrong  = "#4a1908"
accent       = "#c0571d"   # paprika
fontSans     = "'Inter', system-ui, sans-serif"
fontSerif    = "'Playfair Display', Georgia, serif"

# Home-page category chips
[[juicercook.categories]]
label = "All"
url   = "/"

[[juicercook.categories]]
label = "Breads"
url   = "/breads/"
```
