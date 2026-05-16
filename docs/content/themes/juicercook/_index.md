---
title: juicercook
summary: Recipe-site theme. Each recipe is a structured frontmatter block that renders into a card layout with ingredients, numbered instructions, hero image (responsive), optional process photos, and schema.org Recipe JSON-LD.
weight: 95
---

`juicercook` turns a markdown file with structured frontmatter into a publication-quality recipe page: ingredients column, numbered instructions, hero image with responsive `<picture>` markup, optional process photos, and a `schema.org/Recipe` JSON-LD block for Google's recipe-rich-results pipeline. A print stylesheet strips chrome so any recipe prints cleanly to a single sheet.

It lives at `docs/themes/juicercook/` in the juicer repo:

```bash
juicer theme add https://github.com/edadma/juicer.git -n juicercook
```

…and set `theme = "juicercook"` in `site.toml`. The [Demo site](./demo/) — Elder & Ash, a small home-cooking site with six recipes — is the canonical example.

## What's in the box

A recipe is a single markdown file with structured frontmatter. The frontmatter is what drives both the rendered card AND the embedded JSON-LD, so a single edit propagates everywhere. The home page is a card grid of recipes (newest first); per-section landings (`/breads/`, `/desserts/`) work too.

- **Hero image per recipe** — `image:` in frontmatter renders as a wide hero figure above the recipe card. Delivered through `imageVariants` when `[images]` is enabled, so the browser pulls the smallest variant that fits the viewport.
- **Optional process photos** — `photos:` in frontmatter (list of strings or `{src, alt, caption}` maps) renders below the recipe card as a "In the kitchen" grid. Useful for shaping shots, doneness reference photos, crumb cross-sections.
- **Two-column recipe card** — ingredients (left, ~40 %) and numbered instructions (right, ~60 %). Each step has a circled step-number. Stacks on mobile.
- **schema.org Recipe JSON-LD** auto-emitted from the same frontmatter — `recipeIngredient`, `recipeInstructions` (HowToStep), `prepTime`, `cookTime`, `recipeYield`, `recipeCategory`, `recipeCuisine`.
- **Print-friendly stylesheet** — a `print` media query strips topbar, footer, theme toggle, tags, and the step-photos grid so a printout fits one page.
- **Light + dark mode** — warm butter / terracotta vs deep umber / cream. Pre-paint apply script in `<head>`.
- **Category chips** above the home grid driven by `[[juicercook.categories]]` in `site.toml`.

## Frontmatter shape

```yaml
---
title: Sourdough loaf
date: 2024-03-12
servings: 1 loaf
prepTime: PT30M           # ISO 8601 duration — 30 min
cookTime: PT45M
totalTime: PT18H          # optional
image: /img/sourdough.jpg
imageAlt: Crusty boule with an open crumb
summary: A reliable everyday loaf — 25 % whole wheat, 70 % hydration.
category: Bread           # → schema.org recipeCategory
cuisine: European         # → schema.org recipeCuisine
ingredients:
  - 500 g bread flour
  - 350 g water
  - 100 g active starter
  - 10 g sea salt
instructions:
  - Mix flour and water; rest 30 minutes (autolyse).
  - Add starter and salt; mix until incorporated.
  - Bulk ferment 4–6 hours.
photos:
  - src: /img/sourdough-shape.jpg
    caption: Final shape, before the cold retard
  - /img/sourdough-crumb.jpg      # plain string also works
tags: [bread, sourdough]
---

Optional body content — variations, notes, the story of how the recipe
came together — goes here in regular markdown, below the recipe card.
```

## When to pick juicercook vs juicerblog

**juicerblog** is a generic dated-post blog. You can use it for recipes by putting the ingredients list in the body, but you lose the structured rendering AND the schema.org JSON-LD.

**juicercook** is purpose-built. The structured frontmatter renders into a publication-quality card AND emits the JSON-LD Google needs for the rich-results recipe carousel. Pick juicercook if you publish recipes; pick juicerblog if you write *about* food without publishing structured recipes.
