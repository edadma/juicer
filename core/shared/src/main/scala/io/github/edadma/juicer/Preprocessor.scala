package io.github.edadma.juicer

import io.github.edadma.char_reader._
import io.github.edadma.squiggly.{TemplateLoader, TemplateRenderer}

import java.io.{ByteArrayOutputStream, PrintStream}
import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.language.postfixOps

class Preprocessor(startDelim: String = "[=",
                   endDelim: String = "=]",
                   shortcodes: TemplateLoader,
                   renderer: TemplateRenderer) {

  case class Shortcode(pos: CharReader, name: String, params: Seq[ShortcodeParameter], buf: mutable.StringBuilder)

  /** Process a content string, expanding every `<startDelim> name =<endDelim>`
    * shortcode call against the shortcode template registry. Args
    * (positional + named) and inline content are the only data the
    * template sees — page and site context are absent.
    *
    * This is the "immediate" pass that runs BEFORE markdown rendering,
    * so shortcodes here can emit markdown that the parser then
    * processes. */
  def process(content: String): String = process(content, Map.empty[String, Any])

  /** Process with an extra context map merged into the shortcode
    * template's data scope under the keys provided. Used by the
    * deferred-shortcode pass to thread `.page` + `.site` records
    * (built only after the markdown + section pipeline completes) into
    * shortcodes that need them.
    *
    * Existing shortcodes that don't reference `.page.*` or `.site.*`
    * are unaffected — the keys just sit unused in the data map. */
  def process(content: String, extraContext: Map[String, Any]): String = {
    val buf = new mutable.StringBuilder
    val stack = new mutable.Stack[Shortcode]

    @tailrec
    def processShortcode(r: CharReader): Unit = {
      def render(name: String, params: Seq[ShortcodeParameter], content: Option[String]): Unit = {
        val unamed = new ListBuffer[String]
        val named = new ListBuffer[(String, String)]

        params foreach {
          case NamedParameter(Ident(_, k), v) => named += k -> v
          case PositionalParameter(v)         => unamed += v
        }

        val unamedData = List("args" -> unamed.toList)
        val contentData = content map (s => List("content" -> s)) getOrElse Nil
        val data = (unamedData ++ named.toList ++ contentData toMap) ++ extraContext

        shortcodes(name) match {
          case Some(template) =>
            val code = new ByteArrayOutputStream
            val out = new PrintStream(code)

            renderer.render(data, template, out)

            (if (stack.isEmpty) buf else stack.top.buf) ++= code.toString
          case None => r.error(s"unknown shortcode: $name")
        }
      }

      // Backslash-escape support for the start delimiter. Writing
      // `\[= name =]` in source emits the literal `[= name =]` and skips
      // shortcode parsing — useful when documenting the shortcode syntax
      // itself, since the preprocessor doesn't otherwise distinguish
      // markdown code blocks from prose. The trailing `\` is consumed; only
      // the *start* delimiter needs escaping (the matching `=]` won't be
      // looked at if we never enter shortcode parsing).
      def isEscapedStart: Boolean = {
        if (r.ch != '\\') false
        else {
          var nxt = r.next
          var i   = 0
          var ok  = true
          while (ok && i < startDelim.length) {
            if (!nxt.more || nxt.ch != startDelim.charAt(i)) ok = false
            else { nxt = nxt.next; i += 1 }
          }
          ok
        }
      }

      if (r.more) {
        if (isEscapedStart) {
          // Skip the leading backslash; emit the rest of the start delim
          // as literal text (one char at a time so escape inside a
          // shortcode body still works).
          var nxt = r.next
          (if (stack.isEmpty) buf else stack.top.buf) ++= startDelim
          for (_ <- startDelim) nxt = nxt.next
          processShortcode(nxt)
        } else
          r.matchDelimited(startDelim, endDelim) match {
            case Some(Some((shortcode, rest))) =>
              new ShortcodeParser(shortcode, r.line, r.col).parseShortcode match {
                case ShortcodeStartAST(Ident(_, name), attrs, closed) =>
                  if (closed)
                    render(name, attrs, None)
                  else
                    stack push Shortcode(r, name, attrs, new mutable.StringBuilder)
                case ShortcodeEndAST(Ident(_, endname)) =>
                  val Shortcode(_, name, attrs, buf) = stack.pop()

                  if (name != endname)
                    r.error(s"shortcode end tag name does not match start tag name: $name")

                  render(name, attrs, Some(buf.toString))
              }

              processShortcode(rest)
            case Some(None) =>
              (if (stack.isEmpty) buf else stack.top.buf) += r.ch
              processShortcode(r.next)
            case None => r.error("unclosed shortcode tag")
          }
      }
    }

    processShortcode(CharReader.fromString(content))

    if (stack.nonEmpty)
      stack.top.pos.error(s"unclosed shortcode body: ${stack.top.name}")

    buf.toString
  }

}
