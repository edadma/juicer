---
title: Démarrage rapide
summary: Construisez un petit site d'une seule page en trois minutes.
weight: 20
---

Ce guide construit le plus petit site juicer possible — une page, un gabarit — pour que vous voyiez les rouages avant d'ajouter quoi que ce soit de sophistiqué.

## 1. Créer un répertoire

```bash
mkdir my-site
cd my-site
```

## 2. Écrire `site.toml`

```toml
title   = "My Site"
author  = "You"
baseURL = "http://localhost:8080"
```

C'est le minimum. Juicer superpose ceci par-dessus la configuration de référence `standard` (les thèmes vont dans `themes/`, le contenu dans `content/`, etc.) — voir [Configuration](/reference/config/) pour l'ensemble complet.

## 3. Écrire un gabarit

Juicer a besoin d'au moins un gabarit *file* (pour les pages individuelles) et d'un gabarit *folder* (pour les index de section). Ils vivent sous `layouts/_default/` :

```bash
mkdir -p layouts/_default
```

`layouts/_default/folder.html` :
```squiggly
<!DOCTYPE html>
<html>
<head><title>{{ .page.title }} – {{ .site.title }}</title></head>
<body>
  <h1>{{ .page.title }}</h1>
  {{ .content }}
</body>
</html>
```

`layouts/_default/file.html` :
```squiggly
{{ partial 'folder' . }}
```

(Ce second gabarit délègue simplement à `folder.html` — pour un site d'une seule page, les deux gabarits peuvent produire le même rendu.)

## 4. Écrire du contenu

```bash
mkdir content
```

`content/_index.md` :

```markdown
---
title: Hello
---

# It works!

This is **juicer**, rendering markdown.
```

## 5. Construire et servir

```bash
sbt 'juicerJVM/run serve -s . -d public'
```

Ouvrez <http://localhost:8080>. Vous devriez voir votre page.

## Que s'est-il passé ?

- Juicer a parcouru l'arborescence `content/`, repérant les fichiers markdown et analysant leur frontmatter YAML.
- Pour chaque fichier de contenu, il a choisi un gabarit (`folder.html` pour `_index.md`, `file.html` pour le reste) et l'a rendu.
- La sortie est allée dans `public/` — `_index.md` est devenu `public/index.html`.
- Un `sitemap.xml` et un `search.json` ont aussi été produits automatiquement.

Passez aux [Concepts](/concepts/) pour voir le rôle de chacune de ces pièces.
