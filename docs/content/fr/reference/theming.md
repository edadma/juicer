---
title: Thématisation
summary: Surcharges par site pour les couleurs, polices, dimensionnement et disposition d'un thème — sans forker le thème.
weight: 50
---

Les thèmes fournis (`juicerdocs`, `juicerblog`, `juicerchurch`,
`juicercafe`, `juicerlanding`, `juicerportfolio`) sont conçus pour qu'un site
puisse les rehabiller et les réajuster sans toucher à la source du thème. Il y a
trois couches de surcharge — utilisez celle qui convient au changement que vous
voulez faire.

## Couche 1 — surcharges de palette et de jetons via `[juicerXxx]`

Chaque thème lit une table `[juicerXxx]` de `site.toml` (où `Xxx` est le nom du
thème) et émet un bloc `<style>` en ligne dans `<head>`. Les propriétés CSS
personnalisées sont supplantées sur `:root` (clair) ou `[data-theme="dark"]`
(sombre). Le fichier CSS fourni est inchangé à travers chaque site qui utilise le
thème — seul le petit bloc en ligne diffère.

C'est la couche de thématisation la moins chère et la plus rapide — utilisez-la pour
les ajustements de couleur, les changements de police, l'élargissement de la colonne
de prose.

### Jetons de palette (les quatre thèmes)

| Clé            | Variable CSS     | Quoi |
|----------------|------------------|------|
| `brand`        | `--brand`        | Couleur de marque primaire. Accents de héros, soulignements de liens, anneaux de focus. |
| `brandStrong`  | `--brand-strong` | Variante plus sombre pour le texte mis en valeur + les titres sur fond clair. |
| `brandSoft`    | `--brand-soft`   | Fond teinté pour les états actifs, badges, citations en exergue. |
| `accent`       | `--accent`       | Couleur d'accent secondaire (souvent chaude) pour les appels à l'action et les surbrillances. |
| `leaf`         | `--leaf`         | Couleur tertiaire « vivante / fraîche » — pastilles d'événement récurrent, états de succès. |

Pour chaque jeton clair il y a une variante sombre : `brandDark`,
`brandStrongDark`, `brandSoftDark`, `accentDark`, `leafDark`. Définissez-les pour
supplanter la palette sombre codée en dur du thème sans déborder dans le mode clair.

### Jetons de typographie

| Clé         | Variable CSS    | Quoi |
|-------------|-----------------|------|
| `fontSans`  | `--font-sans`   | Pile sans-empattement du corps / de l'UI |
| `fontSerif` | `--font-serif`  | Pile à empattement (cafe / church / blog / portfolio — juicerdocs et juicerlanding sont sans-empattement seulement) |
| `fontMono`  | `--font-mono`   | Pile code / à chasse fixe |

Passez une pile de polices CSS complète incluant les replis — p. ex.
`fontSans = "'Inter', system-ui, sans-serif"`.

### Jetons de dimensionnement

La surface de dimensionnement de l'habillage diffère par thème — chacun expose les
jetons qu'il utilise réellement.

Pour `juicercafe`, `juicerchurch`, `juicerblog`, `juicerlanding`,
`juicerportfolio` :

| Clé        | Variable CSS    | Quoi |
|------------|-----------------|------|
| `measure`  | `--measure`     | Largeur max de la colonne de prose (défaut `38rem` ; juicerlanding `65ch` ; juicerportfolio `62ch`) |
| `gutter`   | `--gutter`      | Rembourrage externe autour des blocs de contenu |
| `radiusLg` | `--radius-lg`   | Grand rayon de bordure (cartes, panneaux de héros) |

Pour `juicerdocs` :

| Clé          | Variable CSS     | Quoi |
|--------------|------------------|------|
| `contentMax` | `--content-max`  | Largeur max de la colonne de prose (défaut `48rem`) |
| `sidebarW`   | `--sidebar-w`    | Largeur de la barre latérale gauche (défaut `16rem`) |
| `railW`      | `--rail-w`       | Largeur du rail de TDM droit (défaut `18rem`) |
| `radiusLg`   | `--radius-lg`    | Grand rayon de bordure |

