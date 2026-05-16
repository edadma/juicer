package io.github.edadma.juicer

import io.github.edadma.path.Path

/** Build-time asset pipeline: walk a list of declared entries from the
  * site config, invoke the configured backend (`sass`, `esbuild`),
  * optionally content-hash the output filenames, and produce a manifest
  * the `asset` template builtin can look up.
  *
  * Cross-platform shape mirrors [[ImageVariantGenerator]]: the
  * [[AssetBuilderBackend]] is supplied at construction time (real on
  * JVM, stub on Native/JS), the rest is shared Scala. When a backend
  * reports unavailable, the pipeline degrades to a verbatim copy of
  * the source file — the URL still resolves so templates don't break,
  * and the missing-tool advisory has already gone to stderr from the
  * backend's probe.
  *
  * Fingerprinting reuses [[ImageVariants.contentHash]] (FNV-1a 64-bit).
  * It's non-cryptographic but the threat model is "same bytes →
  * same URL across builds", which is what FNV does well. */
object AssetPipeline {

  /** One declared pipeline entry: a Sass compile, an esbuild bundle,
    * or a verbatim copy. `logical` is the manifest key templates use
    * (`{{ asset 'foo.css' }}`); `output` is the on-disk destination
    * URL — `/css/foo.css`, leading slash is the site root. */
  sealed trait Entry { def logical: String; def output: String; def input: String }
  final case class SassEntry(logical: String, input: String, output: String, minify: Boolean)    extends Entry
  final case class EsbuildEntry(logical: String, input: String, output: String, minify: Boolean) extends Entry
  final case class CopyEntry(logical: String, input: String, output: String)                     extends Entry

  /** Site-wide `[assets]` config block. Disabled by default — sites
    * that don't opt in get byte-identical output to before this
    * feature shipped. */
  final case class Config(
      enabled:     Boolean,
      fingerprint: Boolean,
      entries:     List[Entry],
  )

  object Config {

    val Disabled: Config = Config(enabled = false, fingerprint = false, entries = Nil)

    /** Parse the `[assets]` block from the parsed site.toml map.
      *
      * Recognised top-level keys:
      *   - `enabled` (bool, default false)
      *   - `fingerprint` (bool, default false)
      *
      * Recognised entry tables:
      *   - `[[assets.sass]]   { input = "...", output = "...", minify = bool }`
      *   - `[[assets.esbuild]] { input = "...", output = "...", minify = bool }`
      *   - `[[assets.copy]]    { input = "...", output = "..." }`
      *
      * Sites that only want one or two entries can also use the
      * inline-table shorthand under the `[assets]` table:
      *   - `[assets] sass = { input = "...", output = "..." }`
      *   - `[assets] esbuild = { input = "...", output = "..." }`
      * which is exactly equivalent to a single-element list.
      *
      * `logical` defaults to the basename of `output` (`/css/foo.css`
      * → `foo.css`) so templates can use the friendly form
      * `{{ asset 'foo.css' }}` without a separate key. */
    def parseFromToml(siteToml: Map[String, Any]): Config = {
      siteToml.get("assets") match {
        case Some(t: Map[?, ?]) =>
          val table = t.asInstanceOf[Map[Any, Any]]
              .collect { case (k: String, v) => k -> v }
          val enabled = table.get("enabled").collect {
            case b: Boolean => b
            case s: String  => s.equalsIgnoreCase("true")
          }.getOrElse(false)
          if (!enabled) Disabled
          else {
            val fingerprint = table.get("fingerprint").collect {
              case b: Boolean => b
              case s: String  => s.equalsIgnoreCase("true")
            }.getOrElse(false)
            val entries = collectEntries(table)
            Config(enabled = true, fingerprint = fingerprint, entries = entries)
          }
        case _ => Disabled
      }
    }

    private def collectEntries(table: Map[String, Any]): List[Entry] = {
      val buf = scala.collection.mutable.ListBuffer.empty[Entry]
      for (kind <- List("sass", "esbuild", "copy")) {
        table.get(kind) match {
          case Some(xs: Seq[?]) =>
            xs.foreach {
              case m: Map[?, ?] => parseOne(kind, m.asInstanceOf[Map[Any, Any]]).foreach(buf += _)
              case _            => ()
            }
          case Some(m: Map[?, ?]) =>
            parseOne(kind, m.asInstanceOf[Map[Any, Any]]).foreach(buf += _)
          case _ => ()
        }
      }
      buf.toList
    }

    private def parseOne(kind: String, raw: Map[Any, Any]): Option[Entry] = {
      val m = raw.collect { case (k: String, v) => k -> v }
      val input  = m.get("input").collect { case s: String => s.trim }.getOrElse("")
      val output = m.get("output").collect { case s: String => s.trim }.getOrElse("")
      if (input.isEmpty || output.isEmpty) None
      else {
        val logical = m.get("logical").collect { case s: String => s.trim }.filter(_.nonEmpty)
          .getOrElse {
            val cleaned = if (output.startsWith("/")) output.drop(1) else output
            cleaned.split('/').lastOption.getOrElse(cleaned)
          }
        val minify = m.get("minify").collect {
          case b: Boolean => b
          case s: String  => s.equalsIgnoreCase("true")
        }.getOrElse(false)
        kind match {
          case "sass"    => Some(SassEntry(logical, input, output, minify))
          case "esbuild" => Some(EsbuildEntry(logical, input, output, minify))
          case "copy"    => Some(CopyEntry(logical, input, output))
          case _         => None
        }
      }
    }
  }

