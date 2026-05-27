---
title: Données de gabarit
summary: Le contexte de données complet exposé aux gabarits — site, page, section, content, toc.
weight: 30
---

Chaque gabarit se rend contre une seule carte. Les clés de premier niveau sont documentées ci-dessous ; les champs imbriqués suivent.

## `.site`

La configuration de site fusionnée (`site.toml` superposé sur la base) plus quelques extras calculés :

| Clé              | Type                | Quoi |
|------------------|---------------------|------|
| `.site.<config>` | varie               | Chaque clé de `site.toml` |
| `.site.toc`      | `List[TOCItem]`     | Auto-navigation à l'échelle du site (seulement si aucun `nav` n'est défini) |
| `.site.start`    | `String?`           | URL de la page d'accueil conventionnelle |
| `.site.pages`    | `List[Map]`         | L'enregistrement enrichi de chaque page |
| `.site.pagesByPath` | `Map[String, Map]`  | Mêmes enregistrements, indexés par `relPermalink` |
| `.site.posts`    | `List[Map]`         | Pages datées, non-section, non-`static`, plus récent d'abord (voir ci-dessous) |
| `.site.pagesByYear` | `List[Map]`      | Mêmes articles groupés par année, année décroissante (voir ci-dessous) |
| `.site.root`     | `Map?`              | L'enregistrement `_index` de la section racine (ou `null`) |
| `.site.tags`     | `List[Term]`        | Chaque mot-clé distinct utilisé dans le site (voir ci-dessous) |
| `.site.categories` | `List[Term]`      | Même forme que `.site.tags`, pour l'axe des catégories |
| `.site.authors`  | `List[Map]`         | Chaque auteur avec au moins une page qui le référence — enregistrement du registre + `id`, `url`, `count`, `pages` |
| `.site.authorRegistry` | `List[Map]`   | La table `[[authors]]` brute de `site.toml` dans l'ordre de déclaration — inclut les auteurs avec zéro page qui les référence, utile pour les layouts d'annuaire du personnel (voir ci-dessous) |
| `.site.authorsPath` | `String`         | Préfixe d'URL sous lequel le listing d'équipe et les archives par auteur sont émis. Configuré par `authorsPath` dans `site.toml` ; par défaut `/authors/`. Voir ci-dessous |
| `.site.now`      | `Map`               | Horodatage « maintenant » du moment de la construction en quatre formes (voir ci-dessous) |
| `.site.events`   | `List[Map]`         | Pages dans l'`eventsSection` configurée avec un frontmatter `date:` explicite, triées croissant. Inclut les événements datés dans le futur (voir ci-dessous) |
| `.site.calendar` | `List[Map]`         | N mois pré-calculés de grille de calendrier en commençant au mois courant, avec les événements récurrents hebdomadaires déployés sur chaque jour de semaine correspondant (voir ci-dessous) |
| `.site.photos`   | `List[Map]`         | Entrées de frontmatter `photos:` agrégées depuis chaque page, triées par date de la page parente décroissante (voir ci-dessous) |
| `.site.data`     | `Map[String, Any]`  | Espace de noms imbriqué de données structurées chargées depuis les fichiers `data/` (voir ci-dessous) |
| `.site.comments` | `Map[String, Any]?` | Le bloc de configuration `[comments]` de `site.toml` (fournisseur + clés propres au fournisseur), ou absent si aucune table `[comments]` n'est définie (voir ci-dessous) |

### Forme de `Term`

Chaque entrée de `.site.tags` et `.site.categories` (et sur les gabarits d'archive
de mots-clés, les éléments de `.terms`) a ces champs :

| Clé       | Type        | Quoi |
|-----------|-------------|------|
| `name`    | `String`    | Le nom de mot-clé original tel qu'il apparaissait dans le frontmatter |
| `slug`    | `String`    | Forme sûre pour les URL (minuscules, repliée en ASCII, suites non alphanumériques réduites à `-`) |
| `url`     | `String`    | URL d'archive relative au site — `/tags/<slug>/` ou `/categories/<slug>/` |
| `count`   | `Int`       | Combien de pages référencent ce terme |
| `pages`   | `List[Map]` | Les pages elles-mêmes, chacune dans la même forme qu'une entrée de `.site.pages` |

### `.site.posts` et `.site.pagesByYear`

Vues organisées du flux d'articles — les mêmes données, découpées de deux façons.

