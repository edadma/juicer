---
title: Configuration
summary: Chaque clé de configuration que juicer comprend, sa valeur par défaut et ce qu'elle contrôle.
weight: 20
---

La configuration du site réside dans `site.toml` à la racine source. Juicer superpose votre fichier par-dessus l'une de trois bases (`simple`, `standard`, `norme`) sélectionnée par l'option CLI `-c <name>`.

## Bases

| Nom      | Quoi |
|----------|------|
| `simple` | Disposition à plat — contenu, layouts, partials, statique tous à la racine source |
| `standard` | Disposition imbriquée façon Hugo (le défaut) |
| `norme` | Française (conforme à la Charte de la langue française) — comme `standard` mais avec des noms de répertoires français |

[= tip =]
La base `norme` est ici parce que la Charte de la langue française du Québec exige que les logiciels destinés au public se présentent principalement en français. Même code, noms de fichiers français. Si votre contenu est bilingue, utilisez cette base aux côtés de la fonctionnalité i18n.
[= /tip =]

## Clés

### Identité

| Clé      | Défaut                          | Quoi |
|----------|---------------------------------|------|
| `title`  | `"Untitled"`                    | Titre du site ; disponible comme `.site.title` |
| `author` | `"Unnamed"`                     | Auteur du site ; disponible comme `.site.author` |
| `baseURL`| `"http://localhost:8080"`       | URL de base absolue — utilisée pour les permaliens, le sitemap, OpenGraph |

### Thème

| Clé        | Défaut      | Quoi |
|------------|-------------|------|
| `theme`    | (aucun)     | Nom de thème (chaîne) ou chaîne de thèmes (tableau de chaînes). Résolu sous `themeDir` |
| `themeDir` | `"themes"`  | Répertoire contenant les sous-dossiers de thèmes |

### Disposition des répertoires

