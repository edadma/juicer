---
title: Thèmes
summary: Des habillages prêts à l'emploi qui fournissent gabarits, partials, shortcodes et ressources statiques.
weight: 30
---

Un thème juicer est un répertoire sous `themes/<name>/` ayant la même forme qu'un site :

[= filetree =]
themes/juicerdocs/
├── layouts/
│   └── _default/
│       ├── baseof.html
│       ├── file.html
│       └── folder.html
├── partials/
│   └── ...
├── shortcodes/
│   └── ...
└── static/
    └── ...
[= /filetree =]

Activez-le depuis `site.toml` :

```toml
theme = "juicerdocs"
```

C'est tout. Juicer parcourt le répertoire du thème avant le répertoire du site et fusionne les deux — les fichiers du site **l'emportent en cas de collision de clé**, vous pouvez donc supplanter n'importe quel gabarit, partial, shortcode ou ressource statique sans forker le thème.

## Chaînes de thèmes

La clé `theme` accepte aussi un tableau, auquel cas les entrées antérieures supplantent les suivantes :

```toml
theme = ["my-overrides", "juicerdocs"]
```

Maintenant `my-overrides/` l'emporte sur `juicerdocs/`, qui l'emporte sur le site lui-même ? Non — le site l'emporte toujours. L'ordre est **site > my-overrides > juicerdocs**. C'est utile quand vous voulez réutiliser l'essentiel de `juicerdocs` mais personnaliser, disons, la barre supérieure avec un petit thème de superposition à vous.

Un thème peut aussi tirer d'autres thèmes lui-même, en les déclarant dans son propre `theme.toml` — c'est ainsi que chaque thème fourni hérite de `juicercommon`. La liste `theme` au niveau du site et les `inherits` déclarés par chaque thème sont résolus ensemble en une seule chaîne de recherche, le premier l'emportant partout. [L'héritage de thèmes](/fr/concepts/theme-inheritance/) couvre les règles de résolution.

[= tip =]
Les répertoires de thèmes résident sous `themes/` parce que c'est la valeur par défaut de la clé de configuration `themeDir`. Mettez `themeDir = ""` si vous voulez que les thèmes résident à la racine du site, ou tout autre chemin qui convient à la disposition de votre dépôt.
[= /tip =]

## Ce qu'un thème peut fournir

| Sous-répertoire | Rôle |
|--------------|------|
| `layouts/`   | Gabarits pour les divers types de page (`_default/file.html`, etc.) |
| `partials/`  | Fragments de gabarit réutilisables — appelés via `{{ partial 'name' . }}` |
| `shortcodes/`| Gabarits appelables depuis le markdown — invoqués via `\[= name =]…\[= /name =]` |
| `static/`    | Fichiers copiés tels quels dans l'arborescence de sortie |