`.site.posts` est la liste plate, plus récent d'abord. Une page est incluse si elle
a une `date:` analysée explicite dans le frontmatter, n'est pas un index de section
(`_index.md`), et n'est pas marquée `static: true`. Les dates issues du repli sur la
mtime du système de fichiers ne **comptent pas** — seules les dates rédigées y
entrent. Le résultat est exactement l'ensemble de pages qu'un blogue ou un site
d'actualités appellerait « les articles ».

`.site.pagesByYear` groupe la même liste par année calendaire, avec les années
décroissantes et les pages au sein de chaque année toujours plus récent d'abord :

| Clé     | Type        | Quoi |
|---------|-------------|------|
| `year`  | `Int`       | L'année à 4 chiffres |
| `count` | `Int`       | Articles dans cette année |
| `pages` | `List[Map]` | Les articles eux-mêmes, chacun en forme d'entrée de `.site.pages` |

Utile pour les pages d'archive chronologiques (`<h2>2024</h2>` puis une liste).

### `.site.authorRegistry`

La table `[[authors]]` brute de `site.toml`, préservée dans l'ordre de déclaration.
Chaque entrée est la table TOML telle quelle — `id`, `name`, `role`, `bio`,
`avatar`, `email`, plus tout champ personnalisé que vous avez ajouté.

Distincte de `.site.authors`, qui est filtrée aux auteurs ayant au moins une page
qui les référence et est augmentée de `count`, `url` et `pages`. Utilisez
`.site.authorRegistry` pour les layouts d'annuaire du personnel qui devraient
montrer chaque membre de l'équipe peu importe combien de sermons ou d'articles ils
ont rédigés ; utilisez `.site.authors` pour les archives « parcourir par auteur ».

#### Quand `/authors/` et `/authors/<id>/` sont rendues

Les deux layouts ont des conditions différentes pour que les thèmes puissent livrer
une « page d'équipe » montrant tout le monde, même sur un site neuf sans articles :

- `layouts/_default/author-list.html` → `/authors/index.html` est rendue dès qu'un
  registre existe tout court (`[[authors]]` non vide dans `site.toml`). Le layout
  itère typiquement `.site.authorRegistry`.
- `layouts/_default/author-page.html` → `/authors/<id>/index.html` n'est rendue que
  pour les auteurs ayant au moins une page qui les référence. Une page d'archive par
  auteur vide serait trompeuse.

L'un ou l'autre layout est optionnel — l'absence du fichier signifie que la sortie
correspondante est silencieusement sautée.

#### Renommer le préfixe d'URL — `authorsPath`

Par défaut, le listing d'équipe et les archives par auteur résident tous deux sous
`/authors/`. Les sites utilisant un thème non orienté blogue veulent souvent un
libellé différent — un café veut `/team/`, un site de documentation veut
`/people/`. Définissez `authorsPath` dans `site.toml` :

```toml
authorsPath = "/team/"
```

Le moteur pivote trois choses de concert : le répertoire de sortie sur le disque, le
champ `url` de chaque terme `.site.authors`, et la chaîne `.site.authorsPath` faite
remonter aux gabarits. Les thèmes devraient lire `{{ .site.authorsPath }}` plutôt
que de coder en dur `/authors/` pour qu'une surcharge au niveau du site se propage
sans édition de layout.

Normalisation indulgente : `team`, `/team`, `team/` et `/team/` deviennent tous
`/team/`. Les segments de traversée de chemin (`.`, `..`) sont rejetés avec un
avertissement et le défaut est utilisé.

### `.site.now`

Horodatage du moment de la construction capturé une fois par construction, en quatre
formes pour que la bonne soit toujours sous la main :

| Clé    | Type     | Quoi |
|--------|----------|------|
| `iso`  | `String` | Décalage ISO complet — `2026-05-09T14:00:00Z` |
| `date` | `String` | `YYYY-MM-DD`, comparable lexicalement avec `.page.dateShort` pour les filtres passé/futur |
| `long` | `String` | `May 9, 2026`, même formateur que `.page.dateLong` |
| `year` | `Int`    | Utile pour le copyright de pied de page |

Le modèle de site statique signifie que « maintenant » est légitimement figé au
moment de la construction. Le rechargement à chaud relance la construction à chaque
changement, donc la valeur reste fraîche pendant l'aperçu local.

### `.site.events`

