package io.github.edadma.juicer

import io.github.edadma.path.Path

/** Stub. `juicer theme add` shells out to `git`, which Scala Native's
  * limited process support makes painful here. Run via the JVM target. */
def gitClone(url: String, target: Path, ref: Option[String]): Either[String, Unit] =
  Left(
    "`juicer theme add` is not implemented on Scala Native — run via the JVM target. " +
      "(Or clone the theme manually with `git clone <url> themes/<name>/`.)",
  )
