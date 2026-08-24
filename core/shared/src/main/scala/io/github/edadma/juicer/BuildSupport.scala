package io.github.edadma.juicer

import io.github.edadma.markdown.{Document, Heading => MdHeading, Inline, Link, Paragraph}
import io.github.edadma.squiggly.{BaseURL, TemplateAST, TemplateFunction, TemplateRenderer}

/** Build-pipeline helpers that hold no per-build state — pure transforms over
  * markdown documents and strings, the squiggly render-to-String shim, and the
  * juicer-side URL/markdown/i18n template builtins (which take all the state
  * they need as parameters). Split out of [[App]] so the build file is just
  * the pipeline; everything here is callable as a free function via
  * `import BuildSupport.*` inside [[App]].
  */
object BuildSupport {

  /** Minimal XML escaping for sitemap URLs — covers the five entities the
    * sitemap protocol cares about. No need for full HTML entity coverage
    * since URLs already restrict the character set.
    */
  private[juicer] def escapeXml(s: String): String =
    s.replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")

  /** Crude HTML→text stripper for the search index. Drops every tag, decodes
    * the five named entities, collapses whitespace. Not a full HTML parser —
    * fine for matching prose in a search box, not fine for displaying
    * attacker-controlled content (don't pipe this back into HTML). */
  private[juicer] def stripHtmlForSearch(html: String): String = {
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
  private[juicer] def jsonString(s: String): String = {
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
  private[juicer] def juicerUrlBuiltins(
      mdConfig:      io.github.edadma.markdown.MarkdownConfig,
      srcRoot:       io.github.edadma.path.Path,
      dstRoot:       io.github.edadma.path.Path,
      imageGen:      ImageVariantGenerator,
      assetManifest: Map[String, String],
  ): Map[String, TemplateFunction] = {
    val imageDimsCache = scala.collection.mutable.HashMap.empty[String, Option[ImageDimensions.Dims]]

    /** Read the rendering page's bundle source directory out of pagedata
      * (stashed as the top-level `__bundleSrc` key in App.build's
      * pagedata Map). Empty string when absent (templates rendered
      * outside a page context — taxonomy archives, year pages, etc.).
      * The resolver uses this to prefer bundle-relative paths over
      * srcRoot-relative ones when the path doesn't start with `/`. */
    def bundleSrcFromContext(con: io.github.edadma.squiggly.Context): String =
      con.data match {
        case m: Map[?, ?] =>
          m.asInstanceOf[Map[Any, Any]].get("__bundleSrc") match {
            case Some(s: String) => s
            case _               => ""
          }
        case _ => ""
      }

    def resolveImagePath(
        arg: String,
        bundleSrc: String = "",
    ): Option[io.github.edadma.path.Path] = {
      val trimmed = arg.trim
      if (trimmed.isEmpty || absoluteURL(trimmed)) None
      else {
        // Bare paths (no leading `/`) try the page's bundle dir first.
        // This is what makes `imageVariants 'photo.jpg'` work inside a
        // bundle without an absolute URL — the same path Hugo's bundle
        // shortcodes use, and the same shape `_index.md` co-locates
        // assets with.
        if (!trimmed.startsWith("/") && bundleSrc.nonEmpty) {
          val bundle = io.github.edadma.path.Path(bundleSrc)
          val parts  = trimmed.split('/').filter(_.nonEmpty).toList
          val bp     = parts.foldLeft(bundle)(_ / _)
          if (bp.exists) return Some(bp)
        }
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

    /** Prefix a site-relative path with a language segment, matching the
      * URLs juicer emits for that language: nothing for the empty language
      * or for the default language under `defaultLanguageInRoot` (which
      * lives at the root), `/<lang>` otherwise. The baseURL path is applied
      * by the caller. */
    def langArg(con: io.github.edadma.squiggly.Context, lang: String, arg: String): String = {
      val defLang =
        con.renderer.data.get("defaultLang").collect { case s: String => s }.getOrElse("")
      val defInRoot =
        con.renderer.data.get("defaultLangInRoot").collect { case b: Boolean => b }.getOrElse(false)
      if (lang.isEmpty || (lang == defLang && defInRoot)) arg
      else joinUrlPath("/" + lang, arg)
    }

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
      // Language-aware companions of relURL / absURL (Hugo's relLangURL /
      // absLangURL). Take the language as the first argument — typically
      // `.page.lang` — and prefix the path with that language's URL segment
      // so links in shared layouts point within the current language.
      "relLangURL" -> TemplateFunction(
        "relLangURL",
        2,
        { case (con, Seq(lang: String, arg: String)) =>
          if (absoluteURL(arg)) arg
          else joinUrlPath(baseFromContext(con).path, langArg(con, lang, arg))
        },
      ),
      "absLangURL" -> TemplateFunction(
        "absLangURL",
        2,
        { case (con, Seq(lang: String, arg: String)) =>
          if (absoluteURL(arg)) arg
          else {
            val base = baseFromContext(con)
            base.base + joinUrlPath(base.path, langArg(con, lang, arg))
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
          val base                       = baseFromContext(con)
          val callback: String => String = (url: String) => rewriteLinkDest(url, base.path)
          val raw                        = parseMarkdown(s, mdConfig)
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
          val bundleSrc = bundleSrcFromContext(con)
          val key       = bundleSrc + "|" + arg.trim
          val dimsOpt = imageDimsCache.getOrElseUpdate(
            key,
            resolveImagePath(arg.trim, bundleSrc).flatMap(ImageDimensions.fromFile),
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
          val vs = imageGen.variantsFor(arg, bundleSrcFromContext(con))
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
          imageGen.srcsetFor(arg, format.trim.toLowerCase, bundleSrcFromContext(con))
        },
      ),
      // Asset-pipeline manifest lookup: `{{ asset 'foo.css' }}` returns
      // the URL of the compiled (and optionally fingerprinted) output.
      // Sites that haven't configured `[assets]` get the input string
      // back unchanged, so authors can drop `asset` into templates
      // before they wire up a pipeline without breaking the build.
      // Missing-key lookups return the input — a typo's effect shows
      // up as a broken `<link href="foo.css">` rather than a silent
      // empty string, which is faster to diagnose.
      "asset" -> TemplateFunction(
        "asset",
        1,
        { case (_, Seq(name: String)) =>
          val trimmed = name.trim
          assetManifest.getOrElse(trimmed, trimmed)
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
  private[juicer] def computeSummary(
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
  private[juicer] def shiftHeadings(doc: Document, by: Int): Document = {
    def shiftBlock(b: io.github.edadma.markdown.Block): io.github.edadma.markdown.Block = b match {
      case h: MdHeading                            => h.copy(level = math.min(6, math.max(1, h.level + by)))
      case io.github.edadma.markdown.BlockQuote(c) => io.github.edadma.markdown.BlockQuote(c.map(shiftBlock))
      case other                                   => other
    }
    Document(doc.children.map(shiftBlock))
  }

  /** Give a repeated heading id a numeric suffix, the way GitHub does: the
    * first `## Buf` keeps `buf`, the second becomes `buf-1`, the third `buf-2`.
    *
    * Two headings with the same text produce the same slug, and two elements
    * sharing an `id` is invalid HTML — the second is unreachable, so `#buf`
    * scrolls to the first whatever the author meant. That is a pre-existing
    * defect rather than behaviour anybody depends on, which is why this runs
    * for every site rather than only under `slugStyle = "github"`.
    *
    * It matters most for generated API reference, where a type and the function
    * that constructs it conventionally share a name — sysl's `buf()` and `Buf`,
    * `map()` and `Map`. GitHub renders such a file with `buf` and `buf-1`, so
    * matching it is what keeps ONE set of links correct in both places.
    *
    * Ids are assigned at PARSE time and carried on the AST, so this is an
    * ordinary transform beside [[shiftHeadings]] — and being a pass over one
    * document, it needs no state that could leak between pages.
    *
    * An EXPLICIT id (`## Heading {#anchor}`) is deduped too: it occupies the
    * same namespace, and letting a collision through there would make the
    * author's stated anchor the one that breaks. */
  private[juicer] def dedupeHeadingIds(doc: Document): Document = {
    val seen = scala.collection.mutable.HashMap.empty[String, Int]

    def unique(id: String): String =
      seen.get(id) match {
        case None =>
          seen(id) = 0
          id
        case Some(n) =>
          // Walk forward until an unused suffix turns up — a document carrying
          // `buf` twice AND a literal `buf-1` must not hand out `buf-1` twice.
          var next = n + 1
          var cand = s"$id-$next"

          while (seen.contains(cand)) {
            next += 1
            cand = s"$id-$next"
          }

          seen(id) = next
          seen(cand) = 0
          cand
      }

    def walk(b: io.github.edadma.markdown.Block): io.github.edadma.markdown.Block = b match {
      case h: MdHeading =>
        h.attrs.flatMap(_.id) match {
          case Some(id) => h.copy(attrs = h.attrs.map(a => a.copy(id = Some(unique(id)))))
          case None     => h
        }
      case io.github.edadma.markdown.BlockQuote(c) => io.github.edadma.markdown.BlockQuote(c.map(walk))
      case other                                   => other
    }

    Document(doc.children.map(walk))
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
  private[juicer] def collectLinkTargets(doc: Document): Set[String] = {
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
  private[juicer] def transformLinks(doc: Document, f: String => String): Document = {
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
  private[juicer] def renderToString(renderer: TemplateRenderer, data: Any, template: TemplateAST): String = {
    val buf = new java.io.ByteArrayOutputStream
    val out = new java.io.PrintStream(buf)
    renderer.render(data, template, out)
    buf.toString
  }
}
