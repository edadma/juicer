---
title: Site de démonstration
summary: Ce site (juicer.build) EST la démo de juicerdocs — chaque page que vous lisez est rendue avec le thème.
weight: 20
---

Contrairement aux six autres thèmes fournis — chacun ayant son propre site de
démonstration sous `docs/demos/<theme>/` que le pipeline de déploiement construit
dans `/themes/<theme>/demo/` — **juicerdocs n'a pas de source de démo distincte.** Ce
site, [juicer.build](/fr/), est construit avec juicerdocs. Chaque page que vous lisez
est le thème en direct.

## Ce que ce site démontre

Le thème juicerdocs se rendant contre du contenu réel — plus d'une centaine de pages
markdown, des arbres de sections profonds, les 17 shortcodes en usage, la recherche
adossée à `/search.json`, la TDM « Sur cette page » du rail droit, la superposition
de barre latérale mobile, le mode clair + sombre piloté par la bascule de la barre
supérieure, la coloration syntaxique côté serveur dans les blocs de code.

[← Retour à la page d'accueil](/fr/) et parcourez la documentation pour voir chaque
fonctionnalité de juicerdocs en contexte. Ou, si vous voulez une visite rapide :

- **[Traitement de héros + grille de cartes de fonctionnalités](/fr/)** — la page d'accueil.
- **[Navigation latérale à profondeur d'imbrication arbitraire](/fr/themes/)** — la
  section Thèmes livre trois niveaux (Thèmes → juicerblog →
  Configuration) et le partial récursif en prend en charge davantage.
- **[TDM du rail droit](/fr/concepts/blogging/)** — les longues pages qui optent
  pour `toc: true` obtiennent le rail « Sur cette page » à auto-surbrillance.
- **[Blocs de code avec boutons de copie + pastilles de langage](/fr/getting-started/quickstart/)** —
  le bouton « Copy » affiche brièvement « Copied » en cas de succès.
- **[Shortcodes en usage](/fr/reference/shortcodes/)** — la page de catalogue rend
  chaque shortcode contre lui-même, donc le style est visible en ligne.

## L'utiliser comme modèle

La source de juicer.build réside à `docs/` dans le [dépôt
juicer](https://github.com/edadma/juicer). La forme :

```
docs/
├── site.toml             # site config
├── content/              # this entire docs tree
├── themes/juicerdocs/    # the theme itself
└── demos/                # per-theme demo sources (built separately)
```

Retirez l'arborescence `content/` existante, remplacez-la par la vôtre, et pointez
`[juicerdocs] logo` vers votre logotype. La référence
[Configuration](../configuration/) compagnon catalogue chaque clé site.toml, jeton
de palette et bouton de frontmatter par page que le thème lit.
