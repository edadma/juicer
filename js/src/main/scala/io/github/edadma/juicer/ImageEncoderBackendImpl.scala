package io.github.edadma.juicer

/** Scala.js image encoder backend stub.
  *
  * Node has `child_process.spawnSync` which would be the natural
  * implementation here, but the shim isn't in `cross_platform` yet and
  * juicer's JS target is primarily a library-level guarantee (the CLI
  * is JVM-launched). For now, this stub reports the encoder as
  * unavailable; the shared generator then degrades to passthrough
  * originals — site builds still succeed, only the explicit
  * `<picture>` `<source>` rows go quiet.
  *
  * To promote later: implement against a `child_process` facade
  * (synchronous `spawnSync` is the simplest match for the trait). */
def newImageEncoderBackend(): ImageEncoderBackend = ImageEncoderBackend.Unavailable
