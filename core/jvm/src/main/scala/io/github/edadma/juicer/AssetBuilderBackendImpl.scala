package io.github.edadma.juicer

import io.github.edadma.path.Path

import java.io.{ByteArrayOutputStream, IOException}

/** JVM asset-pipeline backend — shells out to `sass` (the
  * dart-sass / npm package; the Ruby `sass` gem also works since the
  * flags overlap on the small subset we use) and `esbuild` via
  * `java.lang.ProcessBuilder`.
  *
  * Each tool is probed lazily on first use (one cheap `--version`
  * call). A missing tool flips that subsystem to `available = false`
  * for the rest of the build, the pipeline degrades to a verbatim
  * copy of the source (so the asset URL still resolves), and a single
  * advisory line goes to stderr — the build keeps going. This mirrors
  * `ImageEncoderBackendImpl`'s "missing magick" handling.
  *
  * Why two separate probes (sass / esbuild) rather than one: a site
  * may want SCSS but not JS bundling, or vice versa; punishing both
  * subsystems for one missing tool would force users to install both
  * even when they only configured one. */
def newAssetBuilderBackend(): AssetBuilderBackend = JvmAssetBuilderBackend

private object JvmAssetBuilderBackend extends AssetBuilderBackend {

  @volatile private var sassProbed:    java.lang.Boolean = null
  @volatile private var esbuildProbed: java.lang.Boolean = null

  private def probe(cmd: String, advisory: String, cache: () => java.lang.Boolean, set: java.lang.Boolean => Unit): Boolean = {
    val current = cache()
    if (current != null) current.booleanValue
    else {
      val ok =
        try {
          val pb = new java.lang.ProcessBuilder(cmd, "--version")
          pb.redirectErrorStream(true)
          val proc = pb.start()
          val code = proc.waitFor()
          code == 0
        } catch {
          case _: IOException => false
          case _: Throwable   => false
        }
      set(java.lang.Boolean.valueOf(ok))
      if (!ok) Console.err.println(advisory)
      ok
    }
  }

  def sassAvailable: Boolean =
    probe(
      "sass",
      "juicer: `sass` not found on PATH — Sass compilation disabled for this build. " +
        "Install with `brew install dart-sass` / `npm install -g sass`, " +
        "or set `[assets] sass.enabled = false` in site.toml to silence this message.",
      () => sassProbed,
      b => sassProbed = b,
    )

  def esbuildAvailable: Boolean =
    probe(
      "esbuild",
      "juicer: `esbuild` not found on PATH — JS bundling disabled for this build. " +
        "Install with `brew install esbuild` / `npm install -g esbuild`, " +
        "or remove the `[assets] esbuild.*` entries in site.toml to silence this message.",
      () => esbuildProbed,
      b => esbuildProbed = b,
    )

  private def runProcess(cmd: Seq[String]): Either[String, Unit] = {
    try {
      val pb = new java.lang.ProcessBuilder(cmd.toArray*)
      pb.redirectErrorStream(true)
      val proc = pb.start()
      val out  = new ByteArrayOutputStream()
      val in   = proc.getInputStream
      val buf  = new Array[Byte](8192)
      var n    = in.read(buf)
      while (n > 0) {
        out.write(buf, 0, n)
        n = in.read(buf)
      }
      val code = proc.waitFor()
      if (code == 0) Right(())
      else Left(s"${cmd.headOption.getOrElse("")} exited with status $code: ${out.toString.trim}")
    } catch {
      case e: IOException => Left(s"could not run ${cmd.headOption.getOrElse("")}: ${e.getMessage}")
      case e: Throwable   => Left(s"${cmd.headOption.getOrElse("")} invocation failed: ${e.getMessage}")
    }
  }

  def compileSass(src: Path, dst: Path, minify: Boolean): Either[String, Unit] =
    if (!sassAvailable) Left("sass is not available on PATH")
    else {
      // dart-sass invocation: `sass [--no-source-map] [--style=...] <in> <out>`.
      // No source map by default — juicer's audience is static-site
      // production builds; an opinionated `[assets] sourceMap = true`
      // can opt back in later.
      val style = if (minify) "compressed" else "expanded"
      runProcess(Seq("sass", "--no-source-map", s"--style=$style", src.toString, dst.toString))
    }

  def bundleJs(src: Path, dst: Path, minify: Boolean): Either[String, Unit] =
    if (!esbuildAvailable) Left("esbuild is not available on PATH")
    else {
      // esbuild: `esbuild <in> --bundle [--minify] --outfile=<out>`.
      // `--bundle` is the whole point — we resolve imports rather than
      // just minify a single file. Sites that explicitly want a
      // no-bundle minify can drop the `bundle = true` flag in a later
      // iteration; for now `[assets] esbuild.*` always bundles.
      val cmd = scala.collection.mutable.ListBuffer[String](
        "esbuild",
        src.toString,
        "--bundle",
        s"--outfile=${dst.toString}",
      )
      if (minify) cmd += "--minify"
      runProcess(cmd.toSeq)
    }
}
