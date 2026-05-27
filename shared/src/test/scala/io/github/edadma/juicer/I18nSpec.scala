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

  it should "load i18n dictionaries shipped by the active theme, site overriding" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\ntheme = \"t\"\n")
    // The theme ships a full English chrome dictionary…
    writeAt("themes/t/i18n/en.toml", "search = \"Theme Search\"\nclose = \"Theme Close\"\n")
    // …and the site overrides just one key, leaving the rest to the theme.
    writeAt("i18n/en.toml", "search = \"Site Search\"\n")
    writeAt("content/_index.md", "---\ntitle: H\n---\n\n.\n")
    writeAt("layouts/_default/folder.html", "[s={{ i18n 'en' 'search' }}][c={{ i18n 'en' 'close' }}]")
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("[s=Site Search]") // site wins over the theme
    html should include("[c=Theme Close]") // theme-only key still resolves
  }

  it should "let an earlier theme in the chain override an inherited theme's i18n" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\ntheme = \"hi\"\n")
    writeAt("themes/hi/theme.toml", "inherits = [\"lo\"]\n")
    writeAt("themes/hi/i18n/en.toml", "label = \"High\"\n")
    writeAt("themes/lo/i18n/en.toml", "label = \"Low\"\nonly = \"FromLow\"\n")
    writeAt("content/_index.md", "---\ntitle: H\n---\n\n.\n")
    writeAt("layouts/_default/folder.html", "[l={{ i18n 'en' 'label' }}][o={{ i18n 'en' 'only' }}]")
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("[l=High]")    // the inheriting theme wins
    html should include("[o=FromLow]") // base-only key still resolves
  }

  it should "scope .site.root to the page's language for multilingual navigation" in {
    writeAt(
      "site.toml",
      """title = "S"
        |baseURL = "http://x"
        |languages = ["en", "fr"]
        |defaultLanguage = "en"
        |""".stripMargin,
    )
    writeAt("content/en/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/en/guide/_index.md", "---\ntitle: Guide\n---\n\n.\n")
    writeAt("content/fr/_index.md", "---\ntitle: Accueil\n---\n\n.\n")
    writeAt("content/fr/guide/_index.md", "---\ntitle: Guide FR\n---\n\n.\n")
    writeAt(
      "layouts/_default/folder.html",
      "[root={{ .site.root.title }}]{{ for s <- .site.root.subsections }}[sec={{ s.title }}]{{ end }}",
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val en = out("html/en/index.html")
    en should include("[root=Home]")
    en should include("[sec=Guide]")
    en should not include "[sec=Guide FR]"

    val fr = out("html/fr/index.html")
    fr should include("[root=Accueil]")
    fr should include("[sec=Guide FR]")
    fr should not include "[sec=Guide]"
  }

  it should "write per-section feeds at the prefix-free path under defaultLanguageInRoot" in {
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
    writeAt("content/en/guide/_index.md", "---\ntitle: Guide\n---\n\n.\n")
    writeAt("content/en/guide/intro.md", "---\ntitle: Intro\n---\n\n.\n")
    writeAt("content/fr/_index.md", "---\ntitle: Accueil\n---\n\n.\n")
    writeAt("content/fr/guide/_index.md", "---\ntitle: Guide\n---\n\n.\n")
    writeAt("content/fr/guide/intro.md", "---\ntitle: Intro\n---\n\n.\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")

    build()

    // English section feed lands at the prefix-free path the page links to…
    out("html/guide/feed.xml") should include("<feed")
    // …and not duplicated under the stripped /en/ directory.
    List("html", "en", "guide", "feed.xml").foldLeft(dst)(_ / _).exists shouldBe false
    // French keeps its /fr/ prefix.
    out("html/fr/guide/feed.xml") should include("<feed")
  }

  it should "prefix a site-relative path with the language segment via relLangURL" in {
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
    writeAt("content/fr/_index.md", "---\ntitle: Accueil\n---\n\n.\n")
    writeAt("layouts/_default/folder.html", "[{{ relLangURL .page.lang '/getting-started/' }}]")
    writeAt("layouts/_default/file.html", "x")

    build()

    // Default language under defaultLanguageInRoot lives at the root — no prefix.
    out("html/index.html") should include("[/getting-started/]")
    // A non-default language gets its /fr/ segment.
    out("html/fr/index.html") should include("[/fr/getting-started/]")
  }

  it should "render theme chrome via the default-language fallback on a single-language site" in {
    // No `languages` declared, so `.page.lang` is "" and `defaultLanguage`
    // defaults to "en" — exactly how a plain juicerdocs site renders. The
    // helper call mirrors the theme chrome (`i18n .page.lang 'key'`); it must
    // resolve the theme's English dictionary, not fall through to the key.
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\ntheme = \"t\"\n")
    writeAt("themes/t/i18n/en.toml", "search = \"Search…\"\n")
    writeAt("content/_index.md", "---\ntitle: H\n---\n\n.\n")
    writeAt("layouts/_default/folder.html", "[{{ i18n .page.lang 'search' }}]")
    writeAt("layouts/_default/file.html", "x")

    build()

    out("index.html") should include("[Search…]")
  }

  it should "expose .page.languages with a switch URL and current flag for every language" in {
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
    writeAt("content/en/only.md", "---\ntitle: Only\n---\n\n.\n") // English-only page
    writeAt("content/fr/_index.md", "---\ntitle: Accueil\n---\n\n.\n")
    writeAt("content/fr/install.md", "---\ntitle: Installer\n---\n\n.\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/file.html",
      "{{ for l <- .page.languages }}[{{ l.lang }}={{ l.url }}{{ if l.current }}*{{ end }}]{{ end }}",
    )

    build()

    // Translated page: current language flagged, sibling links to its translation.
    out("html/install/index.html")    should include("[en=/install/*][fr=/fr/install/]")
    out("html/fr/install/index.html") should include("[en=/install/][fr=/fr/install/*]")
    // English-only page: French has no translation, so it falls back to the
    // French home — the language is still offered, never hidden.
    out("html/only/index.html")       should include("[en=/only/*][fr=/fr/]")
  }

  it should "keep prev/next navigation within a single language" in {
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
    writeAt("content/en/guide/_index.md", "---\ntitle: Guide\nweight: 10\n---\n\n.\n")
    writeAt("content/en/guide/install.md", "---\ntitle: Install\nweight: 10\n---\n\n.\n")
    writeAt("content/fr/_index.md", "---\ntitle: Accueil\n---\n\n.\n")
    writeAt("content/fr/guide/_index.md", "---\ntitle: Guide FR\nweight: 10\n---\n\n.\n")
    val pager = "[prev={{ if .page.prev then .page.prev.title else 'none' }}]" +
      "[next={{ if .page.next then .page.next.title else 'none' }}]"
    writeAt("layouts/_default/folder.html", pager)
    writeAt("layouts/_default/file.html", pager)

    build()

    // English guide index walks into its own English child, never the French sibling.
    val enGuide = out("html/guide/index.html")
    enGuide should include("[prev=Home][next=Install]")
    enGuide should not include "Guide FR"

    // French guide index has no children → next is empty, and never an English page.
    val frGuide = out("html/fr/guide/index.html")
    frGuide should include("[prev=Accueil][next=none]")
    frGuide should not include "Install"
  }
}
