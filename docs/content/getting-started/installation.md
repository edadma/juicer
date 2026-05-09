---
title: Installation
summary: Add juicer as an sbt dependency or check out the repo and run from source.
weight: 10
---

## From source (recommended for now)

Juicer is published from a single repo at <https://github.com/edadma/juicer>. Until binary releases are wired up, the simplest path is to clone and run with sbt.

```bash
git clone https://github.com/edadma/juicer.git
cd juicer
sbt 'juicerJVM/run --help'
```

You'll see the usage banner. If sbt prints anything other than the help output, see [Troubleshooting](/getting-started/troubleshooting/).

## sbt dependency (library use)

Juicer is mostly an *application* — the CLI does what most users need — but the build pipeline is exposed as a library too. Add it to your `build.sbt`:

```scala
libraryDependencies += "io.github.edadma" %%% "juicer" % "0.1.0"
```

The `%%%` form picks the right artifact for whichever Scala platform you're on (`juicerJVM`, `juicerJS`, `juicerNative`).

## Requirements

- **Scala 3.8.3** — juicer is Scala-3 only. No Scala 2 backport planned.
- **sbt 1.12.x** — earlier versions might work; not tested.
- **JVM 17+** — for the JVM target. Scala.js needs Node 20+; Scala Native needs Clang.

## Verifying the install

The smoke test is `juicer build` against the `examples/minimal` directory in the repo:

```bash
sbt 'juicerJVM/run build -s examples/minimal'
ls examples/minimal/public
```

You should see an `index.html` and a `static/` mirror.
