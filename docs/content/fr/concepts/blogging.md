---
title: Fonctionnalités de blogue
summary: Mots-clés, catégories, pagination, dates, temps de lecture — l'ensemble de fonctionnalités qui transforment un site juicer en blogue.
weight: 40
---

Les fonctionnalités de site de documentation de juicer (sections, `_index.md`, partials, thèmes) conviennent telles quelles à un blogue, mais un vrai blogue veut habituellement quatre choses de plus : un moyen d'étiqueter les articles, des pages d'archive qui regroupent les articles par mot-clé, des pages de liste paginées pour que la page d'accueil ne traîne pas, et des dates de publication analysées pour que les pages de liste puissent trier par récence. Cette page couvre tout cela, plus les estimations de temps de lecture et le thème `juicerblog` fourni qui exerce chaque fonctionnalité.

[= note =]
Chaque fonctionnalité de cette page est **optionnelle**. Un site de documentation qui ne définit pas de frontmatter `tags`, ne configure pas `paginate` et ne fournit pas de frontmatter `date` se rendra exactement de la même manière après ces fonctionnalités qu'avant — mêmes fichiers, mêmes URL, sortie identique à l'octet près.
[= /note =]

## Mots-clés et catégories

Ajoutez un champ `tags` au frontmatter d'un article pour le marquer. Une liste ou une simple chaîne sont toutes deux acceptées :

```yaml
---
title: Whirlwind tour of Scala 3 enums
date: 2024-03-12
tags: [scala, language]
---
```

```yaml
---
title: A short note
tags: meta
---
```

Deux pages d'archive sont émises pour chaque mot-clé utilisé par le site :

| Motif d'URL             | Ce qu'elle liste |
|-------------------------|------------------|
| `/tags/`                | Chaque mot-clé utilisé par le site, avec le nombre d'articles |
| `/tags/<slug>/`         | Chaque article étiqueté avec ce mot-clé précis |

Les noms de mots-clés sont « slugifiés » pour les URL — mis en minuscules, repliés en ASCII (`café` → `cafe`), et toute suite de caractères non alphanumériques est réduite à un seul `-`. Ainsi un mot-clé nommé `"Functional Programming"` devient `/tags/functional-programming/`.

`categories` est analysé à l'identique et traité comme un **axe distinct**. Vous pouvez utiliser les deux ou aucun — les sites qui veulent une seule façon de regrouper les articles en choisissent un et ignorent l'autre :

```yaml
---
title: Setting up the studio
date: 2024-03-12
categories: [behind-the-scenes]
tags: [meta, design]
---
```

Cet article serait atteignable à `/categories/behind-the-scenes/` ainsi qu'à `/tags/meta/` et `/tags/design/`.

### Gabarits

Les archives de mots-clés et de catégories ont besoin de leurs propres layouts. Le thème `juicerblog` livre une paire par défaut ; si vous montez votre propre thème, déposez ces deux layouts sous `layouts/_default/` :

| Fichier layout    | Ce qu'il rend |
|-------------------|---------------|
| `tag-list.html`   | L'index `/tags/` — répertoire complet de chaque mot-clé |
| `tag-page.html`   | L'archive d'un seul mot-clé, p. ex. `/tags/scala/` |

Les mêmes noms avec `category-list.html` / `category-page.html` couvrent l'axe des catégories.

Les données que voient vos layouts de mots-clés :

```squiggly
{{ .terms }}                 // List of every term — for tag-list.html
{{ .terms[0].name }}         // "scala"
{{ .terms[0].slug }}         // "scala"
{{ .terms[0].url }}          // "/tags/scala/"
{{ .terms[0].count }}        // 7

{{ .term }}                  // The current term — for tag-page.html
{{ .term.name }}             // "scala"
{{ .term.pages }}            // List of pages tagged with this term
{{ .term.pages[0].title }}   // "Whirlwind tour of Scala 3 enums"
```

[= tip =]
Si un layout est absent, l'archive correspondante est silencieusement sautée. Ainsi un site de documentation qui ne livre ni `tag-list.html` ni `tag-page.html` n'obtiendra pas d'URL de mots-clés même si vous écrivez accidentellement `tags: [foo]` quelque part.
[= /tip =]

### Accès depuis n'importe quel gabarit

Les gabarits qui ne sont pas des archives de mots-clés — disons, votre `topbar.html` — peuvent quand même itérer sur chaque mot-clé utilisé par le site :