Pages dans la section d'événements configurée (`eventsSection` dans `site.toml`, par
défaut `"events"`) avec un frontmatter `date:` explicite, triées croissant. Chaque
entrée est en forme d'enregistrement `.site.pages` — `title`, `url`, `date`,
`dateShort`, `summary`, plus tout frontmatter que la page d'événement déclare
(`eventTime`, `eventLocation`, `recurring`, etc.).

Inclut les événements datés dans le futur : les événements sont annoncés *avant*
qu'ils n'arrivent, et le filtre des articles futurs à l'échelle du site est exempté
pour les pages de la section d'événements pour que les entrées `.site.events` et les
pages de détail d'événement correspondantes survivent à la construction. Les
*articles* datés dans le futur (tout ce qui est hors de la section d'événements)
continuent d'être sautés comme futurs — voir l'option CLI `--future` pour
supplanter par construction.

Les événements récurrents apparaissent une fois chacun (l'enregistrement de page
canonique) ; utilisez `.site.calendar` pour le déploiement occurrence par occurrence.

### `.site.calendar`

N mois pré-calculés (par défaut 12, supplantez via `calendarMonths` dans
`site.toml`) en commençant au mois courant. Chaque mois est :

| Clé         | Type            | Quoi |
|-------------|-----------------|------|
| `year`      | `Int`           | Année à 4 chiffres |
| `month`     | `Int`           | 1–12 |
| `monthName` | `String`        | `"May"` |
| `label`     | `String`        | `"May 2026"` |
| `weeks`     | `List[List[Cell]]` | Toujours six rangées de 7 jours, dimanche d'abord |

Chaque cellule :

| Clé       | Type        | Quoi |
|-----------|-------------|------|
| `day`     | `Int?`      | 1–31, ou `null` pour les cellules de remplissage hors-mois |
| `isToday` | `Boolean`   | `true` pour la cellule dont l'année/mois/jour correspond à `.site.now` |
| `events`  | `List[Map]` | Enregistrements de page de base pour les événements de ce jour (liste vide quand aucun) |

Les événements hebdomadaires récurrents (`recurring: weekly` + `recurringDay:`
optionnel) sont déployés sur chaque jour de semaine correspondant depuis leur date
de début vers l'avant. Un événement récurrent sans `recurringDay:` retombe sur le
jour de la semaine de sa date de début. Les événements non récurrents apparaissent
une fois sur leur `date`.

La grille est toujours 6×7 = 42 cellules par mois pour que les gabarits de
calendrier n'aient pas à traiter spécialement les limites de mois ; les cellules
hors-mois se rendent comme remplissage `day=null`.

### `.site.photos`

Entrées de frontmatter `photos:` agrégées depuis chaque page du site, triées par la
date de la page parente décroissante. Chaque entrée :

| Clé       | Type     | Quoi |
|-----------|----------|------|
| `src`     | `String` | URL de l'image (absolue ou relative au site) |
| `caption` | `String` | Légende d'affichage — chaîne vide quand non fournie |
| `alt`     | `String` | Texte d'accessibilité — retombe sur `caption` quand absent |
| `page`    | `Map`    | Enregistrement de page de base de la page parente (permet aux gabarits de relier la photo à son événement/album/sermon) |

Forme du frontmatter — soit une liste de chaînes (URL seulement), soit une liste de
cartes avec les clés `src` / `caption` / `alt` :

```yaml
photos:
  - "/img/picnic-01.jpg"
  - { src: "/img/picnic-02.jpg", caption: "The pie table" }
```

Les pages sans frontmatter `photos:` ne contribuent rien.

### `.site.data`

Données structurées que le thème ou l'auteur du site veut que les gabarits puissent
atteindre sans les forcer dans le frontmatter de chaque page. juicer balaie le
`dataDir` (par défaut `data/`) sous la racine du site et sous chaque thème actif :

```
data/team.toml              → .site.data.team
data/menu/lunch.yaml        → .site.data.menu.lunch
data/menu/dinner.toml       → .site.data.menu.dinner
```

Le nom de base du fichier (sans extension) devient la clé feuille ; chaque
répertoire sous `data/` devient un niveau d'imbrication. Les `.toml` et les
`.yaml` / `.yml` sont tous deux acceptés — choisissez la forme qui convient le mieux
aux données. Le JSON n'est pas analysé nativement, mais comme c'est un strict
sous-ensemble de YAML, vous pouvez renommer `.json` → `.yaml` et ça fonctionne.

Un usage typique :

```toml
# data/team.toml
[[members]]
name = "Alice"
role = "Lead"

[[members]]
name = "Bob"
role = "Eng"
```

```
{{ for m <- .site.data.team.members }}
  <li>{{ m.name }} — {{ m.role }}</li>
{{ end }}
```

**Superposition de thème.** Les thèmes peuvent livrer leur propre répertoire
`data/` ; les entrées du site l'emportent à la granularité du fichier feuille. Si un
thème fournit `data/site.toml` et que le site fournit `data/site.toml`, le fichier
du site remplace celui du thème au chemin de clé `.site.data.site`. Le site ne
**fusionne pas en profondeur** les champs — pour prendre en charge les surcharges
partielles en tant qu'auteur de thème, isolez dans un sous-répertoire
(`data/colors/default.toml` + `data/colors/dark.toml`).

**Notes de format.** Le TOML à la racine d'un fichier s'analyse toujours en une
carte ; le YAML peut s'analyser en une liste (`data/sponsors.yaml` commençant par
`- name: ...`) ou un scalaire, mais une carte est de loin la forme la plus utile car
l'accès aux champs imbriqués (`.site.data.sponsors[0].name`) est plus lisible que
l'indexation positionnelle dans la plupart des gabarits.

