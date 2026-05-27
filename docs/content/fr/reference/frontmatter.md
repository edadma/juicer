---
title: Frontmatter
summary: Chaque clé YAML que juicer (et les thèmes fournis) lisent dans le frontmatter d'une page.
weight: 25
---

Le bloc YAML en tête de chaque fichier de contenu. Juicer lit un
ensemble fixe de clés ; tout le reste est transmis aux gabarits comme
`.page.<key>`. Cette page catalogue les clés de **niveau moteur** (lues
par juicer lui-même) et pointe vers les clés propres aux thèmes (lues
par un thème fourni chacune, documentées sous la section de ce thème).

```markdown
---
title: Hello, World
date: 2024-03-12
tags: [intro, meta]
summary: A short description used in list pages and OpenGraph tags.
draft: false
---

The body of the page goes here.
```

## Clés de niveau moteur

Celles-ci sont lues par juicer lui-même. Chaque thème fourni peut compter
sur leur présence (ou leur normalisation vers un défaut sensé) sur chaque
enregistrement de page dans les gabarits.

### Identité de page

| Clé      | Type     | Quoi |
|----------|----------|------|
| `title`  | `String` | Titre de page. Retombe sur le premier titre du corps, puis sur le nom de fichier quand les deux sont absents. |
| `summary`| `String` | Résumé de page explicite. Auto-dérivé d'un marqueur `<!--more-->` ou du premier paragraphe (plafonné à 30 mots) quand absent. |
| `layout` | `String` | Supplante le layout par défaut. Pour une page unique, `file` est le défaut ; pour `_index.md`, `folder`. Définir `layout: home` (etc.) rend la page par `_default/home.html` à la place. |

### Publication et visibilité

| Clé      | Type                          | Quoi |
|----------|-------------------------------|------|
| `date`   | chaîne ISO-8601 ou date YAML  | Date de publication. Trois formes d'entrée reconnues : `2024-03-12T10:30:00Z` (avec décalage), `2024-03-12T10:30:00` (locale, supposée UTC), `2024-03-12` (date seule, minuit UTC). |
| `draft`  | `Boolean`                     | Quand `true`, la page est exclue de la construction à moins que `--drafts` ne soit passé. Les brouillons sont invisibles à la TDM, au sitemap, à l'index de recherche et aux listings de section. |
| `static` | `Boolean`                     | Marque une page comme **non-article** même si elle réside dans une section de style articles. Exclue de `.site.posts`, `.site.pagesByYear`, et des listings d'archive de mots-clés/auteurs. Utilisée pour les pages à-propos/colophon/contact d'un blogue. |

```yaml
---
title: Big announcement
date: 2024-12-25T09:00:00Z
draft: false
---
```

```yaml
---
title: About this site
static: true
---
```

[= note =]
**La règle de `date` :** seules les pages avec un frontmatter `date:`
**explicite** sont incluses dans `.site.posts`, `.site.pagesByYear` et la
sortie de `dateArchives`. Quand `date:` est absente, juicer retombe sur la
date de modification (mtime) du fichier source pour l'*ordre de tri* —
c'est une séparation délibérée : trier silencieusement par mtime est
pratique ; traiter silencieusement chaque page comme un article daté ne l'est pas.
[= /note =]

### Taxonomie

| Clé          | Type                       | Quoi |
|--------------|----------------------------|------|
| `tags`       | `String` ou `[String]`     | Mots-clés de l'article. Une simple chaîne est auto-enveloppée en une liste à un élément. Génère des pages d'archive `/tags/<slug>/` (conditionné par l'existence d'un layout `tag-page.html`). |
| `categories` | `String` ou `[String]`     | Un second axe de taxonomie. Même slugification et même motif d'archive mais à `/categories/<slug>/`. Utilisez l'un, l'autre, ou les deux. |

```yaml
---
title: Functional programming in Scala 3
tags: [scala, functional, language]
categories: tutorials
---
```

Slugification : minuscules → repli ASCII (`café` → `cafe`) → suites de
caractères non alphanumériques → `-`. Ainsi `"Functional Programming"`
devient `functional-programming`.

### Ordonnancement

