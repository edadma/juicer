---
title: Configuration
summary: Chaque clé de configuration de site que le thème juicerdocs lit, avec les valeurs par défaut.
weight: 10
---

juicerdocs lit sa configuration à deux endroits dans `site.toml` :

- Un petit ensemble de **clés de site de premier niveau** (les mêmes que juicer lui-même utilise pour des choses comme le titre de page et le drapeau `showCopyright`).
- Une **table `[juicerdocs]`** dotée d'un espace de noms pour les boutons propres au thème — logo, couleurs de marque, surcharges du mode sombre.

Tout ce que vous ne définissez pas garde les valeurs par défaut du thème. Les thèmes peuvent lire depuis n'importe quel espace de noms TOML ; juicerdocs utilise `[juicerdocs]` pour ne pas entrer en collision avec les clés que d'autres thèmes pourraient vouloir.

## Exemple rapide

```toml
title           = "My Project"
author          = "Acme Inc."
baseURL         = "https://docs.acme.example/"

theme           = "juicerdocs"
headingShift    = 0
showCopyright   = true

[juicerdocs]
logo            = "/acme-logo.png"
brand           = "#3b82f6"        # blue-500
brandStrong     = "#2563eb"        # blue-600
brandSoft       = "#dbeafe"        # blue-100
accent          = "#0891b2"        # cyan-600
leaf            = "#10b981"        # emerald-500

# Dark-mode overrides (optional; sensible defaults derived from the lights)
brandDark       = "#60a5fa"
brandStrongDark = "#93c5fd"
brandSoftDark   = "rgb(59 130 246 / .12)"
accentDark      = "#22d3ee"
leafDark        = "#34d399"
```

## Clés de premier niveau (lues par juicerdocs)

| Clé             | Type    | Défaut       | Ce qu'elle fait |
|-----------------|---------|--------------|-----------------|
| `title`         | string  | `"Untitled"` | Logotype dans la barre supérieure ; apparaît dans les balises `<title>` et le pied de page |
| `author`        | string  | `"Unnamed"`  | Copyright du pied de page (seulement quand `showCopyright = true`) |
| `baseURL`       | string  | (requis)     | Utilisé pour les permaliens absolus, le sitemap, OpenGraph |
| `theme`         | string  | (requis)     | Mettez ceci à `"juicerdocs"` pour activer le thème |
| `headingShift`  | int     | `2`          | La disposition juicerdocs fournit déjà un `<h1>{{ .page.title }}</h1>` externe, donc mettez ceci à `0` pour que le markdown `## Foo` se rende comme `<h2>` (la convention naturelle des auteurs de documentation) |
| `showCopyright` | bool    | `false`      | Quand vrai, le pied de page rend une ligne `© {{ .site.author }}` à côté de l'attribution « Built with Juicer » |

## Clés `[juicerdocs]`

### Logo

| Clé                | Type   | Défaut             | Quoi |
|--------------------|--------|--------------------|------|
| `juicerdocs.logo`  | string | `"/juicer-logo.png"` | Chemin (relatif à la racine de votre site) de l'image de logo. Déposez votre fichier dans `<src>/static/` et pointez ceci dessus. Utilisé dans la barre supérieure, le héros de la page d'accueil, la page 404, et comme favicon. |

La valeur par défaut pointe vers le `juicer-logo.png` fourni avec le thème — convenable si vous prévisualisez juicerdocs seul, mais vous voudrez votre propre logo pour tout déploiement réel.

### Appel à l'action du héros

| Clé                       | Type | Défaut  | Quoi |
|---------------------------|------|---------|------|
| `juicerdocs.getStarted`   | bool | `false` | Quand vrai, le héros de la page d'accueil montre un bouton primaire « Get started » liant à `/getting-started/`. Laissez non défini pour les sites qui n'ont pas de section de démarrage. |

