---
title: Installation
summary: Install juicer with Homebrew, grab a release binary, or build it from source.
weight: 10
---

## Homebrew (recommended)

juicer is a tool, so it installs as one:

```bash
brew tap edadma/tap
brew trust edadma/tap
brew install edadma/tap/juicer
```

`brew trust` is Homebrew's gate on third-party taps — it trusts only its own core and cask by default, and refuses to read a formula from any other tap until you say so.

Check what you got:

```bash
juicer --version
```

## A release binary

Every release attaches a plain executable for macOS on Apple silicon and Linux on x86_64 and arm64. Download it from the [releases page](https://github.com/edadma/juicer/releases), make it executable, and put it on your `PATH`:

```bash
chmod +x juicer-*
mv juicer-* /usr/local/bin/juicer
```

`libuv` is the only shared library it needs — `brew install libuv`, or `apt install libuv1` on Debian and Ubuntu.

## From source

Any other platform builds from the repo:

```bash
git clone https://github.com/edadma/juicer.git
cd juicer
sbt juicerNative/nativeLink
```

That leaves a standalone binary under `native/target/scala-<ver>/`. To skip the binary and run straight from the build, `sbt 'juicerJVM/run --help'`. If sbt prints anything other than the help output, see [Troubleshooting](/getting-started/troubleshooting/).

Building needs **Scala 3.8.3** (juicer is Scala-3 only; no Scala 2 backport is planned), **sbt 1.12.x**, **JVM 17+**, and, for the native binary, **Clang**.

## Using juicer as a library

The build pipeline is reachable as an API — `io.github.edadma.juicer.App.build(...)` and `App.run(args)` — but **juicer is not published to Maven Central**, deliberately: nobody consumes a site generator as a build dependency. Reach it as a source dependency on the repo if you need to embed it.

## Verifying the install

The smoke test is a build of the smallest site in the repo:

```bash
juicer build -s docs/demos/minimal
ls docs/demos/minimal/public
```

You should see an `index.html` and a `static/` mirror.
