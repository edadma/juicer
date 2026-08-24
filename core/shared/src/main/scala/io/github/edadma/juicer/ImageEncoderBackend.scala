package io.github.edadma.juicer

import io.github.edadma.path.Path

/** Per-target shell-out to an image encoder (`magick`, `cwebp`,
  * `avifenc`, ...). The interface is intentionally narrow — produce one
  * variant file on disk given a source path, a target path, a target
  * width, a target format, and a quality knob — so that the per-target
  * implementation surface stays small.
  *
  * The cross-platform story:
  *
  *   - **JVM** ships a real implementation using
  *     `java.lang.ProcessBuilder` to invoke `magick` (the universal
  *     fallback) with format-specific tweaks. See
  *     `jvm/src/main/scala/.../ImageEncoderBackendImpl.scala`.
  *
  *   - **Scala Native** and **Scala.js** ship passthrough stubs that
  *     report the encoder as unavailable. The shared generator falls
  *     back to no-variant `VariantSet`s in that case — the site
  *     continues to build, originals get served, the `imageDims`
  *     builtin still produces sane `<img width=... height=...>`
  *     attributes, and no template change is required.
  *
  * When `cross_platform` grows a `runCommand` helper, the Native and
  * JS backends can be promoted to real implementations against the
  * same trait without touching the shared generator. */
trait ImageEncoderBackend {

  /** Whether this backend can actually invoke an encoder. The shared
    * generator branches on this to either generate variants (`true`) or
    * skip variant generation and emit a passthrough-only
    * [[ImageVariants.VariantSet]] (`false`). */
  def available: Boolean

  /** Encode `src` to `dst` resized to `width` pixels wide in `format`
    * (`"webp"`, `"avif"`, `"jpeg"`, `"png"`, or `"original"`).
    *
    * Returns `Right(())` on success, `Left(<reason>)` if encoding
    * failed or the requested format is unsupported. Implementations
    * MUST NOT throw — surface failures through `Left` so the
    * generator can fall back cleanly.
    *
    * `quality` is interpreted by the encoder (typically 0–100; lossy
    * formats use it directly, `png` ignores it).
    */
  def encode(
      src:     Path,
      dst:     Path,
      width:   Int,
      format:  String,
      quality: Int,
  ): Either[String, Unit]
}

object ImageEncoderBackend {

  /** A backend that always declines — `available = false`, every
    * `encode` returns `Left("encoder unavailable on this target")`.
    * Used by the Native/JS stubs and as a test seam. */
  val Unavailable: ImageEncoderBackend = new ImageEncoderBackend {
    val available: Boolean = false
    def encode(src: Path, dst: Path, width: Int, format: String, quality: Int): Either[String, Unit] =
      Left("image encoder is unavailable on this target")
  }
}