| Clé            | Type     | Quoi |
|----------------|----------|------|
| `weight`       | `Int`    | Position de tri explicite au sein d'une section. Les poids plus bas se trient en premier. Les pages sans poids viennent après les pages pondérées. |
| `sortBy`       | `String` | (sur le `_index.md` d'une section) Supplante le `sortBy` du site. L'un de `"date"`, `"title"`, `"weight"`. |
| `paginate`     | `Int`    | (sur le `_index.md` d'une section) Supplante la taille de tranche `paginate` du site. |

### Auteurs

| Clé       | Type           | Quoi |
|-----------|----------------|------|
| `author`  | `String`       | ID d'auteur unique — référence un `id` dans `[[authors]]` de `site.toml`. Raccourci singulier pour `authors: [<id>]`. |
| `authors` | `[String]`     | IDs d'auteurs multiples. Quand `author:` et `authors:` sont tous deux absents, `.page.author` vaut `null` et `.page.authors` est la liste vide. |

```yaml
---
title: A co-authored post
authors: [ed, alice]
---
```

Les IDs d'auteur qui ne correspondent à aucun `[[authors]].id` retombent
sur un enregistrement embryonnaire `{id: "<typo>"}` pour que les gabarits
n'échouent pas ; l'archive par auteur est vide dans ce cas.

### Séries

| Clé            | Type     | Quoi |
|----------------|----------|------|
| `series`       | `String` | Nom de série. Les pages avec la même valeur `series:` (sensible à la casse) forment un groupe ordonné. |
| `seriesOrder`  | `Int`    | Position au sein de la série. Les pages sans `seriesOrder` se trient par `date` croissant après les pages ordonnées. |

```yaml
---
title: OS Internals, Part 1
series: OS Internals
seriesOrder: 1
---
```

### Alias (redirections)

| Clé       | Type                  | Quoi |
|-----------|-----------------------|------|
| `aliases` | `String` ou `[String]`| Anciennes URL qui doivent rediriger vers cette page. Chacune génère un talon HTML statique à l'ancienne URL avec un `<meta http-equiv="refresh">` vers l'URL canonique. |

```yaml
---
title: Setting up a Scala 3 project
aliases:
  - /old-blog-name/setting-up-scala/
  - /2023/setting-up/
---
```

Chaque alias doit inclure les barres obliques de tête et de fin :
`/old-url/`, pas `old-url` ni `/old-url`.

### Événements

Ces clés sont lues sur les pages de l'`eventsSection` (par défaut
`events`) — la section que juicer traite comme la source des événements de calendrier.

| Clé            | Type     | Quoi |
|----------------|----------|------|
| `recurring`    | `String` | `"weekly"` pour un événement récurrent hebdomadaire. L'événement se déploie sur chaque jour de semaine correspondant dans `.site.calendar`. |
| `recurringDay` | `String` | Nom de jour (`"Monday"`, …, `"Sunday"`) pour la récurrence. Retombe sur le jour de la semaine de la date de début quand absent. |

```yaml
---
title: Tuesday Coffee
date: 2024-03-12T09:00:00
recurring: weekly
recurringDay: Tuesday
---
```

### Photos

| Clé      | Type                                | Quoi |
|----------|-------------------------------------|------|
| `photos` | `[String]` ou `[{src, caption?, alt?}]` | Photos de la page. Agrégées à l'échelle du site dans `.site.photos` (triées par date de page décroissante). |

```yaml
---
title: Picnic in the park
date: 2024-06-12
photos:
  - "/img/picnic-01.jpg"
  - { src: "/img/picnic-02.jpg", caption: "The pie table", alt: "A long folding table loaded with summer pies" }
---
```

### OpenGraph / cartes sociales

Celles-ci supplantent les valeurs par défaut que l'assistant de gabarit
`ogTags` calcule autrement à partir de `.page.title`, `.page.summary`,
`.page.image`, etc.

| Clé             | Type     | Quoi |
|-----------------|----------|------|
| `description`   | `String` | Repli pour la description OpenGraph quand `summary` n'est pas approprié. |
| `image`         | `String` | Image de page par défaut — utilisée par `og:image` et comme vignette de carte par les thèmes qui en montrent une. |
| `ogTitle`       | `String` | Supplante le titre de page dans les balises de carte OpenGraph et Twitter. |
| `ogDescription` | `String` | Supplante la description dans les balises de carte OpenGraph et Twitter. |
| `ogImage`       | `String` | Supplante l'image dans les balises de carte OpenGraph et Twitter. |

```yaml
---
title: A long, descriptive, SEO-targeted post title
summary: A long summary tuned for the page's own list rendering.
ogTitle: A short, punchy title for social cards
ogDescription: A different summary that fits in a card preview.
ogImage: /img/posts/short-title-card.png
---
```

### Calculées (non définies par vous)

Ces champs apparaissent sur `.page.<key>` dans les gabarits mais ne sont
pas lus depuis le frontmatter — ils sont définis par juicer pendant la construction :

| Clé                  | Quoi |
|----------------------|------|
| `permalink`          | URL canonique relative au site — `/posts/the-post/`. |
| `relPermalink`       | Identique à `permalink` (parité Hugo). |
| `url`                | Identique à `permalink`. |
| `dateISO`            | `2024-03-12T00:00:00Z` (seulement quand `date:` est défini). |
| `dateLong`           | `March 12, 2024` (seulement quand `date:` est défini). |
| `dateShort`          | `2024-03-12` (seulement quand `date:` est défini). |
| `slug`               | Radical d'URL (dernier segment de chemin). |
| `wordCount`          | Nombre de mots du corps rendu. |
| `readingTime`        | `ceil(wordCount / 200)` minutes, plancher 1 pour les pages non vides. |
| `isSection`          | `true` pour les pages `_index.md`. |
| `parent`, `ancestors`| L'enregistrement `_index` de la section, puis la chaîne racine → parent. |
| `next`, `prev`       | Navigation entre pages sœurs par ordre des pages de section. |
| `pages`, `subsections`| (`_index.md` seulement) Enfants de la section. |
| `series` (bloc)      | `{name, pages, prev, next, index, total}` — peuplé quand la page déclare un `series:`. |

La référence complète de `.page`, incluant celles-ci, réside dans
[Données de gabarit → `.page`](/fr/reference/template-data/#page).

## Cascade — frontmatter hérité

Le `_index.md` d'une section peut déclarer une carte `cascade:` ; chaque clé à l'intérieur est héritée par chaque page descendante qui ne définit pas la même clé dans son propre frontmatter. Cela permet à une section d'établir des valeurs par défaut — auteur, layout, licence, drapeaux personnalisés — sans les répéter sur chaque page.

```yaml
# content/posts/_index.md
---
title: Posts
cascade:
  author: ed
  tone: editorial
  license: CC-BY
---
```

Chaque page sous `/posts/` reprend maintenant `.page.author = "ed"`, `.page.tone = "editorial"`, `.page.license = "CC-BY"` automatiquement. Un article spécifique peut supplanter n'importe lequel d'entre eux :

```yaml
# content/posts/guest-piece.md
---
title: Guest piece
author: alice    # overrides the cascaded "ed"
---
```

### Règles de résolution

- **Le frontmatter propre à la page l'emporte** sur toute valeur de cascade.
- **Les ancêtres plus proches l'emportent** sur les ancêtres plus lointains. La cascade du `_index.md` racine est la plus faible ; la cascade d'une section profondément imbriquée supplante tout ce qui est au-dessus d'elle.
- **La cascade d'une section ne s'applique PAS à son propre `_index.md`.** La cascade déclare des valeurs par défaut pour les *descendants*, pas pour la page qui la déclare. Le `_index.md` propre à la section n'hérite que des cascades des sections au-dessus de lui.
- **Les sections héritent de la cascade des sections ancêtres.** Le `_index.md` d'une section imbriquée reprend les cascades de chaque section ancêtre, puis fusionne les siennes (les siennes l'emportent).
- **Une cascade absente / vide est sans effet.** Les pages sans aucune cascade dans leur chaîne d'ancêtres voient exactement le frontmatter qu'elles ont écrit.

### Ce qui peut être mis en cascade

Toute clé de frontmatter que le moteur ou un thème lit. Candidats utiles :

- `author` / `authors` — attribution à l'échelle de la section.
- `layout` — force chaque page d'une section par un gabarit spécifique (p. ex. `layout: project` à travers une section de portfolio).
- `tags` / `categories` — taxonomie à l'échelle de la section ; les pages ajoutent les leurs et supplantent la liste par défaut au besoin.
- Clés personnalisées consommées par votre thème — `tone`, `region`, `license`, `noindex`, …

Moins utile (mais légal) à mettre en cascade : `date`, `title`, `summary` — celles-ci sont intrinsèquement par page et bénéficient rarement d'un défaut. Il n'y a aucune contrainte ; la cascade est un mécanisme général et l'auteur est responsable de choisir des clés qui ont du sens à hériter.

## Frontmatter propre au thème

Les thèmes fournis reconnaissent leurs propres clés de frontmatter par-dessus
la surface du moteur. Celles-ci sont documentées par thème :

| Thème                | Ce qu'il lit en plus |
|----------------------|----------------------|
| [juicerblog](/fr/themes/juicerblog/configuration/#frontmatter-par-page) | `static`, `layout: home`/`archive`, `series` |
| [juicerstudy](/fr/themes/juicerstudy/configuration/#frontmatter-par-page) | `toc`, `summary` (comme amorce), `minutes` |
| [juicerlanding](/fr/themes/juicerlanding/configuration/#frontmatter-par-page) | `layout: home`, `summary` (comme sous-titre), `lang` |
| [juicerportfolio](/fr/themes/juicerportfolio/configuration/#frontmatter-par-projet) | `layout: project`, `year`, `tagline`, `role`, `client`, `tools`, `category`, `hero`, `heroAlt`, `caption`, `gallery`, `link` |
| [juicerdocs](/fr/themes/juicerdocs/configuration/) | Clés de niveau moteur seulement — pas de frontmatter propre au thème. |

## Toute autre clé

Tout ce que vous écrivez dans le frontmatter que juicer ne lit pas est
exposé sur `.page.<key>` pour usage dans les gabarits. Ainsi ajouter un
champ personnalisé est simplement une question de l'écrire :

```yaml
---
title: Cherry pie
yield: 8 servings
prepTime: 45 min
cookTime: 1 h 10 min
---
```

```squiggly
<dl>
  <dt>Yield</dt><dd>{{ .page.yield }}</dd>
  <dt>Prep time</dt><dd>{{ .page.prepTime }}</dd>
  <dt>Cook time</dt><dd>{{ .page.cookTime }}</dd>
</dl>
```
