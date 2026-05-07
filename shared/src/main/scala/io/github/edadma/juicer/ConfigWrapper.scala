package io.github.edadma.juicer

import io.github.edadma.path.Path
import io.github.edadma.toml.{TomlDocument, TomlValue}

import scala.language.dynamics

/** Dynamic-typed convenience wrapper around a [[TomlDocument]] with type-safe
  * accessors via `.int`, `.double`, `.boolean`, `.path`, `.paths`, `.list`.
  *
  * Example: `conf.contentDir` returns the value of the `contentDir` key as a
  * `String`; `conf.path.contentDir` returns it as a [[Path]]; `conf.boolean.stripPrefix`
  * returns it as a `Boolean`. Everything goes through `TomlDocument.get` /
  * `getString` / `getLong` / etc. — see `io.github.edadma.toml.TomlDocument`.
  */
class ConfigWrapper(c: TomlDocument) extends Dynamic {

  private def fail(name: String, want: String): Nothing =
    sys.error(s"config key '$name' is missing or not a $want")

  object IntDynamic extends Dynamic {
    def selectDynamic(name: String): Int =
      c.getLong(name).map(_.toInt).getOrElse(fail(name, "integer"))
  }

  object DoubleDynamic extends Dynamic {
    def selectDynamic(name: String): Double =
      c.getDouble(name)
        .orElse(c.getLong(name).map(_.toDouble))
        .getOrElse(fail(name, "number"))
  }

  object BooleanDynamic extends Dynamic {
    def selectDynamic(name: String): Boolean =
      c.getBool(name).getOrElse(fail(name, "boolean"))
  }

  object PathDynamic extends Dynamic {
    def selectDynamic(name: String): Path =
      c.getString(name).map(Path(_)).getOrElse(fail(name, "string"))
  }

  object PathsDynamic extends Dynamic {
    def selectDynamic(name: String): List[Path] = stringList(name).map(Path(_))
  }

  private def stringList(name: String): List[String] =
    c.get(name) match
      case Some(TomlValue.Str(s))   => List(s)
      case Some(TomlValue.Arr(elems)) =>
        elems.collect { case TomlValue.Str(s) => s }
      case _ => fail(name, "string or array of strings")

  object ListDynamic extends Dynamic {
    def selectDynamic(name: String): List[String] = stringList(name)
  }

  def int: IntDynamic.type         = IntDynamic
  def list: ListDynamic.type       = ListDynamic
  def double: DoubleDynamic.type   = DoubleDynamic
  def boolean: BooleanDynamic.type = BooleanDynamic
  def path: PathDynamic.type       = PathDynamic
  def paths: PathsDynamic.type     = PathsDynamic

  def selectDynamic(name: String): String =
    c.getString(name).getOrElse(fail(name, "string"))

  /** Underlying parsed document, for the rare case the caller needs more
    * structured access than the Dynamic accessors provide.
    */
  def underlying: TomlDocument = c
}
