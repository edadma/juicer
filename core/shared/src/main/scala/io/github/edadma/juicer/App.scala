package io.github.edadma.juicer

import io.github.edadma.path.Path
import io.github.edadma.toml.TomlDocument

/** Top-level driver for the `build` and `config` commands. The `Args` →
  * unit dispatcher lives at [[App.run]]; the actual rendering pipeline is in
  * [[App.build]]. Stateless transforms and the template builtins live in
  * [[BuildSupport]]; the `theme add` / `theme upgrade` subcommands live in
  * [[ThemeCommands]].
  */
object App {

  val run: PartialFunction[Args, Unit] = {
    case Args(baseConfig, verbose, baseurl, Some(BuildCommand(src, dst, drafts, future))) =>
      build(baseConfig, verbose, baseurl, src, dst, drafts, future)
    case Args(baseConfig, verbose, baseurl, Some(ServeCommand(src, dst, host, port, drafts, future, liveReload))) =>
      val outDir = build(baseConfig, verbose, baseurl, src, dst, drafts, future)
      // Rebuild callback for live-reload — re-runs the build pipeline against
      // the same args. Held by the watcher thread; called when the source
      // tree changes. Returns false silently on rebuild failure so the
      // server stays up and shows the last good site (problems still print
      // to stderr from inside `build`).
      val rebuild: () => Boolean = () =>
        try { build(baseConfig, verbose, baseurl, src, dst, drafts, future); true }
        catch { case t: Throwable => Console.err.println(s"rebuild failed: ${t.getMessage}"); false }
      // Pass the configured htmlDir to serve so URL-to-file resolution can
      // try the prefixed path on miss. Without this, nested content (under
      // `<dst>/html/...` by default) is unreachable since URLs strip the
      // `html/` segment.
      val htmlDir = config(src.toAbsolutePath.normalize, baseConfig).getString("htmlDir").getOrElse("")
      serve(
        outDir,
        host,
        port,
        liveReload = liveReload,
        watchRoot  = if (liveReload) src else null,
        rebuild    = rebuild,
        htmlDir    = htmlDir,
        // outDir typically lives under src (e.g. <src>/public/). Exclude
        // it from watch-event consideration so the build's own writes
        // don't trigger another rebuild — that loop produces a "page
        // jumps every couple of seconds" experience.
        excludeDir = if (liveReload) outDir else null,
      )
    case Args(baseConfig, _, _, Some(ThemeAddCommand(src, url, name, ref, subdir, force))) =>
      ThemeCommands.themeAdd(baseConfig, src, url, name, ref, subdir, force)
    case Args(baseConfig, _, _, Some(ThemeUpgradeCommand(src, name, refOverride))) =>
      ThemeCommands.themeUpgrade(baseConfig, src, name, refOverride)
    case Args(baseConfig, _, baseurl, Some(ConfigCommand(src))) =>
      println("Site config:")

      val c    = config(src, baseConfig)
      val data = tomlObject(c)
      val data1 = baseurl match {
        case None    => data
        case Some(b) => data + ("baseURL" -> b)
      }

      for ((k, v) <- data1)
        println(s"  $k = ${renderValue(v)}")
  }

  def build(
      baseConfig: String,
      verbose:    Boolean,
      baseurl:    Option[String],
      src:        Path,
      dst:        Path,
      drafts:     Boolean = false,
      future:     Boolean = false,
  ): Path = new SiteBuild(baseConfig, verbose, baseurl, src, dst, drafts, future).dst1

  // ===== Config display (`juicer config`) =====

  def renderValue(v: Any): String = v match {
    case s: String          => s"\"$s\""
    case n: Long            => n.toString
    case n: Int             => n.toString
    case n: Double          => n.toString
    case b: Boolean         => b.toString
    case l: List[?]         => l.map(renderValue).mkString("[", ", ", "]")
    case m: Map[?, ?]       => m.map { case (k, v) => s"$k: ${renderValue(v)}" }.mkString("{", ", ", "}")
    case other              => other.toString
  }

  def extension(filename: String): String =
    filename.lastIndexOf('.') match {
      case -1  => ""
      case dot => filename.substring(dot + 1)
    }

  def readConfig(path: Path): TomlDocument = {
    import io.github.edadma.toml.TomlParser

    TomlParser.parse(path.readText()) match {
      case Right(doc) => doc
      case Left(err)  => problem(s"could not parse config $path: $err")
    }
  }

  /** Load the site config: start with the named baseline (`simple` /
    * `standard` / `norme`) and overlay every `*.toml` file found at the top
    * of the site directory in name order.
    */
  def config(src: Path, base: String): TomlDocument = {
    BaseConfigs(base) match {
      case Some(baseDoc) =>
        filesIncludingExtensions(list(src), "toml").foldLeft(baseDoc) { (accum, p) =>
          val overlay = readConfig(p)
          // Per-key overlay: keys in the overlay win, others fall back to the baseline.
          TomlDocument(accum.root ++ overlay.root)
        }
      case None => problem(s"unknown base configuration: $base")
    }
  }
}
