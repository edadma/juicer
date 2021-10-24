package io.github.edadma.juicer

import io.github.edadma.squiggly
import io.github.edadma.commonmark
import io.github.edadma.commonmark.{CommonMarkParser, Util}
import org.ekrich.config._

import scala.jdk.CollectionConverters._

import java.nio.file.Paths

object Main extends App {

  //  val functions = squiggly.Builtin.functions
  //  val namespaces = squiggly.Builtin.namespaces
  //  val shortcodeLoader = new Loader(List(Paths.get("test")), functions, namespaces)
  //  val renderer = new squiggly.Renderer(null, null, functions)
  //  val preproc = new Preprocessor("{<", ">}", shortcodeLoader, renderer)
  //  val input =
  //    """
  //      |YouTube Video
  //      |=============
  //      |
  //      |this is a test
  //      |
  //      |another paragraph
  //      |
  //      |{< youtube code=09jf3ow9jfw />}
  //      |""".stripMargin
  //  val md = preproc.process(input)
  //  val mdparser = new CommonMarkParser
  //  val doc = mdparser.parse(md)
  //  val output = Util.html(doc, 2)
  //
  //  println(output)

  val conf = ConfigFactory.parseString(
    """
      |baseURL = "http://localhost:8080"
      |layouts = [.]
      |""".stripMargin /*,
    ConfigParseOptions.defaults.setSyntax(ConfigSyntax.PROPERTIES)*/
  )

  println(conf.getAnyRef("layouts"), conf.getAnyRef("layouts").getClass)

//  val c1 = conf.withValue("layouts", ConfigValueFactory.fromAnyRef())
  //  val conf = ConfigFactory.load
  //  println(conf)

}