---
title: Fichiers de contenu
summary: Des fichiers markdown avec frontmatter YAML — la source de vérité de chaque page.
weight: 10
---

Chaque page d'un site juicer provient d'un fichier markdown sous `content/`. Il en existe deux variétés :

- **Index de section** — un fichier nommé `_index.md` (configurable via `folderContent`). Rendu en `index.html` dans le répertoire de la section. Porte le titre de la section, son résumé et toute personnalisation de page-liste.
- **Page simple** — tout autre fichier markdown (`foo.md`). Rendu en son propre `foo/index.html` pour des URL propres.

[= filetree =]
content/
├── _index.md                 → /index.html
├── about.md                  → /about/index.html
└── docs/
    ├── _index.md             → /docs/index.html
    ├── installation.md       → /docs/installation/index.html
    └── api/
        ├── _index.md         → /docs/api/index.html
        └── spec.md           → /docs/api/spec/index.html
[= /filetree =]

## Frontmatter

Chaque fichier de contenu peut commencer par un bloc de frontmatter YAML, délimité par `---` :

```markdown
---
title: My page
summary: One-line description shown in lists and meta tags.
weight: 30
draft: false
---

# Welcome

The body is plain markdown.
```

Clés de frontmatter reconnues :

| Clé       | Type     | Rôle |
|-----------|----------|--------------|
| `title`   | chaîne   | Titre de la page ; utilisé par les gabarits et l'index de recherche |
| `summary` | chaîne   | Résumé explicite ; supplante celui dérivé automatiquement |
| `weight`  | entier   | Clé de tri dans la section (plus petit = en premier) |
| `draft`   | booléen  | Ignore la page sauf si `--drafts` est passé |
| n'importe quoi | tout | Disponible pour les gabarits via `.page.<key>` |

[= note =]
Le format du frontmatter est YAML. Les frontmatter JSON et TOML ne sont **pas** pris en charge — en partie parce que YAML couvre le cas d'usage avec aisance, en partie parce que prendre en charge trois formats triple la surface à maintenir correcte.
[= /note =]

## URL propres

Les deux variétés de contenu sont rendues en `index.html` afin que les URL ne portent pas d'extension de fichier. `about.md` devient `/about/`, pas `/about.html`. Le `_index.md` d'une section devient `/docs/`, pas `/docs.html`.

## La particularité de `htmlDir`

Par défaut (la configuration de base `standard`), juicer écrit les sections imbriquées dans `dst/html/<section>/...` plutôt que `dst/<section>/...`. Ce segment `html/` est **uniquement sur le système de fichiers** — chaque URL que juicer émet le retire. La convention existe pour que :

- Le `index.html` racine du site réside au sommet de `dst/`, à côté des fichiers `static/` (`favicon.ico`, `robots.txt`, etc.).
- Le contenu imbriqué reste sous un seul sous-arbre, de sorte qu'une racine de serveur web pointant vers `dst/` ne confonde pas les ressources statiques avec les pages rendues.

Si cela vous surprend, mettez `htmlDir = ""` dans votre `site.toml` pour le désactiver. La plupart des utilisateurs n'ont pas besoin d'y toucher.

## Lots de page (page bundles)

Déposez des fichiers non-markdown (images, pièces jointes, tout ce qui n'est pas `.md`/`.toml`/`.yaml`/`.html`) à côté du markdown d'une page et ils l'accompagnent comme **ressources de lot** :

```
content/iceland-2024/
  _index.md
  hero.jpg
  skogafoss.jpg
```

`hero.jpg` est copié dans le répertoire de sortie de la section et servi depuis `/iceland-2024/hero.jpg`. Les gabarits itèrent `.page.assets` pour rendre une galerie ou une liste de pièces jointes, et `imageVariants 'hero.jpg'` (sans barre oblique initiale) se résout relativement au lot — déplacez le lot, le balisage suit.

Référence complète (forme de l'enregistrement, règles de résolution, quand NE PAS utiliser un lot) dans [Données de gabarit → Lots de page](/fr/reference/template-data/#paquets-de-page).

## Brouillons

Une page avec `draft: true` est **entièrement ignorée** lors d'une construction normale — invisible pour la table des matières, le sitemap, l'index de recherche et les listes de section. Pour prévisualiser les brouillons localement, passez `--drafts` (ou `-D`) :

```bash
sbt 'juicerJVM/run serve -s docs -D'
```

## Programmer des articles à venir

Une page dont le `date:` analysé dans le frontmatter est dans le futur est **ignorée** lors d'une construction normale, de la même façon que `draft: true` — invisible pour tous les consommateurs en aval. Le flux de travail prévu est :

[= steps =]
## Définissez la date de publication dans le frontmatter

```yaml
---
title: Big announcement
date: 2024-12-25T09:00:00Z
---
```

## Poussez l'article dans votre dépôt dès qu'il est prêt

L'article est invisible pour un `juicer build` normal jusqu'à ce que l'horloge système rattrape la date.

## Reconstruisez (ou redéployez) à la date de publication ou après

L'article commence à apparaître dans les listes de section, les archives de taxonomie, les flux et le sitemap automatiquement. La plupart des gens mettent en place un cron de construction quotidien ou utilisent un planificateur de CI.
[= /steps =]

Pour prévisualiser localement les articles datés dans le futur, passez `--future` (ou `-F`) :

```bash
sbt 'juicerJVM/run serve -s docs -F'
```

`--future` et `--drafts` sont des drapeaux indépendants. Un article avec `draft: true` *et* une `date` future nécessite les deux drapeaux pour être rendu.

[= note =]
La règle d'exclusion des dates futures ne s'applique qu'aux pages ayant un `date:` **explicite** dans le frontmatter. Les pages reposant sur le repli sur le mtime du système de fichiers ne sont jamais exclues pour cause de date future — un `mtime` ne peut pas être dans le futur dans un flux de travail normal, mais la règle la plus sûre est « seul le fait de dater volontairement dans le futur compte ».
[= /note =]

## Résumés

Chaque page expose un `.page.summary`. Trois sources sont essayées, dans l'ordre :

[= steps =]
## Frontmatter explicite

Si `summary: ...` est défini dans le frontmatter, il est utilisé tel quel. On s'arrête.

## Marqueur `<!--more-->`

Le corps jusqu'à (mais sans inclure) `<!--more-->` est rendu en HTML et utilisé comme résumé. Utile quand le résumé naturel est le premier paragraphe ou deux, mais que vous ne voulez pas vous répéter.

## Premier paragraphe (repli)

Le texte brut du premier paragraphe, plafonné à 30 mots et tronqué par des points de suspension. Hugo utilise 70 mots par défaut ; juicer coupe plus court pour des pages-listes compactes.
[= /steps =]
