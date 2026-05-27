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

  it should "publish the default language at the root under defaultLanguageInRoot" in {
    writeAt(
      "site.toml",
      """title = "S"
        |baseURL = "http://x"
        |languages = ["en", "fr"]
        |defaultLanguage = "en"
        |defaultLanguageInRoot = true
        |""".stripMargin,
    )
    writeAt("content/en/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/en/install.md", "---\ntitle: Install\n---\n\n.\n")
    writeAt("content/fr/_index.md", "---\ntitle: Accueil\n---\n\n.\n")
    writeAt("content/fr/install.md", "---\ntitle: Installer\n---\n\n.\n")
    writeAt("layouts/_default/folder.html", "lang={{ .page.lang }} url={{ .page.url }} title={{ .page.title }}")
    writeAt("layouts/_default/file.html",   "lang={{ .page.lang }} url={{ .page.url }} title={{ .page.title }}")

    build()

    // Default language (en): no /en/ prefix, lands at the html root.
    out("html/index.html")          should include("lang=en url=/ title=Home")
    out("html/install/index.html")  should include("lang=en url=/install/ title=Install")

    // Other languages keep their prefix.
    out("html/fr/index.html")          should include("lang=fr url=/fr/ title=Accueil")
    out("html/fr/install/index.html")  should include("lang=fr url=/fr/install/ title=Installer")
  }

  it should "give default-language translation links no prefix under defaultLanguageInRoot" in {
    writeAt(
      "site.toml",
      """title = "S"
        |baseURL = "http://x"
        |languages = ["en", "fr"]
        |defaultLanguage = "en"
        |defaultLanguageInRoot = true
        |""".stripMargin,
    )
    writeAt("content/en/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/en/install.md", "---\ntitle: Install\n---\n\n.\n")
    writeAt("content/fr/_index.md", "---\ntitle: Accueil\n---\n\n.\n")
    writeAt("content/fr/install.md", "---\ntitle: Installer\n---\n\n.\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "{{ for t <- .page.translations }}[{{ t.lang }}={{ t.url }}]{{ end }}")

    build()

    // The French page links back to the prefix-free English URL.
    out("html/fr/install/index.html") should include("[en=/install/]")
    // The English page links to the prefixed French URL.
    out("html/install/index.html")    should include("[fr=/fr/install/]")
  }

  it should "emit hreflang alternates in the sitemap for multilingual pages" in {
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
    writeAt("layouts/_default/file.html", "x")

    build()

    val sm = out("sitemap.xml")
    sm should include("""xmlns:xhtml="http://www.w3.org/1999/xhtml"""")
    sm should include("""<xhtml:link rel="alternate" hreflang="en" href="http://x/en/install/"/>""")
    sm should include("""<xhtml:link rel="alternate" hreflang="fr" href="http://x/fr/install/"/>""")
  }

  it should "leave the sitemap free of hreflang for single-language sites" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/about.md", "---\ntitle: About\n---\n\n.\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")

    build()

    val sm = out("sitemap.xml")
    sm should not include "xhtml"
    sm should not include "hreflang"
  }
}