```squiggly
{{ .site.tags }}            // List of every tag, sorted by count desc
{{ .site.categories }}      // Same shape, for categories
```

Chaque entrée porte `name`, `slug`, `url`, `count`, `pages` — même forme que `.terms`.

## Pagination

Un blogue avec trente articles ne devrait pas rendre chaque article sur la page d'accueil. Définissez `paginate` dans `site.toml` pour découper les pages d'index de section en plusieurs pages :

```toml
paginate = 10
sortBy = "date"
```

Cela indique à juicer :

[= steps =]
## Découper les pages de section

Pour chaque page d'index de section (chaque `_index.md`), prendre les pages enfants de la section et les diviser en tranches de 10.

## Rendre la tranche 1 vers `index.html`

La première tranche atterrit à l'URL naturelle de la section — `/posts/index.html`, `/index.html` pour la racine du site, etc.

## Rendre les tranches 2..N vers `page/N/index.html`

La deuxième tranche atterrit à `/posts/page/2/index.html`, la troisième à `/posts/page/3/`, et ainsi de suite. Compatible avec l'hébergement statique : chaque URL est un vrai répertoire avec un vrai fichier.
[= /steps =]

Si une section a moins de pages que `paginate`, seule la première tranche est émise et `total` vaut `1` — votre gabarit n'a pas besoin de traiter ce cas spécialement.

### `sortBy`

