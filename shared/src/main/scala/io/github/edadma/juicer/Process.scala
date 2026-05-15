package io.github.edadma.juicer

import io.github.edadma.path.Path
import io.github.edadma.squiggly.TemplateAST

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.language.postfixOps

/** First pass over the source tree: walks the directories under `src`,
  * classifies each file (content / data / layout / partial / shortcode /
  * static / other-template), and returns a [[Site]] manifest the rendering
  * pass consumes.
  */
object Process {

  /** Bundle of per-pass roots — site or theme. Themes share the same shape
    * as a site (own `layouts/` / `partials/` / `shortcodes/` / `static/` /
    * `data/`) but contribute no content and no top-level "other templates"
    * (those paths only make sense relative to the user's own source root).
    */
  private case class Roots(
      src:            Path,
      content:        Path, // null when this pass should not process content
      layouts:        Path,
      partials:       Path,
      shortcodes:     Path,
      static:         Path,
      data:           Path,
      excludes:       Set[Path] = Set.empty, // dirs to skip during recursion
      otherTemplates: Boolean   = true,
  )

  def apply(src: Path, dst: Path, conf: ConfigWrapper, drafts: Boolean = false): Site = {
    val html          = conf.htmlDir
    val stripPrefix   = conf.boolean.stripPrefix
    val folderContent = conf.folderContent
    val themeDirName  = conf.themeDir
    val themeNames    = conf.themes

    val contentItems       = new ListBuffer[ContentItem]
    val contentMap         = new mutable.HashMap[String, ContentFile]
    // `siteData` is the nested namespace built from `<src>/data/**` and from
    // every active theme's `<themeRoot>/data/**`. Keys at any level are
    // populated theme-first then site, so a site's `data/team.toml` wins
    // over a theme's `data/team.toml` (whole-file replacement at the leaf
    // key — no deep merge in v1).
    val siteData           = mutable.LinkedHashMap.empty[String, Any]
    val layoutTemplates    = new mutable.HashMap[(List[String], String), TemplateFile]
    val partialTemplates   = new mutable.HashMap[String, TemplateFile]
    val shortcodeTemplates = new mutable.HashMap[String, TemplateFile]
    val otherTemplates     = new ListBuffer[TemplateFile]

    val themeRoots: List[Path] = themeNames
      .map(n => if (themeDirName.isEmpty) (src / n).normalize else (src / themeDirName / n).normalize)
      .filter(isDir)

    val siteRoots = Roots(
      src        = src,
      content    = (src / conf.path.contentDir).normalize,
      layouts    = (src / conf.path.layoutDir).normalize,
      partials   = (src / conf.path.partialDir).normalize,
      shortcodes = (src / conf.path.shortcodeDir).normalize,
      static     = (src / conf.path.staticDir).normalize,
      data       = (src / conf.path.dataDir).normalize,
      // Don't double-process theme directories during the site pass. Two
      // mechanisms layered together:
      //   - `themeRoots` excludes the *active* themes (already processed
      //     via the explicit theme passes above the site pass).
      //   - The whole `<src>/<themeDir>/` tree is also excluded so that
      //     INACTIVE themes living alongside the active one (e.g. the
      //     juicer repo ships both juicerdocs and juicerblog under
      //     `docs/themes/`) don't get scanned as the site's "other
      //     templates" and rendered into the output tree. Themes are
      //     loaded explicitly by name; sit them under `themeDir` and
      //     they stay invisible until activated.
      // Also exclude `<src>/<publicDir>` outright — it's the conventional
      // output location, and recursing into it tries to re-parse rendered
      // HTML as squiggly templates the moment a user passes `-d` to a
      // different path. The `dst` exclude (added later) only catches the
      // active dst, not the default one when `-d` redirects elsewhere.
      //
      // Finally, fold in any `excludeDirs` from `site.toml`. Each entry is
      // a path relative to `src`; nested paths work (`assets/raw`). These
      // aren't glob patterns — exact directory matches only — and they
      // apply to every depth of the walk (the recursion-time check uses
      // structural Path equality against this set).
      excludes       = themeRoots.toSet
        ++ (if (themeDirName.nonEmpty) Set((src / themeDirName).normalize) else Set.empty)
        + (src / conf.publicDir).normalize
        ++ conf.excludeDirs.map(d => (src / Path(d)).normalize),
      otherTemplates = true,
    )

    if (!isDir(siteRoots.content)) problem(s"can't read content directory: ${siteRoots.content}")

    def processDir(dir: Path, r: Roots): Unit = {
      show(s">>> $dir")

      val listing = list(dir)
      val content    = r.content
      val layouts    = r.layouts
      val partials   = r.partials
      val shortcodes = r.shortcodes
      val static     = r.static
      val data       = r.data

      if (content != null && dir.startsWith(content)) {
        val files = filesIncludingExtensions(listing, markdownExtensions*)
        val outdir = {
          val uncleaned = dst / dir.relativeTo(content)

          if (contentItems.isEmpty) {
            if (uncleaned == dst) dst
            else
              (if (html == "") dst else dst / html) /
                cleanName(uncleaned.filename, stripPrefix = true)
          } else {
            val prev = contentItems.last.outdir

            if (prev.segments.length >= uncleaned.segments.length)
              Path("/") / prev.subpath(0, uncleaned.segments.length - (if (html == "") 1 else 0)) /
                cleanName(uncleaned.filename, stripPrefix = true)
            else
              (if (html == "") prev else prev / html) /
                cleanName(uncleaned.filename, stripPrefix = true)
          }
        }

        if (outdir != dst) {
          show(s"content destination subfolder: $outdir")
          contentItems += ContentFolder(outdir)
        }

        show(s"content file(s): ${files.map(_.filename).mkString(", ")}", files.nonEmpty)
        show("no content files", files.isEmpty)

        files foreach { p =>
          val s     = p.readText()
          val lines = scala.io.Source.fromString(s).getLines()
          val (first, frontmatter) = {
            val first = lines.next()

            first match {
              case "---" =>
                val buf = new mutable.StringBuilder

                @tailrec
                def line(): Unit =
                  if (lines.hasNext) {
                    lines.next() match {
                      case "---" =>
                      case s =>
                        buf ++= s
                        buf += '\n'
                        line()
                    }
                  } else
                    problem(s"unexpected end of file while reading front matter: $p")

                line()
                (first, buf.toString)
              case _ => (first, "")
            }
          }

          val name =
            withoutExtension(p.filename) match {
              case `folderContent` => folderContent
              case n               => cleanName(n, stripPrefix)
            }

          val pageData = parseYamlData(frontmatter)

          // Skip draft pages unless --drafts was passed. A "draft" page is one
          // whose frontmatter has `draft: true` (boolean). Skipping happens here
          // rather than later so the page is invisible to TOC, site.pages,
          // sitemap, etc. — anything downstream of Process.
          val isDraft = pageData match {
            case m: Map[?, ?] =>
              m.asInstanceOf[Map[Any, Any]].get("draft").contains(true)
            case _ => false
          }

          if (isDraft && !drafts) {
            show(s"skipping draft: ${p.filename}")
          } else {
            val contentFile = ContentFile(
              outdir,
              name,
              pageData,
              ((if (first == "---") ""
                else first :+ '\n') ++ lines.map(_ :+ '\n').mkString).trim,
              null,
              null,
              srcPath = p,
            )

            contentMap(p.relativeTo(content).toString) = contentFile
            contentItems += contentFile
          }
        }
      }

      // Data files: `<dataDir>/team.toml` → `.site.data.team`,
      // `<dataDir>/menu/lunch.yaml` → `.site.data.menu.lunch`. Theme passes
      // run first, then the site pass; site keys overwrite theme keys at the
      // leaf-file granularity. Whole-file replacement at the key path — no
      // deep merge (a theme that wants to be partially overridable should
      // namespace into a subdirectory).
      if (data != null && dir.startsWith(data)) {
        val dataFiles = filesIncludingExtensions(listing, "toml", "yaml", "yml")
        val keyPrefix = dir.relativeTo(data).segments.toList

        show(s"data files: ${dataFiles.map(_.filename).mkString(", ")}", dataFiles.nonEmpty)

        dataFiles foreach { p =>
          val name = withoutExtension(p.filename)
          val text = p.readText()
          val parsed: Any =
            if (p.toString.endsWith(".toml")) parseTomlData(text, p.toString)
            else parseYamlData(text)
          insertAtPath(siteData, keyPrefix :+ name, parsed)
        }
      }

      if (dir.startsWith(layouts)) {
        val folder = dir.relativeTo(layouts).segments.toList
        val files  = filesIncludingExtensions(listing, "html", "sq")

        show(s"layouts: ${files.map(_.filename).mkString(", ")}", files.nonEmpty)

        files foreach { p =>
          val name = withoutExtension(p.filename)
          layoutTemplates((folder, name)) = TemplateFile(p, name, null)
        }
      }

      if (dir.startsWith(partials)) {
        val files = filesIncludingExtensions(listing, "html", "sq")

        show(s"partials: ${files.map(_.filename).mkString(", ")}", files.nonEmpty)

        files foreach { p =>
          val name = withoutExtension(p.filename)
          partialTemplates(name) = TemplateFile(p, name, null)
        }
      }

      if (dir.startsWith(shortcodes)) {
        val files = filesIncludingExtensions(listing, "html", "sq")

        show(s"shortcodes: ${files.map(_.filename).mkString(", ")}", files.nonEmpty)

        files foreach { p =>
          val name = withoutExtension(p.filename)
          shortcodeTemplates(name) = TemplateFile(p, name, null)
        }
      }

      if (dir.startsWith(static)) {
        val subdir = dst / dir.relativeTo(static)

        show(s"static: create directory $subdir")
        subdir.createDirectories()

        val toCopy =
          if (static == r.src)
            filesExcludingExtensions(
              listing,
              "html", "sq", "css", "scss", "sass",
              "YML", "YAML", "yml", "yaml",
              "mkd", "mkdn", "mdown", "md", "markdown",
              "props", "properties", "conf", "hocon", "toml",
            )
          else listing.filter(isFile)

        toCopy foreach { p =>
          val dp = dst / p.relativeTo(static)

          show(s"static: copy $p => $dp")
          if (dp.exists) dp.delete()
          p.copyTo(dp)
        }
      }

      if (
        r.otherTemplates &&
        !(layouts != r.src && dir.startsWith(layouts)) &&
        !(partials != r.src && dir.startsWith(partials)) &&
        !(shortcodes != r.src && dir.startsWith(shortcodes)) &&
        !(static != r.src && dir.startsWith(static))
      ) {
        val l = filesIncludingExtensions(listing, "html", "css", "scss", "sass")

        show(s"other templates: ${l.map(_.filename).mkString(", ")}", l.nonEmpty)
        l foreach { p =>
          val outfile = dst / p.relativeTo(r.src)

          show(s"parse template $p")
          otherTemplates += TemplateFile(outfile, null, templateParser.parse(p.readText()))
        }
      }

      val excluded = r.excludes + dst
      dirsExcluding(listing, excluded.toSeq*) foreach (d => processDir(d, r))
    }

    // Theme passes run BEFORE the site pass so site entries naturally win on
    // map collisions (mutable HashMap put-overwrites). Within a theme chain
    // (`theme = ["a", "b"]`), `a` wins over `b`, so process `b` first.
    for (themeRoot <- themeRoots.reverse) {
      show(s"theme: $themeRoot")
      val themeR = Roots(
        src        = themeRoot,
        content    = null, // themes never contribute content
        layouts    = (themeRoot / conf.path.layoutDir).normalize,
        partials   = (themeRoot / conf.path.partialDir).normalize,
        shortcodes = (themeRoot / conf.path.shortcodeDir).normalize,
        static     = (themeRoot / conf.path.staticDir).normalize,
        data       = (themeRoot / conf.path.dataDir).normalize,
        excludes       = Set.empty,
        otherTemplates = false,
      )
      processDir(themeRoot, themeR)
    }

    processDir(src, siteRoots)
    Site(
      contentItems.toList,
      contentMap.toMap,
      freezeData(siteData),
      layoutTemplates.toMap,
      partialTemplates.toMap,
      shortcodeTemplates.toMap,
      otherTemplates.toList,
    )
  }

