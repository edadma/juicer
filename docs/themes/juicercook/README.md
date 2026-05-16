# juicercook

Recipe-site theme for [juicer](https://github.com/edadma/juicer). Each recipe is a single markdown file with a structured frontmatter block — title, servings, prep/cook time (ISO 8601 durations), ingredients list, instructions list — that renders into a card-style layout and a schema.org `Recipe` JSON-LD block for Google's recipe-rich-results pipeline.

## Shape

- **Recipes are flat or grouped.** `content/<recipe>.md` works, or group them under `content/breads/<recipe>.md` for "All breads" landing pages.
- **Hero image, then ingredients + steps.** The visual anchor is the dish; the ingredients list runs alongside numbered instructions.
- **schema.org Recipe JSON-LD** auto-emitted from frontmatter — `recipeIngredient`, `recipeInstructions` (HowToStep), `prepTime`, `cookTime`, `recipeYield`, `nutrition`.
- **Print-friendly stylesheet** — `print` media query strips chrome.

## Frontmatter

```yaml
---
title: Sourdough loaf
date: 2024-03-12
servings: 1 loaf
prepTime: PT30M           # ISO 8601 duration — 30 min
cookTime: PT45M
totalTime: PT5H           # optional; auto-summed if absent
image: /img/sourdough.jpg
imageAlt: Crusty loaf with open crumb
summary: A reliable everyday loaf — 25 % whole wheat, 70 % hydration.
ingredients:
  - 500 g bread flour
  - 350 g water
  - 100 g active starter
  - 10 g salt
instructions:
  - Mix flour and water; rest 30 minutes (autolyse).
  - Add starter and salt; mix until incorporated.
  - Bulk ferment 4–6 hours with stretch-and-folds every 30 min.
tags: [bread, sourdough]
---

Optional notes / variations / commentary go in the markdown body, below
the recipe card. The card itself is built from frontmatter.
```

## site.toml palette overrides

```toml
[juicercook]
brand        = "#7a2e10"   # terracotta
accent       = "#c0571d"   # cooked-paprika accent
fontSans     = "'Inter', system-ui, sans-serif"
fontSerif    = "'Playfair Display', Georgia, serif"
```
