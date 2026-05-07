package io.github.edadma.juicer

import io.github.edadma.path.Path

/** Stub. juicer's `serve` command is JVM-only; the cross-platform story
  * is a separate concern (see the project README — it'll likely live in
  * a future cross-platform `microserve`).
  */
def serve(
    root:       Path,
    host:       String       = "localhost",
    port:       Int          = 8080,
    liveReload: Boolean      = false,
    watchRoot:  Path         = null,
    rebuild:    () => Boolean = () => false,
): Unit =
  problem(
    "`juicer serve` is not implemented on Scala Native — run via the JVM target. " +
      "(A cross-platform server library is on the roadmap; until then, use `juicerJVM/run serve …`.)",
  )