  /** Insert `value` into a nested mutable map at the given path. Intermediate
    * levels are created as mutable `LinkedHashMap` when missing; if a level
    * already exists as a *value* (not a sub-map) it is replaced — a leaf
    * `data/x.toml` and a directory `data/x/` at the same key path is a user
    * configuration mistake, and last-write-wins is the same precedence rule
    * the rest of the walk uses. */
  private def insertAtPath(
      root:  mutable.LinkedHashMap[String, Any],
      path:  List[String],
      value: Any,
  ): Unit = path match {
    case Nil =>
      // Path is always non-empty in practice: the caller appends the filename
      // (sans extension) to the directory prefix, so even a top-level
      // `data/x.toml` produces a one-element path. Defensive no-op.
      ()
    case leaf :: Nil => root(leaf) = value
    case head :: rest =>
      val child = root.get(head) match {
        case Some(existing: mutable.LinkedHashMap[String, Any] @unchecked) => existing
        case _ =>
          val m = mutable.LinkedHashMap.empty[String, Any]
          root(head) = m
          m
      }
      insertAtPath(child, rest, value)
  }

  /** Deep-freeze the mutable nested map built during the walk into the
    * immutable `Map[String, Any]` shape squiggly's renderer consumes. Only
    * the intermediate "directory" maps are mutable; leaves (parser output)
    * are already immutable, so this is a one-level recursion at every
    * `LinkedHashMap` node. */
  private def freezeData(m: mutable.LinkedHashMap[String, Any]): Map[String, Any] =
    m.iterator.map {
      case (k, v: mutable.LinkedHashMap[String, Any] @unchecked) => k -> freezeData(v)
      case (k, v)                                                => k -> v
    }.toMap