Le héros montre aussi un bouton secondaire « Star on GitHub » quand le `repoURL` de premier niveau est défini (voir [Référence de thématisation → Clés d'habillage à l'échelle du site](/fr/reference/theming/#clés-d-habillage-à-l-échelle-du-site)). Quand les **deux** sont désactivés — `getStarted` non défini et pas de `repoURL` — la rangée d'actions se replie entièrement et le héros se termine à son paragraphe de résumé (pas d'espace vide réservé).

### Couleurs de marque (mode clair)

| Clé                          | Type   | Défaut      | Quoi |
|------------------------------|--------|-------------|------|
| `juicerdocs.brand`           | color  | `#d61472`   | Couleur de marque primaire — liens, remplissages de boutons, bordures actives de la barre latérale, début du dégradé de héros |
| `juicerdocs.brandStrong`     | color  | `#a30d57`   | État de survol des boutons primaires + liens mis en valeur |
| `juicerdocs.brandSoft`       | color  | `#fce7f3`   | Teinte de marque douce — survol de la barre latérale, fonds d'encadrés brand-soft, badges |
| `juicerdocs.accent`          | color  | `#6f1f9e`   | Accent secondaire — fin du dégradé de héros, surbrillances décoratives |
| `juicerdocs.leaf`            | color  | `#65a30d`   | « Vert de confirmation » — état « copié » du bouton de copie, indicateurs de succès |

Toute expression de couleur CSS fonctionne : hex (`#...`), `rgb(...)`, `hsl(...)`, `oklch(...)`, `color-mix(...)`, couleurs nommées. Les valeurs sont déposées dans des déclarations `<style>`, donc tout ce que le CSS accepterait ici fonctionne.

### Couleurs de marque (surcharges du mode sombre)

| Clé                              | Type   | Défaut                        |
|----------------------------------|--------|-------------------------------|
| `juicerdocs.brandDark`           | color  | `#ec4899`                     |
| `juicerdocs.brandStrongDark`     | color  | `#f472b6`                     |
| `juicerdocs.brandSoftDark`       | color  | `rgb(236 72 153 / .12)`       |
| `juicerdocs.accentDark`          | color  | `#c084fc`                     |
| `juicerdocs.leafDark`            | color  | `#84cc16`                     |

Celles-ci supplantent les valeurs du mode clair quand l'utilisateur bascule en mode sombre (ou que la page se charge avec la préférence système `prefers-color-scheme: dark`). Ne définissez que celles que vous voulez différentes — tout ce que vous omettez garde sa valeur du mode clair, ce qui est *presque certainement faux* en mode sombre pour les roses/bleus de marque, donc il vaut la peine de définir les cinq.

[= tip =]
**Choisir une palette.** Un point de départ pratique : choisissez une teinte de marque, puis dérivez `brandStrong` (≈10 % plus sombre), `brandSoft` (teinte très claire à ~10 % de luminosité), `brandDark` (≈10 % plus clair — le mode sombre veut plus de luminance), `brandStrongDark` (légèrement plus clair encore), `brandSoftDark` (superposition transparente à ~12 % d'alpha). Des outils comme [oklch.com](https://oklch.com/) et [coolors.co](https://coolors.co/) rendent cela rapide.
[= /tip =]

## Où atterrissent les valeurs

Le partial `<head>` émet un bloc `<style>` immédiatement après `juicerdocs.css`, redéfinissant uniquement les variables que vous avez définies :

```css
:root {
  --jd-brand:        #3b82f6;   /* from juicerdocs.brand */
  --jd-brand-strong: #2563eb;   /* from juicerdocs.brandStrong */
  /* ... */
}
[data-theme="dark"] {
  --jd-brand:        #60a5fa;   /* from juicerdocs.brandDark */
  /* ... */
}
```

Tout ce qui n'est pas défini retombe sur les valeurs par défaut déclarées en tête de `juicerdocs.css`. Le thème bascule toujours entre clair et sombre via l'attribut `data-theme` sur `<html>`, défini par le script avant-peinture dans `<head>`.

## Au-delà de la configuration

Déposez un fichier au même chemin sous votre propre site pour supplanter quoi que ce soit dans le thème — c'est le motif ordinaire de [superposition de thème](/fr/concepts/themes/) :

| Vous voulez changer… | Supplantez à… |
|----------------------|---------------|
| La barre supérieure  | `<src>/partials/topbar.html` |
| Le balisage du pied de page | `<src>/partials/footer.html` |
| L'encadré « Note »   | `<src>/shortcodes/note.html` |
| La page 404          | `<src>/layouts/_default/404.html` |
| Du CSS personnalisé  | `<src>/static/site.css` (et liez-le depuis votre `head.html` supplanté) |

Les fichiers du site l'emportent **toujours** sur les fichiers du thème, donc les surcharges sont une opération un-fichier-à-la-fois — pas besoin de forker le thème.

## SEO

Chaque thème juicer livre un partial SEO partagé (`partials/seo.html`) qui émet le bloc meta standard : description (avec repli au niveau du site), lien canonique, méta auteur, robots `noindex`, cartes OpenGraph + Twitter (via le builtin `ogTags`), découverte de flux Atom, et JSON-LD propre au thème. Le moteur écrit séparément `sitemap.xml` et `robots.txt`.

### Clés à l'échelle du site

```toml
description = "Reference and how-to docs for the juicer static site generator."
ogImage     = "/og/default.png"

robots   = true
noindex  = false                  # set true for staging/preview domains
disallow = ["/internal/"]
```

### Frontmatter par page

```yaml
---
title: Configuration reference
summary: A one-sentence dek for search results and OG cards.
image: /img/og/configuration.png
ogTitle: A snappier social headline
ogDescription: Tightened for socials
noindex: true   # excluded from sitemap, JSON-LD suppressed, <meta robots> emitted
---
```

### Données structurées émises

juicerdocs émet `TechArticle` sur chaque page (plus spécifique qu'`Article` ; apparaît dans le carrousel de documentation pour développeurs de Google et dans le panneau de rail droit de Stack Overflow pour les termes étiquetés) :

| Page | Schéma |
|------|--------|
| Section racine | `WebSite` |
| Toute autre page | `TechArticle` |
| Toute page avec ancêtres | `BreadcrumbList` (particulièrement précieux pour la documentation — Google l'utilise souvent pour la hiérarchie par résultat) |

`noindex: true` supprime TOUT le JSON-LD de cette page.

### Supplanter le partial SEO

Déposez un fichier à `<src>/partials/seo.html` (ou `seo-jsonld.html` pour seulement la partie données structurées). Les surcharges du site l'emportent sur la copie du thème.
