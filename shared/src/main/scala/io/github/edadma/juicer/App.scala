package io.github.edadma.juicer

import io.github.edadma.markdown.{Document, Inline, Heading => MdHeading, Link, Paragraph}
import io.github.edadma.path.Path
import io.github.edadma.squiggly.{BaseURL, TemplateAST, TemplateBuiltin, TemplateFunction, TemplateLoader, TemplateRenderer}
import io.github.edadma.toml.{TomlDocument, TomlValue}

import scala.annotation.tailrec
import scala.collection.immutable.VectorMap
import scala.collection.mutable.ListBuffer

/** Top-level driver for the `build` and `config` commands. The `Args` →
  * unit dispatcher lives at [[App.run]]; the actual rendering pipeline is in
  * [[App.build]].
  */
object App {

  val run: PartialFunction[Args, Unit] = {
    case Args(baseConfig, verbose, baseurl, Some(BuildCommand(src, dst, drafts))) =>
      build(baseConfig, verbose, baseurl, src, dst, drafts)
    case Args(baseConfig, verbose, baseurl, Some(ServeCommand(src, dst, host, port, drafts, liveReload))) =>
      val outDir = build(baseConfig, verbose, baseurl, src, dst, drafts)
      // Rebuild callback for live-reload — re-runs the build pipeline against
      // the same args. Held by the watcher thread; called when the source
      // tree changes. Returns false silently on rebuild failure so the
      // server stays up and shows the last good site (problems still print
      // to stderr from inside `build`).
      val rebuild: () => Boolean = () =>
        try { build(baseConfig, verbose, baseurl, src, dst, drafts); true }
        catch { case t: Throwable => Console.err.println(s"rebuild failed: ${t.getMessage}"); false }
      // Pass the configured htmlDir to serve so URL-to-file resolution can
      // try the prefixed path on miss. Without this, nested content (under
      // `<dst>/html/...` by default) is unreachable since URLs strip the
      // `html/` segment.
      val htmlDir = config(src.normalize.toAbsolutePath, baseConfig).getString("htmlDir").getOrElse("")
      serve(
        outDir,
        host,
        port,
        liveReload = liveReload,
        watchRoot  = if (liveReload) src else null,
        rebuild    = rebuild,
        htmlDir    = htmlDir,
      )
    case Args(baseConfig, _, _, Some(ThemeAddCommand(src, url, name, ref, force))) =>
      themeAdd(baseConfig, src, url, name, ref, force)
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
  ): Path = {
    showSteps = verbose

    val src1 = src.normalize.toAbsolutePath

    show(s"source path = $src1")

    if (!isDir(src1)) problem(s"not a readable directory: $src1")

    val confdoc      = config(src1, baseConfig)
    val baseURLstr   = baseurl.orElse(confdoc.getString("baseURL")).getOrElse("http://localhost:8080")
    val confdata: VectorMap[String, Any] = {
      val base = tomlObject(confdoc)
      base + ("baseURL" -> baseURLstr)
    }

    // ConfigWrapper takes a TomlDocument; for fields we bake into the
    // wrapper by overlaying the explicit baseURL on top of the parsed doc.
    val conf = new ConfigWrapper(confdoc.copy(root = confdoc.root + ("baseURL" -> TomlValue.Str(baseURLstr))))

    val dst1 = if (dst eq null) (src1 / conf.publicDir).normalize.toAbsolutePath else dst.normalize.toAbsolutePath

    show(s"destination path = $dst1")

    if (!canCreate(dst1)) problem(s"not a writable directory: $dst1")

    if (!isDir(dst1)) {
      show(s"create destination path $dst1")
      dst1.createDirectory()
    }

    val baseURL = parseURL(baseURLstr).getOrElse(problem(s"invalid base URL: $baseURLstr"))
    val linkCallback = (url: String) =>
      if (absoluteURL(url)) url
      else {
        val basePath = if (baseURL.path.endsWith("/")) baseURL.path.dropRight(1) else baseURL.path
        val tail     = if (url.startsWith("/")) url else "/" + url
        basePath + tail
      }
    val rendererData =
      Map(
        "baseURL" -> baseURL,
        "link"    -> linkCallback,
      )

    show(s"base URL = ${baseURL.base}${baseURL.path}")

    val site = Process(src1, dst1, conf, drafts)
    val partialsLoader: TemplateLoader =
      (name: String) =>
        site.partialTemplates.get(name).map { t =>
          if (t.template eq null)
            t.template = templateParser.parse(t.path.readText())

          t.template
        }.orElse(problem(s"partial '$name' not found"))
    val templateRenderer: TemplateRenderer =
      new TemplateRenderer(
        partials  = partialsLoader,
        data      = rendererData,
        functions = TemplateBuiltin.functions ++ juicerUrlBuiltins,
      )
    val shortcodesLoader: TemplateLoader =
      (name: String) =>
        site.shortcodeTemplates.get(name).map { t =>
          if (t.template eq null)
            t.template = templateParser.parse(t.path.readText())

          t.template
        }.orElse(problem(s"shortcode '$name' not found"))
    val preprocessor = new Preprocessor(shortcodes = shortcodesLoader, renderer = templateRenderer)

    // Markdown render pass: parse each content file's source, build the TOC,
    // produce the rendered HTML body, and compute a summary. Heading levels
    // and link destinations are pre-transformed at the AST level so the
    // output blends into a layout that already provides an outer `<h1>` for
    // the page title.
    for (case c @ ContentFile(_, name, _, _, _, _, _) <- site.content) {
      show(s"parse markdown file $name")

      val raw = parseMarkdown(preprocessor.process(c.source))
      val doc = transformLinks(shiftHeadings(raw, by = conf.int.headingShift), linkCallback)

      c.toc = buildToc(doc)
      c.content = io.github.edadma.markdown.renderToHTML(doc, markdownConfig).trim
      c.summary = computeSummary(c, doc, preprocessor, linkCallback)
    }

    trait TOCItem
    case class TOCLabel(label: String)                extends TOCItem
    case class TOCLink(html: String, href: String)
    case class TOCList(headings: List[TOCLink])       extends TOCItem

    val sitetoc = new ListBuffer[TOCItem]

    @tailrec
    def mktocFromContent(l: List[ContentItem], start: String = null): String =
      l match {
        case Nil => start
        case ContentLabel(label) :: t =>
          sitetoc += TOCLabel(label)
          mktocFromContent(t, start)
        case ContentFolder(outdir) :: t =>
          sitetoc += TOCLabel(outdir.filename)
          mktocFromContent(t, start)
        case (c: ContentFile) :: _ =>
          val (headings: List[ContentItem], rest) = l.span(_.isInstanceOf[ContentFile])
          val files                               = headings.collect { case f: ContentFile => f }

          sitetoc += TOCList(
            files.map(h =>
              // Prefer the first heading; fall back to frontmatter title;
              // last resort is the file name. Heading-less content used to
              // crash here on `headings.head`.
              TOCLink(
                if (h.toc.headings.nonEmpty) renderInlinesHtml(h.toc.headings.head.contents)
                else
                  (h.page match {
                    case m: Map[?, ?] =>
                      m.collect { case (k: String, v: String) if k == "title" => v }.headOption
                    case _ => None
                  }).getOrElse(h.name),
                s"${h.outdir.relativeTo(dst1)}/${h.name}",
              )),
          )
          mktocFromContent(rest, if (start eq null) s"${c.outdir.relativeTo(dst1)}/${c.name}/" else start)
      }

    def mktocFromConfig: String = {
      val buf = new ListBuffer[ContentItem]

      confdata("nav") match {
        case l: List[?] =>
          l foreach {
            case label: String =>
              if (markdownExtensions.exists(label.endsWith))
                buf += site.map.getOrElse(label, problem(s"content file not found $label"))
              else
                buf += ContentLabel(label)
            case file: Map[?, ?]
                if file.size == 1 && file.head._1.isInstanceOf[String] && file.head._2.isInstanceOf[String] =>
              val (_: String, path: String) = file.head: @unchecked
              buf += site.map.getOrElse(path, problem(s"content file not found $path"))
            case e => problem(s"invalid nav element: $e")
          }

          mktocFromContent(buf.toList)
        case n => problem(s"invalid 'nav': $n")
      }
    }

    val start =
      if (confdata.contains("nav")) mktocFromConfig
      else if (site.content.nonEmpty) mktocFromContent(site.content.tail)
      else null
    val defaultLayout = conf.defaultLayout
    val baseofLayout  = conf.baseofLayout
    val fileLayout    = conf.fileLayout
    val folderLayout  = conf.folderLayout
    val folderContent = conf.folderContent
    val html          = conf.htmlDir

    // ----- per-page URL computation + site.pages enrichment -----
    //
    // Each ContentFile gets one "enriched page record" — the parsed
    // frontmatter map plus derived fields (`permalink`, `relPermalink`,
    // `url`, eventually `summary`). The same record is used twice: as
    // the `.page` data when rendering that file's own template, and as
    // an entry in `site.pages` / `site.pagesByPath` for sitemap, list
    // pages, and cross-references in other templates.

    /** Path-only URL for a content file, prefixed by `baseURL.path`.
      * Folder content (`_index.md`) lives at the section directory; every
      * other file becomes its own pretty-URL directory (`/<name>/`).
      * The configured `htmlDir` is *stripped* from the URL since it's a
      * filesystem-only convenience for keeping static assets alongside
      * rather than under rendered pages.
      */
    def relPermalinkFor(c: ContentFile): String = {
      val rel        = c.outdir.relativeTo(dst1)
      val allSegs    = if (c.outdir == dst1) Nil else rel.segments.toList
      val withoutHtml = if (html != "" && allSegs.nonEmpty) allSegs.drop(1) else allSegs
      val pathSegs   =
        if (c.name == folderContent) withoutHtml
        else withoutHtml :+ c.name
      val basePath   =
        if (baseURL.path == "/" || baseURL.path.isEmpty) ""
        else baseURL.path
      val joined     = pathSegs.mkString("/")
      if (joined.isEmpty) basePath + "/"
      else basePath + "/" + joined + "/"
    }

    /** Coerce a frontmatter `Any` (typically `Map[String, Any]`, possibly
      * empty) to a `Map[String, Any]`; non-map / null frontmatter degrades
      * to an empty map.
      */
    def frontmatterMap(page: Any): Map[String, Any] = page match {
      case m: Map[?, ?] =>
        m.collect { case (k: String, v) => k -> v }.toMap
      case _ => Map.empty[String, Any]
    }

    val contentFiles: List[ContentFile] = site.content.collect { case c: ContentFile => c }

    // ----- Section / navigation graph -----

    /** Map from outdir → its `_index.md` page (if any). One section per
      * directory; absent if the section was created implicitly via a
      * non-`_index` child. */
    val sectionIndex: Map[Path, ContentFile] =
      contentFiles.collect { case c if c.name == folderContent => c.outdir -> c }.toMap

    /** Frontmatter weight for sorting. Default is `Long.MaxValue / 2` so
      * pages with no `weight` cluster after explicitly weighted ones but
      * before any sentinel value an author might choose. */
    def pageWeight(c: ContentFile): Long =
      frontmatterMap(c.page).get("weight").collect {
        case n: BigDecimal => n.toLong
        case n: Long       => n
        case n: Int        => n.toLong
      }.getOrElse(Long.MaxValue / 2)

    /** Sort by weight ascending, then by name ascending. Stable secondary
      * key so pages with the same weight have a predictable order. */
    def pageOrder(cs: List[ContentFile]): List[ContentFile] =
      cs.sortBy(c => (pageWeight(c), c.name))

    /** All content files grouped by outdir — one bucket per section
      * directory. Each bucket holds the section's `_index` (if present) and
      * its non-`_index` siblings, unordered. */
    val pagesByOutdir: Map[Path, List[ContentFile]] =
      contentFiles.groupBy(_.outdir)

    /** Outdir → sorted list of immediate child sections (their `_index`
      * pages). "Immediate" here means *nearest enclosing* section — under
      * the default `htmlDir = "html"` layout the on-disk parent of
      * `dst1/html/docs` is `dst1/html`, which is never an _index outdir, so
      * we walk up until we hit one. A section whose nearest enclosing
      * section is the root therefore lands in the root's subsection list,
      * even though the on-disk path goes through `html/`. */
    val subsectionsByParent: Map[Path, List[ContentFile]] = {
      val by = scala.collection.mutable.HashMap.empty[Path, List[ContentFile]]
      for (c <- contentFiles if c.name == folderContent) {
        var cur: Option[Path] = c.outdir.parent.map(_.normalize)
        while (cur.isDefined && !sectionIndex.contains(cur.get))
          cur = cur.get.parent.map(_.normalize)
        cur.foreach { p =>
          by(p) = c :: by.getOrElse(p, Nil)
        }
      }
      by.toMap.view.mapValues(pageOrder).toMap
    }

    /** Per-outdir digest: index + sorted children + sorted subsections.
      * Drives the `.section` data block at render time and the `.pages` /
      * `.subsections` keys on `_index` page records. */
    case class SectionInfo(
        index:       Option[ContentFile],
        pages:       List[ContentFile],
        subsections: List[ContentFile],
    )

    val sectionInfoByOutdir: Map[Path, SectionInfo] =
      pagesByOutdir.map { case (outdir, all) =>
        outdir -> SectionInfo(
          index       = sectionIndex.get(outdir),
          pages       = pageOrder(all.filter(_.name != folderContent)),
          subsections = subsectionsByParent.getOrElse(outdir, Nil),
        )
      }

    // ----- Per-page basic records -----

    /** Build the "basic" record for a content file. Frontmatter + URL trio
      * + summary + isSection. For section indexes, the record additionally
      * carries `pages` and `subsections` — each itself a basic record built
      * recursively. The section graph is a tree (acyclic by construction:
      * outdirs nest), so the recursion always terminates at leaf
      * (non-_index) pages, which carry no descendants.
      *
      * The recursive shape costs some duplication (an ancestor's record
      * contains a snapshot of every descendant's record) but lets templates
      * walk the section tree from any starting point — `.site.root.subsections[i].pages`,
      * `.section.subsections[j].subsections[k]`, etc. — without having to
      * thread a separate lookup table through every partial.
      *
      * Frontmatter wins on key collisions only for fields we don't own.
      * The URL fields (`permalink`, `relPermalink`, `url`), `summary`, and
      * `isSection` always overwrite so authors can't accidentally shadow
      * them.
      */
    def buildBasic(c: ContentFile): Map[String, Any] = {
      val rel  = relPermalinkFor(c)
      val abs  = baseURL.base + rel
      val core = frontmatterMap(c.page) ++ Map(
        "permalink"    -> abs,
        "relPermalink" -> rel,
        "url"          -> rel,
        "summary"      -> (if (c.summary eq null) "" else c.summary),
        "isSection"    -> (c.name == folderContent),
      )
      if (c.name == folderContent) {
        val info = sectionInfoByOutdir(c.outdir)
        core ++ Map(
          "pages"       -> info.pages.map(buildBasic),
          "subsections" -> info.subsections.map(buildBasic),
        )
      } else core
    }

    val basicEntries: List[(ContentFile, Map[String, Any])] =
      contentFiles.map(c => c -> buildBasic(c))

    /** Look up the basic record for a content file by reference identity.
      * Avoids using `ContentFile` as a HashMap key — case-class equality
      * on `var` fields was a footgun before. */
    def basic(c: ContentFile): Map[String, Any] =
      basicEntries.find(_._1 eq c).map(_._2).getOrElse(Map.empty)

    /** Nearest enclosing `_index` page. For a non-`_index` page this is the
      * `_index` of its own outdir (same section). For an `_index` page it's
      * the nearest ancestor outdir's `_index`. Walks up through outdir
      * parents until either an index is found or the root is reached. */
    def parentSectionOf(c: ContentFile): Option[ContentFile] =
      if (c.name != folderContent) sectionIndex.get(c.outdir)
      else {
        var cur: Option[Path] = c.outdir.parent.map(_.normalize)
        while (cur.isDefined) {
          sectionIndex.get(cur.get) match {
            case Some(idx) => return Some(idx)
            case None      => cur = cur.get.parent.map(_.normalize)
          }
        }
        None
      }

    /** Chain of ancestor `_index` pages from the root section down to the
      * page's parent (exclusive of the page itself). Used by templates to
      * render breadcrumbs. */
    def ancestorsOf(c: ContentFile): List[ContentFile] = {
      val out = scala.collection.mutable.ListBuffer.empty[ContentFile]
      var cur = parentSectionOf(c)
      while (cur.isDefined) {
        out.prepend(cur.get)
        cur = parentSectionOf(cur.get)
      }
      out.toList
    }

    /** Flat depth-first reading order across the whole content tree. Each
      * section contributes its `_index` first, then its non-`_index` pages
      * (`pageOrder`), then recurses into each subsection. Drives prev/next
      * navigation that walks across section boundaries — so the last page
      * in section A points "next" to section B's `_index`, and the first
      * page in section B points "prev" back to A's last page.
      *
      * Falls back to a weight-sorted flat list when the site has no root
      * `_index` to start from (rare for docs sites; possible for blogs). */
    val readingOrder: List[ContentFile] = {
      def flatten(c: ContentFile): List[ContentFile] = {
        if (c.name != folderContent) List(c)
        else {
          val info = sectionInfoByOutdir.getOrElse(
            c.outdir, SectionInfo(Some(c), Nil, Nil),
          )
          c :: info.pages ::: info.subsections.flatMap(flatten)
        }
      }
      sectionIndex.get(dst1) match {
        case Some(root) => flatten(root)
        case None       => pageOrder(contentFiles)
      }
    }

    /** Previous / next pages in `readingOrder`. Applies to every page
      * including section `_index` pages — so navigating sequentially walks
      * Home → first section's `_index` → that section's pages → next
      * section's `_index` → its pages → … */
    def prevNextOf(c: ContentFile): (Option[ContentFile], Option[ContentFile]) = {
      val idx = readingOrder.indexWhere(_ eq c)
      if (idx < 0) (None, None)
      else {
        val prv = if (idx > 0)                       Some(readingOrder(idx - 1)) else None
        val nxt = if (idx < readingOrder.length - 1) Some(readingOrder(idx + 1)) else None
        (prv, nxt)
      }
    }

    /** Second-pass enriched record. Adds navigation cross-references whose
      * targets are basic records (one level only — `.page.parent.parent`
      * is never defined; templates walk `.page.ancestors` for the chain). */
    def enrichedRecord(c: ContentFile): Map[String, Any] = {
      val base    = basic(c)
      val parent  = parentSectionOf(c).map(basic).orNull
      val ancs    = ancestorsOf(c).map(basic)
      val (p, n)  = prevNextOf(c)
      val withNav = base ++ Map(
        "parent"    -> parent,
        "ancestors" -> ancs,
        "prev"      -> p.map(basic).orNull,
        "next"      -> n.map(basic).orNull,
      )
      if (c.name == folderContent) {
        val info = sectionInfoByOutdir(c.outdir)
        withNav ++ Map(
          "pages"       -> info.pages.map(basic),
          "subsections" -> info.subsections.map(basic),
        )
      } else withNav
    }

    /** `.section` data block — the enclosing section's pages list +
      * subsections list + section-index page record. For a non-`_index`
      * page this describes the page's own section; for an `_index` page
      * this describes the section the page heads. */
    def sectionDataFor(c: ContentFile): Map[String, Any] = {
      val info = sectionInfoByOutdir(c.outdir)
      Map(
        "pages"       -> info.pages.map(basic),
        "subsections" -> info.subsections.map(basic),
        "index"       -> info.index.map(basic).orNull,
      )
    }

    val pageEntries: List[(ContentFile, Map[String, Any])] =
      contentFiles.map(c => c -> enrichedRecord(c))
    val pages: List[Map[String, Any]]              = pageEntries.map(_._2)
    val pagesByPath: Map[String, Map[String, Any]] =
      pages.map(p => p("relPermalink").asInstanceOf[String] -> p).toMap

    // The root section's _index page (if any), looked up by its URL. Themes
    // use this for site-wide navigation that walks `.site.root.subsections`.
    val rootRecord: Map[String, Any] = {
      val rootKey =
        if (baseURL.path == "/" || baseURL.path.isEmpty) "/"
        else baseURL.path + "/"
      pagesByPath.getOrElse(rootKey, null)
    }

    val sitedata = confdata +
      ("toc"         -> sitetoc.toList) +
      ("start"       -> start) +
      ("pages"       -> pages) +
      ("pagesByPath" -> pagesByPath) +
      ("root"        -> rootRecord)

    def findLayout(folders: List[String], name: String): Option[TemplateFile] =
      site.layoutTemplates
        .get((folders, name))
        .orElse(
          if (folders.isEmpty) site.layoutTemplates.get((List(defaultLayout), name))
          else findLayout(folders.init, name),
        )
        .map { t =>
          if (t.template eq null)
            t.template = templateParser.parse(t.path.readText())
          t
        }

    case class SubHeading(heading: String, id: String, level: Int, sub: List[SubHeading])

    def subheadings(l: List[TocEntry]): List[SubHeading] =
      l.map(h => SubHeading(renderInlinesHtml(h.contents), h.id, h.level, subheadings(h.sub.headings)))

    for ((c, pageMap) <- pageEntries) {
      val outdir  = c.outdir
      val name    = c.name
      val content = c.content
      val toc     = c.toc

      templateRenderer.blocks.clear()

      val outfile =
        if (name == folderContent) {
          // Section index — outdir IS the section dir. For nested sections
          // the directory may not yet exist on disk (no `static/` overlap
          // forced its creation), so make sure it does. Idempotent.
          outdir.createDirectories()
          (outdir / "index.html").toString
        } else {
          val pagedir = outdir / name

          show(s"content: create directory $pagedir")
          pagedir.createDirectories()
          (pagedir / "index.html").toString
        }
      val sub = toc.headings.headOption match {
        case Some(h) => subheadings(h.sub.headings)
        case None    => Nil
      }
      // Full TOC tree as SubHeading data — used by the "On this page" rail
      // for showing every heading on the page, not just the children of the
      // first one (which is what `.sub` carries for back-compat).
      val tocList = subheadings(toc.headings)
      val pagedata = Map(
        "site"    -> sitedata,
        "page"    -> pageMap,
        "section" -> sectionDataFor(c),
        "content" -> content,
        "toc"     -> toc,
        "sub"     -> sub,
        "tocList" -> tocList,
      )
      val folders = {
        val rel = outdir.relativeTo(dst1)

        if (dst1 == outdir) Nil
        else {
          val all = rel.segments.toList
          if (html != "") all.drop(1) else all
        }
      }
      val layout = if (name == folderContent) folderLayout else fileLayout
      val particularTemplate = findLayout(folders, layout) match {
        case Some(TemplateFile(templatePath, _, template)) =>
          show(s"render $name using ${templatePath.relativeTo(src1)}")
          Some(template)
        case None =>
          show(s"layout '$layout' not found for rendering '$name'")
          None
      }
      val baseofTemplate = findLayout(folders, baseofLayout) match {
        case Some(TemplateFile(templatePath, _, template)) =>
          show(s"render $name using ${templatePath.relativeTo(src1)}")
          Some(template)
        case None =>
          show(s"layout '$baseofLayout' not found for rendering '$name'")
          None
      }

      def render(template: TemplateAST): Unit = {
        show(s"content: write file $outfile")
        val rendered = renderToString(templateRenderer, pagedata, template)
        Path(outfile).writeText(rendered)
      }

      (particularTemplate, baseofTemplate) match {
        case (None, None)       => problem(s"no template was found for rendering $name")
        case (Some(p), Some(b)) =>
          // First pass populates `define` blocks; rendered output is discarded.
          renderToString(templateRenderer, pagedata, p)
          render(b)
        case (Some(p), None) => render(p)
        case (None, Some(b)) => render(b)
      }
    }

    for (TemplateFile(path, _, template) <- site.otherTemplates) {
      show(s"template: write file $path")
      val rendered = renderToString(templateRenderer, Map("site" -> sitedata), template)
      path.writeText(rendered)
    }

    // ----- sitemap.xml -----
    //
    // Standard sitemaps protocol — one <url><loc>…</loc></url> per page.
    // No <lastmod> until i18n / dated frontmatter lands; <priority> /
    // <changefreq> are out-of-spec for most modern crawlers anyway.
    {
      val sb = new StringBuilder
      sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
      sb.append("""<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">""").append('\n')
      for (p <- pages) {
        val abs = p("permalink").asInstanceOf[String]
        sb.append("  <url><loc>").append(escapeXml(abs)).append("</loc></url>\n")
      }
      sb.append("</urlset>\n")
      val path = dst1 / "sitemap.xml"
      show(s"write $path")
      path.writeText(sb.toString)
    }

    // ----- Atom feeds (per section + site-wide) -----
    //
    // Atom 1.0 over RSS 2.0 because it's better-spec'd; both are still
    // recognized by every feed reader. One feed per section at
    // `<section>/feed.xml`, plus the site-wide feed at `/feed.xml`.
    //
    // Pages inside a feed sort by frontmatter `date` (ISO-8601 string)
    // descending — newest first; pages without `date` sort to the end.
    // The feed's own `<updated>` is the latest entry date, falling back to
    // the build time if no entries are dated.
    //
    // Disable site-wide with `feeds = false` in site.toml.
    val feedsEnabled: Boolean =
      confdoc.getBool("feeds").getOrElse(true)

    if (feedsEnabled) {
      def pageDate(c: ContentFile): String =
        frontmatterMap(c.page).get("date").collect { case s: String => s }.getOrElse("")

      def byDateDesc(cs: List[ContentFile]): List[ContentFile] =
        cs.sortBy(c => (pageDate(c).isEmpty, -pageDate(c).hashCode, c.name))
          .sortBy(c => if (pageDate(c).isEmpty) "9999-12-31" else pageDate(c)).reverse

      val nowIso: String =
        java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).format(
          java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        )

      def normalizeDate(s: String): String =
        // Accept "2024-01-15", "2024-01-15T10:00:00Z", or full ISO-8601 — emit
        // an Atom-friendly form. Bare YYYY-MM-DD becomes T00:00:00Z.
        if (s.isEmpty) nowIso
        else if (s.length == 10) s + "T00:00:00Z"
        else s

      def writeFeed(targetPath: Path, feedTitle: String, feedUrl: String, entries: List[ContentFile]): Unit = {
        val sb = new StringBuilder
        // Site root URL — `baseURL.base + baseURL.path` already terminates
        // either at the host (path = "/") or at the resolved subpath (no
        // trailing /). Normalize to exactly one trailing slash.
        val siteRootUrl = {
          val s = baseURL.base + baseURL.path
          if (s.endsWith("/")) s else s + "/"
        }
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<feed xmlns="http://www.w3.org/2005/Atom">""").append('\n')
        sb.append("  <title>").append(escapeXml(feedTitle)).append("</title>\n")
        sb.append("  <link href=\"").append(escapeXml(feedUrl)).append("\" rel=\"self\"/>\n")
        sb.append("  <link href=\"").append(escapeXml(siteRootUrl)).append("\"/>\n")
        sb.append("  <id>").append(escapeXml(feedUrl)).append("</id>\n")
        val newest = entries.iterator.map(pageDate).filter(_.nonEmpty).maxOption.getOrElse("")
        sb.append("  <updated>").append(escapeXml(normalizeDate(newest))).append("</updated>\n")
        confdoc.getString("author").foreach { a =>
          sb.append("  <author><name>").append(escapeXml(a)).append("</name></author>\n")
        }
        for (c <- entries) {
          val rec = basic(c)
          val abs = rec("permalink").asInstanceOf[String]
          sb.append("  <entry>\n")
          sb.append("    <title>").append(escapeXml(rec.get("title").collect { case s: String => s }.getOrElse(c.name))).append("</title>\n")
          sb.append("    <link href=\"").append(escapeXml(abs)).append("\"/>\n")
          sb.append("    <id>").append(escapeXml(abs)).append("</id>\n")
          sb.append("    <updated>").append(escapeXml(normalizeDate(pageDate(c)))).append("</updated>\n")
          val summary = rec.get("summary").collect { case s: String => s }.getOrElse("")
          if (summary.nonEmpty)
            sb.append("    <summary>").append(escapeXml(summary)).append("</summary>\n")
          if (c.content != null && c.content.nonEmpty)
            sb.append("    <content type=\"html\">").append(escapeXml(c.content)).append("</content>\n")
          sb.append("  </entry>\n")
        }
        sb.append("</feed>\n")
        targetPath.parent.foreach(_.createDirectories())
        show(s"write $targetPath")
        targetPath.writeText(sb.toString)
      }

      // Site-wide feed — every non-`_index` page, newest first.
      val allEntries = byDateDesc(contentFiles.filter(_.name != folderContent))
      val siteFeedUrl = baseURL.base + baseURL.path + (if (baseURL.path.endsWith("/")) "" else "/") + "feed.xml"
      writeFeed(
        dst1 / "feed.xml",
        confdoc.getString("title").getOrElse("Untitled"),
        siteFeedUrl,
        allEntries,
      )

      // Per-section feeds. Skip sections that have no non-`_index` pages
      // (an empty Atom feed is technically valid but adds nothing). Also
      // skip the root section — the site-wide feed already lives at
      // `dst1/feed.xml` and would otherwise be overwritten with a duplicate
      // entry list under the section's own title.
      for ((outdir, info) <- sectionInfoByOutdir if info.pages.nonEmpty && outdir != dst1) {
        val sectionTitle = info.index.flatMap { c =>
          frontmatterMap(c.page).get("title").collect { case s: String => s }
        }.getOrElse(outdir.filename)
        val sectionRel = info.index.map(c => relPermalinkFor(c)).getOrElse {
          // Fallback for sections without an _index: derive from outdir.
          val rel = outdir.relativeTo(dst1)
          val segs = if (outdir == dst1) Nil else rel.segments.toList
          val withoutHtml = if (html != "" && segs.nonEmpty) segs.drop(1) else segs
          val basePath = if (baseURL.path == "/" || baseURL.path.isEmpty) "" else baseURL.path
          if (withoutHtml.isEmpty) basePath + "/" else basePath + "/" + withoutHtml.mkString("/") + "/"
        }
        val feedUrl = baseURL.base + sectionRel + "feed.xml"
        writeFeed(
          outdir / "feed.xml",
          confdoc.getString("title").getOrElse("Untitled") + " · " + sectionTitle,
          feedUrl,
          byDateDesc(info.pages),
        )
      }
    }

    // ----- search.json -----
    //
    // Per-page index for client-side full-text search. Each entry has title,
    // url, summary, plain-text content (HTML-stripped), and the section path.
    // Themes can fetch /search.json and run substring / fuzzy match in the
    // browser. Always emitted; small enough that an unused index is no
    // burden. Strips HTML rather than re-walking the AST since the AST is
    // already gone by this point — the regex is intentionally crude
    // (replaces tags + the five named entities) but enough for matching
    // text in a search box.
    {
      val sb = new StringBuilder
      sb.append('[')
      var first = true
      for ((c, pageMap) <- pageEntries) {
        val title    = pageMap.get("title").collect { case s: String => s }.getOrElse("")
        val url      = pageMap("relPermalink").asInstanceOf[String]
        val summary  = pageMap("summary").asInstanceOf[String]
        val plain    = stripHtmlForSearch(if (c.content eq null) "" else c.content)
        if (!first) sb.append(',')
        first = false
        sb.append('{')
        sb.append("\"title\":").append(jsonString(title)).append(',')
        sb.append("\"url\":").append(jsonString(url)).append(',')
        sb.append("\"summary\":").append(jsonString(stripHtmlForSearch(summary))).append(',')
        sb.append("\"content\":").append(jsonString(plain))
        sb.append('}')
      }
      sb.append(']')
      val path = dst1 / "search.json"
      show(s"write $path")
      path.writeText(sb.toString)
    }

    // ----- 404.html (optional) -----
    //
    // If a layout named `404` exists under the default layout folder, render
    // it with site context only (no `page`) and write it to the site root.
    // This is what nginx / GitHub Pages / Netlify pick up as the not-found
    // page. Skipping silently when no such layout exists keeps it opt-in.
    findLayout(Nil, "404") match {
      case Some(TemplateFile(templatePath, _, template)) =>
        show(s"render 404.html using ${templatePath.relativeTo(src1)}")
        val rendered = renderToString(templateRenderer, Map("site" -> sitedata), template)
        (dst1 / "404.html").writeText(rendered)
      case None =>
        show("no 404 layout found; skipping 404.html")
    }

    dst1
  }

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
      force:      Boolean,
  ): Unit = {
    if (url.isEmpty) problem("theme add: a git URL is required")

    val src1     = src.normalize.toAbsolutePath
    if (!isDir(src1)) problem(s"not a readable directory: $src1")

    val confdoc  = config(src1, baseConfig)
    val themeDir = confdoc.getString("themeDir").getOrElse("themes")
    val themeName = name.getOrElse(deriveThemeName(url))

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

    println(s"juicer theme add: cloning $url${ref.map(r => s" ($r)").getOrElse("")} → $target")
    gitClone(url, target, ref) match {
      case Right(()) =>
        println(s"installed theme: $themeName")
        println(s"  set `theme = \"$themeName\"` in site.toml to activate")
      case Left(err) =>
        problem(s"theme add: $err")
    }
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

  /** Minimal XML escaping for sitemap URLs — covers the five entities the
    * sitemap protocol cares about. No need for full HTML entity coverage
    * since URLs already restrict the character set.
    */
  private def escapeXml(s: String): String =
    s.replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")

  /** Crude HTML→text stripper for the search index. Drops every tag, decodes
    * the five named entities, collapses whitespace. Not a full HTML parser —
    * fine for matching prose in a search box, not fine for displaying
    * attacker-controlled content (don't pipe this back into HTML). */
  private def stripHtmlForSearch(html: String): String = {
    val noTags = html.replaceAll("<[^>]+>", " ")
    val decoded = noTags
      .replace("&nbsp;", " ")
      .replace("&amp;", "&")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&quot;", "\"")
      .replace("&apos;", "'")
    decoded.replaceAll("\\s+", " ").trim
  }

  /** JSON-string encoder. Quotes the value, escapes the standard six
    * characters (`"`, `\`, `\n`, `\r`, `\t`, plus DEL+control chars via
    * `\uXXXX`). Avoids the overhead of pulling in a full JSON library just
    * for this one emitter. */
  private def jsonString(s: String): String = {
    val sb = new StringBuilder
    sb += '"'
    var i = 0
    while (i < s.length) {
      val ch = s.charAt(i)
      ch match {
        case '"'                  => sb.append("\\\"")
        case '\\'                 => sb.append("\\\\")
        case '\n'                 => sb.append("\\n")
        case '\r'                 => sb.append("\\r")
        case '\t'                 => sb.append("\\t")
        case c if c < ' '         => sb.append(f"\\u${c.toInt}%04x")
        case c                    => sb += c
      }
      i += 1
    }
    sb += '"'
    sb.toString
  }

  // ===== juicer-side URL template builtins =====
  //
  // squiggly dropped its own absURL / relURL when it lost java.nio.file.Paths
  // in the cross-platform port. juicer has the cross-platform `path` lib and
  // already carries a BaseURL through the renderer's `data`, so it's the
  // natural home for these. Both are arity-1 functions; the URL is the sole
  // argument (or the pipe target).

  /** Join `base` (already-resolved baseURL.path, may be empty or "/") with a
    * link-target path that may itself be already absolute (`/foo`) or relative
    * (`foo`).
    */
  private def joinUrlPath(base: String, url: String): String = {
    val basePath = if (base.endsWith("/")) base.dropRight(1) else base
    val tail     = if (url.startsWith("/")) url else "/" + url
    basePath + tail
  }

  /** Juicer-side template builtins: URL helpers + a `markdownify` wrapper
    * around the new `markdown` library. Merged into the renderer's function
    * map alongside squiggly's defaults.
    */
  private lazy val juicerUrlBuiltins: Map[String, TemplateFunction] = {
    def baseFromContext(con: io.github.edadma.squiggly.Context): BaseURL =
      con.renderer.data("baseURL").asInstanceOf[BaseURL]

    Map(
      "relURL" -> TemplateFunction(
        "relURL",
        1,
        { case (con, Seq(arg: String)) =>
          if (absoluteURL(arg)) arg
          else joinUrlPath(baseFromContext(con).path, arg)
        },
      ),
      "absURL" -> TemplateFunction(
        "absURL",
        1,
        { case (con, Seq(arg: String)) =>
          if (absoluteURL(arg)) arg
          else {
            val base = baseFromContext(con)
            base.base + joinUrlPath(base.path, arg)
          }
        },
      ),
      // Render a markdown string to HTML directly, for templates that want
      // to mix markdown into a layout without going through a content file.
      "markdownify" -> TemplateFunction(
        "markdownify",
        1,
        { case (con, Seq(s: String)) =>
          io.github.edadma.markdown.renderToHTML(parseMarkdown(s), markdownConfig).trim
        },
      ),
      // Substitute :shortcode: tokens with the corresponding Unicode emoji.
      "emojify" -> TemplateFunction(
        "emojify",
        1,
        { case (con, Seq(s: String)) => io.github.edadma.emoji.Emoji(s) },
      ),
    )
  }

  // ===== Summary computation =====

  /** Word cap applied to the auto-derived (option 3) summary path. Hugo
    * defaults to 70; juicer's docs/blog use case favours short list-page
    * blurbs, so 30 reads better in compact UIs. Easy to expose as a
    * config key later if the use cases diverge.
    */
  private val summaryWordLimit = 30

  /** Resolve `.page.summary` using a three-tier waterfall:
    *
    *   1. Explicit frontmatter `summary` field — taken verbatim.
    *   2. `<!--more-->` HTML comment in source — render the prefix (up
    *      to but not including the marker) as HTML.
    *   3. Fallback — first paragraph's plain text, capped at
    *      [[summaryWordLimit]] words and ellipsised when truncated.
    */
  private def computeSummary(
      c:            ContentFile,
      doc:          Document,
      preprocessor: Preprocessor,
      linkCallback: String => String,
  ): String = {
    val frontmatter = c.page match {
      case m: Map[?, ?] => m.asInstanceOf[Map[Any, Any]]
      case _            => Map.empty[Any, Any]
    }
    frontmatter.get("summary").collect { case s: String => s } match {
      case Some(s) => s
      case None =>
        val src     = c.source
        val moreIdx = src.indexOf("<!--more-->")
        if (moreIdx >= 0) {
          val before = src.substring(0, moreIdx)
          val parsed = transformLinks(parseMarkdown(preprocessor.process(before)), linkCallback)
          io.github.edadma.markdown.renderToHTML(parsed, markdownConfig).trim
        } else {
          // Walk the AST for the first paragraph (skip leading headings,
          // thematic breaks, etc.). plainText strips inline formatting
          // (emphasis, code spans, link wrapping) and we then collapse runs
          // of whitespace before splitting on word boundaries.
          doc.children.collectFirst {
            case p: io.github.edadma.markdown.Paragraph => p
          } match {
            case Some(io.github.edadma.markdown.Paragraph(inlines)) =>
              val text  = io.github.edadma.markdown.plainText(inlines)
              val words = text.split("\\s+").filter(_.nonEmpty).toList
              if (words.length <= summaryWordLimit) text
              else words.take(summaryWordLimit).mkString(" ") + "…"
            case None => ""
          }
        }
    }
  }

  // ===== AST transforms applied to each content document before rendering =====

  /** Shift every heading's level by `by` (clamped to [1, 6]). */
  private def shiftHeadings(doc: Document, by: Int): Document = {
    def shiftBlock(b: io.github.edadma.markdown.Block): io.github.edadma.markdown.Block = b match {
      case h: MdHeading                            => h.copy(level = math.min(6, math.max(1, h.level + by)))
      case io.github.edadma.markdown.BlockQuote(c) => io.github.edadma.markdown.BlockQuote(c.map(shiftBlock))
      case other                                   => other
    }
    Document(doc.children.map(shiftBlock))
  }

  /** Apply `f` to every link / image destination in the document. */
  private def transformLinks(doc: Document, f: String => String): Document = {
    def goInline(i: Inline): Inline = i match {
      case Link(dest, title, children)              => Link(f(dest), title, children.map(goInline))
      case io.github.edadma.markdown.Image(dest, title, alt, attrs) =>
        io.github.edadma.markdown.Image(f(dest), title, alt.map(goInline), attrs)
      case io.github.edadma.markdown.Emphasis(c)    => io.github.edadma.markdown.Emphasis(c.map(goInline))
      case io.github.edadma.markdown.Strong(c)      => io.github.edadma.markdown.Strong(c.map(goInline))
      case io.github.edadma.markdown.Strikethrough(c) =>
        io.github.edadma.markdown.Strikethrough(c.map(goInline))
      case other => other
    }
    def goBlock(b: io.github.edadma.markdown.Block): io.github.edadma.markdown.Block = b match {
      case Paragraph(inlines)                       => Paragraph(inlines.map(goInline))
      case h: MdHeading                             => h.copy(inlines = h.inlines.map(goInline))
      case io.github.edadma.markdown.BlockQuote(c)  => io.github.edadma.markdown.BlockQuote(c.map(goBlock))
      case other                                    => other
    }
    Document(doc.children.map(goBlock))
  }

  // ===== Squiggly rendering helper =====

  /** Render a template against `data` and return the result as a `String`,
    * since juicer ultimately writes the bytes through `path.writeText` rather
    * than streaming directly to a file handle.
    */
  private def renderToString(renderer: TemplateRenderer, data: Any, template: TemplateAST): String = {
    val buf = new java.io.ByteArrayOutputStream
    val out = new java.io.PrintStream(buf)
    renderer.render(data, template, out)
    buf.toString
  }

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
