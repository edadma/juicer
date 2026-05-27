---
title: Héritage de thèmes
summary: Comment un thème en tire d'autres via theme.toml, et comment la chaîne de recherche complète est résolue.
weight: 35
---

Un thème peut s'appuyer sur d'autres thèmes. Il les déclare dans un fichier
`theme.toml` à sa propre racine :

[= filetree =]
themes/juicerdocs/
├── theme.toml          ← inherits = ["juicercommon"]
├── layouts/
├── partials/
└── static/
[= /filetree =]

```toml
# themes/juicerdocs/theme.toml
inherits = ["juicercommon"]
```

Quand un site active `juicerdocs`, juicer ne regarde pas seulement dans
`juicerdocs/` — il parcourt aussi la liste `inherits`, insérant chaque thème
nommé dans la chaîne de recherche juste après le thème qui l'a nommé. Partials,
shortcodes, gabarits et fichiers statiques se résolvent tous contre cette chaîne
étendue.

C'est ainsi que les thèmes fournis partagent du code : chacun hérite de
[`juicercommon`](/fr/concepts/themes/#themes-fournis), le thème de base caché
qui contient le bloc `seo`, les scripts d'initialisation/bascule du mode sombre et
les shortcodes communs (`note`, `tip`, `warning`, `tabs`, `github`, …). Aucun
thème ne les duplique.

## La chaîne de recherche

Partez du réglage `theme` du site — un nom unique ou un tableau — et parcourez
les `inherits` de chaque thème en profondeur d'abord. Le résultat est une seule
chaîne ordonnée. La résolution est **le premier l'emporte** : le premier endroit
où un fichier est trouvé est celui qui est utilisé.

```toml
# site.toml
theme = "juicerdocs"
```

```toml
# themes/juicerdocs/theme.toml
inherits = ["juicercommon"]
```

se résout en la chaîne :

```
site  >  juicerdocs  >  juicercommon
```

Ainsi un `partials/seo.html` dans votre site l'emporte sur celui de `juicerdocs`,
qui l'emporte sur celui de `juicercommon`. Déposez votre propre
`partials/note.html` dans `juicerdocs/` et il supplante le shortcode partagé sans
toucher à `juicercommon`.

L'héritage se compose avec le **tableau** de thèmes au niveau du site. Les deux
sont résolus dans la même chaîne — le tableau d'abord, puis les `inherits` de
chaque entrée :

```toml
# site.toml
theme = ["my-overrides", "juicerdocs"]
```

```
site  >  my-overrides  >  juicerdocs  >  juicercommon
```

`inherits` est transitif : si `juicercommon` déclarait lui-même
`inherits = ["base"]`, `base` atterrirait à la fin de la chaîne.

[= note =]
La priorité ne change jamais : **les fichiers du site l'emportent toujours**,
puis les thèmes dans l'ordre de la chaîne. L'héritage ne fait qu'allonger la
chaîne — il ne permet jamais à un thème de supplanter le site.
[= /note =]

## Déduplication

Un thème atteint plus d'une fois — un losange, où deux thèmes héritent tous deux
d'une base commune — apparaît dans la chaîne **une seule fois**, à sa position la
plus précoce :

```toml
# site.toml
theme = ["themeA", "themeB"]   # tous deux héritent de "shared"
```

```
site  >  themeA  >  shared  >  themeB
```

`shared` se résout une fois, conservé à sa première occurrence. Un fichier défini
dans `themeA`, `themeB` et `shared` est remporté par `themeA` (le plus précoce
dans la chaîne).

## Erreurs

[= warning =]
Un thème qui liste un thème inexistant dans `inherits` fait échouer la
construction avec un message clair :

```
theme 'juicerdocs' inherits unknown theme 'typo' — no directory at …
```

Un **cycle** — `A` hérite de `B` qui hérite de `A` — échoue aussi, avec le chemin
tracé pour que vous voyiez où il se referme :

```
theme cycle: themeA → themeB → themeA
```
[= /warning =]

Un thème manquant nommé au **niveau du site** (dans le `theme` de `site.toml`) est
traité avec indulgence — il est simplement ignoré — conformément au comportement
de longue date de juicer. Seuls les liens `inherits` sont validés strictement,
parce qu'un auteur de thème contrôle ses propres dépendances.

## Pas de theme.toml ?

Un thème sans `theme.toml` n'hérite de rien — c'est exactement équivalent à
`inherits = []`. Les thèmes écrits avant l'existence de cette fonctionnalité
continuent de fonctionner à l'octet près ; l'héritage est purement additif.

[= tip =]
`theme.toml` comprend actuellement une seule clé, `inherits`. C'est le seul
endroit où les thèmes déclarent leurs dépendances, donc un thème se décrit
lui-même — vous pouvez lire son `theme.toml` et connaître toute la chaîne qu'il
attend.
[= /tip =]
