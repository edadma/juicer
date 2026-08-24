package io.github.edadma.juicer

case class Ident(pos: Int, name: String)

trait ShortcodeParserAST

trait ShortcodeParameter
case class NamedParameter(name: Ident, value: String) extends ShortcodeParameter
case class PositionalParameter(value: String) extends ShortcodeParameter

case class ShortcodeStartAST(name: Ident, params: Seq[ShortcodeParameter], closed: Boolean) extends ShortcodeParserAST

case class ShortcodeEndAST(name: Ident) extends ShortcodeParserAST
