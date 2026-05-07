package io.github.edadma

import io.github.edadma.markdown.{Document, Heading, Inline, Paragraph}
import io.github.edadma.path.Path
import io.github.edadma.squiggly.{BaseURL, TemplateParser}
import io.github.edadma.toml.{TomlDocument, TomlValue}

import scala.collection.immutable.VectorMap
import scala.util.matching.Regex

package object juicer {

  // ===== Filesystem helpers (delegate to path.Path / cross_platform) =====

  def isFile(p: Path): Boolean    = p.exists && p.isFile && p.isReadable
  def isDir(p: Path): Boolean     = p.exists && p.isDirectory && p.isReadable
  def canCreate(p: Path): Boolean = p.parent.exists(d => d.isDirectory && d.isWritable)

  def problem(msg: String): Nothing = {
    Console.err.println(msg)
    sys.exit(1)
  }

  def list(dir: Path): List[Path] =
    dir.listDirectory().toList.sortBy(_.name).map(e => dir / e.name)

  lazy val markdownExtensions: Seq[String] = List("md", "markdown", "mkd", "mkdn", "mdown")

  def filesIncludingExtensions(listing: List[Path], exts: String*): List[Path] = {
    val suffixes = exts.map('.' +: _)
    listing.filter(p => isFile(p) && (suffixes.isEmpty || suffixes.exists(p.toString.endsWith(_))))
  }

  def filesExcludingExtensions(listing: List[Path], exts: String*): List[Path] = {
    require(exts.nonEmpty)
    val suffixes = exts.map('.' +: _)
    listing.filter(p => isFile(p) && !suffixes.exists(p.toString.endsWith(_)))
  }

  def dirsExcluding(listing: List[Path], exclude: Path*): List[Path] =
    listing.filter(p => isDir(p) && !exclude.contains(p))

  // ===== Site config (TOML → squiggly's any-data shape) =====

  def tomlValue(v: TomlValue): Any = v match {
    case TomlValue.Str(s)            => s
    case TomlValue.Num(n)            => n
    case TomlValue.FloatVal(d)       => d
    case TomlValue.Bool(b)           => b
    case TomlValue.Arr(elems)        => elems.map(tomlValue)
    case TomlValue.Obj(fields)       => fields.map { case (k, v) => k -> tomlValue(v) }
    case TomlValue.OffsetDateTime(t) => t.toString
    case TomlValue.LocalDateTime(t)  => t.toString
    case TomlValue.LocalDate(t)      => t.toString
    case TomlValue.LocalTime(t)      => t.toString
  }

  /** Convert a [[TomlDocument]]'s root to a sorted `VectorMap[String, Any]`,
    * suitable for passing into squiggly's renderer.
    */
  def tomlObject(doc: TomlDocument): VectorMap[String, Any] =
    doc.root.toList.sortBy(_._1).map { case (k, v) => k -> tomlValue(v) }.to(VectorMap)

  // ===== URL parsing for `baseURL` =====

  val BaseURLRegex: Regex = raw"(https?://[a-zA-Z0-9-.]+(?::\d+)?|file://)?((?:/[a-zA-Z0-9-.]+)*/?)".r

  def parseURL(s: String): Option[BaseURL] = s match {
    case BaseURLRegex(base, path) =>
      Some(
        BaseURL(
          if (base eq null) "" else base,
          if (path == "") "/"
          else if (path.endsWith("/")) path.dropRight(1)
          else path,
        ),
      )
    case _ => None
  }

  case class Args(
      config: String = "standard",
      verbose: Boolean = false,
      baseurl: Option[String] = None,
      cmd: Option[Command] = None,
  )

  var showSteps: Boolean = false

  def show(msg: => String, cond: Boolean = true): Unit = if (showSteps && cond) println(msg)

  // ===== Markdown parser + helpers =====

  /** Eagerly-cached `markdown.MarkdownConfig`. Currently we use the default
    * (pure CommonMark, no extensions); change here to enable tables, math, etc.
    */
  lazy val markdownConfig: io.github.edadma.markdown.MarkdownConfig =
    io.github.edadma.markdown.MarkdownConfig.default

  /** Parse markdown text into a [[Document]] AST. */
  def parseMarkdown(s: String): Document =
    io.github.edadma.markdown.parseDocumentContent(s, markdownConfig)

  /** Render a list of inline nodes to HTML (for heading-text rendering in TOC
    * entries). The library only exposes whole-block / whole-document renderers,
    * so we wrap the inlines in a one-off `Paragraph`, render, and strip the
    * surrounding `<p>` tag.
    */
  def renderInlinesHtml(inlines: List[Inline]): String = {
    val html = io.github.edadma.markdown.renderBlockToHTML(Paragraph(inlines), markdownConfig).trim
    if (html.startsWith("<p>") && html.endsWith("</p>"))
      html.substring(3, html.length - 4)
    else html
  }

  /** Walk a [[Document]] and return its top-level headings in source order. */
  def collectHeadings(doc: Document): List[Heading] =
    doc.children.collect { case h: Heading => h }

  /** Concatenate the textual content of an inline list, stripping all
    * formatting. Used as input to [[slugify]] when generating heading IDs and
    * to display TOC labels in plain-text form when needed.
    */
  def inlineText(inlines: List[Inline]): String = {
    import io.github.edadma.markdown.*
    val buf = new StringBuilder
    def go(node: Inline): Unit = node match {
      case Text(s)              => buf ++= s
      case CodeSpan(s)          => buf ++= s
      case Emphasis(children)   => children.foreach(go)
      case Strong(children)     => children.foreach(go)
      case Link(_, _, children) => children.foreach(go)
      case Image(_, _, alt, _)  => buf ++= renderAltText(alt)
      case AutoLink(href, _)       => buf ++= href
      case RawHTML(_)              => () // skip
      case SoftLineBreak()         => buf += ' '
      case HardLineBreak()         => buf += ' '
      case Strikethrough(children) => children.foreach(go)
      case other                   => buf ++= other.toString
    }
    inlines.foreach(go)
    buf.toString.trim.replaceAll("\\s+", " ")
  }

  private def renderAltText(inlines: List[Inline]): String = inlineText(inlines)

  /** Convert a heading's text to a URL-safe anchor slug
    * (`Hello, World!` → `hello-world`).
    */
  def slugify(text: String): String = {
    val lower = text.toLowerCase
    val sb    = new StringBuilder
    var dash  = false
    lower.foreach { ch =>
      if (ch.isLetterOrDigit) {
        sb += ch
        dash = false
      } else if (!dash && sb.nonEmpty) {
        sb += '-'
        dash = true
      }
    }
    val s = sb.toString
    if (s.endsWith("-")) s.dropRight(1) else s
  }

  /** Build a tree-shaped table of contents from a flat heading list. The
    * tree is grouped by heading level: a heading's `sub` contains the
    * subsequent headings whose levels are strictly deeper, up to the next
    * heading at the same (or shallower) level.
    */
  def buildToc(headings: List[Heading]): TOC = {
    if (headings.isEmpty) TOC(Nil)
    else {
      val rootLevel = headings.iterator.map(_.level).min
      val out       = collection.mutable.ListBuffer.empty[TocEntry]
      var i         = 0
      val arr       = headings.toIndexedSeq

      while (i < arr.length) {
        val h = arr(i)
        if (h.level <= rootLevel) {
          var j = i + 1
          while (j < arr.length && arr(j).level > rootLevel) j += 1
          out += TocEntry(
            h.level,
            h.inlines,
            slugify(inlineText(h.inlines)),
            buildToc(arr.slice(i + 1, j).toList),
          )
          i = j
        } else {
          // Heading appears at a level shallower than the root — stray; skip.
          i += 1
        }
      }
      TOC(out.toList)
    }
  }

  /** Convenience: build a TOC directly from a [[Document]]. */
  def buildToc(doc: Document): TOC = buildToc(collectHeadings(doc))

  // ===== YAML frontmatter parsing (scala-yaml) =====

  /** Parse a YAML string into the same any-data shape squiggly's renderer
    * expects (`Map[String, Any]` / `List[Any]` / `String` / `BigDecimal` /
    * `Boolean` / `null`). Used for the YAML frontmatter in markdown files
    * (between `---` delimiters) and for `*.yaml` / `*.yml` data files.
    */
  def parseYamlData(input: String): Any = {
    import org.virtuslab.yaml.*

    if (input.trim.isEmpty) Map.empty[String, Any]
    else
      input.asNode match {
        case Right(node) => yamlNodeToAny(node)
        case Left(err)   => sys.error(s"YAML parse error: ${err.msg}")
      }
  }

  private def yamlNodeToAny(n: org.virtuslab.yaml.Node): Any = {
    import org.virtuslab.yaml.Node

    // Type patterns rather than `Node.ScalarNode(value, tag)` extractors
    // because scala-yaml's companion-object `unapply` is a generic extractor
    // (returns Option), which the Scala 3 exhaustivity checker can't prove
    // covers all subtypes.
    n match {
      case s: Node.ScalarNode   => coerceScalar(s.value, s.tag)
      case s: Node.SequenceNode => s.nodes.map(yamlNodeToAny).toList
      case m: Node.MappingNode =>
        m.mappings.map { case (k, v) => keyAsString(k) -> yamlNodeToAny(v) }.toMap
    }
  }

  private def keyAsString(n: org.virtuslab.yaml.Node): String = n match {
    case s: org.virtuslab.yaml.Node.ScalarNode => s.value
    case other                                 => other.toString
  }

  private def coerceScalar(value: String, tag: org.virtuslab.yaml.Tag): Any = {
    import org.virtuslab.yaml.Tag
    tag match {
      case Tag.nullTag => null
      case Tag.boolean => value == "true"
      case Tag.int     => BigDecimal(value)
      case Tag.float =>
        value match {
          case ".nan" | ".NaN" | ".NAN"           => Double.NaN
          case ".inf" | ".Inf" | ".INF"           => Double.PositiveInfinity
          case "-.inf" | "-.Inf" | "-.INF"        => Double.NegativeInfinity
          case _                                   => BigDecimal(value)
        }
      case _ => value // strings, timestamps, anything else falls through as text
    }
  }

  // ===== Squiggly template parser (shared singleton) =====

  lazy val templateParser: TemplateParser = new TemplateParser()

  // ===== URL helpers =====

  val absoluteURLRegex: Regex = "[a-z]+://.*".r

  def absoluteURL(url: String): Boolean = absoluteURLRegex.matches(url)
}
