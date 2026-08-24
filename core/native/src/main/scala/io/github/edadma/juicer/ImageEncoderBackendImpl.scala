package io.github.edadma.juicer

/** Scala Native image encoder backend stub.
  *
  * Process spawning in Scala Native is doable via `scala.sys.process`
  * (since SN 0.5) or via direct POSIX `fork`/`execvp`, but neither is
  * threaded into the `cross_platform` library yet. Until that lands,
  * variant generation is JVM-only — on Native the shared generator
  * sees `available = false` and degrades cleanly to passthrough
  * originals. The site still builds; only the variant-emission
  * branches of `<picture>` shortcodes become no-ops.
  *
  * To promote this to a real implementation later: replace the body
  * with a `scala.sys.process.Process(...)` invocation matching the
  * JVM backend in `jvm/src/main/scala/.../ImageEncoderBackendImpl.scala`. */
def newImageEncoderBackend(): ImageEncoderBackend = ImageEncoderBackend.Unavailable
