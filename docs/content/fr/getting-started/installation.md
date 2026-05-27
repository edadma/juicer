---
title: Installation
summary: Ajoutez juicer comme dépendance sbt, ou clonez le dépôt et exécutez-le depuis les sources.
weight: 10
---

## Depuis les sources (recommandé pour l'instant)

Juicer est publié depuis un dépôt unique à l'adresse <https://github.com/edadma/juicer>. En attendant la mise en place des distributions binaires, le plus simple est de cloner le dépôt et de l'exécuter avec sbt.

```bash
git clone https://github.com/edadma/juicer.git
cd juicer
sbt 'juicerJVM/run --help'
```

Vous verrez la bannière d'aide. Si sbt affiche autre chose que cette aide, consultez la page [Dépannage](/getting-started/troubleshooting/).

## Dépendance sbt (utilisation comme bibliothèque)

Juicer est surtout une *application* — l'interface en ligne de commande couvre les besoins de la plupart des utilisateurs — mais le pipeline de construction est aussi exposé comme bibliothèque. Ajoutez-le à votre `build.sbt` :

```scala
libraryDependencies += "io.github.edadma" %%% "juicer" % "0.2.0"
```

La forme `%%%` choisit le bon artefact selon la plateforme Scala que vous utilisez (`juicerJVM`, `juicerJS`, `juicerNative`).

## Prérequis

- **Scala 3.8.3** — juicer est exclusivement en Scala 3. Aucun rétroportage vers Scala 2 n'est prévu.
- **sbt 1.12.x** — les versions antérieures peuvent fonctionner ; non testées.
- **JVM 17+** — pour la cible JVM. Scala.js requiert Node 20+ ; Scala Native requiert Clang.

## Vérifier l'installation

Le test de fumée consiste à lancer `juicer build` sur le répertoire `docs/demos/minimal` du dépôt :

```bash
sbt 'juicerJVM/run build -s docs/demos/minimal'
ls docs/demos/minimal/public
```

Vous devriez voir un `index.html` et une copie de `static/`.
