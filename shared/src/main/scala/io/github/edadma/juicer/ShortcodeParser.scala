package io.github.edadma.juicer

import scala.util.parsing.combinator.RegexParsers

/** Combinator-based parser for the bracket-delimited shortcode syntax that
  * lives between `[=` and `=]` in markdown source. Two forms:
  *
  * {{{
  *   [= name [param ...] [/] =]      // self-closed when '/' is present
  *   [= /name =]                      // closing tag
  * }}}
  *
  * Each `param` is either positional (a bare value) or named (`key=value`);
  * values may be single-quoted, double-quoted, or unquoted.
  */
class ShortcodeParser(input: String, line: Int, col: Int) extends RegexParsers {

  // The `=]` end delimiter is consumed by the surrounding [[Preprocessor]];
  // we just parse the body interior up to end-of-input.
  private def endOfShortcode: Parser[Unit] = """\z""".r ^^^ (())

  private def shortcode: Parser[ShortcodeParserAST] =
    (shortcodeStart | shortcodeEnd) <~ endOfShortcode

  private def closed: Parser[Boolean] = opt("/") ^^ { _.isDefined }

  private def value: Parser[String] = singleQuoteString | doubleQuoteString | unquotedString

  private def parameter: Parser[ShortcodeParameter] =
    (ident ~ ("=" ~> value)) ^^ { case n ~ v => NamedParameter(n, v) }
      | value                ^^ PositionalParameter.apply

  private def shortcodeStart: Parser[ShortcodeStartAST] =
    ident ~ rep(parameter) ~ closed ^^ { case n ~ ps ~ c => ShortcodeStartAST(n, ps, c) }

  private def shortcodeEnd: Parser[ShortcodeEndAST] =
    "/" ~> ident ^^ ShortcodeEndAST.apply

  // Strings: capture body sans delimiters; allow `\'` / `\"` escapes; no newlines.
  private def singleQuoteString: Parser[String] =
    """'(?:\\'|[^'\n])*'""".r ^^ { s => s.substring(1, s.length - 1).replace("\\'", "'") }

  private def doubleQuoteString: Parser[String] =
    """"(?:\\"|[^"\n])*"""".r ^^ { s => s.substring(1, s.length - 1).replace("\\\"", "\"") }

  // Unquoted: alpha-num + _ - : ; allow `/` only when the surrounding chars are still
  // ident-shaped (so a closing `/` token before `=]` doesn't get glued onto the value).
  private def unquotedString: Parser[String] =
    """[A-Za-z0-9_\-:][A-Za-z0-9_\-:/]*""".r

  private def ident: Parser[Ident] =
    """[A-Za-z_\-][A-Za-z0-9_\-]*""".r ^^ (s => Ident(0, s))

  def parseShortcode: ShortcodeParserAST =
    parseAll(shortcode, input) match {
      case Success(ast, _) => ast
      case f: NoSuccess    => sys.error(s"Shortcode is not valid (line $line, col $col): ${f.msg}")
    }
}