| Valeur    | Ordre                                                        |
|-----------|--------------------------------------------------------------|
| `"date"`  | `.page.date` décroissant (plus récent d'abord) ; le défaut du blogue |
| `"title"` | `.page.title` croissant (alphabétique)                       |
| `"weight"`| frontmatter `weight` croissant — comme le défaut de juicer   |

Une page avec un frontmatter `weight` supplante quel que soit le tri utilisé par la section, vous pouvez donc épingler un article « Bienvenue » au-dessus des listings triés par date.

### Surcharges par section

Définissez `paginate` ou `sortBy` dans le frontmatter du `_index.md` d'une section pour supplanter le réglage du site :

```markdown
---
title: Notes
paginate: 20
sortBy: title
---

A handful of short, alphabetised notes.
```

### Gabarits

Les données que voient les layouts — tant les layouts `_index` que les archives de mots-clés :

```squiggly
{{ .section.paginator.current }}     // 1-based index of THIS slice
{{ .section.paginator.total }}       // total slice count
{{ .section.paginator.pages }}       // pages on THIS slice (already sliced)
{{ .section.paginator.first }}       // URL of slice 1
{{ .section.paginator.last }}        // URL of last slice
{{ .section.paginator.prevURL }}     // empty string on slice 1
{{ .section.paginator.nextURL }}     // empty string on the last slice
```

Un pied de pagination typique :

```squiggly
{{ if .section.paginator.prevURL }}
  <a href="{{ .section.paginator.prevURL }}">← Newer</a>
{{ end }}
<span>Page {{ .section.paginator.current }} of {{ .section.paginator.total }}</span>
{{ if .section.paginator.nextURL }}
  <a href="{{ .section.paginator.nextURL }}">Older →</a>
{{ end }}
```

## Dates

Le frontmatter `date` est analysé en un véritable horodatage, pas une chaîne transmise telle quelle. Trois formes d'entrée sont reconnues :

| Forme                          | Traitée comme                      |
|--------------------------------|------------------------------------|
| `2024-03-12T10:30:00Z`         | ISO-8601 complet avec décalage     |
| `2024-03-12T10:30:00`          | Date-heure locale — supposée UTC   |
| `2024-03-12`                   | Date simple — minuit UTC           |

Si `date` est absente, juicer retombe sur la date de modification (mtime) du fichier markdown source. Cela signifie qu'un article fraîchement écrit se trie correctement sans que vous ayez à définir `date` explicitement.

### Rendu

Trois assistants pré-formatés accompagnent la valeur analysée, pour que les gabarits n'aient pas à appeler de fonctions de formatage :

| Champ             | Exemple                       | À utiliser pour   |
|-------------------|-------------------------------|-------------------|
| `.page.date`      | `OffsetDateTime`              | Tri, calculs      |
| `.page.dateISO`   | `2024-03-12T00:00:00Z`        | Attributs `<time datetime=...>` |
| `.page.dateLong`  | `March 12, 2024`              | Corps de texte    |
| `.page.dateShort` | `2024-03-12`                  | Pages de liste compactes |

Une ligne de méta-article standard :

```squiggly
<time datetime="{{ .page.dateISO }}">{{ .page.dateLong }}</time>
```

[= note =]
Les noms de mois anglais en toutes lettres (`January`, `February`, …) sont codés à la main dans juicer plutôt que pilotés par un `DateTimeFormatter` conscient de la locale. La raison est purement pratique : la construction Native de juicer livre une base de données de locales minimale et `MMMM` y retombe sur `M01`/`M02`. Les noms de mois codés à la main se rendent à l'identique sur les cibles JVM, JS et Native. Si vous avez besoin d'un format long non anglais dès maintenant, écrivez votre propre assistant à partir de `.page.dateShort` plus votre propre table de mois.
[= /note =]

## Temps de lecture et nombre de mots

Deux champs de plus calculés automatiquement pour chaque page :

| Champ                | Type | Quoi |
|----------------------|------|------|
| `.page.wordCount`    | int  | Nombre de mots du corps HTML rendu (après les shortcodes, avant le retrait) |
| `.page.readingTime`  | int  | Minutes — `ceil(wordCount / 200)`, avec un plancher de 1 pour les pages non vides |

Le chiffre de 200 mots par minute est la moyenne popularisée par Medium. Si vous voulez une autre cadence, rendez `wordCount` directement et divisez-le vous-même dans le gabarit :

```squiggly
{{ .page.readingTime }} min read
```

```squiggly
about {{ .page.wordCount }} words
```

Les pages vides obtiennent `wordCount = 0` et `readingTime = 0` pour qu'un `_index.md` embryonnaire ne dise pas « 1 min de lecture » de façon trompeuse.

## Permaliens

Par défaut, l'URL que juicer émet pour une page est en correspondance un pour un
avec son emplacement dans l'arborescence `content/` : `content/posts/hello.md`
devient `/posts/hello/`. C'est le bon comportement pour les sites de documentation
où la disposition des fichiers *est* l'architecture de l'information. Pour un
blogue, vous voulez habituellement autre chose — la plupart des blogues routent
chaque article par `/<année>/<mois>/<slug>/`, que l'article réside dans
`content/posts/`, `content/posts/scala/` ou `content/drafts/`.

Configurez cela avec la table `[permalinks]` dans `site.toml` :

```toml
[permalinks]
posts = ":year/:month/:slug/"
notes = ":slug/"
```

Chaque clé est le **nom de section** (le premier segment de chemin après le retrait
de `htmlDir`). Chaque valeur est un gabarit d'URL avec des jetons de substitution.
Un article sous `content/posts/2024-03-12-hello.md` avec `date: 2024-03-12` dans
son frontmatter se rend en :

| URL par défaut                | Avec `posts = ":year/:month/:slug/"` |
|-------------------------------|--------------------------------------|
| `/posts/2024-03-12-hello/`    | `/2024/03/2024-03-12-hello/`         |

(Astuce : quand vous utilisez des permaliens préfixés par date, retirez le préfixe
de date du nom de fichier et laissez `stripPrefix` faire son travail — gardez l'URL propre.)

### Les jetons disponibles

| Jeton       | Se résout en |
|-------------|--------------|
| `:slug`     | Le nom de fichier nettoyé (préfixes numériques retirés, suites non alphanumériques réduites) |
| `:title`    | `slugify(.page.title)` — le `title` du frontmatter mis en minuscules et replié en ASCII |
| `:year`     | Année à 4 chiffres depuis `.page.date` |
| `:month`    | Mois à 2 chiffres depuis `.page.date` |
| `:day`      | Jour à 2 chiffres depuis `.page.date` |
| `:section`  | Le nom de section lui-même — utile dans des motifs imbriqués comme `:year/:section/:slug/` |

`:title` et `:slug` sont habituellement différents — le slug vient du nom de
fichier (donc `01-getting-started.md` devient `getting-started`), le titre vient du
frontmatter `title:` (donc `"Getting Started"` devient `getting-started` aussi, mais
`"Café au Lait"` devient `cafe-au-lait`). Utilisez `:title` quand les noms de
fichiers sont opaques ou numérotés et que vous voulez quand même des URL lisibles :

```toml
[permalinks]
posts = ":year/:title/"
```

### Les pages d'index de section restent en place

Les fichiers `_index.md` ne sont **jamais** routés par un gabarit de permalien.
Même quand `posts = ":year/:slug/"` est défini, la page `posts/_index.md` réside
toujours à `/posts/`, pas à `/2024/posts/`. La raison est structurelle : le
`_index.md` décrit une section, pas un morceau de contenu, et l'URL d'une section
doit être prévisible pour que les liens de votre navigation et de votre fil
d'Ariane continuent de fonctionner.

### Les sections que vous ne listez pas gardent leur URL par défaut

`[permalinks]` est une **carte de surcharges**, pas un interrupteur tout-ou-rien.
Les sections qui ne sont pas des clés dans la table se rendent à leur URL de
chemin physique par défaut. Ainsi un site avec à la fois des articles de blogue
(sous `/posts/` permalié) et des pages de prose (`about.md`, `contact.md` à la
racine) liste simplement `posts` dans `[permalinks]` et laisse le reste tranquille :

```toml
[permalinks]
posts = ":year/:month/:slug/"
# `about.md` is still at /about/, `contact.md` still at /contact/.
```

[= tip =]
Si un gabarit de permalien utilise `:year` / `:month` / `:day` pour un article qui
n'a pas de frontmatter `date:`, juicer retombe sur la date de modification (mtime)
du fichier markdown source. Cela signifie qu'un article fraîchement écrit obtient
la date courante même quand vous avez oublié de définir le frontmatter — pratique
pour les brouillons, mais bon à savoir quand vous faites `git mv` sur des fichiers
et trouvez l'URL déplacée.
[= /tip =]

### Ce qui se passe sur le disque

Les gabarits de permalien changent à la fois l'URL et l'emplacement d'écriture sur
le disque : juicer ne garde pas deux copies de la page. Ainsi avec
`posts = ":year/:month/:slug/"`, un article s'écrit uniquement vers
`<dst>/2024/03/hello/index.html` ; le répertoire hérité `<dst>/posts/hello/` n'est
jamais créé. (Si vous définissez `htmlDir = "html"` dans `site.toml`, le chemin est
`<dst>/html/2024/03/hello/index.html`, puisque `htmlDir` est le préfixe de système
de fichiers retiré des URL.)

## Archives par date

La plupart des blogues finissent par vouloir une navigation « parcourir les
articles par date » : une page d'archive pour chaque année, éventuellement avec une
sous-archive pour chaque mois. Activez-la avec une ligne dans `site.toml` :

```toml
dateArchives = true
```

Juicer émet alors deux sortes de pages, conditionnées indépendamment par la
présence de leurs layouts :

| Layout                        | Motif d'URL             | Ce qu'il liste |
|-------------------------------|-------------------------|----------------|
| `_default/date-year.html`     | `/<année>/index.html`   | Chaque article daté de cette année, plus un récapitulatif par mois |
| `_default/date-month.html`    | `/<année>/<mois>/index.html` | Chaque article daté de cette année + ce mois |

Les deux layouts sont optionnels. Livrez le layout d'année pour obtenir des
archives à l'année seulement ; livrez les deux pour une granularité au mois
complète ; n'en livrez aucun et `dateArchives = true` ne fait rien de visible.

[= note =]
Seules les pages avec un frontmatter `date:` **explicite** entrent dans les
archives. Les pages dont le `.page.date` provient du repli sur la mtime du système
de fichiers sont exclues — sinon un site de documentation qui active la
fonctionnalité trouverait soudain chaque page de référence dans l'archive de
l'année courante. La règle est « si vous avez défini une date, vous y êtes ; sinon,
vous n'y êtes pas ».
[= /note =]

### Données d'archive d'année

Le layout `date-year.html` voit :

```squiggly
{{ .year }}                // 2024 (BigDecimal)
{{ .pages }}               // every dated post in 2024, newest first
{{ .pages[0].title }}      // most recent post's title
{{ .months }}              // per-month roll-up, ascending by month number
{{ .months[0].month }}     // 1 (BigDecimal)
{{ .months[0].monthName }} // "January"
{{ .months[0].url }}       // "/2024/01/"
{{ .months[0].count }}     // 4 (BigDecimal)
{{ .months[0].pages }}     // pages in that month, newest first
```

Un layout d'archive d'année typique :

```squiggly
<h1>{{ .year }}</h1>
<ol>
  {{ for m <- .months }}
  <li>
    <a href="{{ m.url }}">{{ m.monthName }}</a>
    <span>({{ m.count }} posts)</span>
  </li>
  {{ end }}
</ol>
```

### Données d'archive de mois

Le layout `date-month.html` voit :

```squiggly
{{ .year }}        // 2024
{{ .month }}       // 3 (BigDecimal)
{{ .monthName }}   // "March"
{{ .pages }}       // posts in March 2024, newest first
```

```squiggly
<h1>{{ .monthName }} {{ .year }}</h1>
<ol>
  {{ for p <- .pages }}
  <li>
    <time datetime="{{ p.dateISO }}">{{ p.dateShort }}</time>
    <a href="{{ p.url }}">{{ p.title }}</a>
  </li>
  {{ end }}
</ol>
```

### Combiner avec les permaliens

Les archives par date se composent proprement avec les gabarits de permalien :
l'espace d'URL reste hiérarchique et unique. Avec :

```toml
dateArchives = true

[permalinks]
posts = ":year/:month/:slug/"
```

un seul article avec `date: 2024-03-15` réside à `/2024/03/hello/`, et les pages
d'archive couvrent ce même préfixe :

| URL                      | Quoi |
|--------------------------|------|
| `/2024/03/hello/`        | L'article unique |
| `/2024/03/`              | Archive de mars 2024 |
| `/2024/`                 | Archive de l'année 2024 |

Il n'y a pas de collision parce que les chemins de sortie des permaliens et les
chemins d'archive atterrissent à des endroits différents — un article permalié
s'écrit vers `/<année>/<mois>/<slug>/index.html`, tandis que l'archive d'année
s'écrit vers `/<année>/index.html` et l'archive de mois vers
`/<année>/<mois>/index.html`. La structure de répertoires s'entrelace proprement.

## Séries / articles en plusieurs parties

Certains articles se lisent mieux en série — trois parties sur le débogage, quatre
sur la mise en place d'un projet, douze sur l'écriture de votre propre OS. Juicer
relie les articles apparentés en une série navigable via deux champs de frontmatter :

```yaml
---
title: OS Internals, Part 1 — The Boot Process
series: OS Internals
seriesOrder: 1
---
```

```yaml
---
title: OS Internals, Part 2 — The Memory Subsystem
series: OS Internals
seriesOrder: 2
---
```

Les pages partageant la même valeur `series` (sensible à la casse, correspondance
exacte) sont reliées. Chacune voit un bloc `.page.series` :

| Champ                    | Quoi |
|--------------------------|------|
| `.page.series.name`      | Le nom de série tel qu'il apparaît dans le frontmatter |
| `.page.series.pages`     | Liste de chaque page de la série, ordonnée |
| `.page.series.prev`      | L'enregistrement de la page précédente, ou `null` sur la première |
| `.page.series.next`      | L'enregistrement de la page suivante, ou `null` sur la dernière |
| `.page.series.index`     | Position de la page courante (à partir de 1) |
| `.page.series.total`     | Nombre de pages dans la série |

Une barre latérale « dans cette série » typique :

```squiggly
{{ if .page.series }}
<aside aria-label="In this series">
  <h2>{{ .page.series.name }}</h2>
  <p>Part {{ .page.series.index }} of {{ .page.series.total }}.</p>
  <ol>
    {{ for s <- .page.series.pages }}
    <li><a href="{{ s.url }}">{{ s.title }}</a></li>
    {{ end }}
  </ol>
</aside>
{{ end }}
```

Le thème `juicerblog` livre exactement ce widget sous forme du partial
`partials/series-nav.html` ; `_default/file.html` l'appelle après le corps de
l'article. Supplantez-le depuis le `partials/` de votre site si vous voulez un
texte personnalisé.

### Règles d'ordonnancement

Au sein d'une série, les pages se trient par :

[= steps =]
## `seriesOrder` croissant

Les pages avec un `seriesOrder` explicite viennent en premier dans l'ordre qu'elles
déclarent. Une page avec `seriesOrder: 2` se trie avant une avec `seriesOrder: 5`.

## Date croissante (plus ancien d'abord)

Les pages SANS `seriesOrder` se trient par `.page.date` croissant. Les séries se
lisent habituellement chronologiquement — les articles plus anciens avant les plus
récents — et le sens croissant correspond à cela.

## Nom de fichier, comme départage stable

Deux articles non ordonnés du même jour retombent sur l'ordre du nom de fichier,
pour que le résultat soit déterministe quel que soit l'ordre de parcours du système
de fichiers.
[= /steps =]

[= note =]
Une page peut être dans **au plus une** série — `series:` est une chaîne, pas une
liste. Si vous avez besoin d'une relation d'appartenance multiple, c'est exactement
à ça que sert `tags`. Les séries sont pour les articles liés narrativement qui
partagent un seul arc.
[= /note =]

## Registre d'auteurs

Un blogue à auteur unique peut s'appuyer sur `.site.author` depuis `site.toml` et
s'en contenter. Un site multi-auteurs a besoin de plus : biographie, avatar, liens
externes par auteur, et une page d'archive qui liste les articles de chaque auteur.

Déclarez les auteurs comme un tableau de tables :

```toml
[[authors]]
id     = "ed"
name   = "Edward A Maxedon"
email  = "ed@example.com"
bio    = "Writes a lot of code."
avatar = "/img/ed.jpg"

[[authors.links]]
label = "GitHub"
url   = "https://github.com/edadma"

[[authors.links]]
label = "Mastodon"
url   = "https://hachyderm.io/@edadma"

[[authors]]
id     = "alice"
name   = "Alice Smith"
bio    = "Does a lot of design."
avatar = "/img/alice.jpg"
```

Puis pointez un article vers un (ou plusieurs) des id enregistrés :

```yaml
---
title: A solo post
author: ed
---
```

```yaml
---
title: A co-authored post
authors: [ed, alice]
---
```

`author:` est le raccourci singulier ; `authors:` est la forme multi-auteurs. L'une
ou l'autre fonctionne. Les gabarits voient les deux champs, normalisés :

| Champ             | Type        | Quoi |
|-------------------|-------------|------|
| `.page.author`    | `Map?`      | Premier (ou seul) enregistrement d'auteur résolu, ou `null` |
| `.page.authors`   | `List[Map]` | Tous les enregistrements d'auteurs résolus — liste vide quand aucun |

Chaque enregistrement porte chaque clé du registre — `id`, `name`, `bio`,
`avatar`, `links[]` — pour qu'une signature par article puisse rendre directement
l'avatar, le nom et les liens externes :

```squiggly
{{ if .page.author }}
<div class="byline">
  {{ if .page.author.avatar }}
  <img src="{{ .page.author.avatar }}" alt="" />
  {{ end }}
  <div>
    <p class="name">{{ .page.author.name }}</p>
    {{ if .page.author.bio }}<p class="bio">{{ .page.author.bio }}</p>{{ end }}
  </div>
</div>
{{ end }}
```

[= note =]
Une valeur `author:` du frontmatter qui ne correspond à aucun id du registre
retombe sur un enregistrement embryonnaire `{id: "<typo>"}`. Les gabarits qui
lisent `.page.author.name` obtiennent une chaîne vide plutôt qu'un échec dur — la
construction ne casse pas sur une faute de frappe, mais l'archive d'auteur est
vide. Auditez `.site.authors` si un nom disparaît de façon inattendue.
[= /note =]

### Archives d'auteurs

Deux pages d'archive par registre d'auteurs sont émises, chacune conditionnée par
un layout optionnel :

| Layout                              | Motif d'URL              | Ce qu'il liste |
|-------------------------------------|--------------------------|----------------|
| `_default/author-list.html`         | `/authors/index.html`    | Chaque auteur avec au moins un article qui le référence |
| `_default/author-page.html`         | `/authors/<id>/index.html` | Les articles d'un auteur, plus récent d'abord |

Le layout `author-list.html` voit :

```squiggly
{{ .authors }}                  // List of records, in registry order
{{ .authors[0].name }}
{{ .authors[0].url }}           // "/authors/ed/"
{{ .authors[0].count }}         // 12
{{ .authors[0].pages }}         // their posts (already date-desc)
```

Le layout `author-page.html` voit :

```squiggly
{{ .author }}                   // single record
{{ .author.name }}
{{ .author.bio }}
{{ .author.count }}
{{ .author.pages }}             // posts, newest first
```

Les auteurs avec **zéro** article qui les référence sont omis des deux archives —
cela empêche un registre `[[authors]]` partiellement rempli d'émettre des pages
vides « voir tous les articles de Bob ».

### `.site.authors`

Comme `.site.tags` et `.site.categories`, les mêmes enregistrements d'auteurs sont
exposés à l'échelle du site pour usage dans n'importe quel gabarit :

```squiggly
{{ for a <- .site.authors }}
<a href="{{ a.url }}">{{ a.name }} ({{ a.count }})</a>
{{ end }}
```

Utile pour une liste de « contributeurs » dans un pied de page ou une page « à propos ».

## Alias / redirections

Quand vous changez l'URL d'un article — en éditant son nom de fichier, en ajoutant
un gabarit `[permalinks]` ou en renommant une section — chaque lien vers l'ancienne
URL casse. Les alias arrêtent l'hémorragie sans réécritures côté serveur.

Ajoutez la (les) ancienne(s) URL au frontmatter de la nouvelle page :

```yaml
---
title: Setting up a Scala 3 project
date: 2024-03-12
aliases:
  - /old-blog-name/setting-up-scala/
  - /2023/setting-up/
---
```

Juicer émet une petite page HTML statique à chaque URL listée. La page définit un
`<meta http-equiv="refresh">` vers l'URL canonique, plus un lien de repli visible
au cas où le rafraîchissement meta serait bloqué :

```html
<!doctype html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta http-equiv="refresh" content="0; url=/posts/setting-up-scala-3/">
  <link rel="canonical" href="https://example.com/posts/setting-up-scala-3/">
  <title>Redirecting…</title>
</head>
<body>
  <p>This page has moved. Redirecting to <a href="/posts/setting-up-scala-3/">/posts/setting-up-scala-3/</a>.</p>
</body>
</html>
```

C'est le **défaut intégré**. Il fonctionne sans aucun thème. Si vous voulez un autre
balisage — style de marque, texte personnalisé, repli JavaScript — déposez un layout
à `layouts/_default/alias.html` :

```squiggly
<!doctype html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta http-equiv="refresh" content="0; url={{ .target }}">
  <link rel="canonical" href="{{ .absTarget }}">
  <title>{{ .page.title }} — moved</title>
</head>
<body>
  <h1>This page has moved</h1>
  <p><a href="{{ .target }}">Continue to {{ .page.title }}</a></p>
</body>
</html>
```

Le layout personnalisé a accès à :

| Champ          | Quoi |
|----------------|------|
| `.target`      | URL canonique relative au site |
| `.absTarget`   | URL canonique absolue (`baseURL` + `.target`) |
| `.page.<...>`  | Chaque champ de l'enregistrement de la page canonique |
| `.site.<...>`  | Le contexte de site complet |

Les alias acceptent une simple chaîne ou une liste :

```markdown
aliases: /old-url/
```

```markdown
aliases:
  - /old-url/
  - /even-older-url/
```

[= note =]
Les alias ne sont pas la même chose que la réécriture d'URL. Chaque alias est un
vrai fichier HTML statique dans l'arborescence de sortie — `<dst>/old-url/index.html`,
`<dst>/even-older-url/index.html`, etc. C'est là tout l'intérêt : il fonctionne sur
n'importe quel hôte statique (GitHub Pages, Cloudflare Pages, S3, Netlify…) sans
configuration de règle de périphérie. Le coût est d'un minuscule fichier HTML par alias.
[= /note =]

## Cartes OpenGraph / Twitter

Quand un article est partagé sur les réseaux sociaux, la plateforme rend un aperçu
en « carte » construit à partir d'un petit bloc de balises `<meta>` dans le
`<head>` de la page. Juicer livre un builtin de gabarit `{{ ogTags .page }}` qui
émet le bloc canonique en une ligne :

```squiggly
<head>
  ...
  {{ ogTags .page }}
</head>
```

Cet appel se déploie en :

```html
<meta property="og:type" content="article" />
<meta property="og:title" content="The post title" />
<meta property="og:url" content="https://example.com/posts/the-post/" />
<meta property="og:description" content="The post summary." />
<meta property="og:image" content="https://example.com/img/hero.jpg" />
<meta property="og:site_name" content="My blog" />
<meta name="twitter:card" content="summary_large_image" />
<meta name="twitter:title" content="The post title" />
<meta name="twitter:description" content="The post summary." />
<meta name="twitter:image" content="https://example.com/img/hero.jpg" />
```

Les balises dont le champ source est vide sont omises (vous n'obtenez pas un
`<meta property="og:image" content="" />` vide qui encombre la sortie).

### Règles de résolution

Pour chaque valeur dont le builtin a besoin, il essaie les champs par ordre de priorité :

| Balise                         | Chaîne de résolution |
|--------------------------------|----------------------|
| `og:title`, `twitter:title`    | `.page.ogTitle` → `.page.title` |
| `og:description`, `twitter:description` | `.page.ogDescription` → `.page.description` → `.page.summary` |
| `og:image`, `twitter:image`    | `.page.ogImage` → `.page.image` → `.site.ogImage` → `.site.image` |
| `og:url`                       | `.page.permalink` (toujours présent — l'URL absolue que juicer a calculée) |
| `og:site_name`                 | `.site.title` |
| `twitter:card`                 | `summary_large_image` si une image a été résolue, sinon `summary` |

Les URL d'image sont promues en absolu via le `baseURL` configuré — un
`/img/hero.jpg` relatif au site devient `https://example.com/img/hero.jpg`. Les
robots d'indexation rejettent typiquement les URL relatives dans `og:image`, donc
cela compte.

### Surcharges par page

Pour définir un titre ou un résumé de carte différent du titre et du résumé propres
à la page, ajoutez `ogTitle` / `ogDescription` / `ogImage` au frontmatter de l'article :

```yaml
---
title: A long, descriptive, SEO-targeted post title
summary: A long summary that's good for the page's own list-page meta…
ogTitle: A short, punchy title for social cards
ogDescription: A different summary that fits in a card preview.
ogImage: /img/posts/short-title-card.png
---
```

[= note =]
Le thème `juicerblog` appelle `{{ ogTags .page }}` depuis son `partials/head.html`,
donc chaque page obtient le bloc meta automatiquement sans que vous ayez à faire
quoi que ce soit dans l'article lui-même. Les sites qui veulent contrôler le lieu de
l'appel (ou le sauter pour certaines pages) peuvent supplanter le partial.
[= /note =]

## Le thème `juicerblog`

Juicer livre un thème de blogue par défaut sous `themes/juicerblog/` qui exerce
chaque fonctionnalité de cette page — plus la coloration syntaxique côté serveur,
les signatures d'auteur, les badges de progression de série, une mince ligne de
progression de lecture, des boutons de copie de bloc de code, et une paire de
layouts accueil / archive. La référence complète du thème réside dans sa propre
section : voir [juicerblog](/fr/themes/juicerblog/) pour l'aperçu et
[juicerblog · Configuration](/fr/themes/juicerblog/configuration/) pour tous les
boutons de configuration, conventions de frontmatter, mise en place de la coloration
syntaxique et motifs de surcharge.

Le moyen le plus rapide de voir chaque fonctionnalité de blogue fonctionner ensemble
est la démo fournie :

```bash
sbt 'juicerJVM/run serve -s docs/demos/juicerblog -L'
```

Cela lance un aperçu en direct de `docs/demos/juicerblog/` — 9 articles datés
couvrant juil.–déc. 2024, trois auteurs avec une co-signature multi-auteurs, une
série de 3 articles, dateArchives + permaliens + alias, coloration syntaxique, tout
le tralala. Touchez n'importe quel fichier markdown dans la source et la page se
recharge en moins d'une seconde.

## Où réside chaque fonctionnalité

Quand une fonctionnalité casse, voici par où commencer à chercher :

| Fonctionnalité | Moteur                                                                 | Thème                                  |
|----------------|------------------------------------------------------------------------|----------------------------------------|
| Mots-clés / catégories | `App.scala` `collectTaxonomy`, `renderTaxonomyArchives`        | `tag-list.html`, `tag-page.html`       |
| Pagination     | `paginate.scala`, découpage de boucle de rendu dans `App.scala`        | `partials/pagination.html`             |
| Dates          | `App.scala` `parseDateString`, `formatDateLong`                        | `partials/post-meta.html`              |
| Temps de lecture | `App.scala` `wordsOf`, `readingTimeOf`                               | Partout où `.page.readingTime` est utilisé |
| Assistant slug | `package.scala` `slugify`, `asciiFold`                                 | (utilisé en interne pour les URL de mots-clés) |
