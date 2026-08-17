---
title: Installation
summary: Installez juicer avec Homebrew, récupérez un binaire de version, ou compilez depuis les sources.
weight: 10
---

## Homebrew (recommandé)

juicer est un outil, et il s'installe comme tel :

```bash
brew tap edadma/tap
brew trust edadma/tap
brew install edadma/tap/juicer
```

`brew trust` est le garde-fou de Homebrew sur les dépôts tiers : il ne fait confiance qu'à ses propres `core` et `cask`, et refuse de lire une formule venant d'ailleurs tant que vous ne l'avez pas autorisée.

Vérifiez ce que vous avez obtenu :

```bash
juicer --version
```

## Un binaire de version

Chaque version publiée joint un exécutable autonome pour macOS sur Apple silicon et pour Linux en x86_64 et arm64. Téléchargez-le depuis la [page des versions](https://github.com/edadma/juicer/releases), rendez-le exécutable et placez-le dans votre `PATH` :

```bash
chmod +x juicer-*
mv juicer-* /usr/local/bin/juicer
```

`libuv` est la seule bibliothèque partagée dont il a besoin — `brew install libuv`, ou `apt install libuv1` sur Debian et Ubuntu.

## Depuis les sources

Toute autre plateforme se construit depuis le dépôt :

```bash
git clone https://github.com/edadma/juicer.git
cd juicer
sbt juicerNative/nativeLink
```

Cela produit un binaire autonome sous `native/target/scala-<ver>/`. Pour vous passer du binaire et exécuter directement depuis la construction : `sbt 'juicerJVM/run --help'`. Si sbt affiche autre chose que cette aide, consultez la page [Dépannage](/fr/getting-started/troubleshooting/).

La compilation requiert **Scala 3.8.3** (juicer est exclusivement en Scala 3 ; aucun rétroportage vers Scala 2 n'est prévu), **sbt 1.12.x**, **JVM 17+**, et, pour le binaire natif, **Clang**.

## Utiliser juicer comme bibliothèque

Le pipeline de construction est accessible comme API — `io.github.edadma.juicer.App.build(...)` et `App.run(args)` — mais **juicer n'est pas publié sur Maven Central**, et c'est délibéré : personne ne consomme un générateur de site comme dépendance de construction. Passez par une dépendance source sur le dépôt si vous devez l'intégrer.

## Vérifier l'installation

Le test de fumée consiste à construire le plus petit site du dépôt :

```bash
juicer build -s docs/demos/minimal
ls docs/demos/minimal/public
```

Vous devriez voir un `index.html` et une copie de `static/`.
