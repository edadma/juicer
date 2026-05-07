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
    case Args(baseConfig, verbose, baseurl, Some(BuildCommand(src, dst))) =>
      build(baseConfig, verbose, baseurl, src, dst)
    case Args(baseConfig, verbose, baseurl, Some(ServeCommand(src, dst, host, port))) =>
      val outDir = build(baseConfig, verbose, baseurl, src, dst)
      serve(outDir, host, port)
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

  def build(baseConfig: String, verbose: Boolean, baseurl: Option[String], src: Path, dst: Path): Path = {
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

    val site = Process(src1, dst1, conf)
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
    // and produce the rendered HTML body. Heading levels and link
    // destinations are pre-transformed at the AST level so the output blends
    // into a layout that already provides an outer `<h1>` for the page title.
    for (case c @ ContentFile(_, name, _, _, _, _) <- site.content) {
      show(s"parse markdown file $name")

      val raw  = parseMarkdown(preprocessor.process(c.source))
      val doc  = transformLinks(shiftHeadings(raw, by = 2), linkCallback)

      c.toc = buildToc(doc)
      c.content = io.github.edadma.markdown.renderToHTML(doc, markdownConfig).trim
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
              TOCLink(
                renderInlinesHtml(h.toc.headings.head.contents),
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
    val sitedata      = confdata + ("toc" -> sitetoc.toList) + ("start" -> start)
    val defaultLayout = conf.defaultLayout
    val baseofLayout  = conf.baseofLayout
    val fileLayout    = conf.fileLayout
    val folderLayout  = conf.folderLayout
    val folderContent = conf.folderContent
    val html          = conf.htmlDir

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

    case class SubHeading(heading: String, id: String, sub: List[SubHeading])

    def subheadings(l: List[TocEntry]): List[SubHeading] =
      l.map(h => SubHeading(renderInlinesHtml(h.contents), h.id, subheadings(h.sub.headings)))

    for (case ContentFile(outdir, name, data, _, content, toc) <- site.content) {
      templateRenderer.blocks.clear()

      val outfile =
        if (name == folderContent) (outdir / "index.html").toString
        else {
          val pagedir = outdir / name

          show(s"content: create directory $pagedir")
          pagedir.createDirectories()
          (pagedir / "index.html").toString
        }
      val sub = toc.headings.headOption match {
        case Some(h) => subheadings(h.sub.headings)
        case None    => Nil
      }
      val pagedata = Map(
        "site"    -> sitedata,
        "page"    -> data,
        "content" -> content,
        "toc"     -> toc,
        "sub"     -> sub,
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

    dst1
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
