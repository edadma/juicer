---
title: Thèmes
summary: Les onze thèmes fournis dans la boîte — à quoi sert chacun et lequel choisir.
weight: 40
---

Juicer fournit onze thèmes dans le répertoire `docs/themes/` du dépôt. Ils sont tous
en CSS fait main, sans framework, conscients du clair/sombre, et ajustés pour une
forme de site spécifique. Un clone de juicer vient avec tous ; pour en utiliser un
sur votre propre site, copiez le répertoire ou tirez-le via `juicer theme add`
(la section de chaque thème montre la commande exacte).

Le modèle de thématisation partagé — jetons de palette, `customCSS`, le motif de
surcharge par remplacement de fichier — réside dans
[Référence → Thématisation](/fr/reference/theming/) et s'applique à l'identique à
chaque thème fourni.

## Lequel me faut-il ?

| Si votre site est surtout…                               | Utilisez…                                         |
|----------------------------------------------------------|---------------------------------------------------|
| Une documentation technique — navigation latérale, rail « sur cette page », recherche | [juicerdocs](/fr/themes/juicerdocs/)              |
| Un blogue — articles datés, signatures d'auteur, archives par mot-clé/année | [juicerblog](/themes/juicerblog/)                 |
| De la prose longue / des notes d'étude — essais, notes de bas de page, math | [juicerstudy](/themes/juicerstudy/)               |
| Une page d'atterrissage produit / SaaS                   | [juicerlanding](/themes/juicerlanding/)           |
| Un portfolio ou un site d'études de cas axé image        | [juicerportfolio](/themes/juicerportfolio/)       |
| Une galerie photo — l'œuvre *est* les photos             | [juicergallery](/themes/juicergallery/)           |
| Un wiki / Zettelkasten piloté par les rétroliens         | [juicerwiki](/themes/juicerwiki/)                 |
| Un site de recettes — ingrédients, méthodes, schema.org Recipe | [juicercook](/themes/juicercook/)                 |
| Un balado — lecteur audio, pièces jointes RSS, bande d'abonnement | [juicerpodcast](/themes/juicerpodcast/)           |
| Un site de café, restaurant ou petite entreprise         | [juicercafe](/themes/juicercafe/)                 |
| Un site d'église / ministère / communauté de foi         | [juicerchurch](/themes/juicerchurch/)             |

## Ce que couvre chaque section

Chaque section de thème de ce groupe documente les trois mêmes couches :

- **Aperçu** — ce que le thème livre, quand le choisir, quand choisir autre chose.
- **Configuration** — chaque clé `site.toml` que le thème lit, chaque jeton de
  palette `[<theme>]`, chaque bouton de frontmatter par page.
- **Site de démonstration** — une démo en direct rendue avec le thème lui-même. Le
  pipeline de déploiement construit chaque démo (les sources résident sous
  `docs/demos/<theme>/`) dans `/themes/<theme>/demo/`, de sorte que la feuille sert
  la démo rendue à cette URL. L'exception est juicerdocs — ce site de documentation
  est construit avec juicerdocs, donc la feuille Site de démonstration de juicerdocs
  pointe en retour vers `/`.

[= note =]
Les sections des dix autres thèmes (juicerblog, juicerstudy, juicerlanding, …) ne
sont pour l'instant disponibles qu'en anglais ; les liens ci-dessus pointent vers
leurs pages anglaises. Seul juicerdocs — le thème qui propulse ce site — est traduit
en français.
[= /note =]
