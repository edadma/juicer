package io.github.edadma.juicer

import io.github.edadma.path.Path

import java.io.{ByteArrayOutputStream, IOException}

/** JVM image encoder backend — shells out to `magick` (ImageMagick 7)
  * via `java.lang.ProcessBuilder` to produce one resized + reformatted
  * variant at a time.
  *
  * `magick` is the universal first choice because a single binary
  * covers PNG/JPEG/WebP/AVIF read + write on Homebrew, Debian, and
  * Fedora out of the box. If the user wants to swap to `cwebp` /
  * `avifenc` for higher-quality output later, that's a follow-up — the
  * trait is wide enough to slot in a fallback chain without touching
  * the shared generator.
  *
  * Tool availability is probed lazily on the first `encode` call (one
  * cheap `magick -version` invocation). If `magick` is not on PATH the
  * backend flips to `available = false` for the rest of the build and
  * the shared generator degrades to passthrough originals — the build
  * still succeeds and surfaces a single advisory line on stderr,
  * rather than failing every variant with the same "magick not found"
  * stack trace. */
def newImageEncoderBackend(): ImageEncoderBackend = JvmImageEncoderBackend

private object JvmImageEncoderBackend extends ImageEncoderBackend {

  // The probe runs at most once per JVM. `null` = not probed yet;
  // `java.lang.Boolean.TRUE/FALSE` = result. We use the boxed type so
  // we can distinguish "not yet probed" from "probed and false".
  @volatile private var probed: java.lang.Boolean = null

  private def probe(): Boolean = {
    if (probed != null) probed.booleanValue
    else {
      val ok =
        try {
          val pb   = new java.lang.ProcessBuilder("magick", "-version")
          pb.redirectErrorStream(true)
          val proc = pb.start()
          val code = proc.waitFor()
          code == 0
        } catch {
          case _: IOException => false
          case _: Throwable   => false
        }
      probed = java.lang.Boolean.valueOf(ok)
      if (!ok) {
        Console.err.println(
          "juicer: `magick` (ImageMagick 7) not found on PATH — image variant generation disabled for this build. " +
            "Install with `brew install imagemagick` / `apt install imagemagick` / `dnf install ImageMagick`, " +
            "or set `[images] enabled = false` in site.toml to silence this message.",
        )
      }
      ok
    }
  }

  def available: Boolean = probe()

  def encode(
      src:     Path,
      dst:     Path,
      width:   Int,
      format:  String,
      quality: Int,
  ): Either[String, Unit] = {
    if (!available) Left("magick is not available on PATH")
    else {
      // The output format is driven by the destination file's
      // extension. `magick input.jpg -resize 640x output.webp` produces
      // a 640px-wide WebP at the requested quality; the source format
      // is inferred from input bytes by magick (no `-format` flag
      // needed, even when extensions disagree).
      //
      // For `original` (no format conversion, just a resize), the dst
      // already carries the source's extension — magick re-encodes in
      // the same format with the same `-quality` knob.
      //
      // PNG is treated as lossless and ignores `-quality` (magick maps
      // it to compression level + filter, both of which we leave at
      // magick defaults); other formats use `-quality` directly.
      val cmd = scala.collection.mutable.ListBuffer[String](
        "magick",
        src.toString,
        // `-strip` drops EXIF / colour profile metadata. Most user
        // photos carry orientation + ICC profile, neither of which is
        // useful at thumbnail sizes and which can balloon file size.
        // Authors who need the metadata can opt out in a follow-up
        // (e.g. `[images] stripMetadata = false`) — defaulting to
        // strip mirrors what every blog-grade pipeline does.
        "-strip",
        // `-resize WxH` with no height keeps aspect ratio and never
        // upscales (the `>` modifier — see ImageMagick geometry docs).
        "-resize", s"${width}x>",
      )
      // Quality knob — skip for png (lossless) so we don't accidentally
      // turn on a degraded encoding path inside magick.
      if (format != "png" && format != "original") {
        cmd += "-quality"
        cmd += quality.toString
      } else if (format == "original") {
        // Original format reuses the source's extension. Honour
        // quality only for lossy source formats — png/gif/webp-lossless
        // sources get the magick-default encoder settings instead.
        val ext = dst.toString.split('.').lastOption.map(_.toLowerCase).getOrElse("")
        if (ext == "jpg" || ext == "jpeg" || ext == "webp" || ext == "avif") {
          cmd += "-quality"
          cmd += quality.toString
        }
      }
      cmd += dst.toString

      try {
        val pb = new java.lang.ProcessBuilder(cmd.toArray*)
        pb.redirectErrorStream(true)
        val proc   = pb.start()
        // Drain stdout/stderr so a chatty encoder doesn't block on its
        // own pipe. We only echo it on failure (success is silent so a
        // 50-image build doesn't drown the log).
        val out    = new ByteArrayOutputStream()
        val in     = proc.getInputStream
        val buf    = new Array[Byte](8192)
        var n      = in.read(buf)
        while (n > 0) {
          out.write(buf, 0, n)
          n = in.read(buf)
        }
        val code = proc.waitFor()
        if (code == 0) Right(())
        else Left(s"magick exited with status $code: ${out.toString.trim}")
      } catch {
        case e: IOException => Left(s"could not run magick: ${e.getMessage}")
        case e: Throwable   => Left(s"magick invocation failed: ${e.getMessage}")
      }
    }
  }
}
