package io.github.edadma.juicer

import io.github.edadma.path.Path

/** Image-variant generation: resize + reformat source images at build
  * time so themes can emit responsive `<img srcset>` / `<picture>`
  * blocks without the user pre-processing every photo by hand.
  *
  * Shared types only — the actual encoder shell-out lives in the
  * per-target backends (`generateImageVariants` in
  * `jvm/src/main/scala/...`; stubs under `native/` and `js/`). The
  * design memo argues for shell-out over a JVM-only `javax.imageio`
  * dependency: users get the freshest WebP/AVIF encoders without juicer
  * tracking upstream library releases, and the dep graph stays clean.
  *
  * The cache key is `(content-hash of source bytes, width, format)`
  * which makes builds incremental: re-running on an unchanged image
  * skips reprocessing entirely, while editing the image invalidates
  * every variant in one shot.
  */
object ImageVariants {

  /** Site-wide `[images]` config, parsed from `site.toml`. When `enabled
    * = false` (the default) every helper returns the original
    * passthrough and no encoder is invoked — the feature is strictly
    * opt-in so existing sites build byte-identically.
    */
  final case class Config(
      enabled:  Boolean,
      widths:   List[Int],
      formats:  List[String],
      quality:  Int,
      cacheDir: String,
  )

  object Config {

    /** All-off baseline. Returned when `[images]` is absent or has
      * `enabled = false`. */
    val Disabled: Config =
      Config(
        enabled  = false,
        widths   = Nil,
        formats  = List("original"),
        quality  = 80,
        cacheDir = ".image-cache",
      )

    /** Sensible defaults when `[images] enabled = true` is set without
      * any other keys. Widths target the common breakpoints; `webp`
      * comes first so browsers that support it prefer it over the
      * original. */
    val DefaultEnabled: Config =
      Config(
        enabled  = true,
        widths   = List(320, 640, 960, 1280),
        formats  = List("webp", "original"),
        quality  = 80,
        cacheDir = ".image-cache",
      )

    /** Pull the `[images]` table out of the parsed site.toml map.
      * Missing keys fall back to [[DefaultEnabled]] when the table
      * sets `enabled = true`, and to [[Disabled]] otherwise. Unknown
      * format strings are dropped silently — themes can still call
      * the helpers, they just won't get those variants. */
    def parseFromToml(siteToml: Map[String, Any]): Config = {
      siteToml.get("images") match {
        case Some(t: Map[?, ?]) =>
          val table = t.asInstanceOf[Map[Any, Any]]
              .collect { case (k: String, v) => k -> v }
          val enabled = table.get("enabled").collect {
            case b: Boolean => b
            case s: String  => s.equalsIgnoreCase("true")
          }.getOrElse(false)
          if (!enabled) Disabled
          else {
            val widths = table.get("widths") match {
              case Some(xs: Seq[?]) =>
                xs.toList.flatMap {
                  case n: Int                  => Some(n)
                  case n: Long                 => Some(n.toInt)
                  case n: java.lang.Integer    => Some(n.intValue)
                  case n: java.lang.Long       => Some(n.longValue.toInt)
                  case b: BigDecimal           => Some(b.toInt)
                  case b: java.math.BigDecimal => Some(b.intValue)
                  case s: String               => s.toIntOption
                  case _                       => None
                }.filter(_ > 0).distinct.sorted
              case _ => DefaultEnabled.widths
            }
            val formats = table.get("formats") match {
              case Some(xs: Seq[?]) =>
                xs.toList.collect { case s: String => s.trim.toLowerCase }
                  .filter(KnownFormats.contains)
                  .distinct
              case _ => DefaultEnabled.formats
            }
            val quality = table.get("quality").collect {
              case n: Int                  => n
              case n: Long                 => n.toInt
              case n: java.lang.Integer    => n.intValue
              case n: java.lang.Long       => n.longValue.toInt
              case b: BigDecimal           => b.toInt
              case b: java.math.BigDecimal => b.intValue
              case s: String               => s.toIntOption.getOrElse(DefaultEnabled.quality)
            }.map(q => math.max(1, math.min(100, q))).getOrElse(DefaultEnabled.quality)
            val cacheDir = table.get("cacheDir").collect { case s: String => s.trim }
              .filter(_.nonEmpty).getOrElse(DefaultEnabled.cacheDir)
            Config(
              enabled  = true,
              widths   = if (widths.nonEmpty) widths else DefaultEnabled.widths,
              formats  = if (formats.nonEmpty) formats else DefaultEnabled.formats,
              quality  = quality,
              cacheDir = cacheDir,
            )
          }
        case _ => Disabled
      }
    }
  }