  /** Path-relative join inside the source tree, accepting leading-slash
    * or bare paths interchangeably. */
  private def resolveUnder(root: Path, rel: String): Path = {
    val cleaned = if (rel.startsWith("/")) rel.drop(1) else rel
    cleaned.split('/').filter(_.nonEmpty).foldLeft(root)(_ / _)
  }

  /** Build the manifest: for every entry, produce (compile / bundle /
    * copy) the output into `dst`, optionally fingerprint the filename,
    * and return a `logical -> final URL` map. Errors degrade to
    * verbatim copies of the source; total failure to read the source
    * leaves the entry out of the manifest (templates see "").
    *
    * The returned manifest is consumed by the `asset` template
    * builtin: `{{ asset 'foo.css' }}` looks `foo.css` up in this map
    * and returns the resolved URL — fingerprinted when `fingerprint =
    * true`, plain otherwise. Missing keys return the input string
    * unchanged so a typo's effect is visible in the rendered HTML
    * rather than silently dropped. */
  def run(
      config:  Config,
      backend: AssetBuilderBackend,
      srcRoot: Path,
      dstRoot: Path,
      log:     String => Unit = _ => (),
  ): Map[String, String] = {
    if (!config.enabled) Map.empty
    else {
      val manifest = scala.collection.mutable.LinkedHashMap.empty[String, String]
      for (entry <- config.entries) {
        runEntry(entry, config.fingerprint, backend, srcRoot, dstRoot, log) match {
          case Some(url) => manifest.put(entry.logical, url)
          case None      => ()
        }
      }
      manifest.toMap
    }
  }

  private def runEntry(
      entry:       Entry,
      fingerprint: Boolean,
      backend:     AssetBuilderBackend,
      srcRoot:     Path,
      dstRoot:     Path,
      log:         String => Unit,
  ): Option[String] = {
    val srcPath = resolveUnder(srcRoot, entry.input)
    if (!srcPath.exists) {
      Console.err.println(s"juicer: asset source not found: ${entry.input}")
      return None
    }

    val outRel = if (entry.output.startsWith("/")) entry.output.drop(1) else entry.output
    val outDir = outRel.split('/').dropRight(1).toList
    val outName = outRel.split('/').lastOption.getOrElse(outRel)
    val parentDir = outDir.foldLeft(dstRoot)(_ / _)
    parentDir.createDirectories()

    // Write to a staging path first so we can hash the produced bytes
    // for fingerprinting after the tool runs. Staging path lives in
    // the same parent dir so we don't cross filesystems.
    val stagingPath = parentDir / s".__juicer-staging-$outName"
    val produceResult: Either[String, Unit] = entry match {
      case e: SassEntry    => backend.compileSass(srcPath, stagingPath, e.minify)
      case e: EsbuildEntry => backend.bundleJs(srcPath, stagingPath, e.minify)
      case _: CopyEntry    => Left("copy")  // sentinel: skip tool, use byte copy below
    }

    val producedOk: Boolean = produceResult match {
      case Right(()) => true
      case Left("copy") =>
        // CopyEntry: byte-for-byte verbatim.
        try {
          stagingPath.writeBytes(srcPath.readBytes)
          true
        } catch {
          case e: Throwable =>
            Console.err.println(s"juicer: asset copy failed (${entry.input}): ${e.getMessage}")
            false
        }
      case Left(reason) =>
        // Tool failed or unavailable — degrade to verbatim copy so the
        // URL still resolves. The backend has already logged the
        // missing-tool advisory; we add a per-entry note so authors
        // know which file got the fallback.
        log(s"asset $entry skipped tool ($reason); copying source verbatim")
        try {
          stagingPath.writeBytes(srcPath.readBytes)
          true
        } catch {
          case e: Throwable =>
            Console.err.println(s"juicer: asset fallback copy failed (${entry.input}): ${e.getMessage}")
            false
        }
    }

    if (!producedOk) return None

    val finalName: String =
      if (!fingerprint) outName
      else {
        val bytes = try stagingPath.readBytes catch { case _: Throwable => Array.empty[Byte] }
        if (bytes.isEmpty) outName
        else {
          val hash = ImageVariants.contentHash(bytes)
          val dot  = outName.lastIndexOf('.')
          if (dot <= 0) s"$outName.$hash"
          else s"${outName.substring(0, dot)}.$hash${outName.substring(dot)}"
        }
      }

    val finalPath = parentDir / finalName
    try {
      if (finalPath.exists) finalPath.delete()
      stagingPath.moveTo(finalPath)
    } catch {
      case _: Throwable =>
        try {
          finalPath.writeBytes(stagingPath.readBytes)
          stagingPath.delete()
        } catch {
          case e: Throwable =>
            Console.err.println(s"juicer: asset final-rename failed (${entry.input}): ${e.getMessage}")
            return None
        }
    }

    val finalUrl = ("/" + (outDir ::: List(finalName)).mkString("/")).replace("//", "/")
    log(s"asset: ${entry.logical} -> $finalUrl")
    Some(finalUrl)
  }
}