  def withoutExtension(filename: String): String =
    filename.lastIndexOf('.') match {
      case -1  => filename
      case dot => filename.substring(0, dot)
    }

  def cleanName(s: String, stripPrefix: Boolean): String = {
    val buf = new mutable.StringBuilder(s)

    if (stripPrefix) {
      while (buf.nonEmpty && buf.head.isDigit) buf.deleteCharAt(0)
      while (buf.nonEmpty && !buf.head.isLetterOrDigit) buf.deleteCharAt(0)
    }

    @tailrec
    def clean(from: Int): Unit =
      buf.indexWhere(!_.isLetterOrDigit, from) match {
        case -1 =>
        case idx =>
          buf.indexWhere(_.isLetterOrDigit, idx) match {
            case -1 =>
              buf.delete(idx, buf.length)
            case end =>
              buf(idx) = '-'
              buf.delete(idx + 1, end)
              clean(idx + 1)
          }
      }

    clean(0)

    if (buf.isEmpty) "-"
    else buf.toString
  }
}

sealed trait ContentItem { val outdir: Path }
case class ContentFile(
    outdir:      Path,
    name:        String,
    page:        Any,
    source:      String,
    var content: String,
    var toc:     TOC,
    var summary: String = null,
    /** Absolute path to the source file under `<src>/content/...`. Captured at
      * parse time so the rendering pass can reach back for filesystem facts
      * (mtime fallback for `.page.date`, etc.) without recomputing the path. */
    srcPath:     Path   = null,
) extends ContentItem
case class ContentFolder(outdir: Path) extends ContentItem
case class ContentLabel(label: String) extends ContentItem { val outdir: Path = null }

case class TemplateFile(path: Path, name: String, var template: TemplateAST)

// `data` is the nested namespace built from `<src>/data/**` and every active
// theme's `<themeRoot>/data/**`. `data/team.toml` becomes `data("team")`;
// `data/menu/lunch.yaml` becomes `data("menu")("lunch")`. Exposed to templates
// as `.site.data`.
case class Site(
    content: List[ContentItem],
    map: Map[String, ContentFile],
    data: Map[String, Any],
    layoutTemplates: Map[(List[String], String), TemplateFile],
    partialTemplates: Map[String, TemplateFile],
    shortcodeTemplates: Map[String, TemplateFile],
    otherTemplates: List[TemplateFile],
)

/** Tree-shaped table of contents for a single content file: each [[TocEntry]]
  * carries the heading level, the original inlines (so we can render the
  * label as HTML or text on demand), an auto-generated id (slug), and any
  * nested headings whose level is deeper.
  */
case class TocEntry(
    level: Int,
    contents: List[io.github.edadma.markdown.Inline],
    id: String,
    sub: TOC,
)

case class TOC(headings: List[TocEntry])
