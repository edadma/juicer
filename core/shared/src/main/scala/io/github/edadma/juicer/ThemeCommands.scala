package io.github.edadma.juicer

import io.github.edadma.path.Path

/** The `juicer theme add` and `juicer theme upgrade` subcommands, plus their
  * shared git-install pipeline and on-disk `.juicer-theme.toml` metadata
  * reader/writer. Split out of [[App]] so the build pipeline file stays
  * focused on rendering; the only cross-reference back into [[App]] is
  * `App.config` for reading the site's `themeDir`.
  */
object ThemeCommands {

  // ===== `juicer theme add` =====

  /** Install a theme from a git URL into `<src>/<themeDir>/<name>`. Reads
    * the site config to discover the theme directory; falls back to
    * `themes` if absent. The clone itself is platform-specific (shells out
    * to `git` on the JVM; stubs out on JS / Native), so the cross-platform
    * driver here just resolves paths and validates inputs. */
  def themeAdd(
      baseConfig: String,
      src:        Path,
      url:        String,
      name:       Option[String],
      ref:        Option[String],
      subdir:     Option[String],
      force:      Boolean,
  ): Unit = {
    if (url.isEmpty) problem("theme add: a git URL is required")

    val src1     = src.toAbsolutePath.normalize
    if (!isDir(src1)) problem(s"not a readable directory: $src1")

    val confdoc  = App.config(src1, baseConfig)
    val themeDir = confdoc.getString("themeDir").getOrElse("themes")
    // Derive a sensible default theme name. Without --subdir the URL's
    // last path segment is right; with --subdir the segment of the subdir
    // is more specific (the URL is likely a multi-theme parent repo).
    val themeName = name.getOrElse {
      subdir.map(_.split('/').filter(_.nonEmpty).lastOption.getOrElse(deriveThemeName(url)))
            .getOrElse(deriveThemeName(url))
    }

    if (themeName.isEmpty) problem(s"could not derive theme name from URL: $url (use --name)")
    if (themeName.contains('/') || themeName.contains('\\') || themeName.startsWith("."))
      problem(s"invalid theme name: $themeName")

    val themeRoot = if (themeDir.isEmpty) src1 else (src1 / themeDir)
    if (!themeRoot.exists) themeRoot.createDirectories()
    val target = themeRoot / themeName

    if (target.exists) {
      if (!force)
        problem(s"theme already exists: $target (pass --force to overwrite)")
      else {
        show(s"removing existing theme directory $target")
        deleteRecursive(target)
      }
    }

    println(s"juicer theme add: cloning $url${ref.map(r => s" ($r)").getOrElse("")} → $target${subdir.map(s => s" (subdir: $s)").getOrElse("")}")
    installFromGit(url, ref, subdir, target) match {
      case Right(()) =>
        writeThemeMeta(target, url, ref, subdir)
        println(s"installed theme: $themeName")
        println(s"  set `theme = \"$themeName\"` in site.toml to activate")
        println(s"  re-fetch later with `juicer theme upgrade $themeName`")
      case Left(err) =>
        problem(s"theme add: $err")
    }
  }

  /** `juicer theme upgrade [<name>]` — re-fetch one or all installed themes
    * using their recorded `.juicer-theme.toml` metadata. Themes without
    * metadata get a clear "seed it with `theme add --force`" message
    * instead of a silent skip.
    */
  def themeUpgrade(
      baseConfig:  String,
      src:         Path,
      name:        Option[String],
      refOverride: Option[String],
  ): Unit = {
    val src1     = src.toAbsolutePath.normalize
    if (!isDir(src1)) problem(s"not a readable directory: $src1")

    val confdoc  = App.config(src1, baseConfig)
    val themeDir = confdoc.getString("themeDir").getOrElse("themes")
    val themeRoot = if (themeDir.isEmpty) src1 else (src1 / themeDir)
    if (!themeRoot.exists || !themeRoot.isDirectory) {
      problem(s"no theme directory at $themeRoot (run `juicer theme add ...` first)")
    }

    // Collect candidate themes: a single named one, or every subdirectory
    // of themeRoot that looks like a theme (skip dotfiles + non-dirs).
    val candidates: List[Path] = name match {
      case Some(n) =>
        val p = themeRoot / n
        if (!p.exists || !p.isDirectory) problem(s"no such theme: $p")
        List(p)
      case None =>
        themeRoot.listDirectory().toList
          .filterNot(_.name.startsWith("."))
          .map(e => themeRoot / e.name)
          .filter(_.isDirectory)
    }

    if (candidates.isEmpty) {
      println("no themes installed under " + themeRoot)
      return
    }

    var upgraded = 0
    var skipped  = 0
    for (themePath <- candidates) {
      readThemeMeta(themePath) match {
        case None =>
          skipped += 1
          val nameForHint = themePath.filename
          println(s"skipping $nameForHint — no .juicer-theme.toml metadata")
          println(s"  seed it with: juicer theme add --force --name $nameForHint <url>")
        case Some(meta) =>
          val effectiveRef = refOverride.orElse(meta.ref)
          println(s"juicer theme upgrade: ${themePath.filename} ← ${meta.url}${effectiveRef.map(r => s" ($r)").getOrElse("")}${meta.subdir.map(s => s" [$s]").getOrElse("")}")
          deleteRecursive(themePath)
          installFromGit(meta.url, effectiveRef, meta.subdir, themePath) match {
            case Right(()) =>
              writeThemeMeta(themePath, meta.url, effectiveRef, meta.subdir)
              upgraded += 1
            case Left(err) =>
              problem(s"theme upgrade ${themePath.filename}: $err")
          }
      }
    }
    println(s"theme upgrade: $upgraded upgraded, $skipped skipped")
  }

