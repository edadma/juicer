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

  def process(content: String): String = {
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
        val data = unamedData ++ named.toList ++ contentData toMap

        shortcodes(name) match {
          case Some(template) =>
            val code = new ByteArrayOutputStream
            val out = new PrintStream(code)

            renderer.render(data, template, out)

            (if (stack.isEmpty) buf else stack.top.buf) ++= code.toString
          case None => r.error(s"unknown shortcode: $name")
        }
      }

      if (r.more)
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

    processShortcode(CharReader.fromString(content))

    if (stack.nonEmpty)
      stack.top.pos.error(s"unclosed shortcode body: ${stack.top.name}")

    buf.toString
  }

}
