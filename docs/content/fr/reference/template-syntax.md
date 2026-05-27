---
title: Syntaxe de gabarit
summary: Aide-mémoire squiggly — les gestes que vous utiliserez sur chaque page.
weight: 40
---

Squiggly est le langage de gabarit qu'utilise juicer. Il ressemble aux gabarits Go. Cette page est l'aide-mémoire pratique ; la [référence squiggly complète](https://github.com/edadma/squiggly) couvre tout le reste.

## Substitution

```squiggly
{{ .name }}              value at .name in the data context
{{ .page.title }}        nested
{{ .args[0] }}           list element by index (in shortcodes)
```

## Fonctions intégrées

Juicer ajoute quelques fonctions propres au projet aux valeurs par défaut de
squiggly. Appelez-les comme des expressions :

| Fonction                    | Quoi |
|-----------------------------|------|
| `{{ relURL '/path' }}`      | URL relative au site (préfixe `baseURL.path` ajouté si non trivial) |
| `{{ absURL '/path' }}`      | URL absolue (`baseURL.base + relURL`) |
| `{{ relLangURL lang '/path' }}` | Comme `relURL`, mais préfixé du segment d'URL de `lang` (le `relLangURL` de Hugo) — voir [i18n](/fr/concepts/i18n/) |
| `{{ absLangURL lang '/path' }}` | Comme `absURL`, mais préfixé du segment d'URL de `lang` |
| `{{ markdownify s }}`       | Rend une chaîne markdown en HTML |
| `{{ emojify s }}`           | Remplace `:smile:` etc. par l'émoji Unicode |
| `{{ i18n lang 'key' }}`     | Recherche une chaîne i18n (retombe sur la langue par défaut puis sur la clé littérale) |
| `{{ ogTags .page }}`        | Émet les balises `<meta>` de carte OpenGraph + Twitter pour un enregistrement de page |
| `{{ imageDims '/path' }}`   | Lit les dimensions en pixels d'une image sur le disque ; renvoie `{width, height}` ou une carte vide (voir ci-dessous) |
| `{{ imageVariants '/path' }}` | Génère des variantes redimensionnées + reformatées d'une image ; renvoie `{original, originalWidth, originalHeight, variants}` (voir ci-dessous) |
| `{{ srcset '/path' 'fmt' }}`  | En une ligne : construit le corps `srcset` séparé par des virgules pour un format de variante (voir ci-dessous) |

### `imageDims`

Lit l'en-tête d'une image sur le disque et renvoie une carte avec `width` et
`height` (entiers en pixels). Utile pour émettre `<img width=... height=...>` afin
que le navigateur puisse réserver l'espace avant l'arrivée des octets — élimine le
décalage de mise en page cumulatif.

```squiggly
{{ d := imageDims '/img/hero.jpg' }}
{{ if d.width }}
  <img src="/img/hero.jpg" width="{{ d.width }}" height="{{ d.height }}" alt="" />
{{ else }}
  <img src="/img/hero.jpg" alt="" />
{{ end }}
```

Ordre de résolution : le chemin est recherché d'abord sous le répertoire de sortie
construit (pour que les fichiers `static/` du thème + du site et toute image générée
fonctionnent), puis sous la racine source. Les chemins peuvent être absolus au site
(`/img/x.png`) ou relatifs ; les URL absolues (`http://…`) ne sont pas récupérées.

Renvoie une carte vide quand le fichier est manquant, illisible, ou dans un format
que l'analyseur d'en-tête ne comprend pas. Formats reconnus :
**PNG, JPEG (de base + progressif), GIF87a/89a, WebP** (VP8 / VP8L / VP8X). Scala
pur — pas de `javax.imageio`, pas de FFI, fonctionne sur chaque cible.

Cache par construction : chaque chemin unique est lu une fois même si un shortcode
se déclenche sur des centaines de pages.

### `imageVariants` et `srcset`

Génération optionnelle de variantes d'image au moment de la construction. Avec un
bloc `[images]` dans `site.toml` et ImageMagick (`magick`) dans le PATH, juicer
redimensionne et reformate les images source en un jeu de variantes prêt pour
`<picture>` :

```toml
[images]
enabled  = true
widths   = [320, 640, 960, 1280]
formats  = ["webp", "original"]   # most-modern first; original is a passthrough
quality  = 80
cacheDir = ".image-cache"
```

`imageVariants` renvoie la forme structurée complète — utile quand un thème émet son
propre balisage `<picture>` :

```squiggly
{{ v := imageVariants '/img/hero.jpg' }}
{{ if v.variants }}
  <picture>
    {{ for src <- v.variants }}
      {{ if src.mime and src.format != 'original' }}
        <source srcset="{{ src.url }} {{ src.width }}w" type="{{ src.mime }}" />
      {{ end }}
    {{ end }}
    <img src="{{ v.original }}" width="{{ v.originalWidth }}" height="{{ v.originalHeight }}" alt="" />
  </picture>
{{ else }}
  <img src="{{ v.original }}" alt="" />
{{ end }}
```

Carte renvoyée :

| Clé              | Type   | Quoi |
|------------------|--------|------|
| `original`       | string | URL de l'original transmis tel quel (absolu au site) |
| `originalWidth`  | int    | Largeur en pixels de la source (`0` si inconnue) |
| `originalHeight` | int    | Hauteur en pixels de la source (`0` si inconnue) |
| `variants`       | list   | Un `{width, format, url, mime}` par variante générée |

`srcset` est un raccourci en une ligne pour le cas courant où un layout veut déposer
la liste de variantes directement dans un attribut `<img srcset=...>` pour un seul
format :

```squiggly
<img src="/img/hero.jpg"
     srcset="{{ srcset '/img/hero.jpg' 'webp' }}"
     sizes="(min-width: 800px) 50vw, 100vw"
     alt="" />
```

Renvoie `""` quand le format demandé n'est pas dans `[images].formats`, que la
source ne peut être résolue, ou que les variantes n'ont pas été générées
(fonctionnalité désactivée, encodeur absent, etc.).

**Cache et encodeur.** Chaque nom de fichier généré intègre un hachage FNV-1a 64
bits des octets source (`hero-640w.<hash>.webp`), donc relancer la construction sur
une image inchangée saute l'appel à l'encodeur et éditer l'image invalide chaque
variante d'un coup. Le backend d'encodeur (`magick`) est sondé une fois par
construction ; s'il n'est pas dans le PATH, un seul avis s'affiche sur stderr et la
fonctionnalité se dégrade proprement en un `VariantSet` de transmission seulement
(`variants` est vide). Même comportement sur les cibles Scala Native et Scala.js
jusqu'à ce que le lancement de processus arrive dans `cross_platform`.

Les largeurs au-dessus de la propre largeur de la source sont abandonnées (pas
d'agrandissement) ; la largeur exacte de la source est toujours incluse pour que le
`<img>` de repli puisse pointer vers une variante de même taille. L'emplacement
`original` à la largeur source est une copie d'octets 1:1 plutôt qu'un aller-retour
par l'encodeur — garde le temps de construction raisonnable sur les sites chargés en
photos et préserve les octets source exactement.

## Conditions

```squiggly
{{ if .page.summary }}
  <p class="lead">{{ .page.summary }}</p>
{{ end }}

{{ if .page.draft }}
  <span class="badge">Draft</span>
{{ else }}
  <span class="badge">Published</span>
{{ end }}
```

`if` est vrai sur les chaînes non vides, les nombres non nuls, les listes/cartes non vides, et `true`. `null` / vide / `false` / `0` sont faux.

Opérateurs logiques : `and`, `or`, préfixe `not`.

```squiggly
{{ if .page.prev or .page.next }}
  <nav>...</nav>
{{ end }}
```

## Boucles

```squiggly
{{ for p <- .section.pages }}
  <li><a href="{{ p.url }}">{{ p.title }}</a></li>
{{ end }}

{{ for k, v <- .page }}
  {{ k }}: {{ v }}
{{ end }}
```

## Partials

```squiggly
{{ partial 'topbar' . }}              call partials/topbar.html with the current data
{{ partial 'page-toc' .page }}        with a different data context
```

## Defines et blocs

```squiggly
{{ define main }}
  <article>{{ .content }}</article>
{{ end }}

{{ block main . }}{{ .content }}{{ end }}    fallback content if main isn't defined
```

`define` est au niveau instruction (aucune sortie). `block <name> <data>` recherche le bloc nommé, l'appelle avec `<data>` comme contexte, et retombe sur son gabarit interne si le bloc n'est pas défini.

## Filtres intégrés

La plupart des builtins squiggly fonctionnent soit comme fonction, soit comme cible de tube :

```squiggly
{{ trim .page.summary }}
{{ .page.summary | trim }}
{{ .name | upper }}
{{ .x | replace 'foo' 'bar' }}
```

Les utiles :

| Builtin | Quoi |
|---------|------|
| `len`   | Longueur d'une liste / chaîne |
| `head`  | Premier élément d'une liste |
| `tail`  | Tout sauf le premier |
| `trim`  | Élague les espaces |
| `upper` / `lower` | Casse |
| `replace 'a' 'b'` | Substitution de chaîne |
| `split 'sep'` | Découpe de chaîne |
| `join 'sep'` | Jointure de liste |

## Commentaires

```squiggly
{{ // squiggly's not too noisy in templates }}
```

## Portée à l'intérieur des boucles

À l'intérieur d'un bloc `{{ for x <- coll }}`, `.foo` se résout contre l'**élément
itéré courant**, pas le contexte de page externe. Utilisez `$.foo` pour remonter
jusqu'à la racine de données de premier niveau :

```squiggly
{{ for p <- .section.pages }}
  <li>
    <a href="{{ p.url }}">{{ p.title }}</a>
    {{ if eq p.url $.page.url }}<span>(this page)</span>{{ end }}
  </li>
{{ end }}
```

Un piège courant : écrire `{{ .site.title }}` à l'intérieur d'une boucle et ne rien
obtenir. Utilisez `{{ $.site.title }}` — `.site` n'existe pas sur l'élément itéré.
