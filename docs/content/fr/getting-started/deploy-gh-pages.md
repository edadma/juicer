---
title: Déployer sur GitHub Pages
summary: Un workflow GitHub Actions prêt à copier-coller qui construit votre site juicer à chaque poussée sur `stable` et le publie sur GitHub Pages.
weight: 40
---

Cette recette est le flux que [juicer.build](https://juicer.build/)
utilise lui-même (et le même flux que [la documentation de
squiggly](https://edadma.github.io/squiggly/)). Elle construit votre site
avec juicer dans un exécuteur GitHub Actions et pousse le résultat
vers GitHub Pages — pas de service de construction tiers, pas d'étape
de déploiement distincte, pas de gestion manuelle d'une branche
`gh-pages`.

[= note =]
Cette page suppose que vous avez déjà un site juicer fonctionnel qui se
construit localement — que `sbt 'juicerJVM/run build -s docs'` produit une
arborescence de HTML qui vous satisfait. Si vous n'en êtes pas encore là,
parcourez d'abord le [Démarrage rapide](/fr/getting-started/quickstart/).
[= /note =]

## La forme du déploiement

```
push sur `stable`  ──▶  tâche build  ──▶  tâche deploy  ──▶  https://<owner>.github.io/<repo>/
                        (sbt build)       (actions/deploy-pages)
```

Deux tâches GitHub Actions, un seul fichier de workflow. La tâche `build`
exécute `juicer build` et téléverse le site rendu comme artefact Pages ;
la tâche `deploy`, conditionnée à la branche `stable`, prend cet
artefact et le publie dans l'environnement `github-pages`.
Les poussées sur d'autres branches exécutent quand même `build` (un
contrôle de cohérence), mais l'artefact est téléversé comme fichier
téléchargeable plutôt que publié.

## Prérequis

[= steps =]
## Un dépôt git sur GitHub contenant la source de votre site

N'importe où sous la racine du dépôt convient — le workflow suppose
`docs/` (donc `sbt 'juicerJVM/run build -s docs'` fonctionne). Ajustez
le chemin `-s` si votre source réside ailleurs.

## Une branche durable nommée `stable` pour les déploiements de production

Le workflow ne déploie que sur les poussées vers `stable`. Utilisez une
branche `dev` (ou `main`) distincte pour le travail en cours, et fusionnez
dans `stable` quand vous voulez publier. Cela évite que chaque `git push`
déclenche un déploiement.

Si vous préférez déployer directement depuis `main`, remplacez `stable` →
`main` dans le `on.push.branches` du workflow et la garde `if:` de la
tâche de déploiement.

## GitHub Pages activé avec « GitHub Actions » comme source

Dans votre dépôt : **Settings → Pages → Source : « GitHub
Actions »**. Régler ceci sur « Deploy from a branch » ne
fonctionnera pas — le workflow utilise le chemin de déploiement fondé
sur Actions.
[= /steps =]

## Le fichier de workflow

Déposez ceci dans `.github/workflows/docs.yml` :

```yaml
# Build & deploy <https://<owner>.github.io/<repo>/>.
#
#   Push to `stable`       → builds and deploys to GitHub Pages.
#   PR targeting `stable`  → builds (sanity check), no deploy.
#   workflow_dispatch      → manual trigger from the Actions tab.

name: Docs

on:
  push:
    branches: ["stable"]
  pull_request:
    branches: ["stable"]
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: "docs-${{ github.ref }}"
  cancel-in-progress: false

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      # actions/setup-java does NOT ship sbt. Install it via the
      # official sbt-maintained action.
      - name: Set up sbt
        uses: sbt/setup-sbt@v1

      - name: Cache sbt
        uses: actions/cache@v4
        with:
          path: |
            ~/.sbt
            ~/.cache/coursier
          key: ${{ runner.os }}-sbt-${{ hashFiles('**/build.sbt', '**/project/**') }}
          restore-keys: ${{ runner.os }}-sbt-

      - name: Build docs site
        run: sbt 'juicerJVM/run build -s docs -d _site -b https://<owner>.github.io/<repo>/'

      - name: Upload site artifact
        uses: actions/upload-artifact@v4
        with:
          name: docs-site
          path: _site

      - name: Upload Pages artifact
        if: github.ref == 'refs/heads/stable'
        uses: actions/upload-pages-artifact@v3
        with:
          path: _site

  deploy:
    if: github.ref == 'refs/heads/stable'
    needs: build
    runs-on: ubuntu-latest
    permissions:
      pages: write
      id-token: write
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    steps:
      - name: Deploy to GitHub Pages
        id: deployment
        uses: actions/deploy-pages@v4
```

Remplacez `<owner>` et `<repo>` dans le drapeau `-b` de l'étape
`Build docs site` (p. ex. `-b https://edadma.github.io/juicer/`). Se
tromper ici est le bogue de déploiement le plus courant — chaque lien
que juicer émet est calculé par rapport à `baseURL`, donc une valeur
périmée envoie les visiteurs vers le mauvais hôte.

[= warning =]
**La barre oblique finale compte.** `baseURL = "https://example.com/repo"`
et `baseURL = "https://example.com/repo/"` produisent des URL
différentes dans certains cas limites (notamment le sitemap et les balises
`og:url`). Incluez toujours la barre oblique finale.
[= /warning =]

## Ce que fait chaque étape

[= steps =]
## Checkout

Récupère le dépôt sur l'exécuteur. La profondeur de checkout par défaut
convient — juicer n'a besoin de l'historique git pour rien.

## Set up JDK 17

Juicer est en Scala 3 et tourne sur JVM 17+. `temurin` est la distribution
OpenJDK libre standard.

## Set up sbt

`actions/setup-java` n'installe que le JDK. sbt est un outil distinct
et est fourni par `sbt/setup-sbt@v1`. Cette étape n'est pas évidente ;
copiez-la telle quelle.

## Cache sbt

La partie lente d'une construction juicer à froid est le téléchargement
par sbt de ses plugins et dépendances depuis Maven Central. Mettre en
cache `~/.sbt` et `~/.cache/coursier`, indexé sur `build.sbt` + les
fichiers de `project/`, fait passer une construction typique de
~3 minutes à ~30 secondes.

## Build docs site

L'appel juicer proprement dit. `-s docs` pointe vers le répertoire source ;
`-d _site` place la sortie là où l'étape suivante l'attend ; `-b`
définit l'URL de base absolue pour les permaliens, les sitemaps et les
balises `og:url`.

## Upload site artifact

Téléverse le site rendu comme artefact GitHub Actions ordinaire
nommé `docs-site`. Les constructions de PR — qui ne déploient pas —
le produisent quand même, ce qui vous permet de le télécharger depuis
l'onglet Actions et d'inspecter la sortie avant de fusionner.

## Upload Pages artifact

Le téléversement spécifique à Pages, conditionné à `stable`. Les artefacts
Pages sont distincts des artefacts ordinaires — ils passent par un
pipeline distinct fondé sur TAR que l'action `deploy-pages` consomme.

## Deploy to GitHub Pages

Lit l'artefact Pages et le publie dans l'environnement `github-pages`,
également conditionné à `stable`. L'URL de déploiement apparaît
dans l'interface Actions et dans la page des paramètres Pages.
[= /steps =]

## Pages de projet vs pages d'utilisateur/organisation

Il y a deux formes d'URL GitHub Pages, et le drapeau `-b` doit
correspondre à celle que vous utilisez :

| Type de site         | Nommage du dépôt          | `baseURL`                              |
|----------------------|---------------------------|----------------------------------------|
| **Pages de projet**  | n'importe quel nom (`juicer`) | `https://<owner>.github.io/<repo>/` |
| **Pages d'utilisateur** | `<owner>.github.io`     | `https://<owner>.github.io/`           |
| **Pages d'organisation** | `<org>.github.io`      | `https://<org>.github.io/`             |

Les pages de projet ont un préfixe de chemin (`/<repo>/`) ; les pages
d'utilisateur/organisation non. Trompez-vous et chaque lien interne
renverra une 404. Lancez `juicer config -s docs -b <votre-url>`
localement pour afficher le `baseURL` résolu et revérifier avant de
pousser.

## Domaine personnalisé (façon `juicer.build`)

Pour déployer sur un domaine que vous possédez — `docs.example.com`,
`example.com`, ou autre — étendez le workflow avec deux
petites choses :

[= steps =]
## Ajoutez une écriture de fichier `CNAME` au workflow

Après l'étape de construction, avant le téléversement Pages, ajoutez une ligne :

```yaml
- name: Write CNAME
  if: github.ref == 'refs/heads/stable'
  run: echo 'docs.example.com' > _site/CNAME
```

GitHub Pages lit `CNAME` dans l'artefact déployé et règle la liaison
de domaine personnalisé à partir de lui. Sans ce fichier, GitHub
continue de servir la page à `<owner>.github.io/<repo>/`.

## Pointez le DNS vers GitHub Pages

Pour un **sous-domaine** (`docs.example.com`), définissez un enregistrement CNAME :

| Hôte            | Type   | Valeur                 |
|-----------------|--------|------------------------|
| `docs`          | CNAME  | `<owner>.github.io`    |

Pour un **domaine apex** (`example.com`), définissez quatre enregistrements A (et
éventuellement quatre AAAA pour IPv6 — GitHub publie le jeu v6
en parallèle du v4) :

```
185.199.108.153
185.199.109.153
185.199.110.153
185.199.111.153
```

## Activez HTTPS

Dans le dépôt : **Settings → Pages → Custom domain**, renseignez le
domaine, attendez que le contrôle DNS passe au vert (de quelques secondes à
quelques minutes selon le TTL). GitHub émet alors automatiquement un
certificat Let's Encrypt (encore quelques minutes). Enfin, cochez **Enforce
HTTPS**.

## Réglez `baseURL` sur le domaine personnalisé

Mettez à jour le drapeau `-b` de l'étape `Build docs site` vers
`https://docs.example.com/` (ou quel que soit votre domaine). Sur
les dépôts de projet avec domaine personnalisé, le préfixe de chemin
disparaît — votre URL n'a plus `/<repo>/`.
[= /steps =]

## Aperçus de pull-request

Le workflow tel qu'écrit téléverse `docs-site` comme artefact ordinaire
à chaque PR — vous pouvez le télécharger depuis l'onglet Actions et le servir
localement :

```bash
unzip docs-site.zip -d /tmp/preview
cd /tmp/preview
python3 -m http.server 8000
```

Pour de véritables URL d'aperçu par PR (chaque branche a sa propre
`<branch>.<project>.pages.dev`), remplacez GitHub Pages par
**Cloudflare Pages** : connectez le dépôt à un projet Cloudflare Pages,
réglez la commande de construction sur
`sbt 'juicerJVM/run build -s docs -d _site -b $CF_PAGES_URL'`,
et le répertoire de sortie sur `_site`. Cloudflare attribue des URL
d'aperçu automatiquement ; la production pointe vers votre domaine personnalisé.

## Dépannage

### Le workflow échoue à `Upload Pages artifact` avec « Pages is not enabled »

Vous n'avez pas encore activé Pages. **Settings → Pages → Source :
« GitHub Actions »**. Le premier déploiement après activation peut prendre
une minute ou deux pour que l'environnement s'enregistre.

### Le site se construit mais chaque lien renvoie une 404

`baseURL` est erroné. Lancez
`sbt 'juicerJVM/run config -s docs -b <votre-url>'` localement et
inspectez la valeur `baseURL` — elle doit correspondre à ce que les visiteurs tapent
dans le navigateur. Erreurs courantes : barre oblique finale manquante, mauvais
préfixe `/<repo>/` pour les déploiements en pages d'utilisateur.

### Le CSS et le JS se chargent, mais les images renvoient une 404

Les chemins d'image dans votre markdown sont absolus (`/img/x.png`) mais
votre site est sous un préfixe de chemin de projet (`/<repo>/`). Deux
correctifs :

- Utilisez l'assistant de gabarit `relURL` sur chaque lien/image émis par
  les gabarits — `<img src="{{ relURL '/img/x.png' }}">`.
- Dans le corps markdown, écrivez les chemins *sans* la barre oblique
  initiale (`img/x.png`) et laissez le moteur de rendu markdown les
  calculer par rapport à la page.

### La construction réussit localement mais échoue en CI

Deux coupables habituels :

- **Version de sbt** : assurez-vous que votre `project/build.properties`
  local fixe une version de sbt que le `sbt/setup-sbt@v1` de l'exécuteur
  connaît. La dernière 1.x.x convient toujours.
- **Version du JDK** : la CI utilise `java-version: "17"`. Si vous construisez
  localement avec le JDK 21, vérifiez l'utilisation d'API propres à Java
  21 et fixez votre JDK local à 17 pour la parité.

### Les déploiements restent bloqués à « Deploy to GitHub Pages »

Les déploiements concurrents font la queue. Vérifiez **Actions → exécutions
récentes** — une exécution antérieure est probablement encore en cours. Le bloc
`concurrency:` empêche l'empilement des déploiements depuis la même référence,
mais les déploiements de références croisées (p. ex. `stable` + une poussée de
tag en même temps) attendent leur tour.

## Voir aussi

- [DEPLOY.md](https://github.com/edadma/juicer/blob/main/DEPLOY.md) dans le
  dépôt juicer — le fichier décrivant le déploiement de juicer.build lui-même. La forme
  est identique à cette page ; les différences sont notées dans DEPLOY.md.
- [Documentation GitHub Pages](https://docs.github.com/en/pages) pour les détails
  côté plateforme (HTTPS, CNAME, domaines personnalisés, limites de requêtes).
