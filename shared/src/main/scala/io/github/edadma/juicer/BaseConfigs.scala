package io.github.edadma.juicer

import io.github.edadma.toml.{TomlDocument, TomlParser}

/** Built-in baseline TOML configurations. The user's site config is overlaid
  * on top of one of these (selected by `--config <name>`, default `standard`).
  */
object BaseConfigs {

  private val configs: Map[String, String] = Map(
    "simple" ->
      """baseURL        = "http://localhost:8080"
        |title          = "Untitled"
        |author         = "Unnamed"
        |contentDir     = "."
        |htmlDir        = ""
        |stripPrefix    = false
        |defaultLayout  = "."
        |baseofLayout   = "baseof"
        |fileLayout     = "file"
        |folderLayout   = "folder"
        |folderContent  = "_index"
        |layoutDir      = "."
        |shortcodeDir   = "."
        |partialDir     = "."
        |staticDir      = "."
        |themeDir       = ""
        |publicDir      = "public"
        |""".stripMargin,
    "standard" ->
      """baseURL        = "http://localhost:8080"
        |title          = "Untitled"
        |author         = "Unnamed"
        |contentDir     = "content"
        |htmlDir        = "html"
        |stripPrefix    = true
        |defaultLayout  = "_default"
        |baseofLayout   = "baseof"
        |fileLayout     = "file"
        |folderLayout   = "folder"
        |folderContent  = "_index"
        |layoutDir      = "layouts"
        |shortcodeDir   = "shortcodes"
        |partialDir     = "partials"
        |staticDir      = "static"
        |themeDir       = "themes"
        |publicDir      = "public"
        |""".stripMargin,
    "norme" ->
      """baseURL        = "http://localhost:8080"
        |title          = "Sans titre"
        |author         = "Sans nom"
        |contentDir     = "contentu"
        |htmlDir        = "html"
        |stripPrefix    = true
        |baseofLayout   = "basede"
        |defaultLayout  = "_défaut"
        |fileLayout     = "fichier"
        |folderLayout   = "dossier"
        |folderContent  = "_indice"
        |layoutDir      = "mises-en-page"
        |shortcodeDir   = "codes-courts"
        |partialDir     = "partiels"
        |staticDir      = "statique"
        |themeDir       = "thèmes"
        |publicDir      = "public"
        |""".stripMargin,
  )

  def apply(name: String): Option[TomlDocument] =
    configs.get(name).map { src =>
      TomlParser.parse(src) match {
        case Right(doc) => doc
        case Left(err)  => sys.error(s"baseline config '$name' is malformed: $err")
      }
    }
}