### Exemple — réajuster discrètement juicercafe vers une palette sarcelle

```toml
[juicercafe]
brand           = "#0f766e"   # teal-700
brandStrong     = "#134e4a"   # teal-900
brandSoft       = "#ccfbf1"   # teal-100
accent          = "#7c3aed"   # violet-600
brandDark       = "#5eead4"   # teal-300 — lifted for dark bg
brandStrongDark = "#99f6e4"   # teal-200
brandSoftDark   = "rgba(94, 234, 212, 0.18)"
accentDark      = "#a78bfa"   # violet-400
fontSerif       = "'Source Serif 4', Georgia, serif"
measure         = "42rem"
```

## Couche 2 — `customCSS` : déposez votre propre feuille de style à côté du thème

Pour les surcharges qui ne tiennent pas dans une variable CSS (sélecteurs
personnalisés, nouvelles animations, widgets tiers), déposez une feuille de style
sous `static/site.css` (ou tout autre nom) et listez-la dans `site.toml` :

```toml
customCSS = ["site.css"]
```

Le partial `<head>` du thème émet chaque entrée comme un `<link rel="stylesheet">`
**après** le CSS du thème fourni, de sorte que tout ce qui est dans votre fichier
supplante tout ce qui est dans le thème. Les entrées multiples se chargent dans
l'ordre listé :

```toml
customCSS = ["fonts.css", "branding.css", "site.css"]
```

C'est la bonne couche pour :

- Charger un bloc `@font-face` pour une police personnalisée (puis pointer
  `[juicerXxx] fontSerif` dessus).
- Ajouter des widgets entièrement nouveaux que le thème ne livre pas.
- Du CSS de fournisseur / tiers (Mermaid, habillage KaTeX, etc.) qui doit se charger
  sur chaque page.

Les fichiers référencés par `customCSS` sont toujours du contenu `static/` du site ;
ils sont copiés tels quels pendant la construction.

## Couche 3 — remplacer un fichier de thème purement et simplement

Quand un site place un fichier dans `static/` avec le même chemin qu'un que le thème
livre, la **copie du site l'emporte** pendant la construction. Ainsi un site peut
livrer son propre `static/juicercafe.css` pour remplacer entièrement la feuille de
style du thème (vérifié par le test de construction `should ship theme static/
files; site static/ overwrites on path collision`).

Utilisez cette couche avec parcimonie — vous perdez les mises à jour du thème pour
tout fichier que vous remplacez. Les couches 1 et 2 couvrent presque tout besoin de
personnalisation ; ne recourez à la couche 3 que lorsque vous avez besoin de forker.

## Clés d'habillage à l'échelle du site

Clés de premier niveau de `site.toml`, distinctes des tables `[juicerXxx]` par
thème. Chaque thème les lit.

| Clé                     | Type      | Défaut  | Quoi |
|-------------------------|-----------|---------|------|
| `favicon`               | `String`  | non défini | Chemin utilisé dans `<link rel="icon">`. Le navigateur déduit le MIME de l'extension. |
| `hideJuicerCredit`      | `Boolean` | `false` | Mettez `true` pour omettre la ligne de pied de page « Built with juicer ». |
| `repoURL`               | `String`  | non défini | URL de l'hôte git. juicerdocs fait apparaître une icône GitHub dans la barre supérieure et un bouton « Star on GitHub » en page d'accueil quand défini ; les autres thèmes l'ignorent. |
| `customCSS`             | `[String]`| non défini | Liste de chemins de feuilles de style à charger après le CSS du thème fourni — voir la couche 2 ci-dessus. |
| `authorsPath`           | `String`  | `/authors/` | Préfixe d'URL pour le listing d'équipe + les archives par auteur. Voir [Données de gabarit → authorsPath](/fr/reference/template-data/#renommer-le-préfixe-d-url-authorspath). |