  /** Lightweight install pipeline shared by `theme add` and `theme upgrade`.
    *
    * Without `--subdir` the repo IS the theme; we clone straight into `target`.
    * With `--subdir`, we clone into a sibling temp directory, then move the
    * subdirectory's contents to `target` and clean up the temp tree. We don't
    * use `git clone --filter` partial-clone tricks because they would still
    * leave a `.git` directory in the theme — we want a tree of plain files
    * the user can vendor cleanly.
    */
  private def installFromGit(
      url:    String,
      ref:    Option[String],
      subdir: Option[String],
      target: Path,
  ): Either[String, Unit] = subdir match {
    case None =>
      gitClone(url, target, ref).map { _ =>
        // Strip the .git directory so the vendored tree is plain files.
        val gitDir = target / ".git"
        if (gitDir.exists) deleteRecursive(gitDir)
      }
    case Some(sub) =>
      val parent: Path = target.parent.getOrElse(problem(s"target has no parent: $target"))
      val tmpName = s".juicer-theme-tmp-${target.filename}-${System.currentTimeMillis()}"
      val tmp = parent / tmpName
      if (tmp.exists) deleteRecursive(tmp)
      val res = gitClone(url, tmp, ref).flatMap { _ =>
        val srcPath: Path = sub.split('/').filter(_.nonEmpty).foldLeft(tmp) { (p, seg) => p / seg }
        if (!srcPath.exists || !srcPath.isDirectory)
          Left(s"subdir not found in cloned repo: $sub")
        else {
          // Move the subdir tree into `target`. `target` was already
          // cleaned before installFromGit was called.
          if (!target.exists) target.createDirectories()
          copyTree(srcPath, target)
          Right(())
        }
      }
      // Always clean the temp clone, success or failure.
      if (tmp.exists) deleteRecursive(tmp)
      res
  }

  /** Recursive copy — keeps the install pipeline readable without pulling in
    * java.nio.file from the shared layer. Skips `.git` so accidental nested
    * git directories don't leak. */
  private def copyTree(src: Path, dst: Path): Unit = {
    if (!dst.exists) dst.createDirectories()
    for (entry <- src.listDirectory()) {
      if (entry.name != ".git") {
        val s = src / entry.name
        val d = dst / entry.name
        if (s.isDirectory) copyTree(s, d)
        else d.writeBytes(s.readBytes)
      }
    }
  }

  /** Theme install metadata. Lives at `<theme>/.juicer-theme.toml`. We
    * keep it minimal — just the inputs needed to redo the install. */
  private case class ThemeMeta(url: String, ref: Option[String], subdir: Option[String])

  private def writeThemeMeta(target: Path, url: String, ref: Option[String], subdir: Option[String]): Unit = {
    val sb = new StringBuilder
    sb.append("# Written by `juicer theme add`. Do not edit by hand — change\n")
    sb.append("# values via `juicer theme upgrade --ref <new-ref>` etc., or remove\n")
    sb.append("# this file to detach the theme from its install source.\n\n")
    sb.append(s"""url = "${url.replace("\"", "\\\"")}"\n""")
    ref.foreach    (r => sb.append(s"""ref = "${r.replace("\"", "\\\"")}"\n"""))
    subdir.foreach (s => sb.append(s"""subdir = "${s.replace("\"", "\\\"")}"\n"""))
    (target / ".juicer-theme.toml").writeText(sb.toString)
  }

  private def readThemeMeta(target: Path): Option[ThemeMeta] = {
    val f = target / ".juicer-theme.toml"
    if (!f.exists || !f.isFile) return None
    // Tiny TOML reader — only `key = "value"` lines, comments, and blanks.
    // The file is mechanically written by writeThemeMeta so we don't need
    // the full TOML parser here (and pulling it in would be circular for
    // the future case where the theme command compiles before site config).
    var url: Option[String] = None
    var ref: Option[String] = None
    var sub: Option[String] = None
    for (raw <- f.readText().split('\n')) {
      val line = raw.trim
      if (!line.isEmpty && !line.startsWith("#")) {
        line.split('=').toList match {
          case k :: rest if rest.nonEmpty =>
            val v = rest.mkString("=").trim.stripPrefix("\"").stripSuffix("\"")
            k.trim match {
              case "url"    => url = Some(v)
              case "ref"    => ref = Some(v)
              case "subdir" => sub = Some(v)
              case _        => () // ignore unknown keys
            }
          case _ => ()
        }
      }
    }
    url.map(u => ThemeMeta(u, ref, sub))
  }

  /** Strip the trailing `.git` and any path prefix from a git URL to get a
    * sensible theme directory name. Handles `https://github.com/foo/bar.git`,
    * `git@github.com:foo/bar.git`, and `./local/path/bar` alike. */
  private[juicer] def deriveThemeName(url: String): String = {
    val tail = url.split('/').lastOption.getOrElse(url)
    // SSH URLs are `git@host:path/repo.git` — split on `:` too.
    val basename = tail.split(':').lastOption.getOrElse(tail)
    val noExt    = if (basename.endsWith(".git")) basename.dropRight(4) else basename
    noExt.trim
  }

  /** Recursive delete — used when `--force` is passed and an existing theme
    * directory needs to be replaced. */
  private def deleteRecursive(p: Path): Unit = {
    if (!p.exists) return
    if (p.isDirectory) {
      p.listDirectory().foreach(e => deleteRecursive(p / e.name))
    }
    p.delete()
  }
}
