package io.github.edadma.juicer

import io.github.edadma.path.Path

import scala.collection.mutable

/** Build-time variant generator: given a source image path, produce
  * resized + reformatted copies on disk according to a [[ImageVariants.Config]],
  * and return a [[ImageVariants.VariantSet]] describing the result for
  * downstream templates.
  *
  * The generator is cross-platform — the only per-target piece is the
  * [[ImageEncoderBackend]] supplied at construction time. When the
  * backend reports `available = false` (Native/JS today, or JVM
  * without `magick` on PATH), generation degrades gracefully to a
  * passthrough-only [[ImageVariants.VariantSet]] carrying the
  * original's URL + decoded dimensions but no variants. Themes can
  * branch on `if vs.variants` to decide whether to emit a `<picture>`
  * wrapper or fall back to a plain `<img>`.
  *
  * Caching contract: a content-addressed [[ImageVariants.contentHash]]
  * is baked into every generated filename, so re-running the build on
  * unchanged source bytes skips encoding (the variant file already
  * exists on disk) and editing the source invalidates every variant
  * in one shot. The hash sits between `<width>w` and the format
  * extension — `photo-640w.a3f9b21c0d8e4571.webp` — so sorted
  * directory listings still group same-source variants together for a
  * human grepping the build output. */
class ImageVariantGenerator(
    config:   ImageVariants.Config,
    backend:  ImageEncoderBackend,
    /** Site src directory — used to locate `static/` images. */
    srcRoot:  Path,
    /** Site dst directory — variants are written under `dst / cacheDir`. */
    dstRoot:  Path,
) {

  /** Per-build memoization: the same image referenced 50 times from
    * 50 layouts should generate variants once, not 50 times. Keyed by
    * normalized URL (the leading `/` stripped, since the resolver
    * treats `/img/x.jpg` and `img/x.jpg` as the same lookup). */
  private val cache: mutable.HashMap[String, ImageVariants.VariantSet] =
    mutable.HashMap.empty

  /** The on-disk root for generated variants. Created lazily on first
    * write — disabled / no-variant sites never produce this directory. */
  private lazy val cacheRoot: Path = {
    val parts = config.cacheDir.split('/').filter(_.nonEmpty).toList
    parts.foldLeft(dstRoot)(_ / _)
  }

  /** Resolve a path argument as it appears in templates. Mirrors
    * `juicerUrlBuiltins.resolveImagePath` in App.scala — preferring
    * the built `dst` tree (handles generated images + theme-shipped
    * statics that have already been copied) and falling back to the
    * `src` tree (handles raw source images that have not been copied
    * yet, e.g. when generating from inside a templating pass).
    *
    * When `bundleSrc` is non-empty, a bare (no-leading-`/`) path tries
    * the page's bundle source dir first — this is what lets
    * `imageVariants 'photo.jpg'` resolve inside a page bundle without
    * an absolute URL. Leading-`/` paths always go straight to the
    * dst→src fallback chain so authors can opt out per-call. */
  private def resolveSource(arg: String, bundleSrc: String): Option[Path] = {
    val trimmed = arg.trim
    if (trimmed.isEmpty || trimmed.startsWith("http://") || trimmed.startsWith("https://")
        || trimmed.startsWith("data:") || trimmed.startsWith("//")) None
    else {
      if (!trimmed.startsWith("/") && bundleSrc.nonEmpty) {
        val bundle = Path(bundleSrc)
        val parts  = trimmed.split('/').filter(_.nonEmpty).toList
        if (parts.nonEmpty) {
          val bp = parts.foldLeft(bundle)(_ / _)
          if (bp.exists) return Some(bp)
        }
      }
      val rel   = if (trimmed.startsWith("/")) trimmed.drop(1) else trimmed
      val parts = rel.split('/').filter(_.nonEmpty).toList
      if (parts.isEmpty) None
      else {
        val dstP = parts.foldLeft(dstRoot)(_ / _)
        if (dstP.exists) Some(dstP)
        else {
          val srcP = parts.foldLeft(srcRoot)(_ / _)
          if (srcP.exists) Some(srcP) else None
        }
      }
    }
  }

  /** Split a path argument into `(stem, ext)` for variant filename
    * construction. `/img/photos/sunrise.jpg` → `("sunrise", "jpg")`.
    * Returns `("file", "")` when the argument has no extension at
    * all — the generator's behaviour for extensionless files is
    * "treat as opaque, no format conversion". */
  private def stemAndExt(arg: String): (String, String) = {
    val trimmed = arg.trim
    val rel     = if (trimmed.startsWith("/")) trimmed.drop(1) else trimmed
    val last    = rel.split('/').lastOption.getOrElse("file")
    val dot     = last.lastIndexOf('.')
    if (dot <= 0) (last, "")
    else (last.substring(0, dot), last.substring(dot + 1).toLowerCase)
  }

  /** Public URL for a generated variant — anchored at the site root so
    * templates can drop it straight into `srcset`. Mirrors the URL
    * layout that juicer's existing `static/` handling produces. */
  private def variantUrl(stem: String, width: Int, ext: String, hash: String): String = {
    val parts = config.cacheDir.split('/').filter(_.nonEmpty).toList
    val name  = ImageVariants.variantFilename(stem, width, ext, hash)
    ("/" :: parts ::: List(name)).mkString("/").replace("//", "/")
  }

  /** Generate (or recall from cache) all variants for one source URL.
    *
    * Returns an empty-variants set when:
    *   - The feature is disabled in config.
    *   - The encoder backend is unavailable on this target.
    *   - The source image can't be resolved (missing file, external URL).
    *   - The source header can't be decoded (corrupted or unsupported format).
    *
    * In every degraded case the `VariantSet.original` URL still
    * reflects the input argument so themes can emit a sensible
    * `<img src=...>` fallback. */
  def variantsFor(arg: String, bundleSrc: String = ""): ImageVariants.VariantSet = {
    // Cache by (bundleSrc, arg) — the same bare path resolved from
    // two different bundles is two different source images, and
    // memoizing only by arg would silently alias them.
    val key = bundleSrc + "|" + arg.trim
    cache.get(key) match {
      case Some(vs) => vs
      case None =>
        val vs = compute(arg.trim, bundleSrc)
        cache.put(key, vs)
        vs
    }
  }

  private def compute(arg: String, bundleSrc: String): ImageVariants.VariantSet = {
    val passthroughUrl =
      if (arg.startsWith("/") || arg.startsWith("http://") || arg.startsWith("https://")
          || arg.startsWith("data:") || arg.startsWith("//")) arg
      else "/" + arg

    resolveSource(arg, bundleSrc) match {
      case None => ImageVariants.VariantSet(passthroughUrl, 0, 0, Nil)
      case Some(srcPath) =>
        val srcBytes = try srcPath.readBytes catch { case _: Throwable => Array.empty[Byte] }
        val dims     = ImageDimensions.fromBytes(srcBytes).getOrElse(ImageDimensions.Dims(0, 0))

        // Feature off, no encoder, or unreadable source → just the
        // passthrough original, no <source> rows.
        if (!config.enabled || !backend.available || srcBytes.isEmpty)
          ImageVariants.VariantSet(passthroughUrl, dims.width, dims.height, Nil)
        else {
          val hash         = ImageVariants.contentHash(srcBytes)
          val (stem, ext)  = stemAndExt(arg)
          // Skip widths that are >= the source's own width — upscaling
          // a 800px photo to 1280px just makes a bigger blurry file.
          // Always include the source's exact width (so the fallback
          // <img> can point at a same-size variant when needed).
          val targetWidths =
            if (dims.width <= 0) config.widths
            else config.widths.filter(_ < dims.width) :+ dims.width

          val produced = scala.collection.mutable.ListBuffer.empty[ImageVariants.Variant]
          for (format <- config.formats; w <- targetWidths.distinct) {
            val outExt   = ImageVariants.extForFormat(format, ext)
            val outName  = ImageVariants.variantFilename(stem, w, outExt, hash)
            val outPath  = config.cacheDir.split('/').filter(_.nonEmpty)
                             .foldLeft(dstRoot)(_ / _) / outName
            if (!outPath.exists) {
              outPath.parent.foreach(_.createDirectories())
              // `original` at the exact source width is a 1:1 copy —
              // no encoder round-trip, just a byte copy. Cuts
              // build time meaningfully on photo-heavy sites and
              // preserves source bytes exactly.
              if (format == "original" && w == dims.width) {
                try outPath.writeBytes(srcBytes)
                catch { case _: Throwable => () }
              } else {
                backend.encode(srcPath, outPath, w, format, config.quality) match {
                  case Right(()) => ()
                  case Left(reason) =>
                    Console.err.println(s"juicer: image variant skipped ($arg @ ${w}w/$format): $reason")
                }
              }
            }
            if (outPath.exists) {
              produced += ImageVariants.Variant(
                width  = w,
                format = format,
                url    = variantUrl(stem, w, outExt, hash),
                mime   = ImageVariants.mimeForFormat(format, ext),
              )
            }
          }
          // Order matters for `<picture>`: most-modern format first
          // (browsers pick the first `<source>` they can decode).
          // Variants within a format are sorted ascending so the
          // smallest is the default `srcset` baseline.
          val ordered = produced.toList.sortBy(v =>
            (config.formats.indexOf(v.format), v.width),
          )
          ImageVariants.VariantSet(passthroughUrl, dims.width, dims.height, ordered)
        }
    }
  }

  /** Build a CSS-friendly `srcset` string for the variants of a single
    * format: `"foo-320w.<hash>.webp 320w, foo-640w.<hash>.webp 640w"`. */
  def srcsetFor(arg: String, format: String, bundleSrc: String = ""): String = {
    val vs = variantsFor(arg, bundleSrc)
    vs.variants.filter(_.format == format).map(v => s"${v.url} ${v.width}w").mkString(", ")
  }
}