Les thèmes ne fournissent **pas** de contenu (aucun répertoire `content/` à l'intérieur d'un thème n'est jamais lu), ni n'obtiennent de « autres gabarits » de premier niveau rendus (les gabarits HTML hors de ces répertoires sont toujours relatifs au site de l'utilisateur).

## Choisir la bonne primitive

[= tabs =]
[= tab "Partial" =]
Un *partial* est un fragment HTML réutilisable appelé depuis un autre gabarit. Il reçoit le contexte de données que vous lui passez.

```squiggly
{{ partial 'topbar' . }}
{{ partial 'topbar' .page }}
```

Utilisez les partials pour : l'habillage de page, les éléments d'UI répétés, tout ce que vous factoriseriez hors du HTML.
[= /tab =]
[= tab "Shortcode" =]
Un *shortcode* est un partial appelé depuis **l'intérieur du contenu markdown**, via la syntaxe `\[= name =]`. Les auteurs écrivent le contenu avec des shortcodes en ligne :

```markdown
\[= note =]
This is a note callout.
\[= /note =]
```

Utilisez les shortcodes pour : les encadrés, les exemples à onglets, les médias intégrés, tout ce qui est du contenu mais nécessite un HTML plus riche que le markdown seul.
[= /tab =]
[= tab "Layout" =]
Un *layout* est le gabarit de premier niveau pour un type de page. Juicer choisit le bon par nom et recherche de dossier.

Utilisez les layouts pour : le véritable squelette `<html>` et les règles structurelles par type de page.
[= /tab =]
[= /tabs =]

## Thèmes fournis

Onze thèmes sont livrés dans le dépôt juicer, chacun ajusté pour une forme de
site différente. Choisissez le plus proche et supplantez les fichiers individuels depuis vos propres
`layouts/` ou `static/` :

| Thème              | Où il réside                    | À quoi il sert |
|--------------------|---------------------------------|----------------|
| `juicerdocs`       | `docs/themes/juicerdocs/`       | Sites de documentation — navigation latérale, rail « Sur cette page », shortcodes d'encadrés. Propulse ce site. |
| `juicerblog`       | `docs/themes/juicerblog/`       | Blogues — listes d'articles avec pagination, archives par mot-clé/catégorie/année, navigation de série, signatures d'auteur, coloration syntaxique côté serveur. |
| `juicerstudy`      | `docs/themes/juicerstudy/`      | Prose longue — essais, notes d'étude, journaux de lecture, notes de cours. Colonne de lecture avec empattement, KaTeX optionnel, sans date par défaut. |
| `juicerlanding`    | `docs/themes/juicerlanding/`    | Pages d'atterrissage produit / SaaS — pile de sections d'une seule page (héros, fonctionnalités, tarifs, témoignages, FAQ) entièrement pilotée depuis `site.toml`. |
| `juicerportfolio`  | `docs/themes/juicerportfolio/`  | Portfolios de designers / studios — grille de projets axée image, héros par projet + barre latérale de métadonnées + galerie. |
| `juicergallery`    | `docs/themes/juicergallery/`    | Galeries photo — albums en mosaïque/grille, cartes photo prêtes pour la lightbox, légendes EXIF légères. |
| `juicercafe`       | `docs/themes/juicercafe/`       | Cafés, petites entreprises, restaurants — widget d'horaires, sections de menu, albums photo, liste d'événements. |
| `juicercook`       | `docs/themes/juicercook/`       | Sites de recettes — JSON-LD Recipe, fiches recettes, disposition ingrédients/étapes, feuille de style adaptée à l'impression. |
| `juicerchurch`     | `docs/themes/juicerchurch/`     | Églises, ministères — archive de sermons, grille de calendrier d'événements récurrents, section ministères, albums photo. |
| `juicerpodcast`    | `docs/themes/juicerpodcast/`    | Balados — liste d'épisodes, lecteur par épisode + notes d'émission, liens d'abonnement RSS. |
| `juicerwiki`       | `docs/themes/juicerwiki/`       | Wikis personnels / jardins numériques — rétroliens, nuage de mots-clés, recherche de style wiki. |

Ces onze héritent tous d'un douzième thème, caché : **`juicercommon`**, la
base partagée qui contient les partials, shortcodes et scripts que chaque thème
a en commun (le bloc `seo`, les scripts d'initialisation/bascule du mode sombre, et les
shortcodes note/tip/warning/tabs/github/…). Vous ne sélectionnez jamais `juicercommon`
directement — le `theme.toml` de chaque thème le liste sous `inherits`. Voir
[L'héritage de thèmes](/fr/concepts/theme-inheritance/) pour le fonctionnement de cette
résolution.

Chaque thème a sa propre section sous [Thèmes](/fr/themes/) (liée depuis la
barre latérale) avec un aperçu « ce qu'il y a dans la boîte » et une page de
configuration qui catalogue chaque jeton `[juicerXxx]`, chaque clé d'habillage de
premier niveau et chaque convention de frontmatter par page.

Un site de démonstration fonctionnel réside sous `docs/demos/` pour chaque thème fourni
— par exemple, `docs/demos/juicerblog/` reflète la matrice de fonctionnalités de
`juicerblog`, et `docs/demos/juicerlanding/` exerce chaque bloc de section de
`juicerlanding`. Le pipeline de déploiement construit chaque démo dans
`/themes/<theme>/demo/` afin que la feuille « Site de démonstration » de la barre latérale pour chaque thème
le serve en direct. Partez de la démo la plus proche et éditez à partir de là :

```bash
sbt 'juicerJVM/run serve -s docs/demos/juicerblog -L'
sbt 'juicerJVM/run serve -s docs/demos/juicerportfolio -L'
```

## Construire le vôtre

Lire n'importe quel thème fourni de bout en bout est une visite rapide des
conventions. `juicerdocs` est la démonstration la plus complète de chaque primitive
de thème (layouts, partials, shortcodes, static) ; `juicerblog` est le
plus petit thème qui touche aux fonctionnalités du moteur propres au blogue ;
`juicerlanding` est la démonstration la plus nette d'une page pilotée par le frontmatter qui
s'assemble elle-même à partir de blocs `site.toml`.

[= github "edadma/juicer" /=]