### `.site.comments`

Une transmission de la table `[comments]` de `site.toml` (voir
[Config → `[comments]`](/fr/reference/config/#comments-emplacement-de-configuration-du-fournisseur-de-commentaires)).
juicer ne livre jamais de backend de commentaires — le bloc de configuration est
opaque pour le moteur, et les partials de thème sont responsables de le lire et
d'émettre le HTML d'intégration.

`provider` est la seule convention ; tout le reste est propre au fournisseur et
défini par le thème. Un thème qui prend en charge plusieurs fournisseurs s'embranche
typiquement dessus :

```squiggly
{{ if .site.comments }}
  {{ if eq .site.comments.provider 'giscus' }}
    {{ partial 'comments/giscus.html' . }}
  {{ end }}
  {{ if eq .site.comments.provider 'utterances' }}
    {{ partial 'comments/utterances.html' . }}
  {{ end }}
  {{ if eq .site.comments.provider 'disqus' }}
    {{ partial 'comments/disqus.html' . }}
  {{ end }}
{{ end }}
```

Le `{{ if .site.comments }}` externe est la bonne condition pour « commentaires
activés ou désactivés à l'échelle du site » — quand aucune table `[comments]` n'est
définie dans `site.toml`, `.site.comments` est absent et la condition s'évalue à
faux. Les thèmes qui permettent le renoncement par page vérifient aussi
`{{ if .page.comments }}` (ou similaire) pour que des articles individuels puissent
supplanter le réglage du site.

## Pages d'alias

Le frontmatter `aliases: [...]` fait émettre à juicer une page de redirection à
chaque URL listée. Le layout d'alias (ou le repli intégré) voit un contexte de
données distinct :

| Clé            | Type     | Quoi |
|----------------|----------|------|
| `.target`      | `String` | URL canonique relative au site |
| `.absTarget`   | `String` | URL canonique absolue (`baseURL` + `.target`) |
| `.page.<...>`  | varie    | Chaque champ de l'enregistrement de la page canonique |
| `.site.<...>`  | varie    | Le contexte de site complet |

Si `layouts/_default/alias.html` existe, il est rendu avec les données ci-dessus.
Sinon juicer écrit une page HTML intégrée minimale avec un
`<meta http-equiv="refresh">` vers `.target`.

## `.page`

L'enregistrement enrichi de la page courante :

| Clé                    | Type      | Quoi |
|------------------------|-----------|------|
| `.page.title`          | `String`  | Titre du frontmatter |
| `.page.summary`        | `String`  | Résumé résolu (frontmatter / `<!--more-->` / repli) |
| `.page.url`            | `String`  | URL relative au site |
| `.page.relPermalink`   | `String`  | Identique à `url`, nommé pour la parité Hugo |
| `.page.permalink`      | `String`  | URL absolue (baseURL + url) |
| `.page.slug`           | `String`  | Radical d'URL (dernier segment de chemin, sans barres obliques) — utile pour les ancres en page quand un layout parcourt `.section.pages` et veut un id HTML stable par section. `""` pour l'index racine. |
| `.page.isSection`      | `Boolean` | `true` pour les pages `_index.md` |
| `.page.parent`         | `Map?`    | Enregistrement `_index` de la section englobante |
| `.page.ancestors`      | `List[Map]` | Chaîne racine → parent (sans soi-même) |
| `.page.next`           | `Map?`    | Page suivante dans la section par `pageOrder` |
| `.page.prev`           | `Map?`    | Page précédente dans la section |
| `.page.tags`           | `List[String]` | `tags` du frontmatter (toujours une liste, même rédigés comme une simple chaîne) |
| `.page.categories`     | `List[String]` | `categories` du frontmatter, normalisées de la même façon |
| `.page.date`           | `OffsetDateTime` | Date de publication analysée (frontmatter `date` ou repli sur la mtime du système de fichiers) |
| `.page.dateISO`        | `String`  | `2024-03-12T00:00:00Z` — pour `<time datetime=...>` |
| `.page.dateLong`       | `String`  | `March 12, 2024` — pour le corps de texte |
| `.page.dateShort`      | `String`  | `2024-03-12` — pour les listes compactes |
| `.page.wordCount`      | `Int`     | Nombre de mots du corps HTML rendu |
| `.page.readingTime`    | `Int`     | Minutes estimées (`ceil(wordCount / 200)`, plancher 1 pour les pages non vides) |
| `.page.series`         | `Map?`    | Bloc de série — `null` quand la page n'est pas dans une série (voir ci-dessous) |
| `.page.author`         | `Map?`    | Premier (ou seul) enregistrement de registre d'auteur résolu, ou `null` |
| `.page.authors`        | `List[Map]` | Tous les enregistrements d'auteurs résolus — liste vide quand aucun |
| `.page.backlinks`      | `List[Map]` | Enregistrements minces `{title, url, summary}` pour chaque page qui pointe *vers* celle-ci — liste vide quand rien ne pointe ici (voir ci-dessous) |
| `.page.assets`         | `List[Map]` | Ressources de paquet de page au niveau de section de cette page — `{name, url, ext}` par fichier. Liste vide quand le paquet n'a pas de ressources (voir [Paquets de page](/fr/reference/template-data/#paquets-de-page)) |
| `.page.<custom>`       | varie     | Toute clé de frontmatter |

### Forme de `.page.series`

Définie quand la page déclare `series:` dans le frontmatter :

| Clé       | Type        | Quoi |
|-----------|-------------|------|
| `name`    | `String`    | Nom de série tel qu'il apparaissait dans le frontmatter |
| `pages`   | `List[Map]` | Chaque page de la série, ordonnée (voir les règles d'ordonnancement ci-dessous) |
| `prev`    | `Map?`      | Enregistrement de la page précédente, ou `null` sur la première |
| `next`    | `Map?`      | Enregistrement de la page suivante, ou `null` sur la dernière |
| `index`   | `Int`       | Position de cette page au sein de la série (à partir de 1) |
| `total`   | `Int`       | Nombre de pages dans la série |

Ordonnancement : `seriesOrder` explicite croissant d'abord, puis `.page.date`
croissant, puis le nom de fichier comme départage stable.

Pour les pages d'index de section (où `.page.isSection` vaut `true`), en plus :

| Clé                  | Type        | Quoi |
|----------------------|-------------|------|
| `.page.pages`        | `List[Map]` | Pages sœurs non-`_index`, triées |
| `.page.subsections`  | `List[Map]` | Sections enfants directes, triées |

### Forme de `.page.backlinks`

Une liste d'enregistrements minces — un par page contenant un lien interne pointant
vers le permalien de la page courante. Triés par titre du référent pour un rendu
déterministe.

| Clé       | Type     | Quoi |
|-----------|----------|------|
| `title`   | `String` | Titre du frontmatter du référent (ou URL si titre absent) |
| `url`     | `String` | URL relative au site du référent |
| `summary` | `String` | Résumé résolu du référent |

Les gabarits qui ont besoin d'un enregistrement de référent plus riche (mots-clés,
date, auteur, etc.) le recherchent via `.site.pagesByPath[bl.url]` — garder les
enregistrements intégrés minces évite les cycles quand deux pages se lient l'une l'autre.

L'index inversé est construit pendant la passe markdown à partir des destinations de
lien de l'AST de chaque page. Filtrés au moment de la collecte : les URL absolues
(`https://…`, `mailto:`, `tel:`), les ancres à fragment seul (`#section`), et les
auto-liens. Les chaînes de requête et les fragments sont retirés avant la
correspondance, donc `[X](/target/#section)` et `[X](/target/?q=1)` comptent tous
deux comme un lien vers `/target/`. Les listes, tableaux, listes de définitions,
définitions de notes de bas de page, citations et éléments de liste sont tous
parcourus — partout où un auteur peut déposer un `[text](url)` en markdown, ça compte.

Pied de page « Lié depuis » de style wiki dans un layout :

```squiggly
{{ if .page.backlinks }}
  <aside class="backlinks">
    <h2>Linked from</h2>
    <ul>
      {{ for bl <- .page.backlinks }}
        <li><a href="{{ bl.url }}">{{ bl.title }}</a>{{ if bl.summary }} — {{ bl.summary }}{{ end }}</li>
      {{ end }}
    </ul>
  </aside>
{{ end }}
```

## `.section`

Toujours disponible (pour les pages non-`_index`, elle décrit la section englobante) :

| Clé                     | Type        | Quoi |
|-------------------------|-------------|------|
| `.section.pages`        | `List[Map]` | Pages sœurs non-`_index`, triées |
| `.section.subsections`  | `List[Map]` | Sections enfants directes, triées |
| `.section.index`        | `Map?`      | Enregistrement `_index` de la section |
| `.section.assets`       | `List[Map]` | Ressources de paquet de page pour la section englobante (voir [Paquets de page](/fr/reference/template-data/#paquets-de-page)) |
| `.section.paginator`    | `Map`       | État de pagination pour la tranche courante (toujours présent — voir ci-dessous) |

### `.section.paginator`

Les pages d'index de section se rendent une fois par tranche quand `paginate` est
défini. Les pages non-section obtiennent un paginateur avec `total = 1` pour que le
même gabarit puisse le lire sans condition.

| Clé       | Type     | Quoi |
|-----------|----------|------|
| `current` | `Int`    | Index de la tranche courante (à partir de 1) |
| `total`   | `Int`    | Nombre de tranches |
| `pages`   | `List[Map]` | Les pages de **cette** tranche (déjà découpées — ne pas redécouper) |
| `first`   | `String` | URL de la tranche 1 |
| `last`    | `String` | URL de la dernière tranche |
| `prevURL` | `String` | URL de la tranche précédente ; chaîne vide sur la tranche 1 |
| `nextURL` | `String` | URL de la tranche suivante ; chaîne vide sur la dernière tranche |

La tranche 2+ réside à `<section>/page/<N>/index.html` — des URL de style répertoire
qui fonctionnent sur n'importe quel hôte statique sans règles de réécriture.

## Paquets de page

Un *paquet de page* est un répertoire de contenu dont les fichiers non-markdown
(images, pièces jointes, tout ce qui n'est pas `.md` / `.toml` / `.yaml` / `.yml` /
`.html` / `.sq`) sont co-localisés avec la page et sont copiés vers l'URL de sortie
de la section.

Déposez les ressources à côté du markdown :

```
content/iceland-2024/
  _index.md
  hero.jpg
  skogafoss.jpg
  notes.md
```

Au moment de la construction, juicer copie les ressources vers le répertoire de
sortie de la section et les expose sur l'enregistrement de page :

- `hero.jpg` → `dst/html/iceland-2024/hero.jpg` → URL `/iceland-2024/hero.jpg`.
- `{{ .page.assets }}` sur l'index de section OU sur `notes.md` est la même liste —
  les ressources sont par répertoire, pas par page. `.section.assets` est un alias
  pour les gabarits qui préfèrent les lire depuis la section.

Chaque enregistrement de ressource :

| Clé    | Type     | Quoi |
|--------|----------|------|
| `name` | `String` | Nom de fichier sur le disque, préservé tel quel (casse, point) — les ressources ne sont PAS slugifiées |
| `url`  | `String` | URL relative au site — `<section-permalink>/<name>` |
| `ext`  | `String` | Extension en minuscules sans le point, ou `""` pour les fichiers sans extension |

Itérez dans un layout pour rendre une galerie, une liste de pièces jointes, etc. :

```squiggly
{{ if .page.assets }}
  <ul class="bundle-files">
    {{ for a <- .page.assets }}
      <li><a href="{{ a.url }}">{{ a.name }}</a></li>
    {{ end }}
  </ul>
{{ end }}
```

### Chemins d'image relatifs au paquet

À l'intérieur d'un paquet, `imageVariants`, `srcset` et `imageDims` résolvent les
chemins nus (sans `/` de tête) contre le paquet source de la page avant de retomber
sur `static/`. Cela signifie qu'une image de héros réside à la page et que le
gabarit n'a pas besoin de savoir où :

```squiggly
{{ with vs = imageVariants 'hero.jpg' }}
  <picture>
    <source type="image/webp" srcset="{{ srcset 'hero.jpg' 'webp' }}">
    <img src="{{ vs.original }}" width="{{ vs.originalWidth }}" height="{{ vs.originalHeight }}" alt="">
  </picture>
{{ end }}
```

Déplacez le paquet vers une URL différente, le layout fonctionne toujours. Les
chemins absolus (`/static/hero.jpg`) continuent de se résoudre depuis la racine
source — la préférence de paquet ne s'active que pour les chemins nus.

### Quand NE PAS utiliser un paquet

- Habillage à l'échelle du site (logo, favicon, polices) : gardez-les dans
  `static/` pour que chaque page puisse les atteindre via une URL absolue.
- Ressources partagées utilisées depuis de nombreuses pages : pareil — les paquets
  sont pour les fichiers propres à une page. Mettre un logo dans chaque paquet
  dupliquerait des octets et casserait un changement de logo.
- Fichiers dans un répertoire **sans** contenu markdown. Les paquets ont besoin
  d'une page pour les ancrer ; les ressources orphelines sont silencieusement sautées.

## builtin `asset`

La fonction de gabarit `asset` résout un nom logique en l'URL de sa sortie compilée
(et éventuellement avec empreinte). Le manifeste vient du
[pipeline `[assets]`](/fr/reference/config/#assets-pipeline-sass-esbuild).

```handlebars
<link rel="stylesheet" href="{{ asset 'site.css' }}">
<script src="{{ asset 'main.js' }}" defer></script>
```

| Cas de résolution                          | Ce que `asset 'foo.css'` renvoie |
|--------------------------------------------|----------------------------------|
| `[assets]` désactivé ou aucun pipeline exécuté | `foo.css` (l'entrée inchangée) |
| Pipeline exécuté, `fingerprint = false`    | L'URL `output` configurée (p. ex. `/css/foo.css`) |
| Pipeline exécuté, `fingerprint = true`     | L'URL avec empreinte (p. ex. `/css/foo.<16-hex>.css`) |
| Nom absent du manifeste (faute / jamais construit) | `foo.css` (l'entrée inchangée) — visible comme un `<link href>` cassé dans le HTML rendu |

La règle « entrée inchangée en cas d'absence » est délibérée : l'effet d'une faute
de frappe apparaît dans le HTML rendu plutôt que de disparaître dans une chaîne
vide, ce qui est plus rapide à repérer dans une sortie de construction ou un panneau
d'outils de développement du navigateur.

## Clés d'habillage à l'échelle du site

Les clés de premier niveau de `site.toml` que les thèmes fournis lisent depuis
`.site.*` pour piloter l'habillage partagé — favicon, attribution de pied de page,
lien de dépôt, feuilles de style personnalisées — sont cataloguées dans
[Thématisation](/fr/reference/theming/) avec les tables de palette / typographie /
dimensionnement `[juicerXxx]` par thème.

## Autres clés de premier niveau

| Clé         | Type      | Quoi |
|-------------|-----------|------|
| `.content`  | `String`  | Corps markdown rendu, HTML |
| `.toc`      | `Map`     | `{ headings: [TocEntry] }` — arbre de titres complet |
| `.sub`      | `List`    | Enfants du premier titre, aplatis |

## Ordonnancement des pages

`pageOrder` est **poids croissant, puis nom croissant**. Les pages sans frontmatter `weight` se trient après les pages pondérées mais avant toute valeur sentinelle.

[= note =]
Vous voudrez presque toujours définir des valeurs `weight` explicites sur les pages qui nécessitent un ordre particulier — installation avant démarrage rapide, etc. Tout ce qui livre un `weight` plus bas que le défaut (`9223372036854775807` = `Long.MaxValue / 2`) l'emporte.
[= /note =]