  /** Output formats the cache understands. `original` is a literal
    * passthrough — the source bytes get copied (or symlinked-by-name)
    * into the variant slot for that width. */
  val KnownFormats: Set[String] = Set("original", "webp", "avif", "jpeg", "png")

  /** Extension juicer writes for each format. `original` resolves to
    * the source file's own extension at call time and is therefore
    * absent here. */
  val FormatExt: Map[String, String] = Map(
    "webp" -> "webp",
    "avif" -> "avif",
    "jpeg" -> "jpg",
    "png"  -> "png",
  )

  /** MIME type per format — used by `<source type="...">`. */
  val FormatMime: Map[String, String] = Map(
    "webp" -> "image/webp",
    "avif" -> "image/avif",
    "jpeg" -> "image/jpeg",
    "png"  -> "image/png",
  )

  /** One generated variant: where it lives on disk (URL-form, relative
    * to the site root) and the (width, format) pair the template needs
    * to render `<source>` + `srcset` correctly. `mime` is `""` for the
    * passthrough original — themes branch on `if v.mime` to decide
    * whether to emit a typed `<source>` element. */
  final case class Variant(width: Int, format: String, url: String, mime: String)

  /** Result of asking the backend to generate variants for one source
    * image. `original*` carry the source's URL + decoded header
    * dimensions so a theme can emit a sane `<img src=... width=...
    * height=...>` fallback even when no variants were produced (e.g.
    * the encoder was missing or the feature is disabled). */
  final case class VariantSet(
      original:       String,
      originalWidth:  Int,
      originalHeight: Int,
      variants:       List[Variant],
  )

  /** Short, content-addressed hash for cache keying. FNV-1a 64-bit, in
    * pure Scala so the same hash is available on every target —
    * `java.security.MessageDigest` is JVM/Native only and would force a
    * per-target split for what is fundamentally a cache key.
    *
    * FNV-1a is non-cryptographic but the threat model here is "the
    * same source bytes must produce the same cache slot across builds"
    * — collision resistance against adversaries is not a concern. The
    * 64-bit output rendered as 16 lowercase hex chars keeps generated
    * filenames readable without losing meaningful uniqueness inside a
    * single site's image set. */
  def contentHash(bytes: Array[Byte]): String = {
    val fnvOffset = 0xcbf29ce484222325L
    val fnvPrime  = 0x100000001b3L
    var h         = fnvOffset
    var i         = 0
    while (i < bytes.length) {
      h = (h ^ (bytes(i) & 0xffL)) * fnvPrime
      i += 1
    }
    f"$h%016x"
  }

  /** Generated variant filename — `<stem>-<width>w.<hash>.<ext>`.
    *
    * The `-<width>w` segment is human-readable enough that a developer
    * grepping the build output can pick out variants of the same source
    * at a glance. The hash is inserted between width and extension so
    * sorted directory listings still group same-source variants
    * together. */
  def variantFilename(stem: String, width: Int, ext: String, hash: String): String =
    s"$stem-${width}w.$hash.$ext"

  /** Resolve a format name to the on-disk extension for the variant,
    * given the source file's own extension. `original` returns the
    * source's extension unchanged; named formats look up [[FormatExt]]
    * and fall back to the source extension if the format is unknown
    * (defensive — should never happen post-`Config.parseFromToml`). */
  def extForFormat(format: String, srcExt: String): String =
    if (format == "original") srcExt
    else FormatExt.getOrElse(format, srcExt)

  /** MIME for a format slot, or `""` for the passthrough `original`. */
  def mimeForFormat(format: String, srcExt: String): String =
    if (format == "original") {
      // Map the source extension back to a `image/*` MIME for the
      // passthrough <source>. Stay conservative: only known web formats
      // get a typed source; anything else (TIFF, BMP, SVG) just renders
      // through the <img> fallback.
      srcExt.toLowerCase match {
        case "webp"          => "image/webp"
        case "avif"          => "image/avif"
        case "jpg" | "jpeg"  => "image/jpeg"
        case "png"           => "image/png"
        case _               => ""
      }
    } else FormatMime.getOrElse(format, "")
}
