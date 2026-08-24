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

  /** List of theme names from the `theme` config key. Accepts a string
    * (single theme), an array of strings (chain — earlier entries win),
    * or absent (no themes). Unlike most `ConfigWrapper` accessors this
    * tolerates a missing key, returning an empty list.
    */
  def themes: List[String] = c.get("theme") match {
    case Some(TomlValue.Str(s))     => List(s)
    case Some(TomlValue.Arr(elems)) => elems.toList.collect { case TomlValue.Str(s) => s }
    case None                       => Nil
    case _                          => fail("theme", "string or array of strings")
  }

  /** List of language codes the site is published in, from the `languages`
    * config key. Returns an empty list when absent — that's the
    * single-language mode (no URL prefix, no `.page.lang`, no translations).
    *
    * Example:
    *   languages = ["en", "fr"]
    */
  def languages: List[String] = c.get("languages") match {
    case Some(TomlValue.Arr(elems)) => elems.toList.collect { case TomlValue.Str(s) => s }
    case None                       => Nil
    case _                          => fail("languages", "array of strings")
  }

  /** Default language code. Used as the fallback when a translation is
    * missing for a content file — including the `i18n` helper's lookup, which
    * falls back to this language before giving up on a key. Falls back to the
    * first entry of `languages` when unset; on a site that declares no
    * `languages` at all it defaults to `"en"`, so a theme that ships an
    * `i18n/en.toml` chrome dictionary renders in English out of the box. This
    * default is inert for URL and navigation logic, which is all gated on a
    * non-empty `languages` list. */
  def defaultLanguage: String =
    c.getString("defaultLanguage").getOrElse(languages.headOption.getOrElse("en"))

  /** Extra directories to skip during the site walk, expressed as paths
    * relative to the source root. Accepts a single string or an array of
    * strings; absent means "no extras".
    *
    * The walk already skips `themeDir`, the active themes, the `publicDir`,
    * and the build's `dst`. Use `excludeDirs` for anything else that lives
    * under the source root and isn't part of the site: `node_modules`,
    * `scratch`, vendored tooling, drafts, generated assets you write
    * elsewhere, etc. Entries are joined to `src`, normalized, and added to
    * the recursion-time exclude set — they're not glob patterns.
    *
    * Example:
    *   excludeDirs = ["node_modules", "scratch", "assets/raw"]
    */
  def excludeDirs: List[String] = c.get("excludeDirs") match {
    case Some(TomlValue.Str(s))     => List(s)
    case Some(TomlValue.Arr(elems)) => elems.toList.collect { case TomlValue.Str(s) => s }
    case None                       => Nil
    case _                          => fail("excludeDirs", "string or array of strings")
  }

  /** When true, the default language's URLs live at the site root (no
    * `/<lang>/` prefix); other languages still get prefixed. Default false
    * — every language carries its prefix, which is the easier story for a
    * fresh i18n site. */
  def defaultLanguageInRoot: Boolean =
    c.getBool("defaultLanguageInRoot").getOrElse(false)

  /** Underlying parsed document, for the rare case the caller needs more
    * structured access than the Dynamic accessors provide.
    */
  def underlying: TomlDocument = c
}
