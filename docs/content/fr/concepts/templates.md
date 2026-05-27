---
title: Gabarits
summary: Comment les gabarits squiggly transforment le contenu en HTML.
weight: 20
---

Les gabarits résident sous `layouts/` et sont écrits en [squiggly](https://github.com/edadma/squiggly), un langage de style Go-template. Chaque gabarit est du HTML pur avec interpolation `{{ ... }}`.

## Types de gabarit

| Type          | Motif de nom de fichier      | Quand il est utilisé |
|---------------|------------------------------|----------------|
| `baseof`      | `_default/baseof.html`       | Coquille externe — enveloppe chaque page |
| `file`        | `_default/file.html`         | Gabarit de page simple |
| `folder`      | `_default/folder.html`       | Gabarit d'index de section |
| Partial       | `partials/<name>.html`       | Appelé via `{{ partial 'name' . }}` |
| Shortcode     | `shortcodes/<name>.html`     | Appelé depuis le markdown via `\[= name =]` |

Le nom de dossier `_default/` provient de la clé de configuration `defaultLayout`. Vous pouvez aussi placer des dérogations par section dans `layouts/<section>/` — juicer retombe sur `_default/` quand un gabarit propre à la section est absent.

## Le rendu en deux passes

Quand un gabarit particulier (`file.html` ou `folder.html`) ET un `baseof.html` existent tous deux :

[= steps =]
## Première passe — le gabarit particulier s'exécute

Sa sortie est **abandonnée**. Ce qui compte, ce sont les blocs `{{ define <name> }}…{{ end }}` qu'il remplit.

## Seconde passe — `baseof.html` s'exécute

Ses appels `{{ block <name> . }}…{{ end }}` tirent en place le contenu défini précédemment.
[= /steps =]

Ce motif permet à un seul `baseof.html` de fournir tout l'habillage de la page (head, en-tête, barre latérale, pied de page) tandis que `file.html` et `folder.html` ne décrivent que ce qui va dans la colonne principale.

[= tabs =]
[= tab "baseof.html" =]
```squiggly
<!DOCTYPE html>
<html>
<head><title>{{ .page.title }}</title></head>
<body>
  {{ partial 'topbar' . }}
  <main>
    {{ block main . }}{{ .content }}{{ end }}
  </main>
</body>
</html>
```
[= /tab =]
[= tab "file.html" =]
```squiggly
{{ define main }}
  <article class="prose">
    <h1>{{ .page.title }}</h1>
    {{ .content }}
  </article>
{{ end }}
```
[= /tab =]
[= tab "folder.html" =]
```squiggly
{{ define main }}
  <article class="prose">
    <h1>{{ .page.title }}</h1>
    {{ .content }}
  </article>

  <ul>
    {{ for p <- .section.pages }}
      <li><a href="{{ p.url }}">{{ p.title }}</a></li>
    {{ end }}
  </ul>
{{ end }}
```
[= /tab =]
[= /tabs =]

## Le contexte de données

Chaque page est rendue par rapport à un contexte de données fixe :

| Variable      | Ce qu'elle contient |
|---------------|---------------|
| `.site`       | Configuration du site + `pages`, `pagesByPath`, `root` |
| `.page`       | L'enregistrement de la page courante (frontmatter + url + résumé + champs de navigation) |
| `.section`    | Pages + sous-sections + index de la section englobante |
| `.content`    | Corps markdown rendu |
| `.toc`        | Arbre des titres |
| `.sub`        | Enfants du premier titre, aplatis |

Dans `.page` :

- `.page.title`, `.page.summary` — depuis le frontmatter (le résumé est dérivé automatiquement s'il est absent)
- `.page.url`, `.page.relPermalink`, `.page.permalink` — trois variantes d'URL
- `.page.parent`, `.page.ancestors` — navigation de section (l'enregistrement `_index` du parent, puis la chaîne racine → parent)
- `.page.next`, `.page.prev` — navigation entre voisins (par ordre des pages de la section)
- `.page.isSection` — `true` pour les pages `_index.md`
- `.page.pages`, `.page.subsections` — enfants — renseignés uniquement sur les pages `_index.md`
- `.page.date`, `.page.dateISO`, `.page.dateLong`, `.page.dateShort` — définis quand le `date:` du frontmatter est présent (ou repli sur mtime)
- `.page.tags`, `.page.categories` — toujours des listes (une chaîne unique dans le frontmatter est enveloppée)
- `.page.author`, `.page.authors` — enregistrements résolus du registre d'auteurs
- `.page.wordCount`, `.page.readingTime` — calculés automatiquement à partir du corps rendu
- Plus toute clé de frontmatter personnalisée

Voir [Référence / Données de gabarit](/fr/reference/template-data/) pour l'ensemble complet.
