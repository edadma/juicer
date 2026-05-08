package io.github.edadma

import io.github.edadma.markdown.{Document, Heading, Inline}
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

  /** Build-time failure. Prints to stderr and throws — `sys.exit` would
    * be conventional for a CLI but Scala.js can't link `java.lang.System.exit`,
    * so going through an exception is the cross-platform path. The JVM /
    * Native CLI entry points (Main.scala) leave the throw uncaught, which
    * still produces a non-zero exit code; tests catch the exception. */
  def problem(msg: String): Nothing = {
    Console.err.println(msg)
    throw new RuntimeException(msg)
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

  /** Cached `markdown.MarkdownConfig`. Tuned for SSG / docs use: every GFM
    * extension we ship support for is on, plus auto heading ids.
    *
    *   - autoHeadingIds   — every `<hN>` gets an `id` from the slugified text
    *                        so the TOC builder can read it back, and so
    *                        deep-link anchors work.
    *   - tables           — GitHub-style pipe tables.
    *   - strikethrough    — `~~text~~`.
    *   - taskListItems    — `- [ ]` / `- [x]` checkboxes.
    *   - extendedAutolinks — bare URLs like `https://example.com` autolink.
    *   - footnotes        — `[^1]` references and `[^1]: …` definitions.
    *   - smartPunctuation — straight quotes → curly, `--`/`---` → en/em dashes.
    *   - attributes       — `## Heading {#anchor .class key=val}` syntax.
    *   - callouts         — `> [!NOTE]` / `> [!TIP]` admonition blocks.
    *   - definitionLists  — `term\n: definition` blocks.
    *   - emoji = Unicode  — `:smile:` becomes 😄 inline.
    *
    * `math` stays off because KaTeX needs a runtime asset; turn it on per-site
    * if your theme loads KaTeX.
    */
  lazy val markdownConfig: io.github.edadma.markdown.MarkdownConfig =
    io.github.edadma.markdown.MarkdownConfig.default.copy(
      autoHeadingIds    = true,
      tables            = true,
      strikethrough     = true,
      taskListItems     = true,
      extendedAutolinks = true,
      footnotes         = true,
      smartPunctuation  = true,
      attributes        = true,
      callouts          = true,
      definitionLists   = true,
      emoji             = io.github.edadma.markdown.EmojiConfig.Unicode,
    )

  /** Parse markdown text into a [[Document]] AST. */
  def parseMarkdown(s: String): Document =
    io.github.edadma.markdown.parseDocumentContent(s, markdownConfig)

  /** Render a list of inline nodes to HTML (delegates to markdown 0.4.2's
    * public `renderInlines`).
    */
  def renderInlinesHtml(inlines: List[Inline]): String =
    io.github.edadma.markdown.renderInlines(inlines)

  /** Build a tree-shaped table of contents from a flat heading list. The
    * tree is grouped by heading level: a heading's `sub` contains the
    * subsequent headings whose levels are strictly deeper, up to the next
    * heading at the same (or shallower) level.
    *
    * Each entry's `id` is read from `Heading.attrs.id` — populated by the
    * parser when `MarkdownConfig.autoHeadingIds` is true (see
    * [[markdownConfig]]).
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
            h.attrs.flatMap(_.id).getOrElse(""),
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
  def buildToc(doc: Document): TOC = buildToc(doc.headings)

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

  // ===== Slug computation =====
  //
  // Single helper used by tags, categories, future series/authors/permalinks.
  // Don't reinvent five times.
  //
  // Pipeline: lowercase → fold common Latin diacritics to ASCII → replace runs
  // of non-alphanumeric with `-` → strip leading/trailing `-`. The fold table
  // is hand-rolled (rather than `java.text.Normalizer`) so the helper compiles
  // identically on JVM / Scala.js / Native — `java.text.Normalizer` doesn't
  // ship in scala-native's javalib stubs.

  /** Map common Latin diacritics to ASCII. Covers the Latin-1 Supplement +
    * Latin Extended-A characters most likely to appear in author-supplied
    * tags or titles. Anything outside the table falls through unchanged and
    * the slug step then strips it. */
  private val diacriticFold: Map[Char, String] = Map(
    'à' -> "a", 'á' -> "a", 'â' -> "a", 'ã' -> "a", 'ä' -> "a", 'å' -> "a", 'ā' -> "a", 'ą' -> "a",
    'ç' -> "c", 'ć' -> "c", 'č' -> "c",
    'ď' -> "d", 'đ' -> "d",
    'è' -> "e", 'é' -> "e", 'ê' -> "e", 'ë' -> "e", 'ē' -> "e", 'ę' -> "e", 'ě' -> "e",
    'ğ' -> "g",
    'ì' -> "i", 'í' -> "i", 'î' -> "i", 'ï' -> "i", 'ī' -> "i",
    'ł' -> "l", 'ľ' -> "l",
    'ñ' -> "n", 'ń' -> "n", 'ň' -> "n",
    'ò' -> "o", 'ó' -> "o", 'ô' -> "o", 'õ' -> "o", 'ö' -> "o", 'ø' -> "o", 'ō' -> "o", 'ő' -> "o",
    'ŕ' -> "r", 'ř' -> "r",
    'ś' -> "s", 'š' -> "s", 'ş' -> "s",
    'ť' -> "t", 'ţ' -> "t",
    'ù' -> "u", 'ú' -> "u", 'û' -> "u", 'ü' -> "u", 'ū' -> "u", 'ů' -> "u", 'ű' -> "u",
    'ý' -> "y", 'ÿ' -> "y",
    'ź' -> "z", 'ż' -> "z", 'ž' -> "z",
    'ß' -> "ss", 'æ' -> "ae", 'œ' -> "oe", 'ð' -> "d", 'þ' -> "th",
  )

  /** ASCII-fold a string by mapping known Latin diacritics. Characters not in
    * the table pass through; the slugify step decides what to do with them. */
  def asciiFold(s: String): String = {
    val sb = new StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      diacriticFold.get(c) match {
        case Some(repl) => sb.append(repl)
        case None       => sb.append(c)
      }
      i += 1
    }
    sb.toString
  }

  /** Convert a free-form string into a URL-safe slug. Lowercases, ASCII-folds
    * common Latin diacritics, replaces every run of non-alphanumeric ASCII
    * with a single `-`, and trims leading/trailing dashes. Empty / all-symbol
    * inputs collapse to `"-"` so the result is always a non-empty path
    * segment.
    *
    * Examples:
    *   slugify("Hello, World!")  == "hello-world"
    *   slugify("Café au lait")   == "cafe-au-lait"
    *   slugify("C++")            == "c"
    *   slugify("___")            == "-"
    */
  def slugify(s: String): String = {
    val folded = asciiFold(s.toLowerCase)
    val sb     = new StringBuilder(folded.length)
    var lastDash = false
    var i = 0
    while (i < folded.length) {
      val c = folded.charAt(i)
      if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
        sb.append(c)
        lastDash = false
      } else if (!lastDash && sb.nonEmpty) {
        sb.append('-')
        lastDash = true
      }
      i += 1
    }
    // Trim a trailing dash that the loop left behind. Leading dashes can't
    // happen because we suppress dashes until at least one alnum has landed.
    val out = if (sb.nonEmpty && sb.last == '-') sb.dropRight(1).toString else sb.toString
    if (out.isEmpty) "-" else out
  }

  /** Parse a frontmatter `date:` value. Three input shapes are recognised
    * (widest first so a fully-qualified timestamp doesn't lose precision
    * by short-circuiting on the date prefix):
    *
    *   1. Full ISO-8601 with offset:           `2024-01-15T10:30:00Z` / `…+02:00`
    *   2. Local ISO datetime (no offset):      `2024-01-15T10:30:00` → assumed UTC
    *   3. Plain ISO date:                       `2024-01-15` → midnight UTC
    *
    * Returns `None` for any other shape (or empty input) so callers can
    * fall back to filesystem mtime, skip the page, etc.
    */
  def parseDateString(s: String): Option[java.time.OffsetDateTime] = {
    val trimmed = s.trim
    if (trimmed.isEmpty) None
    else {
      try Some(java.time.OffsetDateTime.parse(trimmed))
      catch {
        case _: java.time.format.DateTimeParseException =>
          try {
            val ldt = java.time.LocalDateTime.parse(trimmed)
            Some(ldt.atOffset(java.time.ZoneOffset.UTC))
          } catch {
            case _: java.time.format.DateTimeParseException =>
              try {
                val ld = java.time.LocalDate.parse(trimmed)
                Some(ld.atStartOfDay(java.time.ZoneOffset.UTC).toOffsetDateTime)
              } catch {
                case _: java.time.format.DateTimeParseException => None
              }
          }
      }
    }
  }
}
