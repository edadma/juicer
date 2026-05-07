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

  def apply(src: Path, dst: Path, conf: ConfigWrapper): Site = {
    val content       = (src / conf.path.contentDir).normalize
    val html          = conf.htmlDir
    val stripPrefix   = conf.boolean.stripPrefix
    val static        = (src / conf.path.staticDir).normalize
    val layouts       = (src / conf.path.layoutDir).normalize
    val partials      = (src / conf.path.partialDir).normalize
    val shortcodes    = (src / conf.path.shortcodeDir).normalize
    val folderContent = conf.folderContent

    val contentItems       = new ListBuffer[ContentItem]
    val contentMap         = new mutable.HashMap[String, ContentFile]
    val dataFiles          = new ListBuffer[DataFile]
    val layoutTemplates    = new mutable.HashMap[(List[String], String), TemplateFile]
    val partialTemplates   = new mutable.HashMap[String, TemplateFile]
    val shortcodeTemplates = new mutable.HashMap[String, TemplateFile]
    val otherTemplates     = new ListBuffer[TemplateFile]

    if (!isDir(content)) problem(s"can't read content directory: $content")

    def processDir(dir: Path): Unit = {
      show(s">>> $dir")

      val listing = list(dir)

      if (dir.startsWith(content)) {
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
          val contentFile = ContentFile(
            outdir,
            name,
            parseYamlData(frontmatter),
            ((if (first == "---") ""
              else first :+ '\n') ++ lines.map(_ :+ '\n').mkString).trim,
            null,
            null,
          )

          contentMap(p.relativeTo(content).toString) = contentFile
          contentItems += contentFile
        }
      }

      val data = filesIncludingExtensions(listing, "YML", "YAML", "yml", "yaml")

      show(s"data files: ${data.map(_.filename).mkString(", ")}", data.nonEmpty)
      data foreach (p =>
        dataFiles += DataFile(dir, withoutExtension(p.filename), parseYamlData(p.readText())),
      )

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
          if (static == src)
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
        !(layouts != src && dir.startsWith(layouts)) &&
        !(partials != src && dir.startsWith(partials)) &&
        !(shortcodes != src && dir.startsWith(shortcodes)) &&
        !(static != src && dir.startsWith(static))
      ) {
        val l = filesIncludingExtensions(listing, "html", "css", "scss", "sass")

        show(s"other templates: ${l.map(_.filename).mkString(", ")}", l.nonEmpty)
        l foreach { p =>
          val outfile = dst / p.relativeTo(src)

          show(s"parse template $p")
          otherTemplates += TemplateFile(outfile, null, templateParser.parse(p.readText()))
        }
      }

      dirsExcluding(listing, dst) foreach processDir
    }

    processDir(src)
    Site(
      contentItems.toList,
      contentMap.toMap,
      dataFiles.toList,
      layoutTemplates.toMap,
      partialTemplates.toMap,
      shortcodeTemplates.toMap,
      otherTemplates.toList,
    )
  }

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

case class DataFile(parent: Path, name: String, data: Any)

sealed trait ContentItem { val outdir: Path }
case class ContentFile(
    outdir: Path,
    name: String,
    page: Any,
    source: String,
    var content: String,
    var toc: TOC,
) extends ContentItem
case class ContentFolder(outdir: Path) extends ContentItem
case class ContentLabel(label: String) extends ContentItem { val outdir: Path = null }

case class TemplateFile(path: Path, name: String, var template: TemplateAST)

case class Site(
    content: List[ContentItem],
    map: Map[String, ContentFile],
    data: List[DataFile],
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
