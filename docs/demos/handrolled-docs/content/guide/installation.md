---
title: Installation
---

# Installation

Add juicer to your `build.sbt`:

```scala
libraryDependencies += "io.github.edadma" %% "juicer" % "0.1.0"
```

Or run via sbt directly without an install:

```
sbt 'juicerJVM/run build --source ./mysite'
```

## Requirements

- Scala 3.8.3 or later
- sbt 1.12+ for building from source
- Node.js (only if you build for Scala.js — the JS target is mostly for
  testing parity, not for running the SSG itself)
