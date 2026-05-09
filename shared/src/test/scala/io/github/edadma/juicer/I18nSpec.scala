package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for i18n (Tier 2 #10) — extracted from JuicerBuildSpec for readability. */
class I18nSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "juicer i18n" should "expose .page.lang derived from content/<lang>/ when languages is set" in {
    writeAt(
      "site.toml",
      """title = "S"
        |baseURL = "http://x"
        |languages = ["en", "fr"]
        |defaultLanguage = "en"
        |""".stripMargin,
    )
    writeAt("content/en/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/en/install.md", "---\ntitle: Install\n---\n\n.\n")
    writeAt("content/fr/_index.md", "---\ntitle: Accueil\n---\n\n.\n")
    writeAt("content/fr/install.md", "---\ntitle: Installer\n---\n\n.\n")
    writeAt("layouts/_default/folder.html", "lang={{ .page.lang }} title={{ .page.title }}")
    writeAt("layouts/_default/file.html",   "lang={{ .page.lang }} title={{ .page.title }}")

    build()

    out("html/en/index.html")          should include("lang=en title=Home")
    out("html/en/install/index.html")  should include("lang=en title=Install")
    out("html/fr/index.html")          should include("lang=fr title=Accueil")
    out("html/fr/install/index.html")  should include("lang=fr title=Installer")
  }

  it should "leave .page.lang empty for single-language sites" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("layouts/_default/folder.html", "lang=[{{ .page.lang }}]")
    writeAt("layouts/_default/file.html", "x")

    build()

    out("index.html") should include("lang=[]")
  }

  it should "expose .page.translations linking same-stem pages across languages" in {
    writeAt(
      "site.toml",
      """title = "S"
        |baseURL = "http://x"
        |languages = ["en", "fr"]
        |defaultLanguage = "en"
        |""".stripMargin,
    )
    writeAt("content/en/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/en/install.md", "---\ntitle: Install\n---\n\n.\n")
    writeAt("content/fr/_index.md", "---\ntitle: Accueil\n---\n\n.\n")
    writeAt("content/fr/install.md", "---\ntitle: Installer\n---\n\n.\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/file.html",
      """{{ for t <- .page.translations }}[{{ t.lang }}={{ t.title }}={{ t.url }}]{{ end }}""".stripMargin,
    )

    build()

    val en = out("html/en/install/index.html")
    en should include("[fr=Installer=/fr/install/]")
    en should not include "[en="

    val fr = out("html/fr/install/index.html")
    fr should include("[en=Install=/en/install/]")
    fr should not include "[fr="
  }

  it should "resolve i18n strings via the i18n template helper" in {
    writeAt(
      "site.toml",
      """title = "S"
        |baseURL = "http://x"
        |languages = ["en", "fr"]
        |defaultLanguage = "en"
        |""".stripMargin,
    )
    writeAt("i18n/en.toml", "browse = \"Browse the docs\"\nfooter = \"Made with juicer\"\n")
    writeAt("i18n/fr.toml", "browse = \"Parcourir les docs\"\n")
    writeAt("content/en/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/fr/_index.md", "---\ntitle: Accueil\n---\n\n.\n")
    writeAt(
      "layouts/_default/folder.html",
      """[browse={{ i18n .page.lang 'browse' }}][footer={{ i18n .page.lang 'footer' }}]""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    // English page picks up English strings.
    out("html/en/index.html") should (include("[browse=Browse the docs]") and include("[footer=Made with juicer]"))

    // French page picks up French — and falls back to English when its
    // translation is missing.
    out("html/fr/index.html") should include("[browse=Parcourir les docs]")
    out("html/fr/index.html") should include("[footer=Made with juicer]")
  }

  it should "fall back to the literal key when no translation exists" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: H\n---\n\n.\n")
    writeAt("layouts/_default/folder.html", "[{{ i18n '' 'unknown_key' }}]")
    writeAt("layouts/_default/file.html", "x")

    build()

    out("index.html") should include("[unknown_key]")
  }
}
