---
title: juicerdocs
summary: Le thème de documentation au parti pris affirmé qui est fourni avec juicer — ce qui propulse ce site même.
weight: 40
---

`juicerdocs` est le thème qu'utilise ce site. Il est fourni avec le dépôt juicer à
`docs/themes/juicerdocs/`, donc un clone de juicer vient avec dès le départ. Pour
l'utiliser sur votre propre site, copiez le répertoire ou exécutez :

```bash
juicer theme add https://github.com/edadma/juicer.git -n juicerdocs
```

…et définissez `theme = "juicerdocs"` dans votre `site.toml`.

## Ce qu'il y a dans la boîte

- Une disposition à deux rails : la navigation de section à gauche, la TDM de la
  page à droite, le contenu au milieu.
- Barre supérieure collante avec logo, recherche, lien GitHub et bascule de thème.
- Mode clair + sombre piloté par les propriétés CSS personnalisées (basculable
  depuis la barre supérieure ; préférence système à la première visite).
- Traitement de héros pour la page d'accueil (titre en dégradé, grille de cartes de
  fonctionnalités, boutons d'appel à l'action).
- 16 shortcodes — voir [Shortcodes](/fr/concepts/shortcodes/) pour l'ensemble complet.
- Recherche côté client propulsée par `/search.json` (toujours émis par juicer ;
  voir [recherche](/fr/getting-started/quickstart/) pour l'intégration).
- Rail droit « Sur cette page » avec auto-surbrillance au défilement
  (piloté par IntersectionObserver).
- Superposition de barre latérale mobile avec arrière-plan, s'ouvre sur le bouton
  hamburger de la barre supérieure.

## Quand l'utiliser (et quand non)

Utilisez juicerdocs si vous voulez un thème de documentation façon Hugo qui démarre
rapidement et a l'air convenable sans effort. Il est ajusté pour la documentation
technique : navigation latérale, blocs de code avec boutons de copie, tableaux,
encadrés, recherche.

Choisissez autre chose si vous voulez :

- Un thème de blogue — juicerdocs est structuré autour d'une documentation
  hiérarchique, pas d'une liste chronologique.
- Un thème de page d'atterrissage — le traitement de héros convient à une accueil
  de documentation, mais ce n'est pas une page marketing.
- Des couleurs différentes — en fait, c'est configurable ; voir
  [Configuration](/fr/themes/juicerdocs/configuration/).

## Ce que couvre cette section

Les pages ci-dessous documentent les boutons de configuration que juicerdocs lit
depuis votre `site.toml`, plus comment personnaliser tout ce qui dépasse ce qui est
exposé. La feuille [Site de démonstration](/fr/themes/juicerdocs/demo/) est un rappel rapide que **ce
site est la démo** — juicer.build lui-même utilise juicerdocs.
