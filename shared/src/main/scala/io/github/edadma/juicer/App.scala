package io.github.edadma.juicer

import io.github.edadma.markdown.{Document, Inline, Heading => MdHeading, Link, Paragraph}
import io.github.edadma.path.Path
import io.github.edadma.squiggly.{BaseURL, Slug, TemplateAST, TemplateBuiltin, TemplateFunction, TemplateLoader, TemplateRenderer}
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
      themeAdd(baseConfig, src, url, name, ref, subdir, force)
    case Args(baseConfig, _, _, Some(ThemeUpgradeCommand(src, name, refOverride))) =>
      themeUpgrade(baseConfig, src, name, refOverride)
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
  ): Path = {
    showSteps = verbose

    // Canonicalize the user-supplied source path *after* making it absolute.
    // `Path.normalize` can't fold a leading `..` against an empty segment
    // vector, so an earlier `.normalize.toAbsolutePath` left `-s ../foo`
    // shaped paths as `<cwd>/../foo` with the `..` intact. `Process` then
    // computed its theme/public excludes with another `.normalize` after
    // concatenation (which DID fold), so the walked-dir set and the
    // exclude set never compared equal — vendored themes leaked into the
    // site's "other templates" pass and tripped a NoSuchFileException on
    // the theme's `404.html` layout. See `RelativeSourcePathSpec`.
    val src1 = src.toAbsolutePath.normalize

    show(s"source path = $src1")

    if (!isDir(src1)) problem(s"not a readable directory: $src1")

    // ----- Syntax highlighting (Phase 3, Stage 4 of juicerblog polish) -----
    //
    // Sites opt in by dropping `*.tmLanguage.json` files into `<src>/grammars/`.
    // We parse each one once into a `Highlighter` (cached for the build),
    // then build a `(code, lang) => Option[html]` callback that the markdown
    // lib calls per fenced code block. Sites with no `grammars/` directory
    // get a no-op callback — markdown emits plain `<pre><code>` and the
    // theme's CSS handles the un-highlighted case.
    //
    // ClassMode("hl-") means highlighter emits `<span class="hl-keyword">`
    // etc.; the theme owns the colors via CSS. Swap to `InlineMode(theme)`
    // if you want fixed colors baked into the HTML — see loadHighlighters'
    // `mode` parameter.
    val grammarsDir   = src1 / "grammars"
    val highlighters  = loadHighlighters(grammarsDir)
    if (highlighters.nonEmpty)
      show(s"highlighter: ${highlighters.size} grammar(s) loaded from $grammarsDir (${highlighters.keys.mkString(", ")})")
    val siteMarkdownConfig = buildMarkdownConfig(buildCodeHighlighter(highlighters))

    val confdoc      = config(src1, baseConfig)
    val baseURLstr   = baseurl.orElse(confdoc.getString("baseURL")).getOrElse("http://localhost:8080")
    val confdata: VectorMap[String, Any] = {
      val base = tomlObject(confdoc)
      base + ("baseURL" -> baseURLstr)
    }

    // Hoisted up early so the future-post filter (next stage) can exempt
    // event-section pages — events are inherently announced *before* they
    // happen, so future-skip would silently drop both `.site.events` entries
    // AND the corresponding event detail pages from the build. Default
    // `"events"`; override via `eventsSection = "..."` in `site.toml`.
    val eventsSection: String =
      confdoc.getString("eventsSection").getOrElse("events")

    // ----- Permalink templates (Phase 2.6) -----
    //
    // `[permalinks]` table in `site.toml` maps a section name to a URL
    // pattern. Sections without an entry keep juicer's existing physical-
    // path-derived URL — preserving byte-identical output for docs sites
    // that don't opt in. Tokens recognized in the pattern:
    //
    //   :year   :month   :day   :slug   :title   :section
    //
    // `:year`/`:month`/`:day` come from the parsed `.page.date` (frontmatter
    // or filesystem-mtime fallback); `:slug` is the cleaned filename;
    // `:title` is `Slug.slugify(.page.title)` (falls back to `:slug` when title
    // is absent); `:section` is the section name itself. Section pages
    // (`_index.md`) are NEVER routed through permalink templates — they
    // always sit at the section root.
    val permalinks: Map[String, String] = confdata.get("permalinks") match {
      case Some(m: Map[?, ?]) =>
        m.collect { case (k: String, v: String) => k -> v }.toMap
      case _ => Map.empty[String, String]
    }

    // ConfigWrapper takes a TomlDocument; for fields we bake into the
    // wrapper by overlaying the explicit baseURL on top of the parsed doc.
    val conf = new ConfigWrapper(confdoc.copy(root = confdoc.root + ("baseURL" -> TomlValue.Str(baseURLstr))))

    val dst1 = if (dst eq null) (src1 / conf.publicDir).toAbsolutePath.normalize else dst.toAbsolutePath.normalize

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
    // ----- i18n strings -----
    //
    // Each `<src>/i18n/<lang>.toml` file becomes one entry in the
    // `i18nStrings` map. Top-level string keys are flattened into a
    // Map[String, String]; nested tables are not supported (yet — flat
    // is enough for the common case of UI-string translation, and avoids
    // surprising path syntax in templates).
    val i18nStrings: Map[String, Map[String, String]] = {
      val i18nDir = (src1 / "i18n").normalize
      if (!isDir(i18nDir)) Map.empty
      else
        list(i18nDir)
          .filter(p => isFile(p) && p.toString.endsWith(".toml"))
          .map { p =>
            val lang = p.filename.stripSuffix(".toml")
            val doc  = readConfig(p)
            val flat = doc.root.collect { case (k, TomlValue.Str(v)) => k -> v }.toMap
            lang -> flat
          }.toMap
    }

    val rendererData =
      Map(
        "baseURL"     -> baseURL,
        "link"        -> linkCallback,
        "i18n"        -> i18nStrings,
        "defaultLang" -> conf.defaultLanguage,
      )

    show(s"base URL = ${baseURL.base}${baseURL.path}")

    val unfilteredSite = Process(src1, dst1, conf, drafts)

    // ----- Future-post filter (Phase 2.1) -----
    //
    // Pages whose parsed `date:` is strictly after `now` are removed from
    // the site UNLESS `--future` was passed. Same skip semantics as the
    // `drafts` filter — invisible to TOC, sitemap, search, section
    // listings, taxonomy archives, etc. Pages without a `date:` (mtime
    // fallback) are never future-skipped: only authored future-dating
    // counts.
    //
    // Event-section pages are exempt: events are announced *before* they
    // happen, so future-filtering would silently eat them — both their
    // detail pages and their `.site.events` entries — exactly the wrong
    // behaviour for a calendar-driven section. The exemption keys off the
    // `eventsSection` config (default `"events"`); themes that don't ship
    // an events section are unaffected because nothing matches.
    val site = if (future) unfilteredSite
    else {
      val now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
      // dst1 is the *output* root, not the content root, so we can't use
      // `c.outdir.relativeTo(dst1)` to detect section here without first
      // computing the source-relative path. The simpler check: does the
      // ContentFile's source path live under `<src>/content/<eventsSection>/`?
      // src1 is available in the enclosing scope via `Process(src1, ...)`.
      val contentRoot = (src1 / "content").normalize.toAbsolutePath
      val eventsRoot  = (contentRoot / eventsSection).normalize.toAbsolutePath
      def isInEventsSection(c: ContentFile): Boolean = {
        if (c.srcPath eq null) false
        else {
          // Cross-platform prefix check: accept both `/` and `\` as the
          // segment separator. `java.io.File.separator` would be cleaner
          // on JVM but is unavailable in Scala.js, breaking the shared
          // build. The Path library normalises paths internally; in
          // practice this always sees `/` on macOS / Linux / Native /
          // Node, and `\` on Windows JVM — admitting both is correct
          // everywhere.
          val p   = c.srcPath.normalize.toAbsolutePath.toString
          val ev  = eventsRoot.toString
          if p == ev then true
          else if !p.startsWith(ev) then false
          else {
            val next = p.charAt(ev.length)
            next == '/' || next == '\\'
          }
        }
      }
      def fmStr(c: ContentFile): Option[String] = c.page match {
        case m: Map[?, ?] => m.asInstanceOf[Map[Any, Any]].get("date").collect { case s: String => s }
        case _ => None
      }
      def isFuture(c: ContentFile): Boolean =
        fmStr(c).flatMap(parseDateString).exists(_.isAfter(now))
      val filteredContent = unfilteredSite.content.filterNot {
        case c: ContentFile =>
          val skip = isFuture(c) && !isInEventsSection(c)
          if (skip) show(s"skipping future-dated: ${c.name}")
          skip
        case _ => false
      }
      val filteredMap = unfilteredSite.map.filterNot { case (_, c) =>
        isFuture(c) && !isInEventsSection(c)
      }
      unfilteredSite.copy(content = filteredContent, map = filteredMap)
    }

    val partialsLoader: TemplateLoader =
      (name: String) =>
        site.partialTemplates.get(name).map { t =>
          if (t.template eq null)
            t.template = templateParser.parse(t.path.readText())

          t.template
        }.orElse(problem(s"partial '$name' not found"))

    // `[images]` block in site.toml → variant generator. Parsing is
    // tolerant of missing/malformed keys (falls back to disabled),
    // and the backend probes for `magick` on first use, so the bare
    // construction here never throws or shells out.
    val imageVariantConfig = ImageVariants.Config.parseFromToml(confdata.toMap)
    val imageGen: ImageVariantGenerator =
      new ImageVariantGenerator(
        config  = imageVariantConfig,
        backend = newImageEncoderBackend(),
        srcRoot = src1,
        dstRoot = dst1,
      )

    val templateRenderer: TemplateRenderer =
      new TemplateRenderer(
        partials  = partialsLoader,
        data      = rendererData,
        functions = TemplateBuiltin.functions ++ juicerUrlBuiltins(siteMarkdownConfig, src1, dst1, imageGen),
      )
    val shortcodesLoader: TemplateLoader =
      (name: String) =>
        site.shortcodeTemplates.get(name).map { t =>
          if (t.template eq null)
            t.template = templateParser.parse(t.path.readText())

          t.template
        }.orElse(problem(s"shortcode '$name' not found"))
    val preprocessor = new Preprocessor(shortcodes = shortcodesLoader, renderer = templateRenderer)
    // Deferred preprocessor: same shortcode registry, but uses the
    // `[~ ... ~]` delimiter pair and runs AFTER the section / page /
    // site pipeline has produced its records. Shortcodes invoked this
    // way see `.page.pages`, `.page.subsections`, `.page.permalink`,
    // and a full `.site.*` — anything that ordinary `[= ... =]`
    // shortcodes can't reach because they run before the pipeline.
    // The two passes share templates: a theme can call the same
    // template from either delimiter, and only the surrounding
    // context differs. Trade-off: deferred shortcodes operate on the
    // RENDERED HTML, so they emit HTML directly (the markdown parser
    // has already run by the time they fire).
    val deferredPreprocessor =
      new Preprocessor(
        startDelim = "[~",
        endDelim   = "~]",
        shortcodes = shortcodesLoader,
        renderer   = templateRenderer,
      )

    // Outgoing-link inverted index, built as the markdown pass scans
    // each file. The map's key is the source ContentFile; the value is
    // the set of internal link destinations that file references —
    // site-absolute, with absolute URLs (`http://`, `mailto:`, etc.)
    // and fragment-only anchors (`#…`) filtered out at collection
    // time. `buildBasic` inverts this to produce each page's
    // `.page.backlinks` list.
    val outLinksPerFile: scala.collection.mutable.HashMap[ContentFile, Set[String]] =
      scala.collection.mutable.HashMap.empty

    // Markdown render pass: parse each content file's source, build the TOC,
    // produce the rendered HTML body, and compute a summary. Heading levels
    // and link destinations are pre-transformed at the AST level so the
    // output blends into a layout that already provides an outer `<h1>` for
    // the page title.
    for (case c @ ContentFile(_, name, _, _, _, _, _, _) <- site.content) {
      show(s"parse markdown file $name")

      val raw = parseMarkdown(preprocessor.process(c.source), siteMarkdownConfig)
      val doc = transformLinks(shiftHeadings(raw, by = conf.int.headingShift), linkCallback)

      outLinksPerFile(c) = collectLinkTargets(raw)
      c.toc = buildToc(doc)
      c.content = io.github.edadma.markdown.renderToHTML(doc, siteMarkdownConfig).trim
      c.summary = computeSummary(c, doc, preprocessor, linkCallback, siteMarkdownConfig)
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

    // Permalink helpers (`sectionName`, `applyPermalinkPattern`,
    // `permalinkSegments`, `relPermalinkFor`) live below `pageInstant` —
    // they reference the parsed-date helper and Scala's local-def forward-
    // reference rule disallows skipping over an intervening `val`
    // declaration (`monthNames`).

    /** Coerce a frontmatter `Any` (typically `Map[String, Any]`, possibly
      * empty) to a `Map[String, Any]`; non-map / null frontmatter degrades
      * to an empty map.
      */
    def frontmatterMap(page: Any): Map[String, Any] = page match {
      case m: Map[?, ?] =>
        m.collect { case (k: String, v) => k -> v }.toMap
      case _ => Map.empty[String, Any]
    }

    // ----- contentFiles + section graph (early — required by cascade) -----
    //
    // Hoisted above pageInstant / applyPermalinkPattern / etc. so the
    // cascade helpers below (which need `sectionIndex`) are reachable
    // from any def downstream. Scala's local-def forward-reference rule
    // disallows skipping over an intervening `val`, so if cascade lived
    // farther down it couldn't be called from helpers up here.

    val contentFiles: List[ContentFile] = site.content.collect { case c: ContentFile => c }

    /** Map from outdir → its `_index.md` page (if any). One section per
      * directory; absent if the section was created implicitly via a
      * non-`_index` child. */
    val sectionIndex: Map[Path, ContentFile] =
      contentFiles.collect { case c if c.name == folderContent => c.outdir -> c }.toMap

    // ----- Frontmatter cascade -----
    //
    // A section's `_index.md` may declare a `cascade:` map in frontmatter;
    // every descendant page inherits those keys unless it sets its own.
    // Nearer ancestor wins over farther; the page's own frontmatter wins
    // over all cascade. Mirrors Hugo's `cascade` shape.
    //
    // Implementation: per-call walk from the root down to the page's
    // containing section, accumulating each level's cascade map. A
    // section's own `_index.md` does NOT inherit its own cascade (the
    // cascade applies to descendants, not to the declaring section
    // itself), so the chain for an `_index.md` drops its own outdir.

    /** Extract the `cascade:` map from a section's frontmatter. Returns
      * empty if absent or the value isn't a map. */
    def cascadeOfSection(section: ContentFile): Map[String, Any] =
      frontmatterMap(section.page).get("cascade") match {
        case Some(m: Map[?, ?]) =>
          m.collect { case (k: String, v) => k -> v }.toMap
        case _ => Map.empty[String, Any]
      }

    /** Ordered chain of section outdirs whose cascade applies to `c` —
      * root first, c's own section last. Excludes c's own outdir when c
      * IS its section's `_index.md` (a section's cascade doesn't apply
      * to itself). */
    def cascadeChain(c: ContentFile): List[Path] = {
      val rel   = c.outdir.relativeTo(dst1)
      val parts = rel.segments.toList
      val chain = (0 to parts.length).map(i => parts.take(i).foldLeft(dst1)(_ / _)).toList
      val isOwn = c.name == folderContent
      if (isOwn) chain.dropRight(1) else chain
    }

    /** Resolved cascade map for `c` — merge of every ancestor section's
      * cascade, with nearer ancestors winning. Empty when no cascade is
      * declared anywhere on the chain. */
    def effectiveCascadeFor(c: ContentFile): Map[String, Any] =
      cascadeChain(c).foldLeft(Map.empty[String, Any]) { (acc, path) =>
        sectionIndex.get(path).map(idx => acc ++ cascadeOfSection(idx)).getOrElse(acc)
      }

    /** Effective frontmatter for `c` — cascade merged with the page's
      * own frontmatter (page wins). This is what every downstream reader
      * should call when it has a ContentFile in hand; the raw
      * `frontmatterMap` remains for non-cascade contexts (e.g.
      * inspecting an arbitrary page map). */
    def effectiveFrontmatter(c: ContentFile): Map[String, Any] = {
      val own = frontmatterMap(c.page)
      val cas = effectiveCascadeFor(c)
      if (cas.isEmpty) own else cas ++ own
    }

    // ----- Date formatting (Phase 1.4) -----
    //
    // `date` frontmatter is parsed by the package-level `parseDateString`
    // (in package.scala) so the future-post filter can call it before
    // `App.build`'s local helpers come into scope. Three rendered helpers
    // ride alongside: `.page.dateISO` (ISO-8601 with offset, machine-
    // friendly), `.page.dateLong` (e.g. "January 15, 2024"),
    // `.page.dateShort` (`YYYY-MM-DD`). The parsed instant itself is exposed
    // as `.page.date` so templates that want their own format can call
    // `format` through any future formatter helpers.
    //
    // English month names hand-written rather than
    // `DateTimeFormatter.ofPattern("MMMM …")` so the long-form date renders
    // identically on JVM / JS / Native. The `scala-java-time` Native build
    // ships a minimal locale db, and `MMMM` there falls back to "M01"/"M02"/…
    // without explicit Locale plumbing.
    val monthNames = Array(
      "January", "February", "March", "April", "May", "June",
      "July", "August", "September", "October", "November", "December",
    )
    val dateShortFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
    def formatDateLong(t: java.time.OffsetDateTime): String =
      s"${monthNames(t.getMonthValue - 1)} ${t.getDayOfMonth}, ${t.getYear}"

    // ----- Word count + reading time (Phase 1.3) -----
    //
    // Word count walks the rendered HTML rather than the markdown AST so it
    // sees the same prose users do — tables, lists, callout body text, even
    // shortcode-expanded paragraphs all contribute. Code blocks are included
    // (Hugo and Jekyll both do); a future config knob could exclude them if
    // someone files an issue.
    //
    // Reading time follows the Medium-popularized 200-words-per-minute
    // ceiling. Empty pages get 0; otherwise the floor is 1 minute so a
    // 30-word stub doesn't say "0 min read".
    def wordsOf(html: String): Int = {
      if (html eq null) 0
      else {
        val plain = stripHtmlForSearch(html)
        if (plain.isEmpty) 0
        else plain.split("\\s+").count(_.nonEmpty)
      }
    }

    def readingTimeOf(words: Int): Int =
      if (words <= 0) 0
      else math.max(1, math.ceil(words / 200.0).toInt)

    /** Resolve a content file's effective publication time. Frontmatter
      * `date` wins; when absent or unparseable, fall back to the source
      * file's filesystem mtime. Fresh tmp paths used in tests have a
      * recent mtime, so the fallback always yields a valid timestamp. */
    def pageInstant(c: ContentFile): java.time.OffsetDateTime = {
      val fmDate = effectiveFrontmatter(c).get("date").collect { case s: String => s }
      fmDate.flatMap(parseDateString).getOrElse {
        val ms = if (c.srcPath ne null) c.srcPath.lastModified else 0L
        java.time.OffsetDateTime.ofInstant(
          java.time.Instant.ofEpochMilli(ms),
          java.time.ZoneOffset.UTC,
        )
      }
    }

    // ----- Permalink-template helpers (Phase 2.6) -----
    //
    // Sit below `pageInstant` because `applyPermalinkPattern` resolves date
    // tokens through it (Scala's local-def forward-reference rule disallows
    // jumping over an intervening `val`).

    /** First-segment section name for a content file's URL. Used as the key
      * into `[permalinks]`. `htmlDir` is stripped before the head segment
      * is taken — the URL surface is what counts, not the on-disk layout.
      * Returns `""` for content sitting at the site root (which can never
      * permalink-route since there's no section table key to match).
      */
    def sectionName(c: ContentFile): String = {
      val rel = c.outdir.relativeTo(dst1)
      val segs = if (c.outdir == dst1) Nil else rel.segments.toList
      val withoutHtml = if (html != "" && segs.nonEmpty) segs.drop(1) else segs
      withoutHtml.headOption.getOrElse("")
    }

    /** Substitute `:slug` / `:year` / `:month` / `:day` / `:title` /
      * `:section` in a permalink pattern. Returns the resulting URL
      * segments (no leading or trailing slash, no `htmlDir`). The pattern
      * is treated as a path: extra `/`s are normalized, and an empty
      * pattern yields an empty list (URL = site root).
      */
    def applyPermalinkPattern(c: ContentFile, pattern: String, sectionN: String): List[String] = {
      val instant = pageInstant(c)
      val frontmatter = effectiveFrontmatter(c)
      val title = frontmatter.get("title").collect { case s: String => s }.getOrElse(c.name)
      // Order matters: `:section` must substitute before `:s...` shorts in
      // case a future token shares a prefix. Today every token is unique
      // so the order is incidental, but preserving the rule keeps a future
      // `:slug-fragment` token (or similar) safe.
      val expanded = pattern
        .replace(":year",    f"${instant.getYear}%04d")
        .replace(":month",   f"${instant.getMonthValue}%02d")
        .replace(":day",     f"${instant.getDayOfMonth}%02d")
        .replace(":title",   Slug.slugify(title))
        .replace(":section", sectionN)
        .replace(":slug",    c.name)
      val trimmed = expanded.stripPrefix("/").stripSuffix("/")
      if (trimmed.isEmpty) Nil
      else trimmed.split('/').filter(_.nonEmpty).toList
    }

    /** Optional permalink-template URL segments for a content file. Returns
      * `Some(segs)` only when a `[permalinks]` entry matches the section
      * AND the page is a non-`_index` content file. Section pages and
      * unmatched sections fall through (`None`) so the caller uses the
      * default physical-path URL.
      */
    def permalinkSegments(c: ContentFile): Option[List[String]] = {
      if (c.name == folderContent) None
      else {
        val sectionN = sectionName(c)
        permalinks.get(sectionN).map(p => applyPermalinkPattern(c, p, sectionN))
      }
    }

    /** Path-only URL for a content file, prefixed by `baseURL.path`.
      *
      * Two regimes:
      *   1. Section in `[permalinks]` — URL follows the configured pattern
      *      (`:year/:month/:slug/`, etc.).
      *   2. Otherwise — physical layout drives the URL. Folder content
      *      (`_index.md`) lives at the section directory; every other file
      *      becomes its own pretty-URL directory (`/<name>/`). The
      *      configured `htmlDir` is stripped since it's a filesystem-only
      *      convenience for keeping static assets alongside rendered pages.
      */
    def relPermalinkFor(c: ContentFile): String = {
      val basePath =
        if (baseURL.path == "/" || baseURL.path.isEmpty) ""
        else baseURL.path
      permalinkSegments(c) match {
        case Some(segs) =>
          if (segs.isEmpty) basePath + "/"
          else basePath + "/" + segs.mkString("/") + "/"
        case None =>
          val rel        = c.outdir.relativeTo(dst1)
          val allSegs    = if (c.outdir == dst1) Nil else rel.segments.toList
          val withoutHtml = if (html != "" && allSegs.nonEmpty) allSegs.drop(1) else allSegs
          val pathSegs   =
            if (c.name == folderContent) withoutHtml
            else withoutHtml :+ c.name
          val joined     = pathSegs.mkString("/")
          if (joined.isEmpty) basePath + "/"
          else basePath + "/" + joined + "/"
      }
    }

    // ----- i18n (Tier 2 #10) -----

    /** Languages the site is published in. Empty list = single-language site
      * (no URL prefix, no `.page.lang`, no `.page.translations`). */
    val langs: List[String] = conf.languages

    /** Default language code. Falls back to the first declared language, or
      * `""` for single-language sites. */
    val defaultLang: String = conf.defaultLanguage

    /** Reverse map from `ContentFile` to its source path under `contentDir`
      * (e.g. `"en/getting-started.md"`). Looked up by reference identity to
      * avoid case-class equality on a class with var fields. */
    val sourcePathOf: ContentFile => String = {
      val byCf = site.map.iterator.map { case (k, v) => v -> k }.toList
      (c: ContentFile) => byCf.find(_._1 eq c).map(_._2).getOrElse("")
    }

    /** Language of a content file. Detected from the first segment of its
      * source path under `contentDir`: if that segment matches one of the
      * configured `languages`, that's the lang. Otherwise `""`
      * (single-language fallback). */
    def langOf(c: ContentFile): String =
      if (langs.isEmpty) ""
      else {
        val sp = sourcePathOf(c)
        val first = sp.takeWhile(_ != '/')
        if (langs.contains(first)) first else ""
      }

    /** "Stem" of a content file — its source path with the leading lang
      * segment stripped. Translations of the same conceptual page share a
      * stem (e.g. `getting-started.md` for both `en/getting-started.md` and
      * `fr/getting-started.md`). */
    def stemOf(c: ContentFile): String = {
      val sp = sourcePathOf(c)
      val l  = langOf(c)
      if (l.isEmpty) sp
      else if (sp.startsWith(l + "/")) sp.drop(l.length + 1)
      else sp
    }

    /** All content files grouped by their stem. Used to compute
      * `.page.translations`. */
    val byStem: Map[String, List[ContentFile]] =
      if (langs.isEmpty) Map.empty else contentFiles.groupBy(stemOf)

    // ----- Section / navigation graph -----

    /** Frontmatter weight for sorting. Default is `Long.MaxValue / 2` so
      * pages with no `weight` cluster after explicitly weighted ones but
      * before any sentinel value an author might choose. */
    def pageWeight(c: ContentFile): Long =
      effectiveFrontmatter(c).get("weight").collect {
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

    // ----- Author registry (Phase 2.3) -----
    //
    // Site config:
    //
    //   [[authors]]
    //   id     = "ed"
    //   name   = "Edward A Maxedon"
    //   bio    = "..."
    //   avatar = "/img/ed.jpg"
    //
    //   [[authors.links]]
    //   label = "GitHub"
    //   url   = "https://github.com/edadma"
    //
    // The registry surfaces per-page as `.page.author` (singular —
    // resolved from frontmatter `author: <id>`) and `.page.authors`
    // (always a list — frontmatter `authors: [<id>, ...]`, falling back
    // to `[ author ]` if only the singular field is set). Each resolved
    // value is the FULL author record from the registry, not just the id.
    //
    // Authors with at least one referencing page get a `/authors/<id>/`
    // archive page (gated by an optional `author-page.html` layout).
    // Raw `[[authors]]` list in declaration order — useful for staff
    // directories where the full team needs to render even if some members
    // haven't authored a referenced page (`.site.authors` filters to those
    // who have). Surfaced on `.site.authorRegistry`.
    val authorRegistryList: List[Map[String, Any]] = confdata.get("authors") match {
      case Some(xs: List[?]) =>
        xs.collect {
          case m: Map[?, ?] =>
            m.asInstanceOf[Map[Any, Any]].collect { case (k: String, v) => k -> v }.toMap
        }
      case _ => Nil
    }

    val authorRegistry: Map[String, Map[String, Any]] =
      authorRegistryList.flatMap { m =>
        m.get("id").collect { case s: String => s }.map(id => id -> m)
      }.toMap

    // ----- Author archive URL prefix -----
    //
    // `site.toml` `authorsPath` (string, optional) overrides the default
    // `/authors/` path under which both the team listing and per-author
    // archives are emitted. Themes intended for cafes / studios / agencies
    // often want to call this `/team/`; doc sites might want `/people/`,
    // etc. Default keeps the legacy `/authors/` behaviour so existing sites
    // don't move.
    //
    // Forgiving normalization: leading/trailing slashes are added if
    // missing, so `team`, `/team`, `team/`, `/team/` all become `/team/`.
    // `..` segments are rejected outright (path traversal). Empty after
    // normalization → falls back to default. Bad shape (non-string,
    // multi-segment with `..`) → warning + default.
    val authorsPath: String = {
      val raw = confdata.get("authorsPath").collect { case s: String => s }
      raw match {
        case None => "/authors/"
        case Some(s) =>
          val trimmed = s.trim
          val segments = trimmed.split('/').filter(_.nonEmpty).toList
          if (segments.isEmpty) "/authors/"
          else if (segments.contains("..") || segments.contains(".")) {
            Console.err.println(s"site.toml authorsPath '$s' contains '.' or '..' segments; falling back to /authors/")
            "/authors/"
          } else "/" + segments.mkString("/") + "/"
      }
    }
    /** Path segments derived from `authorsPath`, with leading/trailing
      * slashes stripped. Used to build the on-disk output directory. */
    val authorsDirSegments: List[String] =
      authorsPath.split('/').filter(_.nonEmpty).toList

    /** Resolve a frontmatter `author` / `authors` field to a list of
      * registry-rich author records. Unknown ids fall back to a stub
      * record with just `{id: <id>}` so templates that read `.page.author.name`
      * don't NPE on a typo — the missing fields render as empty.
      */
    def resolveAuthors(fm: Map[String, Any]): List[Map[String, Any]] = {
      val ids: List[String] = fm.get("authors") match {
        case Some(xs: List[?])  => xs.collect { case s: String => s }
        case Some(s: String)    => List(s)
        case _ => fm.get("author") match {
          case Some(s: String) => List(s)
          case _               => Nil
        }
      }
      ids.map(id => authorRegistry.getOrElse(id, Map[String, Any]("id" -> id)))
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
    /** Build the thin record entries for `.page.backlinks` — every page
      * that links to `target`'s permalink. Returns `{title, url,
      * summary}` per source page (no nested fields, so we don't
      * recurse into the source's own backlinks). Sources are sorted by
      * title for deterministic output. */
    def backlinksFor(target: ContentFile): List[Map[String, Any]] = {
      val targetUrl = relPermalinkFor(target)
      val rows = outLinksPerFile.iterator.flatMap {
        case (src, outs) if (src ne target) && outs.contains(targetUrl) =>
          val fm      = effectiveFrontmatter(src)
          val srcUrl  = relPermalinkFor(src)
          val title   = fm.get("title").collect { case s: String => s }.getOrElse(srcUrl)
          val summary = if (src.summary eq null) "" else src.summary
          Some(Map[String, Any](
            "title"   -> title,
            "url"     -> srcUrl,
            "summary" -> summary,
          ))
        case _ => None
      }
      rows.toList.sortBy(_("title").asInstanceOf[String])
    }

    def buildBasic(c: ContentFile): Map[String, Any] = {
      val rel   = relPermalinkFor(c)
      val abs   = baseURL.base + rel
      val inst  = pageInstant(c)
      val words = wordsOf(c.content)
      val fm    = effectiveFrontmatter(c)
      val authors = resolveAuthors(fm)
      // `slug` — the URL stem, useful for in-page anchors and CSS hooks
      // when a template walks `.section.pages` and wants to namespace
      // each section's HTML id. For `/menu/espresso/` it's `espresso`;
      // for `/menu/` it's `menu`; for the root `/` it's `""`.
      val slug = {
        val trimmed = rel.stripPrefix("/").stripSuffix("/")
        if (trimmed.isEmpty) "" else trimmed.split('/').last
      }
      val core = fm ++ Map(
        "permalink"    -> abs,
        "relPermalink" -> rel,
        "url"          -> rel,
        "slug"         -> slug,
        "summary"      -> (if (c.summary eq null) "" else c.summary),
        "isSection"    -> (c.name == folderContent),
        "lang"         -> langOf(c),
        "date"         -> inst,
        "dateISO"      -> inst.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        "dateLong"     -> formatDateLong(inst),
        "dateShort"    -> inst.format(dateShortFormatter),
        "wordCount"    -> BigDecimal(words),
        "readingTime"  -> BigDecimal(readingTimeOf(words)),
        // Authors — `.page.author` is the singular shorthand for
        // `.page.authors[0]`, null when the page has no author. `authors`
        // is always present (empty list when none).
        "author"       -> authors.headOption.orNull,
        "authors"      -> authors,
        // Pages that link TO this one — `{title, url, summary}` per
        // referrer. Empty list when nothing in the site links here.
        // Records are thin so we don't recurse into the referrer's
        // own backlinks (avoids cycles when two pages link each
        // other). Templates that need richer referrer data can look
        // up `.site.pagesByPath[bl.url]`.
        "backlinks"    -> backlinksFor(c),
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

    /** Translations of a content file — the same conceptual page in other
      * languages. Computed by stem-matching across language directories.
      * Empty for single-language sites. */
    def translationsOf(c: ContentFile): List[Map[String, Any]] = {
      if (langs.isEmpty) Nil
      else {
        val stem = stemOf(c)
        if (stem.isEmpty) Nil
        else
          byStem.getOrElse(stem, Nil)
            .filter(_ ne c)
            .map(basic)
      }
    }

    /** Second-pass enriched record. Adds navigation cross-references whose
      * targets are basic records (one level only — `.page.parent.parent`
      * is never defined; templates walk `.page.ancestors` for the chain).
      * Also adds `.page.translations` when the site is multilingual. */
    def enrichedRecord(c: ContentFile): Map[String, Any] = {
      val base    = basic(c)
      val parent  = parentSectionOf(c).map(basic).orNull
      val ancs    = ancestorsOf(c).map(basic)
      val (p, n)  = prevNextOf(c)
      val withNav = base ++ Map(
        "parent"       -> parent,
        "ancestors"    -> ancs,
        "prev"         -> p.map(basic).orNull,
        "next"         -> n.map(basic).orNull,
        "translations" -> translationsOf(c),
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

    val baseEntries: List[(ContentFile, Map[String, Any])] =
      contentFiles.map(c => c -> enrichedRecord(c))

    // ----- Series collection (Phase 2.2) -----
    //
    // Frontmatter `series: "OS Internals"` joins pages into a navigable
    // series. Optional `seriesOrder: <int>` sets explicit ordering;
    // unordered pages sort by date (oldest first — series usually read
    // chronologically). Each page in a series gets a `.page.series` map
    // with `name`, `pages`, `prev`, `next`, `index` (1-based).
    //
    // Same single-axis-inverted-index machinery as tags. Pages without a
    // `series:` frontmatter just skip the augmentation entirely.
    def seriesNameOf(c: ContentFile): Option[String] =
      effectiveFrontmatter(c).get("series").collect { case s: String => s }
    def seriesOrderOf(c: ContentFile): Int =
      effectiveFrontmatter(c).get("seriesOrder").collect {
        case n: BigDecimal => n.toInt
        case n: Long       => n.toInt
        case n: Int        => n
      }.getOrElse(Int.MaxValue)

    val seriesIndex: Map[String, List[(ContentFile, Map[String, Any])]] = {
      val grouped = baseEntries.groupBy { case (c, _) => seriesNameOf(c) }
      grouped.collect {
        case (Some(name), entries) =>
          val sorted = entries.sortBy { case (c, _) =>
            (seriesOrderOf(c), pageInstant(c).toEpochSecond, c.name)
          }
          name -> sorted
      }
    }

    val pageEntries: List[(ContentFile, Map[String, Any])] = baseEntries.map { case (c, m) =>
      seriesNameOf(c).flatMap(seriesIndex.get) match {
        case Some(entries) =>
          val idx  = entries.indexWhere(_._1 eq c)
          val prev = if (idx > 0) entries(idx - 1)._2 else null
          val next = if (idx >= 0 && idx < entries.size - 1) entries(idx + 1)._2 else null
          val seriesData = Map[String, Any](
            "name"  -> seriesNameOf(c).getOrElse(""),
            "pages" -> entries.map(_._2),
            "prev"  -> prev,
            "next"  -> next,
            "index" -> BigDecimal(idx + 1),
            "total" -> BigDecimal(entries.size),
          )
          c -> (m + ("series" -> seriesData))
        case None => c -> m
      }
    }

    val pages: List[Map[String, Any]]              = pageEntries.map(_._2)
    val pagesByPath: Map[String, Map[String, Any]] =
      pages.map(p => p("relPermalink").asInstanceOf[String] -> p).toMap

    // ----- Posts view (filtered, dated, sorted) -----
    //
    // `.site.pages` is the raw list (every content file including section
    // indexes and static pages). For "give me the post stream" the theme
    // wants exactly: pages with an explicit parsed `date:`, that aren't
    // section indexes, and that aren't `static: true`. Computed once and
    // surfaced two ways:
    //   .site.posts         flat list, newest first
    //   .site.pagesByYear   same list grouped by year, year descending
    //
    // mtime-fallback dates are intentionally excluded — same rule
    // dateArchives uses, for the same reason: pulling docs / about / etc.
    // into the post stream by accident is ugly.
    def hasExplicitDateFM(c: ContentFile): Boolean =
      effectiveFrontmatter(c).get("date").collect { case s: String => s }
        .flatMap(parseDateString).isDefined
    def isStaticFM(c: ContentFile): Boolean =
      effectiveFrontmatter(c).get("static").collect {
        case true   => true
        case "true" => true
      }.getOrElse(false)

    val datedNonStaticPairs: List[(java.time.OffsetDateTime, Map[String, Any])] =
      pageEntries.collect {
        case (c, m) if c.name != folderContent && hasExplicitDateFM(c) && !isStaticFM(c) =>
          (pageInstant(c), m)
      }
    val postsList: List[Map[String, Any]] =
      datedNonStaticPairs.sortBy(-_._1.toEpochSecond).map(_._2)

    val pagesByYearData: List[Map[String, Any]] =
      datedNonStaticPairs
        .groupBy(_._1.getYear)
        .toList
        .sortBy(-_._1)
        .map { case (year, entries) =>
          val sorted = entries.sortBy(-_._1.toEpochSecond).map(_._2)
          Map[String, Any](
            "year"  -> BigDecimal(year),
            "pages" -> sorted,
            "count" -> BigDecimal(sorted.size),
          )
        }

    // The root section's _index page (if any), looked up by its URL. Themes
    // use this for site-wide navigation that walks `.site.root.subsections`.
    val rootRecord: Map[String, Any] = {
      val rootKey =
        if (baseURL.path == "/" || baseURL.path.isEmpty) "/"
        else baseURL.path + "/"
      pagesByPath.getOrElse(rootKey, null)
    }

    // ----- Taxonomy collection (Phase 1.1) -----
    //
    // `tags` and `categories` are two parallel opt-in axes. For each one we
    // walk every content file's frontmatter, group by slug, and build a
    // record templates can iterate. Pages within a term sort newest-first
    // (parsed `date` falls back to filesystem mtime). The two axes are
    // independent — a site can use just `tags`, just `categories`, both, or
    // neither without behavior changes for the unused side.
    //
    // `.site.tags` / `.site.categories` are populated unconditionally so
    // templates can render tag clouds inline; the per-term archive pages
    // (`/tags/<slug>/`) are only emitted when the theme ships a matching
    // `tag-page.html` layout, which is how docs themes opt out.

    case class TaxonomyTerm(
        name:  String,                    // first-seen display casing
        slug:  String,
        url:   String,
        count: Int,
        pages: List[Map[String, Any]],
    )

    def termToMap(t: TaxonomyTerm): Map[String, Any] =
      Map(
        "name"  -> t.name,
        "slug"  -> t.slug,
        "url"   -> t.url,
        // BigDecimal so squiggly's `{{ if .term.count > 1 }}` matches.
        "count" -> BigDecimal(t.count),
        "pages" -> t.pages,
      )

    /** Sort a list of content files newest-first using the parsed publication
      * instant (frontmatter `date` or filesystem mtime). Secondary key on the
      * file name keeps results deterministic for same-instant ties. */
    def byInstantDesc(cs: List[ContentFile]): List[ContentFile] =
      cs.sortBy(c => (-pageInstant(c).toEpochSecond, c.name))

    /** Build a list of terms for a taxonomy field. `urlSegment` is the path
      * component under which archive pages live (`tags`, `categories`). */
    def collectTaxonomy(field: String, urlSegment: String): List[TaxonomyTerm] = {
      val byTerm = scala.collection.mutable.LinkedHashMap
        .empty[String, (String, scala.collection.mutable.ListBuffer[ContentFile])]
      for (c <- contentFiles if c.name != folderContent) {
        val fm = effectiveFrontmatter(c)
        val terms = fm.get(field) match {
          case Some(s: String)  => List(s)
          case Some(l: List[?]) => l.collect { case s: String => s }
          case _                => Nil
        }
        for (t <- terms) {
          val slug = Slug.slugify(t)
          // `slugify` only returns the placeholder `"-"` for inputs with zero
          // alphanumerics — drop those silently rather than minting a degenerate
          // archive at `/tags/-/`.
          if (slug != "-") {
            val (_, buf) = byTerm.getOrElseUpdate(slug, t -> scala.collection.mutable.ListBuffer.empty)
            buf += c
          }
        }
      }
      val basePath = if (baseURL.path == "/" || baseURL.path.isEmpty) "" else baseURL.path
      byTerm.iterator.map { case (slug, (name, buf)) =>
        val sorted = byInstantDesc(buf.toList)
        TaxonomyTerm(
          name  = name,
          slug  = slug,
          url   = basePath + "/" + urlSegment + "/" + slug + "/",
          count = sorted.length,
          pages = sorted.map(basic),
        )
      }.toList.sortBy(t => (-t.count, t.name))
    }

    val tagTerms      = collectTaxonomy("tags", "tags")
    val categoryTerms = collectTaxonomy("categories", "categories")

    // ----- Author archive collection (Phase 2.3) -----
    //
    // For each id in the registry that has at least one referencing page,
    // build a record with the registry fields PLUS `count`, `pages`, `url`.
    // Sort the result by post count desc, then by id asc, mirroring how
    // `.site.tags` orders.
    val authorTerms: List[Map[String, Any]] = {
      // Map each page record to its list of resolved-author-ids.
      val byAuthorId: Map[String, List[Map[String, Any]]] = pageEntries
        .flatMap { case (c, pageMap) =>
          val fm = effectiveFrontmatter(c)
          val ids = fm.get("authors") match {
            case Some(xs: List[?]) => xs.collect { case s: String => s }
            case _ => fm.get("author") match {
              case Some(s: String) => List(s)
              case _               => Nil
            }
          }
          ids.map(id => id -> pageMap)
        }
        .groupBy(_._1)
        .map { case (id, pairs) => id -> pairs.map(_._2) }
      val basePath =
        if (baseURL.path == "/" || baseURL.path.isEmpty) ""
        else baseURL.path
      // Order: keep registry order (insertion order) for stability across
      // builds, but only include authors that actually have pages.
      authorRegistry.toList.collect {
        case (id, record) if byAuthorId.contains(id) =>
          val pgs = byAuthorId(id).sortBy { p =>
            -p.get("date").collect {
              case t: java.time.OffsetDateTime => t.toEpochSecond
            }.getOrElse(0L)
          }
          record ++ Map[String, Any](
            "id"    -> id,
            "url"   -> (basePath + authorsPath + id + "/"),
            "count" -> BigDecimal(pgs.size),
            "pages" -> pgs,
          )
      }
    }

    // ----- Pagination configuration (Phase 1.2) -----
    //
    // Site-wide `paginate = N` controls slice size for section list pages
    // and taxonomy archives. `paginate <= 0` disables pagination — the full
    // list always lives at the section URL. Per-section override via the
    // section's `_index.md` frontmatter `paginate: <N>`.
    //
    // `sortBy` controls slice ORDER, not `.section.pages` (which keeps its
    // existing weight-then-name order for back-compat with docs sites).
    // Allowed values: `"date"` (newest first; default for blogs),
    // `"title"` (alphabetical), `"weight"` (current docs-site default).
    // Pages with explicit `weight` always come first regardless of sortBy,
    // matching Hugo's pin-to-top behaviour.
    val sitePaginateSize: Int = confdoc.getLong("paginate").map(_.toInt).getOrElse(10)
    val siteSortBy:      String = confdoc.getString("sortBy").getOrElse("weight")

    def paginateSizeFor(c: ContentFile): Int =
      effectiveFrontmatter(c).get("paginate").collect {
        case n: BigDecimal => n.toInt
        case n: Long       => n.toInt
        case n: Int        => n
      }.getOrElse(sitePaginateSize)

    def sortByFor(c: ContentFile): String =
      effectiveFrontmatter(c).get("sortBy").collect { case s: String => s }
        .getOrElse(siteSortBy)

    /** Sort a section's children for the paginator's slice list. Pages with
      * explicit `weight` come first sorted by weight ascending; remaining
      * pages sort by the configured `sortBy`. Stable secondary key on
      * `name` keeps results deterministic. */
    def sortSectionPages(cs: List[ContentFile], sortBy: String): List[ContentFile] = {
      def hasWeight(c: ContentFile): Boolean =
        effectiveFrontmatter(c).contains("weight")
      val (weighted, rest) = cs.partition(hasWeight)
      val weightedSorted = weighted.sortBy(c => (pageWeight(c), c.name))
      val restSorted = sortBy match {
        case "date"  => rest.sortBy(c => (-pageInstant(c).toEpochSecond, c.name))
        case "title" => rest.sortBy(c =>
          (effectiveFrontmatter(c).get("title").collect { case s: String => s }.getOrElse(c.name), c.name),
        )
        case _ => rest.sortBy(c => (pageWeight(c), c.name))
      }
      weightedSorted ::: restSorted
    }

    // ----- Build-time "now" (Phase 4-ish) -----
    //
    // Themes that need to filter on "is this date in the past?" — most often
    // an events section splitting Upcoming vs Past — get a small `now` record
    // on `.site`. Four shapes so the right one's always at hand:
    //   .site.now.iso   — full ISO offset, machine-friendly
    //   .site.now.date  — YYYY-MM-DD, lexicographically comparable to
    //                     `.page.dateShort` for the past/future filter
    //   .site.now.long  — "May 9, 2026", same formatter as `.page.dateLong`
    //   .site.now.year  — BigDecimal, useful for footer copyright
    //
    // Captured once per build; the static-site model means "now" is
    // legitimately frozen at build time, and downstream tooling
    // (live-reload) re-runs the build on every change.
    val nowInst = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
    val nowMap = Map[String, Any](
      "iso"  -> nowInst.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME),
      "date" -> nowInst.format(dateShortFormatter),
      "long" -> formatDateLong(nowInst),
      "year" -> BigDecimal(nowInst.getYear),
    )

    // ----- Events view -----
    //
    // `eventsSection = "events"` (default) names the content section that
    // holds calendar events. An event is a non-`_index` page in that
    // section with an explicit `date:` frontmatter. Surfaced as
    // `.site.events`, sorted ascending — forward-looking views (calendars,
    // "next 3 events" widgets) iterate this in natural order.
    //
    // Recurring events are included as their canonical page record (one
    // entry per event, not per occurrence). Calendar pre-computation
    // expands them to occurrences; everywhere else the canonical record
    // is what theme code wants.
    //
    // `eventsSection` is hoisted to the top of `apply` because the future-
    // post filter needs it too (events must not be future-skipped).
    val datedEventEntries: List[(java.time.OffsetDateTime, ContentFile, Map[String, Any])] =
      pageEntries.collect {
        case (c, m)
            if c.name != folderContent
              && sectionName(c) == eventsSection
              && hasExplicitDateFM(c) =>
          (pageInstant(c), c, m)
      }
    val eventsList: List[Map[String, Any]] =
      datedEventEntries.sortBy(_._1.toEpochSecond).map(_._3)

    // ----- Calendar grid (Phase 4-ish) -----
    //
    // Pre-computed N months (default 12) starting at the current month.
    // Each month is a record with `weeks: [[ {day, isToday, events} ]]`
    // where `weeks` is always six 7-day rows (top/bottom padded with
    // `day=null` cells so templates don't have to special-case month
    // boundaries). Week starts on Sunday.
    //
    // Recurring events expand to every matching weekday from their start
    // date forward. `recurring: weekly` honours `recurringDay:`; absent
    // that key, the event recurs on the start date's day-of-week.
    // Non-recurring events appear once on their `date`.
    val calendarMonths: Int =
      confdoc.getLong("calendarMonths").map(_.toInt).getOrElse(12)

    val calendarData: List[Map[String, Any]] = {
      val today      = nowInst.toLocalDate
      val firstMonth = today.withDayOfMonth(1)

      // Resolve the recurring weekday for an event. Returns `None` if the
      // event is non-recurring, or an unrecognised day name.
      def recurringDayOf(c: ContentFile, start: java.time.LocalDate): Option[java.time.DayOfWeek] = {
        val fm = effectiveFrontmatter(c)
        val isRecurring = fm.get("recurring") match {
          case Some(s: String)  => s.nonEmpty
          case Some(b: Boolean) => b
          case _                => false
        }
        if (!isRecurring) None
        else fm.get("recurringDay") match {
          case Some(s: String) =>
            try Some(java.time.DayOfWeek.valueOf(s.trim.toUpperCase)) catch { case _: IllegalArgumentException => Some(start.getDayOfWeek) }
          case _ => Some(start.getDayOfWeek)
        }
      }

      // Pre-classify each event as (startLocalDate, optionalRecurringDay,
      // basicRecord). Built once outside the month loop so we don't re-walk
      // frontmatter for every cell.
      val classified: List[(java.time.LocalDate, Option[java.time.DayOfWeek], Map[String, Any])] =
        datedEventEntries.map { case (inst, c, m) =>
          val start = inst.toLocalDate
          (start, recurringDayOf(c, start), m)
        }

      (0 until calendarMonths).toList.map { offset =>
        val monthStart = firstMonth.plusMonths(offset.toLong)
        val year       = monthStart.getYear
        val monthVal   = monthStart.getMonthValue
        val daysIn     = monthStart.lengthOfMonth
        val monthEnd   = monthStart.withDayOfMonth(daysIn)
        val monthName  = monthStart.format(java.time.format.DateTimeFormatter.ofPattern("MMMM"))

        // Sunday-first leading padding. Java's DayOfWeek is Mon=1..Sun=7;
        // convert to Sun=0..Sat=6.
        val firstDow   = monthStart.getDayOfWeek.getValue % 7
        // Always emit six 7-day rows (42 cells) so the template's grid is
        // uniform; trailing padding fills whatever's left.
        val totalCells = 42

        // Events that touch this month — either a one-off whose date
        // falls in [monthStart, monthEnd], or a recurring event whose
        // start date is on or before monthEnd.
        val cellEvents: Map[Int, List[Map[String, Any]]] = {
          val acc = scala.collection.mutable.LongMap.empty[List[Map[String, Any]]]
          for ((start, recur, rec) <- classified) {
            recur match {
              case None =>
                if (!start.isBefore(monthStart) && !start.isAfter(monthEnd))
                  acc.update(start.getDayOfMonth.toLong, rec :: acc.getOrElse(start.getDayOfMonth.toLong, Nil))
              case Some(dow) =>
                // First in-month occurrence: walk forward from
                // max(monthStart, start) until we hit the matching
                // weekday, then step by 7 days through monthEnd.
                val begin =
                  if (start.isBefore(monthStart)) monthStart
                  else start
                var cur = begin
                while (cur.getDayOfWeek != dow && !cur.isAfter(monthEnd))
                  cur = cur.plusDays(1L)
                while (!cur.isAfter(monthEnd)) {
                  acc.update(cur.getDayOfMonth.toLong, rec :: acc.getOrElse(cur.getDayOfMonth.toLong, Nil))
                  cur = cur.plusDays(7L)
                }
            }
          }
          // Reverse so events render in their original (date-asc) order.
          acc.iterator.map { case (k, v) => k.toInt -> v.reverse }.toMap
        }

        val cells: List[Map[String, Any]] = (0 until totalCells).toList.map { idx =>
          val day = idx - firstDow + 1
          if (day < 1 || day > daysIn)
            Map[String, Any]("day" -> null, "isToday" -> false, "events" -> List.empty[Map[String, Any]])
          else {
            val isToday =
              today.getYear == year && today.getMonthValue == monthVal && today.getDayOfMonth == day
            Map[String, Any](
              "day"     -> BigDecimal(day),
              "isToday" -> isToday,
              "events"  -> cellEvents.getOrElse(day, Nil),
            )
          }
        }

        val weeks: List[List[Map[String, Any]]] = cells.grouped(7).toList

        Map[String, Any](
          "year"      -> BigDecimal(year),
          "month"     -> BigDecimal(monthVal),
          "monthName" -> monthName,
          "label"     -> s"$monthName $year",
          "weeks"     -> weeks,
        )
      }
    }

    // ----- Photos view -----
    //
    // `.site.photos` aggregates `photos:` frontmatter lists from every page
    // in the site into one flat surface, sorted newest-first by the page's
    // date. Each entry:
    //   {src, caption, alt, page}
    //   - `src`     — image URL, copied verbatim from the frontmatter list
    //   - `caption` — display caption (optional, may be empty)
    //   - `alt`     — accessibility text (optional, falls back to caption)
    //   - `page`    — basic record of the parent page (lets templates link
    //                 the photo back to its event/album/sermon)
    //
    // Frontmatter shape — either a list of strings (just URLs) or a list
    // of maps with optional `src` / `caption` / `alt` keys:
    //   photos:
    //     - "/img/photos/picnic-01.jpg"
    //     - { src: "/img/photos/picnic-02.jpg", caption: "The pie table" }
    //
    // The string shape is shorthand for `{src: "..."}`. Pages without a
    // `photos:` frontmatter contribute nothing.
    val photosList: List[Map[String, Any]] = {
      def normalizeOne(any: Any, pageRec: Map[String, Any], inst: java.time.OffsetDateTime)
          : Option[(java.time.OffsetDateTime, Map[String, Any])] = any match {
        case s: String =>
          Some(inst -> Map[String, Any](
            "src" -> s, "caption" -> "", "alt" -> "", "page" -> pageRec,
          ))
        case m: Map[?, ?] =>
          val sm = m.asInstanceOf[Map[Any, Any]].collect { case (k: String, v) => k -> v }.toMap
          sm.get("src").collect { case s: String => s }.map { src =>
            val caption = sm.get("caption").collect { case s: String => s }.getOrElse("")
            val alt     = sm.get("alt").collect { case s: String => s }.getOrElse(caption)
            inst -> Map[String, Any](
              "src" -> src, "caption" -> caption, "alt" -> alt, "page" -> pageRec,
            )
          }
        case _ => None
      }
      val acc = scala.collection.mutable.ListBuffer.empty[(java.time.OffsetDateTime, Map[String, Any])]
      for ((c, m) <- pageEntries) {
        effectiveFrontmatter(c).get("photos") match {
          case Some(xs: List[?]) =>
            val inst = pageInstant(c)
            xs.foreach(x => normalizeOne(x, m, inst).foreach(acc += _))
          case _ => // nothing
        }
      }
      acc.toList.sortBy(-_._1.toEpochSecond).map(_._2)
    }

    val sitedata = confdata +
      ("toc"             -> sitetoc.toList) +
      ("start"           -> start) +
      ("pages"           -> pages) +
      ("pagesByPath"     -> pagesByPath) +
      ("posts"           -> postsList) +
      ("pagesByYear"     -> pagesByYearData) +
      ("root"            -> rootRecord) +
      ("languages"       -> langs) +
      ("defaultLanguage" -> defaultLang) +
      ("i18n"            -> i18nStrings) +
      ("tags"            -> tagTerms.map(termToMap)) +
      ("categories"      -> categoryTerms.map(termToMap)) +
      ("authors"         -> authorTerms) +
      ("authorRegistry"  -> authorRegistryList) +
      ("authorsPath"     -> authorsPath) +
      ("now"             -> nowMap) +
      ("events"          -> eventsList) +
      ("calendar"        -> calendarData) +
      ("photos"          -> photosList) +
      ("data"            -> site.data)

    // Deferred shortcode pass — runs AFTER pageEntries + sitedata are
    // fully assembled so `[~ name ~]` shortcodes can reach
    // `.page.pages`, `.page.subsections`, `.section.*`, and the full
    // `.site.*`. Operates on each file's already-rendered HTML
    // (`c.content`) — deferred shortcodes emit HTML directly, not
    // markdown. Files with no `[~` delimiter present pay only one
    // `String.contains` per file (the preprocessor scans the buffer
    // for the start delimiter and exits immediately if absent), so
    // this is a near-zero cost for sites that don't use the feature.
    for ((c, pageMap) <- pageEntries) {
      if ((c.content ne null) && c.content.contains("[~")) {
        val extra: Map[String, Any] = Map(
          "page" -> pageMap,
          "site" -> sitedata,
        )
        c.content = deferredPreprocessor.process(c.content, extra)
      }
    }

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
      val isSec   = name == folderContent

      // Slices for paginated section pages. Non-section pages always get one
      // slice that maps to the page's natural URL. Section pages with a
      // configured `paginate` size get one slice per page/N/ subdirectory; if
      // the section's child count is at or below the size the result is a
      // single slice with `total = 1`, indistinguishable from the unpaginated
      // case for templates that don't read `.section.paginator`.
      val baseUrl: String = pageMap("relPermalink").asInstanceOf[String]
      val slices: List[Paginate.Slice] =
        if (isSec) {
          val info       = sectionInfoByOutdir(outdir)
          val sortedKids = sortSectionPages(info.pages, sortByFor(c)).map(basic)
          val size       = math.max(1, paginateSizeFor(c))
          Paginate.paginate(sortedKids, size, baseUrl)
        } else {
          // Paginate is a no-op for non-section pages, but we still want a
          // paginator entry on the data shape for templates that read it
          // unconditionally. Total = 1, no neighbours.
          List(Paginate.Slice(1, 1, Nil, baseUrl, baseUrl, baseUrl, "", ""))
        }

      val sub = toc.headings.headOption match {
        case Some(h) => subheadings(h.sub.headings)
        case None    => Nil
      }
      // Full TOC tree as SubHeading data — used by the "On this page" rail
      // for showing every heading on the page, not just the children of the
      // first one (which is what `.sub` carries for back-compat).
      val tocList = subheadings(toc.headings)

      val folders = {
        val rel = outdir.relativeTo(dst1)

        if (dst1 == outdir) Nil
        else {
          val all = rel.segments.toList
          if (html != "") all.drop(1) else all
        }
      }
      // Layout selection: `layout: foo` frontmatter on a page overrides
      // the default file/folder layout, so themes can ship special
      // layouts (e.g. `home.html` for a curated front page,
      // `archive.html` for a chronological listing) and content authors
      // opt in per page. Default behaviour — no `layout` frontmatter —
      // matches juicer's pre-override shape: sections render via
      // `folderLayout` ("folder"), non-section pages via `fileLayout`
      // ("file").
      val frontmatterLayout =
        effectiveFrontmatter(c).get("layout").collect { case s: String => s }
      val layout = frontmatterLayout.getOrElse(if (isSec) folderLayout else fileLayout)
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

      for (slice <- slices) {
        templateRenderer.blocks.clear()

        val outfile =
          if (slice.current == 1) {
            if (isSec) {
              // Section index — outdir IS the section dir. For nested sections
              // the directory may not yet exist on disk (no `static/` overlap
              // forced its creation), so make sure it does. Idempotent.
              outdir.createDirectories()
              (outdir / "index.html").toString
            } else {
              // Permalink-routed pages override the physical filesystem
              // location: the on-disk path follows the permalink pattern so
              // the URL juicer emits actually resolves on a static host.
              // `htmlDir` (if set) wraps the permalinked path, since the URL
              // strips that prefix. Sections without a `[permalinks]` entry
              // fall through to the legacy outdir-derived path.
              val pagedir = permalinkSegments(c) match {
                case Some(segs) =>
                  val base = if (html != "") dst1 / html else dst1
                  segs.foldLeft(base)(_ / _)
                case None =>
                  outdir / name
              }
              show(s"content: create directory $pagedir")
              pagedir.createDirectories()
              (pagedir / "index.html").toString
            }
          } else {
            // Slice 2+ lives at <outdir>/page/<N>/index.html. Only reachable
            // for section pages (non-section pages are forced to total=1),
            // and section pages never permalink-route — so this path uses
            // the existing outdir-relative location unconditionally.
            val pagedir = outdir / "page" / slice.current.toString
            pagedir.createDirectories()
            (pagedir / "index.html").toString
          }

        val pagedata = Map(
          "site"    -> sitedata,
          "page"    -> pageMap,
          "section" -> (sectionDataFor(c) + ("paginator" -> Paginate.sliceToMap(slice))),
          "content" -> content,
          "toc"     -> toc,
          "sub"     -> sub,
          "tocList" -> tocList,
        )

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
    }

    // ----- Taxonomy archive rendering (Phase 1.1) -----
    //
    // Two layouts per axis: `<kind>-list.html` for the `/tags/index.html`-style
    // index of every term, and `<kind>-page.html` for each per-term archive at
    // `/tags/<slug>/index.html`. Either / both can be absent — silent skip
    // makes archives strictly opt-in.
    //
    // The data block exposes `.term` for the per-page layout (one term, its
    // pages) and `.terms` for the list layout (every term). `.kind` carries
    // the singular form so a shared layout can branch on tag vs category.
    def renderTaxonomyArchives(
        kind:       String,                  // "tag" or "category"
        urlSegment: String,                  // "tags" or "categories"
        terms:      List[TaxonomyTerm],
        listLayout: String,
        pageLayout: String,
    ): Unit = {
      if (terms.isEmpty) return
      findLayout(Nil, listLayout).foreach { tmpl =>
        val outDir = dst1 / urlSegment
        outDir.createDirectories()
        val data = Map(
          "site"  -> sitedata,
          "kind"  -> s"$kind-list",
          "terms" -> terms.map(termToMap),
        )
        val outFile = outDir / "index.html"
        show(s"render $outFile using ${tmpl.path.relativeTo(src1)}")
        outFile.writeText(renderToString(templateRenderer, data, tmpl.template))
      }
      findLayout(Nil, pageLayout).foreach { tmpl =>
        for (t <- terms) {
          val outDir = dst1 / urlSegment / t.slug
          outDir.createDirectories()
          val data = Map(
            "site" -> sitedata,
            "kind" -> kind,
            "term" -> termToMap(t),
          )
          val outFile = outDir / "index.html"
          show(s"render $outFile using ${tmpl.path.relativeTo(src1)}")
          outFile.writeText(renderToString(templateRenderer, data, tmpl.template))
        }
      }
    }

    renderTaxonomyArchives("tag",      "tags",       tagTerms,      "tag-list",      "tag-page")
    renderTaxonomyArchives("category", "categories", categoryTerms, "category-list", "category-page")

    // ----- Author archives (Phase 2.3) -----
    //
    // `/authors/index.html` lists every author in `.site.authorRegistry`
    // (the full team / staff / contributor list), and is rendered if a
    // registry exists at all — themes like juicercafe surface a "team"
    // page that's about *who works here*, not "who has authored posts."
    //
    // `/authors/<id>/index.html` is the per-author archive (their posts);
    // it's only emitted for authors who have at least one referencing
    // page — `authorTerms.nonEmpty`. An empty archive page would be
    // misleading.
    //
    // Same opt-in discipline as taxonomies — missing layout = silent
    // skip. Themes can ship author-list without author-page or vice
    // versa.
    if (authorRegistry.nonEmpty) {
      findLayout(Nil, "author-list").foreach { tmpl =>
        val outDir = authorsDirSegments.foldLeft(dst1)(_ / _)
        outDir.createDirectories()
        val data = Map[String, Any](
          "site"    -> sitedata,
          "authors" -> authorTerms,
        )
        val outFile = outDir / "index.html"
        show(s"render $outFile using ${tmpl.path.relativeTo(src1)}")
        outFile.writeText(renderToString(templateRenderer, data, tmpl.template))
      }
    }
    if (authorTerms.nonEmpty) {
      findLayout(Nil, "author-page").foreach { tmpl =>
        for (a <- authorTerms) {
          val id     = a("id").asInstanceOf[String]
          val outDir = (authorsDirSegments :+ id).foldLeft(dst1)(_ / _)
          outDir.createDirectories()
          val data = Map[String, Any](
            "site"   -> sitedata,
            "author" -> a,
          )
          val outFile = outDir / "index.html"
          show(s"render $outFile using ${tmpl.path.relativeTo(src1)}")
          outFile.writeText(renderToString(templateRenderer, data, tmpl.template))
        }
      }
    }

    // ----- Aliases / redirects (Phase 2.5) -----
    //
    // Frontmatter `aliases: [/old/, /older/]` (or a single string) emits one
    // small `<meta http-equiv="refresh">` page per listed URL pointing at
    // the canonical page. Useful when restructuring URLs — old links keep
    // working without server-side rewrite rules. The `alias.html` layout in
    // `_default/` overrides the built-in HTML if you want custom redirect
    // copy / styling.
    val aliasLayout = findLayout(Nil, "alias")
    for ((c, pageMap) <- pageEntries) {
      val aliasesRaw = effectiveFrontmatter(c).get("aliases")
      val aliases: List[String] = aliasesRaw match {
        case Some(s: String)   => List(s)
        case Some(xs: List[?]) => xs.collect { case s: String => s }
        case _                 => Nil
      }
      if (aliases.nonEmpty) {
        val target    = pageMap("relPermalink").asInstanceOf[String]
        val absTarget = pageMap("permalink").asInstanceOf[String]
        for (alias <- aliases) {
          // Normalise: strip leading/trailing slashes, then split into path
          // segments. Empty / "/" alias is silently skipped — would land on
          // top of the site root.
          val trimmed = alias.stripPrefix("/").stripSuffix("/")
          if (trimmed.nonEmpty) {
            val base    = if (html != "") dst1 / html else dst1
            val outDir  = trimmed.split('/').filter(_.nonEmpty).foldLeft(base)(_ / _)
            outDir.createDirectories()
            val outFile = outDir / "index.html"
            aliasLayout match {
              case Some(tmpl) =>
                val data = Map[String, Any](
                  "site"      -> sitedata,
                  "page"      -> pageMap,
                  "target"    -> target,
                  "absTarget" -> absTarget,
                )
                show(s"render alias $outFile → $target")
                outFile.writeText(renderToString(templateRenderer, data, tmpl.template))
              case None =>
                // Built-in fallback. Static HTML; no template engine needed
                // so this works for any project even without a theme.
                show(s"alias $outFile → $target")
                outFile.writeText(
                  s"""<!doctype html>
                     |<html lang="en">
                     |<head>
                     |  <meta charset="UTF-8">
                     |  <meta http-equiv="refresh" content="0; url=$target">
                     |  <link rel="canonical" href="$absTarget">
                     |  <title>Redirecting…</title>
                     |</head>
                     |<body>
                     |  <p>This page has moved. Redirecting to <a href="$target">$target</a>.</p>
                     |</body>
                     |</html>
                     |""".stripMargin,
                )
            }
          }
        }
      }
    }

    // ----- Date archives (Phase 2.4) -----
    //
    // Site config `dateArchives = true` turns on `/<year>/` and
    // `/<year>/<month>/` index pages. Both are gated by their respective
    // layouts (`date-year.html` and `date-month.html`) — missing layout =
    // silent skip, same convention as taxonomy archives.
    //
    // Only pages with an EXPLICIT `date:` frontmatter that parses are
    // included. Pages whose date came from the mtime fallback are
    // intentionally excluded so that pulling docs / about-page / etc.
    // doesn't pollute the post archive.
    val dateArchivesEnabled: Boolean =
      confdoc.getBool("dateArchives").getOrElse(false)

    if (dateArchivesEnabled) {
      def hasExplicitDate(c: ContentFile): Boolean =
        effectiveFrontmatter(c).get("date").collect { case s: String => s }
          .flatMap(parseDateString).isDefined

      // Pair each dated page with its parsed instant once. Working off the
      // enriched records (`pageEntries`) keeps the archive's `.pages` list
      // shape identical to `.section.pages` / `.term.pages`.
      val datedEntries: List[(java.time.OffsetDateTime, Map[String, Any])] =
        pageEntries
          .collect { case (c, m) if hasExplicitDate(c) => (pageInstant(c), m) }
          .sortBy(-_._1.toEpochSecond)

      if (datedEntries.nonEmpty) {
        val byYear: List[(Int, List[(java.time.OffsetDateTime, Map[String, Any])])] =
          datedEntries.groupBy(_._1.getYear).toList.sortBy(-_._1)

        // Year-archive pages — `/<year>/index.html`.
        findLayout(Nil, "date-year").foreach { tmpl =>
          for ((year, entries) <- byYear) {
            // Per-year month roll-up: one entry per month present, with
            // its own URL, page count, and pages list. Sorted ascending by
            // month number for natural reading order.
            val months = entries.groupBy(_._1.getMonthValue).toList.sortBy(_._1)
              .map { case (m, ms) =>
                Map[String, Any](
                  "month"     -> BigDecimal(m),
                  "monthName" -> monthNames(m - 1),
                  "url"       -> f"${if (baseURL.path == "/" || baseURL.path.isEmpty) "" else baseURL.path}/$year%04d/$m%02d/",
                  "count"     -> BigDecimal(ms.size),
                  "pages"     -> ms.map(_._2),
                )
              }
            val data = Map[String, Any](
              "site"   -> sitedata,
              "year"   -> BigDecimal(year),
              "pages"  -> entries.map(_._2),
              "months" -> months,
            )
            val outDir = dst1 / f"$year%04d"
            outDir.createDirectories()
            val outFile = outDir / "index.html"
            show(s"render $outFile using ${tmpl.path.relativeTo(src1)}")
            outFile.writeText(renderToString(templateRenderer, data, tmpl.template))
          }
        }

        // Month-archive pages — `/<year>/<month>/index.html`.
        findLayout(Nil, "date-month").foreach { tmpl =>
          for ((year, entries) <- byYear) {
            val byMonth = entries.groupBy(_._1.getMonthValue).toList.sortBy(_._1)
            for ((month, ms) <- byMonth) {
              val data = Map[String, Any](
                "site"      -> sitedata,
                "year"      -> BigDecimal(year),
                "month"     -> BigDecimal(month),
                "monthName" -> monthNames(month - 1),
                "pages"     -> ms.map(_._2),
              )
              val outDir = dst1 / f"$year%04d" / f"$month%02d"
              outDir.createDirectories()
              val outFile = outDir / "index.html"
              show(s"render $outFile using ${tmpl.path.relativeTo(src1)}")
              outFile.writeText(renderToString(templateRenderer, data, tmpl.template))
            }
          }
        }
      }
    }

    for (TemplateFile(path, _, template) <- site.otherTemplates) {
      show(s"template: write file $path")
      val rendered = renderToString(templateRenderer, Map("site" -> sitedata), template)
      // Defensive: site authors can put templates in nested subdirs that
      // don't yet exist under `dst`. Without this, `writeText` raises
      // NoSuchFileException — which was how the relative-source-path
      // theme-exclude bug surfaced (see `RelativeSourcePathSpec`).
      path.parent.foreach(_.createDirectories())
      path.writeText(rendered)
    }

    // ----- sitemap.xml -----
    //
    // Standard sitemaps protocol — one <url><loc>…</loc></url> per page.
    // Skips pages with frontmatter `noindex: true` — those pages are still
    // built and reachable by direct URL, but absent from the sitemap so
    // crawlers don't propose them. (`<meta name="robots" content="noindex">`
    // is the matching theme-side signal — see partials/head.html.)
    // No <lastmod> until i18n / dated frontmatter lands; <priority> /
    // <changefreq> are out-of-spec for most modern crawlers anyway.
    {
      val sb = new StringBuilder
      sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
      sb.append("""<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">""").append('\n')
      for (p <- pages if p.get("noindex") != Some(true)) {
        val abs = p("permalink").asInstanceOf[String]
        sb.append("  <url><loc>").append(escapeXml(abs)).append("</loc></url>\n")
      }
      sb.append("</urlset>\n")
      val path = dst1 / "sitemap.xml"
      show(s"write $path")
      path.writeText(sb.toString)
    }

    // ----- robots.txt -----
    //
    // Default-on. Emits a tiny robots.txt that:
    //   - allows everything (`User-agent: *` / `Disallow:`),
    //   - lists each entry of site.toml `disallow` (string or array of
    //     strings) as a `Disallow:` line, and
    //   - advertises the sitemap at <baseURL>/sitemap.xml.
    //
    // Site-wide noindex: set `noindex = true` in site.toml to emit
    // `Disallow: /` for the whole site. Useful for staging / preview
    // domains where you want a real build but no crawler indexing.
    //
    // Opt out entirely with `robots = false` in site.toml — for sites that
    // ship a hand-rolled robots.txt in `static/robots.txt`. The static-asset
    // pass copies that file as-is.
    val robotsEnabled: Boolean =
      confdoc.getBool("robots").getOrElse(true)

    if (robotsEnabled) {
      val siteNoindex = confdoc.getBool("noindex").getOrElse(false)
      val disallows: List[String] = confdoc.get("disallow") match {
        case Some(TomlValue.Str(s))     => List(s)
        case Some(TomlValue.Arr(elems)) => elems.toList.collect { case TomlValue.Str(s) => s }
        case _                          => Nil
      }
      val sitemapUrl: String = {
        val s = baseURL.base + baseURL.path
        (if (s.endsWith("/")) s else s + "/") + "sitemap.xml"
      }
      val sb = new StringBuilder
      sb.append("User-agent: *\n")
      if (siteNoindex) sb.append("Disallow: /\n")
      else {
        if (disallows.isEmpty) sb.append("Disallow:\n")
        else for (d <- disallows) sb.append("Disallow: ").append(d).append('\n')
      }
      sb.append("\nSitemap: ").append(sitemapUrl).append('\n')
      val path = dst1 / "robots.txt"
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
        effectiveFrontmatter(c).get("date").collect { case s: String => s }.getOrElse("")

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
          // Atom enclosure (RFC 4287 §4.2.7.2) — emitted when a page
          // declares an `audio:` (or `enclosure:`) path in frontmatter.
          // Feed readers and podcast platforms (Apple, Overcast, Pocket
          // Casts) pick this up the same way they'd pick up an RSS 2.0
          // `<enclosure>`. `audioType` / `audioLength` (or `enclosureType`
          // / `enclosureLength`) refine the rendering — without them the
          // link still emits with just `rel="enclosure"`.
          val fm = effectiveFrontmatter(c)
          val enclosureRaw = fm.get("enclosure").collect { case s: String => s }
            .orElse(fm.get("audio").collect { case s: String => s })
          enclosureRaw.foreach { encPath =>
            val href = {
              if (encPath.startsWith("http://") || encPath.startsWith("https://")) encPath
              else {
                val base    = baseURL.base + baseURL.path
                val baseTrim = if (base.endsWith("/")) base.dropRight(1) else base
                val rel      = if (encPath.startsWith("/")) encPath else "/" + encPath
                baseTrim + rel
              }
            }
            def asNumberString(v: Any): Option[String] = v match {
              case s: String => Some(s)
              case n: Number => Some(n.toString)
              case _         => None
            }
            val encType = fm.get("enclosureType").collect { case s: String => s }
              .orElse(fm.get("audioType").collect { case s: String => s })
            val encLength = fm.get("enclosureLength").flatMap(asNumberString)
              .orElse(fm.get("audioLength").flatMap(asNumberString))
            sb.append("    <link rel=\"enclosure\" href=\"").append(escapeXml(href)).append("\"")
            encType.foreach(t => sb.append(" type=\"").append(escapeXml(t)).append("\""))
            encLength.foreach(l => sb.append(" length=\"").append(escapeXml(l)).append("\""))
            sb.append("/>\n")
          }
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
          effectiveFrontmatter(c).get("title").collect { case s: String => s }
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
    // it with site context plus a synthetic page record and write it to the
    // site root. This is what nginx / GitHub Pages / Netlify pick up as the
    // not-found page. Skipping silently when no such layout exists keeps it
    // opt-in.
    //
    // The synthetic `.page` carries `noindex = true` (a 404 should never be
    // indexed) plus minimal title/url scaffolding so a theme can delegate to
    // its normal head/SEO partials (which expect `.page` to exist) without
    // special-casing the not-found path. Permalink + summary are empty so
    // theme guards like `{{ if .page.permalink }}` short-circuit cleanly.
    findLayout(Nil, "404") match {
      case Some(TemplateFile(templatePath, _, template)) =>
        show(s"render 404.html using ${templatePath.relativeTo(src1)}")
        val notFoundPage: Map[String, Any] = Map(
          "title"     -> "Page not found",
          "url"       -> "/404.html",
          "permalink" -> "",
          "summary"   -> "",
          "author"    -> null,
          "authors"   -> List.empty[Any],
          "ancestors" -> List.empty[Any],
          "noindex"   -> true,
        )
        val rendered = renderToString(
          templateRenderer,
          Map("site" -> sitedata, "page" -> notFoundPage),
          template,
        )
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
      subdir:     Option[String],
      force:      Boolean,
  ): Unit = {
    if (url.isEmpty) problem("theme add: a git URL is required")

    val src1     = src.toAbsolutePath.normalize
    if (!isDir(src1)) problem(s"not a readable directory: $src1")

    val confdoc  = config(src1, baseConfig)
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

    val confdoc  = config(src1, baseConfig)
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
  /** Build the juicer-side template builtins. Called once per `App.build`
    * (no longer cached as a `lazy val`) so the per-site `mdConfig` — which
    * carries the `codeHighlighter` callback — flows into `markdownify`. */
  private def juicerUrlBuiltins(
      mdConfig: io.github.edadma.markdown.MarkdownConfig,
      srcRoot: io.github.edadma.path.Path,
      dstRoot: io.github.edadma.path.Path,
      imageGen: ImageVariantGenerator,
  ): Map[String, TemplateFunction] = {
    val imageDimsCache = scala.collection.mutable.HashMap.empty[String, Option[ImageDimensions.Dims]]
    def resolveImagePath(arg: String): Option[io.github.edadma.path.Path] = {
      val trimmed = arg.trim
      if (trimmed.isEmpty || absoluteURL(trimmed)) None
      else {
        val rel = if (trimmed.startsWith("/")) trimmed.drop(1) else trimmed
        val parts = rel.split('/').filter(_.nonEmpty).toList
        val dstP = parts.foldLeft(dstRoot)(_ / _)
        if (dstP.exists) Some(dstP)
        else {
          val srcP = parts.foldLeft(srcRoot)(_ / _)
          if (srcP.exists) Some(srcP) else None
        }
      }
    }
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
      // Root-relative `/foo/` links are rewritten through `baseURL.path`
      // the same way they are for content files — without this, a link in
      // a markdownify'd hero summary on a subpath deploy would 404.
      "markdownify" -> TemplateFunction(
        "markdownify",
        1,
        { case (con, Seq(s: String)) =>
          val base = baseFromContext(con)
          val callback: String => String = (url: String) =>
            if (absoluteURL(url)) url
            else {
              val basePath = if (base.path.endsWith("/")) base.path.dropRight(1) else base.path
              val tail     = if (url.startsWith("/")) url else "/" + url
              basePath + tail
            }
          val raw         = parseMarkdown(s, mdConfig)
          val transformed = transformLinks(raw, callback)
          io.github.edadma.markdown.renderToHTML(transformed, mdConfig).trim
        },
      ),
      // `jsonStr` and `emojify` moved to squiggly's TemplateBuiltin
      // in 0.3.0 — they were pure string transforms that didn't need
      // juicer's site context. juicer just inherits them via
      // `TemplateBuiltin.functions ++ juicerUrlBuiltins(...)` now.
      //
      // `markdownify` (below) STAYS overridden here because juicer's
      // version uses the per-site `mdConfig` to pick up the
      // configured code highlighter — a feature retention, not a
      // backwards-compat shim.
      // Read pixel dimensions of an on-disk image so layouts/shortcodes
      // can emit `<img width=... height=...>` and dodge layout-shift.
      // Path is resolved against the built output (`publicDir`) first,
      // then against the source root — so theme + site static assets
      // and any generated images all work. Returns a `Map(width, height)`
      // when the image is found and its header is recognized (PNG, JPEG,
      // GIF, WebP), or an empty map otherwise — never throws. Decoded
      // dimensions are cached per build so a shortcode that fires
      // 50 times only reads each header once.
      "imageDims" -> TemplateFunction(
        "imageDims",
        1,
        { case (con, Seq(arg: String)) =>
          val key = arg.trim
          val dimsOpt = imageDimsCache.getOrElseUpdate(
            key,
            resolveImagePath(key).flatMap(ImageDimensions.fromFile),
          )
          dimsOpt match {
            case Some(d) => Map[String, Any]("width" -> d.width, "height" -> d.height)
            case None    => Map.empty[String, Any]
          }
        },
      ),
      // Image-variant generation (Phase 3). Resolves a source image,
      // generates resized + reformatted copies under
      // `[images] cacheDir`, and returns a Map describing the variant
      // set for a `<picture>` / `<img srcset>` block:
      //
      //   .original       — URL of the passthrough original
      //   .originalWidth  — pixel width of the source (0 if unknown)
      //   .originalHeight — pixel height of the source (0 if unknown)
      //   .variants       — list of { width, format, url, mime }
      //
      // When `[images]` is disabled, the encoder is unavailable
      // (Native/JS targets, or `magick` missing on PATH), or the
      // source can't be resolved, `variants` is empty — themes branch
      // on `if v.variants` and fall back to a plain `<img src>`.
      // Variant generation is memoised per build, so a 50-call layout
      // produces variants once.
      "imageVariants" -> TemplateFunction(
        "imageVariants",
        1,
        { case (con, Seq(arg: String)) =>
          val vs = imageGen.variantsFor(arg)
          Map[String, Any](
            "original"       -> vs.original,
            "originalWidth"  -> vs.originalWidth,
            "originalHeight" -> vs.originalHeight,
            "variants"       -> vs.variants.map(v =>
              Map[String, Any](
                "width"  -> v.width,
                "format" -> v.format,
                "url"    -> v.url,
                "mime"   -> v.mime,
              ),
            ),
          )
        },
      ),
      // Shorthand for the common case: emit just the `srcset`
      // attribute body (`"x-320.webp 320w, x-640.webp 640w"`) for one
      // format. The two-arg call shape — `srcset '/img/x.jpg' 'webp'`
      // — lets a layout author keep their `<img>`/`<source>` markup
      // explicit while still benefiting from generated variants.
      // Returns "" when there are no variants of the requested format.
      "srcset" -> TemplateFunction(
        "srcset",
        2,
        { case (con, Seq(arg: String, format: String)) =>
          imageGen.srcsetFor(arg, format.trim.toLowerCase)
        },
      ),
      // OpenGraph + Twitter card meta tags (Phase 2.7). Pass a page record
      // (typically `.page`); returns a multi-line string of `<meta>`
      // elements ready to drop into `<head>`. Resolves `image` from
      // (frontmatter) `ogImage`, `image`, then site-wide `ogImage` or
      // `image`. `description` falls through to `.page.summary`. URLs are
      // promoted to absolute via the configured `baseURL`.
      "ogTags" -> TemplateFunction(
        "ogTags",
        1,
        { case (con, Seq(p: Map[?, ?])) =>
          val page: Map[String, Any] =
            p.collect { case (k: String, v) => k -> v }.toMap
          // The renderer's STATIC `data` map only carries baseURL / i18n /
          // defaultLang. The per-render scope `con.data` is the page's
          // pagedata Map and that's where `.site` lives at this depth.
          val site: Map[String, Any] = con.data match {
            case m: Map[?, ?] =>
              m.asInstanceOf[Map[Any, Any]].get("site") match {
                case Some(s: Map[?, ?]) =>
                  s.collect { case (k: String, v) => k -> v }.toMap
                case _ => Map.empty[String, Any]
              }
            case _ => Map.empty[String, Any]
          }
          val base       = baseFromContext(con)
          def asString(m: Map[String, Any], keys: String*): Option[String] =
            keys.iterator.flatMap(k => m.get(k).collect { case s: String if s.nonEmpty => s }).nextOption()
          val title      = asString(page, "ogTitle", "title").getOrElse("")
          val descRaw    = asString(page, "ogDescription", "description", "summary").getOrElse("")
          val pageUrl    = page.get("permalink").collect { case s: String => s }.getOrElse("")
          val imageRaw   = asString(page, "ogImage", "image")
            .orElse(asString(site, "ogImage", "image")).getOrElse("")
          val image      =
            if (imageRaw.isEmpty)        ""
            else if (absoluteURL(imageRaw)) imageRaw
            else                         base.base + joinUrlPath(base.path, imageRaw)
          val siteName   = asString(site, "title").getOrElse("")
          val twitterCard =
            if (image.nonEmpty) "summary_large_image" else "summary"
          val sb = new StringBuilder
          def tag(prop: String, attr: String, value: String): Unit =
            if (value.nonEmpty) {
              sb.append(s"""<meta $prop="$attr" content="${escapeXml(value)}" />""").append('\n')
            }
          tag("property", "og:type", "article")
          tag("property", "og:title",       title)
          tag("property", "og:url",         pageUrl)
          tag("property", "og:description", descRaw)
          tag("property", "og:image",       image)
          tag("property", "og:site_name",   siteName)
          tag("name",     "twitter:card",        twitterCard)
          tag("name",     "twitter:title",       title)
          tag("name",     "twitter:description", descRaw)
          tag("name",     "twitter:image",       image)
          sb.toString
        },
      ),
      // i18n string lookup — `{{ i18n .page.lang 'browse_docs' }}`. Falls
      // back to the site's default language, then to the literal key.
      // Strings come from `<src>/i18n/<lang>.toml` (flat key=value pairs).
      "i18n" -> TemplateFunction(
        "i18n",
        2,
        { case (con, Seq(lang: String, key: String)) =>
          val tables = con.renderer.data("i18n").asInstanceOf[Map[String, Map[String, String]]]
          val defaultLang = con.renderer.data.get("defaultLang").collect { case s: String => s }.getOrElse("")
          tables.get(lang).flatMap(_.get(key))
            .orElse(tables.get(defaultLang).flatMap(_.get(key)))
            .getOrElse(key)
        },
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
      mdConfig:     io.github.edadma.markdown.MarkdownConfig,
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
          val parsed = transformLinks(parseMarkdown(preprocessor.process(before), mdConfig), linkCallback)
          io.github.edadma.markdown.renderToHTML(parsed, mdConfig).trim
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

  /** Collect every internal link destination referenced in `doc`.
    * Used to build the backlinks inverted index. Returns site-relative
    * targets only — absolute URLs (`http://`, `mailto:`, `tel:`, …)
    * and fragment-only anchors (`#…`) are filtered out. Query strings
    * and fragments after the path are stripped so `[X](/foo/#bar)`
    * matches `/foo/` cleanly.
    *
    * Walks the same block / inline shapes as [[transformLinks]] plus
    * lists, tables, definition lists, and footnote definitions — any
    * place an author might drop a `[text](url)`. Skips images
    * (their `dest` is an image path, not a content reference). */
  private def collectLinkTargets(doc: Document): Set[String] = {
    import io.github.edadma.markdown._
    val out = scala.collection.mutable.HashSet.empty[String]
    def normalize(dest: String): Option[String] = {
      val stripped = dest.takeWhile(c => c != '#' && c != '?').trim
      if (stripped.isEmpty) None
      else if (absoluteURL(stripped)) None
      // `mailto:`, `tel:`, `javascript:`, `data:` etc. — anything with
      // a scheme that isn't a relative path. The conservative check is
      // a `:` before the first `/`.
      else {
        val slash = stripped.indexOf('/')
        val colon = stripped.indexOf(':')
        if (colon >= 0 && (slash < 0 || colon < slash)) None
        else {
          val rel = if (stripped.startsWith("./")) stripped.drop(2) else stripped
          val abs = if (rel.startsWith("/")) rel else "/" + rel
          Some(abs)
        }
      }
    }
    def goInline(i: Inline): Unit = i match {
      case Link(dest, _, children) =>
        normalize(dest).foreach(out.add)
        children.foreach(goInline)
      case Emphasis(c)      => c.foreach(goInline)
      case Strong(c)        => c.foreach(goInline)
      case Strikethrough(c) => c.foreach(goInline)
      case _                => ()
    }
    def goBlock(b: Block): Unit = b match {
      case Paragraph(inlines)         => inlines.foreach(goInline)
      case h: Heading                 => h.inlines.foreach(goInline)
      case BlockQuote(c)              => c.foreach(goBlock)
      case ListBlock(_, items)        => items.foreach(it => it.content.foreach(goBlock))
      case ListItem(c)                => c.foreach(goBlock)
      case TableRow(cells)            => cells.foreach(_.content.foreach(goInline))
      case TableCell(content)         => content.foreach(goInline)
      case DefinitionListBlock(items) =>
        items.foreach { case (term, defs) =>
          term.foreach(goInline)
          defs.foreach(goBlock)
        }
      case FootnoteDefinition(_, c) => c.foreach(goBlock)
      case _                        => ()
    }
    doc.children.foreach(goBlock)
    out.toSet
  }

  /** Apply `f` to every link / image destination in the document.
    *
    * Walks every block shape the markdown library exposes (lists, tables,
    * definition lists, callouts, collapsibles, footnote definitions,
    * doc-tag bodies). A block type that's silently dropped here is one
    * where the link callback never fires — that's how a root-relative
    * `[text](/foo/)` link inside a list item ends up not being prefixed
    * by `baseURL.path` and 404s on a subpath deploy. Mirror
    * [[collectLinkTargets]] when adding new block shapes. */
  private def transformLinks(doc: Document, f: String => String): Document = {
    import io.github.edadma.markdown._
    def goInline(i: Inline): Inline = i match {
      case Link(dest, title, children)  => Link(f(dest), title, children.map(goInline))
      case Image(dest, title, alt, attrs) =>
        Image(f(dest), title, alt.map(goInline), attrs)
      case Emphasis(c)                  => Emphasis(c.map(goInline))
      case Strong(c)                    => Strong(c.map(goInline))
      case Strikethrough(c)             => Strikethrough(c.map(goInline))
      case other                        => other
    }
    def goCell(cell: TableCell): TableCell = TableCell(cell.content.map(goInline))
    def goRow(row: TableRow): TableRow     = TableRow(row.cells.map(goCell))
    def goBlock(b: Block): Block = b match {
      case Paragraph(inlines)                  => Paragraph(inlines.map(goInline))
      case h: Heading                          => h.copy(inlines = h.inlines.map(goInline))
      case BlockQuote(c)                       => BlockQuote(c.map(goBlock))
      case ListBlock(data, items)              =>
        ListBlock(data, items.map(item => ListItem(item.content.map(goBlock))))
      case ListItem(content)                   => ListItem(content.map(goBlock))
      case Table(header, rows, alignments)     => Table(goRow(header), rows.map(goRow), alignments)
      case row: TableRow                       => goRow(row)
      case cell: TableCell                     => goCell(cell)
      case DefinitionListBlock(items)          =>
        DefinitionListBlock(items.map { case (term, defs) =>
          (term.map(goInline), defs.map(goBlock))
        })
      case FootnoteDefinition(label, content)  => FootnoteDefinition(label, content.map(goBlock))
      case CalloutBlock(ty, title, children)   => CalloutBlock(ty, title, children.map(goBlock))
      case CollapsibleBlock(title, isOpen, children) =>
        CollapsibleBlock(title.map(goInline), isOpen, children.map(goBlock))
      case DocTagBlock(name, target, body, mode) =>
        DocTagBlock(name, target, body.map(goBlock), mode)
      case other                               => other
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