| Clé             | Défaut `standard`  | Quoi |
|-----------------|--------------------|------|
| `contentDir`    | `"content"`        | Racine source du markdown |
| `htmlDir`       | `"html"`           | Préfixe réservé au système de fichiers pour les sections imbriquées ; retiré des URL |
| `publicDir`     | `"public"`         | Répertoire de sortie par défaut |
| `staticDir`     | `"static"`         | Ressources copiées telles quelles |
| `layoutDir`     | `"layouts"`        | Racine des gabarits |
| `partialDir`    | `"partials"`       | Racine des partials |
| `shortcodeDir`  | `"shortcodes"`     | Racine des shortcodes |
| `dataDir`       | `"data"`           | Racine des données structurées — les fichiers `*.toml` / `*.yaml` / `*.yml` ici sont exposés aux gabarits comme `.site.data` (voir [Données de gabarit → `.site.data`](/fr/reference/template-data/#site-data)). Les thèmes peuvent aussi livrer un répertoire `data/` ; les clés du site l'emportent à la granularité du fichier feuille. |
| `excludeDirs`   | non défini         | Répertoires supplémentaires à sauter pendant le parcours du site. Chaîne ou tableau de chaînes ; chaque entrée est un chemin relatif à la racine source (`"node_modules"`, `"assets/raw"`). La correspondance est par répertoire exact seulement — pas un motif glob. |

Le parcours saute déjà les sous-dossiers du `themeDir` actif, le parent du `themeDir` (pour que les thèmes inactifs rangés à côté ne se rendent pas), le `publicDir` configuré et le `dst` de sortie de la construction. Utilisez `excludeDirs` pour tout le reste sous la racine source qui ne fait pas partie du site — outillage rangé, dossiers de brouillon, ressources générées que vous produisez hors bande, brouillons gardés hors de `content/` :

```toml
excludeDirs = ["node_modules", "scratch", "assets/raw"]
```

### Noms de layout

| Clé              | Défaut        | Quoi |
|------------------|---------------|------|
| `defaultLayout`  | `"_default"`  | Sous-dossier de layout de repli sous `layoutDir` |
| `baseofLayout`   | `"baseof"`    | Nom de fichier du layout de coquille externe (sans extension) |
| `fileLayout`     | `"file"`      | Nom de fichier du layout de page unique |
| `folderLayout`   | `"folder"`    | Nom de fichier du layout d'index de section |
| `folderContent`  | `"_index"`    | Nom de fichier (sans extension) reconnu comme l'index de section |

### Comportement

| Clé            | Défaut  | Quoi |
|----------------|---------|------|
| `stripPrefix`  | `true`  | Retire les préfixes numériques de tête des noms de fichiers dans les URL (`01-foo.md` → `foo`) |
| `headingShift` | `2`     | Ajoute ceci à chaque niveau de titre markdown. Le défaut `2` existe parce que les layouts émettent typiquement un `<h1>{{ .page.title }}</h1>` externe et que la plupart des CSS de thème s'attendent à ce que le markdown du corps commence à `<h2>`. Mettez `0` quand un thème rend le titre de la page depuis le corps markdown lui-même. |
| `feeds`        | `true`  | Émet les fichiers de flux Atom et RSS aux côtés du HTML rendu. Mettez `false` pour les sites qui ne veulent pas de flux (pages d'atterrissage uniques, wikis internes). |

### Navigation — `nav`

Tableau de premier niveau qui pilote `.site.toc` (la navigation latérale / barre supérieure).
Quand `nav` est **absent**, juicer construit automatiquement la navigation en parcourant
l'arborescence de contenu. Quand `nav` est défini, juicer parcourt les entrées du tableau :

- Une **chaîne** se terminant par une extension markdown (`.md`, `.markdown`,
  `.mkd`, `.mkdn`, `.mdown`) est une référence à un fichier de contenu
  (chemin relatif à `contentDir`). L'enregistrement de page à ce chemin est
  tiré dans la navigation à la position du tableau.
- Toute autre **chaîne** est un **libellé** de section — un titre de groupe
  non cliquable.
- Une **table à clé unique** associe un libellé explicite à un chemin de contenu —
  utile quand le titre du frontmatter du fichier n'est pas le bon libellé pour la navigation.

```toml
nav = [
  "Getting Started",                       # label
  "getting-started/_index.md",             # link, title from frontmatter
  "getting-started/installation.md",
  { "Get help" = "getting-started/troubleshooting.md" },   # link, explicit label
  "Reference",                             # label
  "reference/cli.md",
  "reference/config.md",
]
```

Le même tableau est aussi parcouru par les partials « précédent / suivant » des thèmes
quand `nav` est défini, donc l'ordre que vous écrivez ici est l'ordre dans lequel les
lecteurs feuillettent.

### i18n — `languages`, `defaultLanguage`

Deux clés optionnelles qui activent le mode multilingue. Le moteur ne duplique pas
le contenu pour vous (un fichier markdown par langue reste la convention) — il fait
remonter la langue active aux gabarits pour que les thèmes puissent rendre un
habillage conscient de la langue et tirer des chaînes d'une table de traduction.

| Clé               | Défaut                   | Quoi |
|-------------------|--------------------------|------|
| `languages`       | non défini (monolingue)  | Tableau des codes de langue que le site prend en charge — `["en", "fr", "ja"]`. Quand non défini, juicer est en mode monolingue et `.page.lang` est la chaîne vide. |
| `defaultLanguage` | première entrée / `""`   | Langue utilisée quand le préfixe d'URL d'une page ne correspond à aucune des `languages`. Retombe sur la première entrée de `languages` quand non défini. |

Les chaînes de traduction résident dans `<src>/i18n/<lang>.toml`, un fichier par
langue déclarée, sous forme de tables plates `clé = "valeur"` :

```toml
# i18n/en.toml
home          = "Home"
browse_docs   = "Browse the docs"
read_more     = "Read more"
```

```toml
# i18n/fr.toml
home          = "Accueil"
browse_docs   = "Parcourir la documentation"
read_more     = "Lire la suite"
```

Les gabarits recherchent les chaînes avec l'assistant `i18n`, en passant la langue
de la page :

```squiggly
<a href="/">{{ i18n .page.lang 'home' }}</a>
<a href="/docs/">{{ i18n .page.lang 'browse_docs' }}</a>
```

Les recherches retombent sur `defaultLanguage` quand une clé manque dans la langue
demandée ; si la clé manque dans les deux, la clé littérale est renvoyée (pour
qu'une traduction manquante soit visible pendant la rédaction sans faire planter la
construction).

### Fonctionnalités de blogue

Ces clés activent les fonctionnalités de blogue documentées sous
[Concepts → Fonctionnalités de blogue](/fr/concepts/blogging/). Les quatre sont
optionnelles ; un site de documentation qui ne les définit pas se rend inchangé.

| Clé          | Défaut    | Quoi |
|--------------|-----------|------|
| `paginate`     | (aucun)   | Taille de tranche par défaut pour les pages d'index de section. Quand non défini, les sections se rendent en une seule page peu importe combien d'enfants elles ont. |
| `sortBy`       | `weight`  | Ordonne les pages de section : `"date"` (plus récent d'abord), `"title"` (alphabétique), ou `"weight"` (le défaut de juicer — `weight` croissant) |
| `dateArchives` | `false`   | Émet les pages d'archive `/<year>/` et `/<year>/<month>/` à partir des dates analysées des articles. Requiert les layouts `date-year.html` / `date-month.html` correspondants ; les layouts manquants sont des sauts silencieux. Seules les pages avec un frontmatter `date:` **explicite** sont incluses — les dates issues du repli sur la mtime ne polluent pas l'archive. |
| `dateFormat`   | (aucun)   | Réservé pour de futures surcharges du format de date par site. Pas encore branché ; les gabarits utilisent pour l'instant les assistants intégrés `dateLong` / `dateShort` / `dateISO`. |

[= note =]
`paginate` et `sortBy` peuvent tous deux être supplantés par section en définissant
la même clé dans le frontmatter du `_index.md` de la section. Ainsi un site qui veut
10 articles par page sur `/posts/` mais 30 notes courtes par page sur `/notes/` met
`paginate = 30` dans `content/notes/_index.md` et laisse la valeur du site à 10.
[= /note =]

### Fonctionnalités de calendrier / événements

Juicer fait remonter une liste d'événements organisée (`.site.events`) et une grille
de calendrier sur 12 mois (`.site.calendar`) pour tout site ayant une section de
pages d'événements. Voir
[Données de gabarit → `.site.events`](/fr/reference/template-data/#site-events) et
[`.site.calendar`](/fr/reference/template-data/#site-calendar).

| Clé              | Défaut      | Quoi |
|------------------|-------------|------|
| `eventsSection`  | `"events"`  | Nom de la section de contenu que juicer traite comme des événements. Les pages de cette section avec un frontmatter `date:` explicite peuplent `.site.events` et `.site.calendar`. Le filtre des articles futurs à l'échelle du site est aussi exempté pour les pages de cette section afin que les pages de détail d'événement datées dans le futur se rendent quand même sur le disque. |
| `calendarMonths` | `12`        | Combien de mois `.site.calendar` pré-calcule, en commençant au mois courant. Des valeurs plus hautes coûtent en temps de construction et taille HTML ; des valeurs plus basses signifient que le calendrier s'épuise plus tôt. |

Les événements récurrents relèvent du thème et du gabarit — le moteur reconnaît un
frontmatter `recurring: weekly` plus un `recurringDay:` optionnel sur les pages
d'événement et déploie l'événement sur chaque jour de semaine correspondant dans
`.site.calendar`. Sans `recurringDay:`, la récurrence retombe sur le jour de la
semaine de la date de début.

## `[permalinks]` — gabarits d'URL par section

Une sous-table TOML qui supplante le motif d'URL d'une section. Chaque clé est un
nom de section (le premier segment de chemin après le retrait de `htmlDir`) ; chaque
valeur est une chaîne de gabarit avec des jetons de substitution. Les jetons sont
résolus contre le frontmatter et la date analysée de la page.

```toml
[permalinks]
posts = ":year/:month/:slug/"
notes = ":slug/"
articles = ":year/:section/:title/"
```

Jetons reconnus :

| Jeton       | Se résout en |
|-------------|--------------|
| `:slug`     | Le nom de fichier nettoyé (`01-foo.md` → `foo` quand `stripPrefix = true`) |
| `:title`    | `slugify(.page.title)` — titre du frontmatter, mis en minuscules et replié en ASCII |
| `:year`     | Année à 4 chiffres depuis `.page.date` |
| `:month`    | Mois à 2 chiffres depuis `.page.date` |
| `:day`      | Jour à 2 chiffres depuis `.page.date` |
| `:section`  | Le nom de section lui-même |

Les sections **sans** entrée `[permalinks]` gardent l'URL par défaut dérivée du
chemin physique de juicer (l'arborescence de fichiers détermine l'URL un pour un).
Les pages d'index de section (`_index.md`) ne sont jamais routées par les gabarits
de permalien — elles résident toujours à la racine de la section.

[= note =]
Les gabarits de permalien changent à la fois l'URL et l'emplacement d'écriture sur
le disque de chaque page affectée. Juicer ne garde pas les deux copies — seul le
chemin permalié existe dans l'arborescence de sortie. Ainsi un `posts/foo.md` avec
`posts = ":year/:slug/"` s'écrit uniquement vers `<dst>/2024/foo/index.html`, jamais
vers `<dst>/posts/foo/index.html`.
[= /note =]

Voir [Concepts → Fonctionnalités de blogue → Permaliens](/fr/concepts/blogging/#permaliens)
pour la version narrative.

## `[comments]` — emplacement de configuration du fournisseur de commentaires

juicer ne livre jamais de backend de commentaires. Les sites qui veulent des
commentaires déclarent le fournisseur et les clés propres au fournisseur sous
`[comments]` dans `site.toml` ; les partials de thème relisent la configuration
comme `.site.comments.*` et émettent le bon HTML d'intégration.

```toml
[comments]
provider = "giscus"
repo     = "edadma/juicer"
repoId   = "R_kgDOXXXXXX"
category = "Announcements"
categoryId = "DIC_kwDOXXXXXX"
mapping  = "pathname"
reactions = true
theme    = "preferred_color_scheme"
```

La seule convention qui intéresse le moteur est le nom de la table lui-même — tout
ce qui est à l'intérieur est opaque pour juicer. Clés conventionnelles pour les
fournisseurs courants :

| Fournisseur  | Clés conventionnelles |
|--------------|-----------------------|
| `giscus`     | `repo`, `repoId`, `category`, `categoryId`, `mapping`, `reactions`, `theme` |
| `utterances` | `repo`, `issueTerm`, `label`, `theme` |
| `disqus`     | `shortname` |

Les thèmes conditionnent typiquement l'intégration sur `{{ if .site.comments }}` et
sur une surcharge de frontmatter par page `comments: false` (pour que des articles
individuels puissent renoncer aux commentaires sans désactiver la configuration du
site). Voir [Données de gabarit → `.site.comments`](/fr/reference/template-data/#site-comments)
pour le contrat côté gabarit.

[= note =]
juicer est uniquement à sortie statique — il ne relaie pas les commentaires, ne
stocke pas l'état de modération, n'appelle pas l'API du fournisseur. Le bloc
ci-dessus est un emplacement de configuration seulement. Si un fournisseur a besoin
d'état côté serveur, cela relève de l'infrastructure propre au fournisseur, pas de juicer.
[= /note =]

## `[images]` — génération de variantes d'image

Génération optionnelle au moment de la construction de variantes d'image
redimensionnées + reformatées (`webp`, `avif`, etc.) pour un balisage `<picture>` /
`<img srcset>` adaptatif. La fonctionnalité est **désactivée par défaut** — les sites
qui ne définissent pas cette table se construisent à l'octet près comme avant
`[images]`.

```toml
[images]
enabled  = true
widths   = [320, 640, 960, 1280]
formats  = ["webp", "original"]   # most-modern first; "original" passes through
quality  = 80
cacheDir = ".image-cache"          # under dst; variants land here
```

| Clé        | Défaut                             | Quoi |
|------------|------------------------------------|------|
| `enabled`  | `false`                            | Interrupteur principal. `false` → les gabarits qui appellent `imageVariants` obtiennent un ensemble de transmission seulement, sans lignes `<source>`. |
| `widths`   | `[320, 640, 960, 1280]`            | Largeurs cibles en pixels. Les largeurs ≥ à la propre largeur de la source sont abandonnées (pas d'agrandissement) ; la largeur exacte de la source est toujours incluse. |
| `formats`  | `["webp", "original"]`             | Formats de sortie par ordre de priorité. Connus : `webp`, `avif`, `jpeg`, `png`, `original`. Les noms inconnus sont abandonnés. |
| `quality`  | `80`                               | Qualité d'encodage (1–100). Utilisée pour les formats avec perte ; `png` l'ignore. |
| `cacheDir` | `".image-cache"`                   | Répertoire sous `dst` où résident les variantes générées. Les noms de fichiers hachés sur le contenu font que les ré-exécutions sur des sources inchangées sautent l'appel à l'encodeur. |

**Encodeur.** Juicer appelle ImageMagick (`magick`) — installez avec
`brew install imagemagick` / `apt install imagemagick` /
`dnf install ImageMagick`. Quand `magick` n'est pas dans le PATH, la construction
réussit quand même, mais `imageVariants` renvoie un ensemble de transmission
seulement (une seule ligne d'avis s'affiche sur stderr). Les cibles Scala Native et
Scala.js livrent un backend de remplacement ; la génération complète de variantes
est aujourd'hui réservée à la JVM.

Côté gabarit : voir
[Syntaxe de gabarit → `imageVariants` et `srcset`](/fr/reference/template-syntax/#imagevariants-et-srcset)
pour les assistants que les thèmes appellent.

## `[assets]` — pipeline Sass / esbuild

Compilation optionnelle au moment de la construction des ressources de thème via
deux CLI largement disponibles : `sass` pour SCSS → CSS et `esbuild` pour le
regroupement et la minification du JS. Plus une option d'empreinte optionnelle qui
ajoute un hachage de contenu aux noms de fichiers de sortie pour que les
déploiements puissent livrer des URL anti-cache sans renoncer aux en-têtes
`Cache-Control` à longue durée de vie. **Désactivé par défaut** — les sites qui ne
définissent pas cette table se construisent à l'octet près comme avant `[assets]`.

```toml
[assets]
enabled     = true
fingerprint = false

[[assets.sass]]
input  = "src/site.scss"
output = "/css/site.css"
minify = true

[[assets.esbuild]]
input  = "src/main.js"
output = "/js/main.js"
minify = true

[[assets.copy]]
input  = "src/robots.txt"
output = "/robots.txt"
```

| Clé de premier niveau | Défaut  | Quoi |
|-----------------------|---------|------|
| `enabled`     | `false` | Interrupteur principal. `false` → aucun pipeline ne s'exécute, le builtin `asset` renvoie son entrée inchangée. |
| `fingerprint` | `false` | Quand `true`, juicer insère le hachage de contenu des octets de sortie avant l'extension : `/css/site.css` → `/css/site.<16-hex>.css`. Le builtin `asset` se résout automatiquement vers l'URL avec empreinte. |

Tables d'entrée (n'importe quel nombre de chacune ; utilisez la forme abrégée de
table en ligne ou la forme tableau `[[assets.kind]]`) :

| Type d'entrée | Clés requises | Clés optionnelles | Quoi |
|---------------|---------------|-------------------|------|
| `[[assets.sass]]`    | `input`, `output` | `minify`, `logical` | SCSS / Sass → CSS via `sass --no-source-map [--style=expanded|compressed]`. |
| `[[assets.esbuild]]` | `input`, `output` | `minify`, `logical` | Regroupement JS via `esbuild <in> --bundle [--minify] --outfile=<out>`. |
| `[[assets.copy]]`    | `input`, `output` | `logical` | Copie à l'octet près (aucun outil impliqué). Utile pour donner une empreinte à un fichier qu'aucun compilateur n'a besoin de toucher. |

`input` est résolu sous la racine source ; `output` est un chemin d'URL enraciné au
site (barre oblique de tête optionnelle). `logical` retombe par défaut sur le nom de
base de `output` — `/css/site.css` devient la clé de manifeste `site.css`, que les
gabarits recherchent comme `{{ asset 'site.css' }}`.

**Installation des outils.** Juicer appelle `sass` (le paquet npm dart-sass ; la gem
Ruby `sass` fonctionne aussi pour le petit sous-ensemble d'options que juicer
utilise) et `esbuild`. Installez avec `brew install sass esbuild` /
`npm install -g sass esbuild` / l'équivalent de votre plateforme. Quand un outil
n'est pas dans le PATH, la construction réussit quand même : cette entrée se
dégrade en une copie telle quelle de sa source, l'URL se résout toujours pour que
les gabarits ne cassent pas, et une seule ligne d'avis va sur stderr. Les cibles
Native et JS livrent des backends de remplacement ; l'exécution complète du pipeline
est aujourd'hui réservée à la JVM.

Côté gabarit : voir [Données de gabarit → builtin `asset`](/fr/reference/template-data/#builtin-asset).

## `[[authors]]` — registre d'auteurs

Un tableau de tables décrivant les personnes qui écrivent des articles sur le site.
Chaque entrée a besoin d'au moins un `id` ; tout le reste est optionnel et s'écoule
directement dans les enregistrements `.page.author` / `.page.authors`.

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
```

| Champ    | Requis | Quoi |
|----------|--------|------|
| `id`     | oui    | Identifiant stable sûr pour les URL ; l'archive réside à `/authors/<id>/` |
| `name`   | non    | Nom d'affichage |
| `email`  | non    | Courriel de l'auteur ; utile dans les gabarits de flux |
| `bio`    | non    | Courte biographie utilisée dans les signatures et en-têtes d'archive d'auteur |
| `avatar` | non    | URL de l'image d'avatar — relative au site ou absolue |
| `links[]`| non    | Chaque entrée a `label` et `url` ; se rend comme une liste de liens externes |

Les pages référencent un auteur via `author: <id>` ou `authors: [<id>, ...]` dans
leur frontmatter. Voir
[Concepts → Fonctionnalités de blogue → Registre d'auteurs](/fr/concepts/blogging/#registre-d-auteurs)
pour la narration complète.

## Clés personnalisées

Toute clé que vous définissez dans `site.toml` est disponible comme `.site.<key>` dans les gabarits. Utilisez ceci pour les réglages à l'échelle du site que le thème expose — p. ex. `editURL`, `discussionURL`, `social.twitter`. Les thèmes documentent typiquement les clés qu'ils reconnaissent dans leur README.
