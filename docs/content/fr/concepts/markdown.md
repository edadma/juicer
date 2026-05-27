---
title: Markdown
summary: Chaque extension markdown que juicer active, avec exemples.
weight: 50
---

Juicer s'appuie sur [`io.github.edadma/markdown`](https://github.com/edadma/markdown) — un analyseur CommonMark 0.31.2 complet. Chaque page reçoit les mêmes valeurs par défaut ; cette section parcourt ce qui est activé et le rendu de chaque élément.

## CommonMark — les bases

Toute la spécification CommonMark fonctionne sans aucune activation. Titres, paragraphes, listes, citations, code en ligne, blocs de code clôturés, liens, images.

```markdown
# Heading 1
## Heading 2
### Heading 3

A *paragraph* with **strong** emphasis and `inline code`.

> A blockquote.
> Lazy continuation works too.

1. Ordered list
2. With items
   1. Nested
3. Continues

[Link text](https://juicer.build/) and ![alt](https://placehold.co/40x40 "title").
```

## Extras GFM

La configuration de juicer active chaque extension GitHub-Flavored-Markdown que l'analyseur prend en charge :

### Tableaux

```markdown
| Tier | What's in it |
|------|--------------|
| 1 | site.pages, page URLs, drafts, sitemap, summary |
| 2 | live reload, render hooks, RSS, sections, i18n, themes |
```

se rend comme

| Tier | What's in it |
|------|--------------|
| 1 | site.pages, page URLs, drafts, sitemap, summary |
| 2 | live reload, render hooks, RSS, sections, i18n, themes |

### Texte barré

`~~old behavior~~` se rend comme ~~old behavior~~.

### Listes de tâches

```markdown
- [x] Tier 1 — done
- [x] Section list pages
- [ ] i18n
- [ ] Render hooks
```

se rend comme

- [x] Tier 1 — done
- [x] Section list pages
- [ ] i18n
- [ ] Render hooks

### Notes de bas de page

Vous pouvez glisser une référence de note[^numbers] en milieu de paragraphe et la définir ailleurs.

[^numbers]: Voici le corps de la note. Plusieurs paragraphes sont pris en charge.

### Liens automatiques

Les URL nues comme https://juicer.build/ sont reconnues et transformées en liens cliquables sans la syntaxe explicite `[…](…)`.

### Ponctuation intelligente

Les guillemets droits deviennent courbes : "hello" devient « hello », et `--` / `---` deviennent des tirets demi-cadratin/cadratin — comme ceci.

## Encadrés (admonitions)

Les encadrés de style GFM — `> [!NOTE]` / `> [!TIP]` / `> [!WARNING]` / `> [!IMPORTANT]` / `> [!CAUTION]` — se rendent comme des blocs stylés. Source markdown :

```markdown
> [!NOTE]
> This is the parser's built-in callout. It produces a styled `<div>` with
> the type baked into a class name.
```

> [!NOTE]
> This is the parser's built-in callout. It produces a styled `<div>` with
> the type baked into a class name.

> [!WARNING]
> The shortcode-based callouts (`\[= note =]…\[= /note =]`) and the markdown-native
> callouts above produce different HTML. The shortcodes are themed by juicerdocs
> directly; the native ones are styled by Tailwind's typography defaults.

## ID de titre automatiques

Chaque `<hN>` reçoit un `id` dérivé de son contenu en texte brut. La table des matières le relit ; les liens profonds fonctionnent d'emblée.

```markdown
## Hello, world!
```

devient `<h2 id="hello-world">Hello, world!</h2>` — regardez le fragment dans votre barre d'adresse si vous cliquez sur un titre de cette page.

## Listes de définitions

```markdown
Apple
: A round fruit, typically red or green.

Orange
: A citrus fruit. Also a color.
```

se rend comme

Apple
: A round fruit, typically red or green.

Orange
: A citrus fruit. Also a color.

## Émojis

`:smile:` devient :smile:, `:rocket:` devient :rocket:, `:tada:` devient :tada: — les points de code Unicode, pas des images.

## Blocs de code avec langage

```squiggly
<!DOCTYPE html>
<html>
<head><title>{{ .page.title }}</title></head>
<body>
  <main class="prose">{{ .content }}</main>
</body>
</html>
```

```scala
// Squiggly's Go-template-style syntax compiles down to a small AST.
case class TemplateRenderer(
  partials:   TemplateLoader = _ => None,
  data:       Map[String, Any] = Map.empty,
  functions:  Map[String, TemplateFunction] = Map.empty,
)
```

```bash
# A code block tagged `bash` gets a "BASH" badge and copy button.
sbt 'juicerJVM/run serve -s docs -L'
```

Le thème juicerdocs ajoute un bouton copier-dans-le-presse-papiers sur chaque `<pre>` — survolez pour le voir.
