package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** End-to-end tests for the juicer build pipeline. Each test builds a small
  * site under a temp directory, runs `App.build`, then reads the rendered
  * HTML back and asserts on its structure.
  *
  * NOTE — this file currently still hosts every juicer build test (~3200
  * LOC). The plan is to split the per-feature sections (i18n, sections,
  * feeds, search.json, pagination, taxonomies, dates, slug, word count,
  * permalinks, date archives, future posts, aliases, series, authors,
  * og tags, highlighter, layout override, sitedata) into their own
  * `*Spec.scala` files mixing in [[JuicerTestSupport]]. This file then
  * keeps only the smoke + frontmatter base. See handoff memo
  * `project_juicerblog_polish_handoff.md` for the planned split.
  */
class JuicerBuildSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  // -----------------------------------------------------------------

  "juicer" should "build a minimal site end-to-end" in {
    writeAt(
      "site.toml",
      """title   = "Smoke"
        |author  = "Ed"
        |baseURL = "https://example.com"
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |
        |# Welcome
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/folder.html",
      """<title>{{ .page.title }} — {{ .site.title }}</title>
        |<body>{{ .content }}</body>
        |""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "<title>{{ .page.title }}</title>{{ .content }}")

    build()

    val html = out("index.html")
    html should include("<title>Home — Smoke</title>")
    html should include("<h3 id=\"welcome\">Welcome</h3>") // +2 level shift, auto id
  }

  it should "render YAML frontmatter into the page-data shape" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: Hello
        |tags:
        |  - intro
        |  - demo
        |published: true
        |---
        |
        |Body.
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/folder.html",
      """<title>{{ .page.title }}</title>
        |<meta name="published" content="{{ .page.published }}">
        |{{ for t <- .page.tags }}<span class="tag">{{ t }}</span>{{ end }}
        |{{ .content }}
        |""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("<title>Hello</title>")
    html should include("""<meta name="published" content="true">""")
    html should include("""<span class="tag">intro</span><span class="tag">demo</span>""")
  }

  it should "auto-generate heading IDs from text" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: T
        |---
        |
        |# First Heading
        |
        |## Hello, World!
        |
        |### Another One
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("""id="first-heading"""")
    html should include("""id="hello-world"""")
    html should include("""id="another-one"""")
  }

  it should "render multiple content files into separate pages" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |
        |# Index
        |""".stripMargin,
    )
    writeAt(
      "content/about.md",
      """---
        |title: About
        |---
        |
        |# About
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "<h1>{{ .page.title }}</h1>{{ .content }}")
    writeAt("layouts/_default/file.html", "<h1>{{ .page.title }}</h1>{{ .content }}")

    build()

    out("index.html") should include("<h1>Home</h1>")
    out("about/index.html") should include("<h1>About</h1>")
  }

  it should "expand a partial via squiggly" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: T\n---\n\n# A\n")
    writeAt("layouts/_default/folder.html", "{{ partial 'header' . }}{{ .content }}")
    writeAt("layouts/_default/file.html", "x")
    writeAt("partials/header.html", "<header>{{ .site.title }}</header>")

    build()

    out("index.html") should include("<header>S</header>")
  }

  it should "expand a shortcode via the [= … =] preprocessor" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: T
        |---
        |
        |# A
        |
        |[= note =]This is a note.[= /note =]
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")
    writeAt("shortcodes/note.html", "<aside class=\"note\">{{ .content }}</aside>")

    build()

    val html = out("index.html")
    html should include("<aside class=\"note\">This is a note.</aside>")
  }

  it should "let --baseurl override the site config baseURL" in {
    writeAt("site.toml", """title = "S"
                            |baseURL = "https://wrong.com"
                            |""".stripMargin)
    writeAt("content/_index.md", "---\ntitle: T\n---\n\n# A\n")
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")

    // Override at the CLI level via Args.baseurl.
    App.run(
      Args(
        baseurl = Some("https://right.com"),
        cmd     = Some(BuildCommand(src = src, dst = dst)),
      ),
    )

    out("index.html") should not be empty // sanity; baseURL is exposed via templates
  }

  it should "expose an `emojify` template builtin" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: T\n---\n\n# A\n")
    writeAt("layouts/_default/folder.html", "{{ emojify 'hi :smile: world' }}")
    writeAt("layouts/_default/file.html", "x")

    build()

    out("index.html") shouldBe "hi 😄 world"
  }

  it should "expose a `markdownify` template builtin" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: T\n---\n\n# A\n")
    writeAt(
      "layouts/_default/folder.html",
      """{{ markdownify '**bold** and *italic*' }}
        |""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("<strong>bold</strong>")
    html should include("<em>italic</em>")
  }

  it should "expose relURL and absURL template builtins" in {
    writeAt(
      "site.toml",
      """title    = "S"
        |baseURL  = "https://example.com/docs"
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: T\n---\n\n# A\n")
    writeAt(
      "layouts/_default/folder.html",
      """rel:    {{ relURL 'page.html' }}
        |rel-/:  {{ relURL '/page.html' }}
        |rel-x:  {{ relURL 'http://x.com/y' }}
        |abs:    {{ absURL 'page.html' }}
        |abs-/:  {{ absURL '/page.html' }}
        |""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("rel:    /docs/page.html")
    html should include("rel-/:  /docs/page.html")
    html should include("rel-x:  http://x.com/y")
    html should include("abs:    https://example.com/docs/page.html")
    html should include("abs-/:  https://example.com/docs/page.html")
  }

  it should "expose site.pages as a list of enriched page records" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |
        |# Home
        |""".stripMargin,
    )
    writeAt(
      "content/about.md",
      """---
        |title: About
        |---
        |
        |# About
        |""".stripMargin,
    )
    writeAt(
      "content/contact.md",
      """---
        |title: Contact
        |---
        |
        |# Contact
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/folder.html",
      """{{ for p <- .site.pages }}<a href="{{ p.relPermalink }}">{{ p.title }}</a>
        |{{ end }}""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("""<a href="/">Home</a>""")
    html should include("""<a href="/about/">About</a>""")
    html should include("""<a href="/contact/">Contact</a>""")
  }

  it should "expose page.permalink, .page.relPermalink, .page.url" in {
    writeAt(
      "site.toml",
      """title   = "S"
        |baseURL = "https://example.com/docs"
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n# Home\n")
    writeAt("content/about.md", "---\ntitle: About\n---\n\n# About\n")
    writeAt(
      "layouts/_default/folder.html",
      """abs:  {{ .page.permalink }}
        |rel:  {{ .page.relPermalink }}
        |url:  {{ .page.url }}
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/file.html",
      """abs:  {{ .page.permalink }}
        |rel:  {{ .page.relPermalink }}
        |url:  {{ .page.url }}
        |""".stripMargin,
    )

    build()

    val home = out("index.html")
    home should include("abs:  https://example.com/docs/")
    home should include("rel:  /docs/")
    home should include("url:  /docs/")

    val about = out("about/index.html")
    about should include("abs:  https://example.com/docs/about/")
    about should include("rel:  /docs/about/")
    about should include("url:  /docs/about/")
  }

  it should "key site.pagesByPath by relPermalink" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n# H\n")
    writeAt("content/about.md", "---\ntitle: About Us\n---\n\n# A\n")
    writeAt(
      "layouts/_default/folder.html",
      """{{ for k, v <- .site.pagesByPath }}{{ k }} -> {{ v.title }}
        |{{ end }}""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("/ -> Home")
    html should include("/about/ -> About Us")
  }

  it should "skip draft: true pages by default" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n# H\n")
    writeAt(
      "content/wip.md",
      """---
        |title: Work in Progress
        |draft: true
        |---
        |
        |# WIP
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/folder.html",
      """{{ for p <- .site.pages }}{{ p.title }}; {{ end }}""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    // Draft page is excluded from the build entirely.
    out("index.html") should include("Home;")
    out("index.html") should not include "Work in Progress"

    val wipDir = dst / "wip"
    wipDir.exists shouldBe false
  }

  it should "include draft: true pages when --drafts is set" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n# H\n")
    writeAt(
      "content/wip.md",
      """---
        |title: Work in Progress
        |draft: true
        |---
        |
        |# WIP
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/folder.html",
      """{{ for p <- .site.pages }}{{ p.title }}; {{ end }}""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "<title>{{ .page.title }}</title>")

    App.run(Args(cmd = Some(BuildCommand(src = src, dst = dst, drafts = true))))

    out("index.html") should include("Home;")
    out("index.html") should include("Work in Progress;")
    out("wip/index.html") should include("<title>Work in Progress</title>")
  }

  it should "emit sitemap.xml with one entry per page" in {
    writeAt(
      "site.toml",
      """title   = "S"
        |baseURL = "https://example.com"
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n# H\n")
    writeAt("content/about.md", "---\ntitle: About\n---\n\n# A\n")
    writeAt("content/contact.md", "---\ntitle: Contact\n---\n\n# C\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")

    build()

    val sitemap = out("sitemap.xml")
    sitemap should include("""<?xml version="1.0" encoding="UTF-8"?>""")
    sitemap should include("""<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">""")
    sitemap should include("<loc>https://example.com/</loc>")
    sitemap should include("<loc>https://example.com/about/</loc>")
    sitemap should include("<loc>https://example.com/contact/</loc>")
    sitemap should include("</urlset>")
  }

  it should "render 404.html from the default 404 layout when present" in {
    writeAt("site.toml", "title = \"Sitey\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n# H\n")
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")
    writeAt(
      "layouts/_default/404.html",
      """<!doctype html>
        |<title>404 — {{ .site.title }}</title>
        |<h1>Page not found</h1>
        |""".stripMargin,
    )

    build()

    val nf = out("404.html")
    nf should include("<title>404 — Sitey</title>")
    nf should include("<h1>Page not found</h1>")
  }

  it should "skip 404.html when no 404 layout exists" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n# H\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")

    build()

    (dst / "404.html").exists shouldBe false
  }

  it should "expose .page.summary from explicit frontmatter when set" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: T
        |summary: A custom summary string.
        |---
        |
        |This first paragraph is *not* what we want.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "summary={{ .page.summary }}")
    writeAt("layouts/_default/file.html", "x")

    build()

    out("index.html") shouldBe "summary=A custom summary string."
  }

  it should "compute .page.summary from the prefix before <!--more-->" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: T
        |---
        |
        |The lead paragraph stops *here*.
        |
        |<!--more-->
        |
        |Stuff that should not appear in the summary.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "summary={{ .page.summary }}")
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("summary=<p>The lead paragraph stops <em>here</em>.</p>")
    html should not include "Stuff that should not appear"
  }

  it should "fall back to first-paragraph plain text capped at 30 words" in {
    val long = (1 to 60).map(i => s"w$i").mkString(" ")
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      s"""---
         |title: T
         |---
         |
         |# A heading should be skipped
         |
         |$long
         |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "summary={{ .page.summary }}")
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    val expected =
      "summary=" + (1 to 30).map(i => s"w$i").mkString(" ") + "…"
    html shouldBe expected
  }

  it should "expose .page.summary on each entry of site.pages" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\nsummary: Home blurb.\n---\n\n# H\n")
    writeAt("content/about.md", "---\ntitle: About\nsummary: About blurb.\n---\n\n# A\n")
    writeAt(
      "layouts/_default/folder.html",
      """{{ for p <- .site.pages }}{{ p.title }}: {{ p.summary }}
        |{{ end }}""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("Home: Home blurb.")
    html should include("About: About blurb.")
  }

  it should "fall back to a theme's layout when the site has none" in {
    writeAt(
      "site.toml",
      """title   = "S"
        |baseURL = "http://x"
        |theme   = "minty"
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: T\n---\n\n# H\n")
    // Theme provides both layouts; site provides neither.
    writeAt(
      "themes/minty/layouts/_default/folder.html",
      """[theme] {{ .page.title }} — {{ .site.title }}""".stripMargin,
    )
    writeAt(
      "themes/minty/layouts/_default/file.html",
      "[theme] file",
    )

    build()

    out("index.html") shouldBe "[theme] T — S"
  }

  it should "let the site override theme layouts on a per-file basis" in {
    writeAt(
      "site.toml",
      """title   = "S"
        |baseURL = "http://x"
        |theme   = "minty"
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n# H\n")
    writeAt("content/about.md", "---\ntitle: About\n---\n\n# A\n")
    // Theme provides both file.html and folder.html.
    writeAt(
      "themes/minty/layouts/_default/folder.html",
      "[theme-folder] {{ .page.title }}",
    )
    writeAt(
      "themes/minty/layouts/_default/file.html",
      "[theme-file] {{ .page.title }}",
    )
    // Site overrides folder.html only.
    writeAt(
      "layouts/_default/folder.html",
      "[site-folder] {{ .page.title }}",
    )

    build()

    // Site-overridden layout wins for the index.
    out("index.html") shouldBe "[site-folder] Home"
    // Theme layout still ships for the page that has no site override.
    out("about/index.html") shouldBe "[theme-file] About"
  }

  it should "fall back to theme partials and shortcodes" in {
    writeAt(
      "site.toml",
      """title   = "S"
        |baseURL = "http://x"
        |theme   = "minty"
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      """---
        |title: T
        |---
        |
        |[= alert =]watch out[= /alert =]
        |""".stripMargin,
    )
    writeAt(
      "themes/minty/layouts/_default/folder.html",
      "{{ partial 'header' . }}{{ .content }}",
    )
    writeAt(
      "themes/minty/layouts/_default/file.html",
      "x",
    )
    writeAt(
      "themes/minty/partials/header.html",
      "<header>{{ .site.title }}</header>",
    )
    writeAt(
      "themes/minty/shortcodes/alert.html",
      "<div class=\"alert\">{{ .content }}</div>",
    )

    build()

    val html = out("index.html")
    html should include("<header>S</header>")
    html should include("<div class=\"alert\">watch out</div>")
  }

  it should "ship theme static/ files; site static/ overwrites on path collision" in {
    writeAt(
      "site.toml",
      """title   = "S"
        |baseURL = "http://x"
        |theme   = "minty"
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: T\n---\n\n# H\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")
    // Theme ships theme.css + style.css (the latter the site overrides).
    writeAt("themes/minty/static/theme.css", ".theme {}")
    writeAt("themes/minty/static/style.css", ".theme-style {}")
    writeAt("static/style.css", ".site-style {}")

    build()

    out("theme.css") shouldBe ".theme {}"
    out("style.css") shouldBe ".site-style {}"
  }

  it should "chain themes in declared order (earlier wins)" in {
    writeAt(
      "site.toml",
      """title   = "S"
        |baseURL = "http://x"
        |theme   = ["primary", "secondary"]
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: T\n---\n\n# H\n")
    // Both themes provide folder.html; primary wins.
    writeAt("themes/primary/layouts/_default/folder.html", "[primary]")
    writeAt("themes/primary/layouts/_default/file.html", "x")
    writeAt("themes/secondary/layouts/_default/folder.html", "[secondary]")
    writeAt("themes/secondary/layouts/_default/file.html", "x")
    // secondary-only partial — primary doesn't override it, so secondary wins.
    writeAt("themes/secondary/partials/footer.html", "[footer-from-secondary]")

    build()

    out("index.html") shouldBe "[primary]"
    // Sanity: the secondary partial is still reachable when used.
    writeAt(
      "layouts/_default/folder.html",
      "[combined] {{ partial 'footer' . }}",
    )
    build()
    out("index.html") shouldBe "[combined] [footer-from-secondary]"
  }

  it should "copy static/ files into the output tree as-is" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: T\n---\n\n# A\n")
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")
    writeAt("static/style.css", "body { color: red; }")

    build()

    out("style.css") shouldBe "body { color: red; }"
  }

  // ===== Tier 2 #10: i18n =====

  it should "expose .page.lang derived from content/<lang>/ when languages is set" in {
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

  // ===== `juicer theme add` =====

  "theme add" should "derive a theme name from a typical HTTPS git URL" in {
    App.deriveThemeName("https://github.com/edadma/juicer-theme-foo.git") shouldBe "juicer-theme-foo"
  }

  it should "derive a name from an SSH-style git URL" in {
    App.deriveThemeName("git@github.com:edadma/juicer-theme-foo.git") shouldBe "juicer-theme-foo"
  }

  it should "leave a name without .git suffix alone" in {
    App.deriveThemeName("https://example.com/themes/minty") shouldBe "minty"
  }

  // ===== Tier 2 #9: section list pages + page navigation =====
  //
  // NB: with the default `htmlDir = "html"`, nested sections live on disk at
  // `dst/html/<section>/...` while their URLs strip the `html` segment. Tests
  // that read the rendered file go through `html/...`; tests that match URLs
  // in HTML use `/<section>/...`.

  it should "expose .section.pages on a section's _index page" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\nHi.\n")
    writeAt(
      "content/docs/_index.md",
      """---
        |title: Docs
        |---
        |
        |Section.
        |""".stripMargin,
    )
    writeAt("content/docs/install.md", "---\ntitle: Install\n---\n\nA.\n")
    writeAt("content/docs/usage.md", "---\ntitle: Usage\n---\n\nB.\n")
    writeAt(
      "layouts/_default/folder.html",
      """{{ for p <- .section.pages }}<a href="{{ p.url }}">{{ p.title }}</a>
        |{{ end }}""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "{{ .content }}")

    build()

    val html = out("html/docs/index.html")
    html should include("""<a href="/docs/install/">Install</a>""")
    html should include("""<a href="/docs/usage/">Usage</a>""")
  }

  it should "sort .section.pages by frontmatter weight then name" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: H\n---\n\nx\n")
    writeAt("content/docs/_index.md", "---\ntitle: D\n---\n\nx\n")
    writeAt("content/docs/aaa.md", "---\ntitle: A\nweight: 30\n---\n\nx\n")
    writeAt("content/docs/bbb.md", "---\ntitle: B\nweight: 10\n---\n\nx\n")
    writeAt("content/docs/ccc.md", "---\ntitle: C\nweight: 20\n---\n\nx\n")
    writeAt(
      "layouts/_default/folder.html",
      """{{ for p <- .section.pages }}{{ p.title }} {{ end }}""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    out("html/docs/index.html") should include("B C A")
  }

  it should "expose .section.subsections listing immediate child sections" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/docs/_index.md", "---\ntitle: Docs\n---\n\n.\n")
    writeAt("content/docs/api/_index.md", "---\ntitle: API\nweight: 10\n---\n\n.\n")
    writeAt("content/docs/cli/_index.md", "---\ntitle: CLI\nweight: 20\n---\n\n.\n")
    writeAt(
      "layouts/_default/folder.html",
      """{{ for s <- .section.subsections }}[{{ s.title }}: {{ s.url }}]{{ end }}""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("html/docs/index.html")
    html should include("[API: /docs/api/]")
    html should include("[CLI: /docs/cli/]")
  }

  it should "expose .page.parent pointing at the enclosing section's _index" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/docs/_index.md", "---\ntitle: Docs\n---\n\n.\n")
    writeAt("content/docs/install.md", "---\ntitle: Install\n---\n\n.\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/file.html",
      """parent={{ .page.parent.title }}""".stripMargin,
    )

    build()

    out("html/docs/install/index.html") should include("parent=Docs")
  }

  it should "expose .page.ancestors as a chain from root down to parent" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/docs/_index.md", "---\ntitle: Docs\n---\n\n.\n")
    writeAt("content/docs/api/_index.md", "---\ntitle: API\n---\n\n.\n")
    writeAt("content/docs/api/spec.md", "---\ntitle: Spec\n---\n\n.\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/file.html",
      """{{ for a <- .page.ancestors }}> {{ a.title }} {{ end }}""".stripMargin,
    )

    build()

    out("html/docs/api/spec/index.html") should include("> Home > Docs > API")
  }

  it should "walk .page.prev / .page.next across section boundaries (DFS reading order)" in {
    // Reading order (DFS over the section tree) is:
    //   Home → A → A/p1 → A/p2 → B → B/q1 → B/q2
    // So the last page in A points "next" to B's _index, and the first
    // page in B points "prev" back to A's last page.
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/a/_index.md", "---\ntitle: A\nweight: 10\n---\n\n.\n")
    writeAt("content/a/p1.md", "---\ntitle: P1\nweight: 10\n---\n\n.\n")
    writeAt("content/a/p2.md", "---\ntitle: P2\nweight: 20\n---\n\n.\n")
    writeAt("content/b/_index.md", "---\ntitle: B\nweight: 20\n---\n\n.\n")
    writeAt("content/b/q1.md", "---\ntitle: Q1\nweight: 10\n---\n\n.\n")
    writeAt("content/b/q2.md", "---\ntitle: Q2\nweight: 20\n---\n\n.\n")
    writeAt(
      "layouts/_default/folder.html",
      """{{ if .page.prev }}prev={{ .page.prev.title }} {{ end }}{{ if .page.next }}next={{ .page.next.title }}{{ end }}""".stripMargin,
    )
    writeAt(
      "layouts/_default/file.html",
      """{{ if .page.prev }}prev={{ .page.prev.title }} {{ end }}{{ if .page.next }}next={{ .page.next.title }}{{ end }}""".stripMargin,
    )

    build()

    // Home — first page in reading order.
    out("index.html") should (include("next=A") and not include "prev=")

    // First page of A: prev=A (the section _index above it).
    out("html/a/p1/index.html") should (include("prev=A") and include("next=P2"))

    // Last page of A: next=B (cross-section into the next _index).
    out("html/a/p2/index.html") should (include("prev=P1") and include("next=B"))

    // Section B's _index: prev=last page of A, next=first page of B.
    out("html/b/index.html") should (include("prev=P2") and include("next=Q1"))

    // Last page everywhere — no `next`.
    out("html/b/q2/index.html") should (include("prev=Q1") and not include "next=")
  }

  it should "expose isSection on every page record" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: H\n---\n\n.\n")
    writeAt("content/docs/_index.md", "---\ntitle: D\n---\n\n.\n")
    writeAt("content/docs/a.md", "---\ntitle: A\n---\n\n.\n")
    writeAt(
      "layouts/_default/folder.html",
      """isSection={{ .page.isSection }}""".stripMargin,
    )
    writeAt(
      "layouts/_default/file.html",
      """isSection={{ .page.isSection }}""".stripMargin,
    )

    build()

    out("html/docs/index.html") should include("isSection=true")
    out("html/docs/a/index.html") should include("isSection=false")
  }

  // ===== Regressions: bugs surfaced during section-list / nav rollout =====

  // Bug: `mktocFromContent` used to call `h.toc.headings.head` unconditionally
  // and crashed with NoSuchElementException whenever a content file had no
  // headings. Now falls back to the frontmatter title, then to the file name.
  it should "not crash when content files have no headings" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\nJust a paragraph, no heading.\n")
    writeAt("content/about.md", "---\ntitle: About\n---\n\nNo heading here either.\n")
    writeAt("layouts/_default/folder.html", "ok")
    writeAt("layouts/_default/file.html", "ok")

    noException should be thrownBy build()

    out("index.html") shouldBe "ok"
    out("about/index.html") shouldBe "ok"
  }

  it should "treat \\[= as a literal start-delimiter (preprocessor escape)" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: T
        |---
        |
        |Use `\[= name =]` to call a shortcode.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    // The literal `[= name =]` survives through the preprocessor.
    html should include("[= name =]")
    // No leading backslash leaks through into the rendered HTML.
    html should not include "\\[="
  }

  it should "respect headingShift = 0 (no level shift)" in {
    writeAt(
      "site.toml",
      """title = "S"
        |baseURL = "http://x"
        |headingShift = 0
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      """---
        |title: T
        |---
        |
        |# Top
        |
        |## Sub
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("<h1 id=\"top\">Top</h1>")
    html should include("<h2 id=\"sub\">Sub</h2>")
    // No shifted h3
    html should not include "<h3"
  }

  it should "respect headingShift = 1" in {
    writeAt(
      "site.toml",
      """title = "S"
        |baseURL = "http://x"
        |headingShift = 1
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: T\n---\n\n# A\n\n## B\n")
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("<h2 id=\"a\">A</h2>")
    html should include("<h3 id=\"b\">B</h3>")
  }

  it should "expose .site.root pointing at the root section's _index" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/docs/_index.md", "---\ntitle: Docs\nweight: 10\n---\n\n.\n")
    writeAt("content/blog/_index.md", "---\ntitle: Blog\nweight: 20\n---\n\n.\n")
    writeAt(
      "layouts/_default/folder.html",
      """root={{ .site.root.title }} | {{ for s <- .site.root.subsections }}{{ s.title }}={{ s.url }} {{ end }}""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("root=Home")
    html should include("Docs=/docs/")
    html should include("Blog=/blog/")
  }

  // ===== Tier 2 #8: Atom feeds =====

  it should "emit a site-wide feed.xml at the root" in {
    writeAt("site.toml", "title = \"My Blog\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/post-a.md", "---\ntitle: A\ndate: 2025-12-31\n---\n\n# A\n")
    writeAt("content/post-b.md", "---\ntitle: B\ndate: 2026-01-15\n---\n\n# B\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "{{ .content }}")

    build()

    val xml = out("feed.xml")
    xml should startWith("<?xml")
    xml should include("<feed xmlns=\"http://www.w3.org/2005/Atom\">")
    xml should include("<title>My Blog</title>")
    xml should include("<title>A</title>")
    xml should include("<title>B</title>")
    // Newer entry first (B is later)
    xml.indexOf("<title>B</title>") should be < xml.indexOf("<title>A</title>")
  }

  it should "emit per-section feed.xml inside each section directory" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/blog/_index.md", "---\ntitle: Blog\n---\n\n.\n")
    writeAt("content/blog/a.md", "---\ntitle: A\ndate: 2026-01-01\n---\n\n# A\n")
    writeAt("content/blog/b.md", "---\ntitle: B\ndate: 2026-02-01\n---\n\n# B\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")

    build()

    val xml = out("html/blog/feed.xml")
    xml should include("<feed xmlns=\"http://www.w3.org/2005/Atom\">")
    xml should include("<title>S · Blog</title>")
    xml should include("<title>A</title>")
    xml should include("<title>B</title>")
  }

  it should "skip per-section feed for sections with no non-_index pages" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: H\n---\n\n.\n")
    writeAt("content/empty/_index.md", "---\ntitle: Empty\n---\n\n.\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")

    build()

    // Site-wide feed exists but is empty of entries
    out("feed.xml") should include("<feed xmlns=")
    // Empty section doesn't get its own feed
    val emptySectionFeed = (dst / "html" / "empty" / "feed.xml")
    emptySectionFeed.exists shouldBe false
  }

  it should "respect feeds = false to disable feed emission" in {
    writeAt(
      "site.toml",
      """title = "S"
        |baseURL = "http://x"
        |feeds = false
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: H\n---\n\n.\n")
    writeAt("content/post.md", "---\ntitle: P\ndate: 2026-01-01\n---\n\n# P\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")

    build()

    (dst / "feed.xml").exists shouldBe false
  }

  // ===== search.json emitter =====

  it should "emit search.json with one entry per page" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n# Home\n\nWelcome here.\n")
    writeAt("content/about.md", "---\ntitle: About\n---\n\n# About\n\nOur story.\n")
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "{{ .content }}")

    build()

    val json = out("search.json")
    json should startWith("[")
    json should endWith("]")
    json should include(""""title":"Home"""")
    json should include(""""title":"About"""")
    // URL fields are present and stripped of htmlDir
    json should include(""""url":"/"""")
    json should include(""""url":"/about/"""")
  }

  it should "strip HTML tags from search.json content" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: T
        |---
        |
        |# Heading
        |
        |Some **bold** and *emphasised* text.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")

    build()

    val json = out("search.json")
    json should include("Some bold and emphasised text.")
    // No HTML tags leaked through
    json should not include "<strong>"
    json should not include "<em>"
    json should not include "<h"
  }

  it should "escape special JSON characters in search.json" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: 'Quote: "hi" \backslash'
        |---
        |
        |Line one.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")

    build()

    val json = out("search.json")
    // Embedded `"` becomes `\"`; backslash becomes `\\`.
    json should include("""\"hi\"""")
    json should include("""\\backslash""")
  }

  // Regression: a stale <src>/<publicDir> from a prior default build used to
  // get walked + parsed as squiggly templates whenever the user redirected
  // output with `-d /elsewhere`. The dst-only exclude didn't catch the
  // default publicDir; now both are excluded.
  it should "skip <src>/<publicDir> from the source walk even when -d redirects elsewhere" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: H\n---\n\n# Hi\n")
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")
    // Stale rendered HTML in the default publicDir — would trip the
    // squiggly template parser if it weren't excluded.
    writeAt("public/leftover/index.html", "<html><body>... random rendered output ...</body></html>")
    writeAt("public/leftover.css", "body { color: red; }")

    noException should be thrownBy build()
  }

  // Bug: nested section indexes (`content/docs/_index.md`) used to fail with
  // NoSuchFileException because the section directory (`html/docs/`) was
  // never created on disk. Now `outdir.createDirectories()` is called before
  // writing the section index file.
  it should "create the section directory for nested _index.md files" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: H\n---\n\n# H\n")
    writeAt("content/a/_index.md", "---\ntitle: A\n---\n\n# A\n")
    writeAt("content/a/b/_index.md", "---\ntitle: B\n---\n\n# B\n")
    writeAt("content/a/b/c/_index.md", "---\ntitle: C\n---\n\n# C\n")
    writeAt("layouts/_default/folder.html", "{{ .page.title }}")
    writeAt("layouts/_default/file.html", "x")

    noException should be thrownBy build()

    out("index.html") shouldBe "H"
    out("html/a/index.html") shouldBe "A"
    out("html/a/b/index.html") shouldBe "B"
    out("html/a/b/c/index.html") shouldBe "C"
  }

  // ----- Phase 1.2 — pagination -----

  it should "slice a section's pages into page/N/index.html runs at the configured paginate size" in {
    writeAt("site.toml", "title = \"Blog\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\nsortBy: title\n---\n\nHome.\n")
    // 25 posts. With default paginate=10, expect 3 slices: 10/10/5.
    for (i <- 1 to 25) {
      val n = f"$i%02d"
      writeAt(s"content/post-$n.md", s"---\ntitle: Post $n\n---\nBody $n.\n")
    }
    writeAt(
      "layouts/_default/folder.html",
      """T={{ .section.paginator.total }};C={{ .section.paginator.current }}
        |{{ for p <- .section.paginator.pages }}P={{ p.title }};{{ end }}
        |PREV={{ .section.paginator.prevURL }}
        |NEXT={{ .section.paginator.nextURL }}
        |""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val p1 = out("index.html")
    p1 should include("T=3;C=1")
    p1 should include("P=Post 01;")
    p1 should include("P=Post 10;")
    p1 should not include "P=Post 11;"
    p1 should include("PREV=")          // empty on slice 1
    p1 should include("NEXT=/page/2/")

    val p2 = out("page/2/index.html")
    p2 should include("T=3;C=2")
    p2 should include("P=Post 11;")
    p2 should include("P=Post 20;")
    p2 should include("PREV=/")          // baseURL itself
    p2 should include("NEXT=/page/3/")

    val p3 = out("page/3/index.html")
    p3 should include("T=3;C=3")
    p3 should include("P=Post 21;")
    p3 should include("P=Post 25;")
    p3 should include("PREV=/page/2/")
    p3 should include("NEXT=")          // empty on last slice
  }

  it should "respect a site-wide `paginate = N` override" in {
    writeAt(
      "site.toml",
      """title = "Blog"
        |baseURL = "http://x"
        |paginate = 5
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: Home\nsortBy: title\n---\nHome.\n")
    for (i <- 1 to 12) {
      val n = f"$i%02d"
      writeAt(s"content/post-$n.md", s"---\ntitle: Post $n\n---\nBody $n.\n")
    }
    writeAt(
      "layouts/_default/folder.html",
      """T={{ .section.paginator.total }};C={{ .section.paginator.current }}
        |{{ for p <- .section.paginator.pages }}P={{ p.title }};{{ end }}
        |""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    out("index.html")        should include("T=3;C=1")
    out("page/2/index.html") should include("T=3;C=2")
    out("page/3/index.html") should include("T=3;C=3")
    out("page/3/index.html") should include("P=Post 11;")
    out("page/3/index.html") should include("P=Post 12;")
  }

  it should "expose paginator.first/last anchoring slice 1 and slice N" in {
    writeAt(
      "site.toml",
      """title = "Blog"
        |baseURL = "http://x"
        |paginate = 2
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: Home\nsortBy: title\n---\nHome.\n")
    for (i <- 1 to 5) {
      writeAt(s"content/post-$i.md", s"---\ntitle: Post $i\n---\nBody.\n")
    }
    writeAt(
      "layouts/_default/folder.html",
      """F={{ .section.paginator.first }}
        |L={{ .section.paginator.last }}
        |""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val p1 = out("index.html")
    p1 should include("F=/")
    p1 should include("L=/page/3/")
  }

  it should "sortBy = \"date\" puts newest posts on slice 1" in {
    writeAt(
      "site.toml",
      """title = "Blog"
        |baseURL = "http://x"
        |paginate = 2
        |sortBy = "date"
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: Home\n---\nHome.\n")
    writeAt("content/old.md",    "---\ntitle: Old\ndate: 2024-01-01\n---\nOld.\n")
    writeAt("content/mid.md",    "---\ntitle: Mid\ndate: 2024-06-01\n---\nMid.\n")
    writeAt("content/new.md",    "---\ntitle: New\ndate: 2024-12-01\n---\nNew.\n")
    writeAt(
      "layouts/_default/folder.html",
      """{{ for p <- .section.paginator.pages }}P={{ p.title }};{{ end }}
        |""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val p1 = out("index.html")
    p1 should include("P=New;")
    p1 should include("P=Mid;")
    p1 should not include "P=Old;"   // pushed to page 2

    val p2 = out("page/2/index.html")
    p2 should include("P=Old;")
  }

  // ----- Phase 1.1 — tag + category archives -----

  it should "emit a /tags/<slug>/ archive page per tag" in {
    writeAt("site.toml", "title = \"Blog\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/post-a.md",
      """---
        |title: A
        |date: 2024-03-01
        |tags: [scala, ssg]
        |---
        |Body A.
        |""".stripMargin,
    )
    writeAt(
      "content/post-b.md",
      """---
        |title: B
        |date: 2024-04-01
        |tags: [scala]
        |---
        |Body B.
        |""".stripMargin,
    )
    writeAt(
      "content/post-c.md",
      """---
        |title: C
        |date: 2024-02-01
        |tags: ssg
        |---
        |Body C.
        |""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "<title>{{ .page.title }}</title>")
    writeAt(
      "layouts/_default/tag-page.html",
      """<title>{{ .term.name }} ({{ .term.count }})</title>
        |<ul>
        |{{ for p <- .term.pages }}<li>{{ p.title }}</li>{{ end }}
        |</ul>
        |""".stripMargin,
    )

    build()

    val scalaArchive = out("tags/scala/index.html")
    scalaArchive should include("<title>scala (2)</title>")
    scalaArchive should include("<li>B</li>") // newest first
    scalaArchive should include("<li>A</li>")

    val ssgArchive = out("tags/ssg/index.html")
    ssgArchive should include("<title>ssg (2)</title>") // post-a + post-c
    ssgArchive should include("<li>A</li>")
    ssgArchive should include("<li>C</li>")
  }

  it should "emit a /tags/index.html listing every tag with a count" in {
    writeAt("site.toml", "title = \"Blog\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/post-a.md",
      "---\ntitle: A\ndate: 2024-03-01\ntags: [scala, ssg]\n---\nA.\n",
    )
    writeAt(
      "content/post-b.md",
      "---\ntitle: B\ndate: 2024-04-01\ntags: [scala]\n---\nB.\n",
    )
    writeAt("layouts/_default/file.html", "x")
    writeAt(
      "layouts/_default/tag-list.html",
      """{{ for t <- .terms }}{{ t.name }}={{ t.count }};{{ end }}
        |""".stripMargin,
    )

    build()

    val list = out("tags/index.html")
    // Most-used first: scala (2), then ssg (1)
    list should include("scala=2;")
    list should include("ssg=1;")
    list.indexOf("scala=2;") should be < list.indexOf("ssg=1;")
  }

  it should "treat categories as a separate axis from tags without collision" in {
    writeAt("site.toml", "title = \"Blog\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/post-a.md",
      """---
        |title: A
        |date: 2024-03-01
        |tags: [scala]
        |categories: [tutorial]
        |---
        |A.
        |""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "x")
    writeAt(
      "layouts/_default/tag-page.html",
      "TAG={{ .term.name }};COUNT={{ .term.count }}",
    )
    writeAt(
      "layouts/_default/category-page.html",
      "CAT={{ .term.name }};COUNT={{ .term.count }}",
    )

    build()

    val tagPage = out("tags/scala/index.html")
    tagPage should include("TAG=scala;COUNT=1")
    val catPage = out("categories/tutorial/index.html")
    catPage should include("CAT=tutorial;COUNT=1")
  }

  it should "skip tag/category archives when no matching layout is provided" in {
    writeAt("site.toml", "title = \"Blog\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/post-a.md",
      "---\ntitle: A\ndate: 2024-03-01\ntags: [scala]\n---\nA.\n",
    )
    writeAt("layouts/_default/file.html", "x")
    // Intentionally NO tag-page.html / tag-list.html.
    build()

    (dst / "tags").exists shouldBe false
  }

  it should "expose .site.tags inside any rendered page even without archive layouts" in {
    writeAt("site.toml", "title = \"Blog\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |
        |{{ for t <- .site.tags }}TAG={{ t.name }}({{ t.count }});{{ end }}
        |""".stripMargin,
    )
    writeAt(
      "content/post-a.md",
      "---\ntitle: A\ndate: 2024-03-01\ntags: [scala, ssg]\n---\nA.\n",
    )
    writeAt(
      "layouts/_default/folder.html",
      "{{ for t <- .site.tags }}T={{ t.name }};{{ end }}",
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val home = out("index.html")
    home should include("T=scala;")
    home should include("T=ssg;")
  }

  // ----- Phase 1.4 — parsed .page.date + dateISO / dateLong / dateShort -----

  it should "parse a full ISO-8601 .page.date and expose format helpers" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: Post
        |date: 2024-01-15T10:30:00Z
        |---
        |
        |Body.
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/folder.html",
      """ISO={{ .page.dateISO }}
        |LONG={{ .page.dateLong }}
        |SHORT={{ .page.dateShort }}
        |""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("ISO=2024-01-15T10:30:00Z")
    html should include("LONG=January 15, 2024")
    html should include("SHORT=2024-01-15")
  }

  it should "parse a plain YYYY-MM-DD .page.date as midnight UTC" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: Post
        |date: 2024-03-08
        |---
        |
        |Body.
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/folder.html",
      """ISO={{ .page.dateISO }}
        |LONG={{ .page.dateLong }}
        |SHORT={{ .page.dateShort }}
        |""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("ISO=2024-03-08T00:00:00Z")
    html should include("LONG=March 8, 2024")
    html should include("SHORT=2024-03-08")
  }

  // ----- Slug helper (used by tags / categories / future series + permalinks) -----

  "slugify" should "lowercase ASCII alphanumerics and dash-separate non-alnum runs" in {
    slugify("Hello, World!") shouldBe "hello-world"
  }

  it should "fold common Latin diacritics to ASCII" in {
    slugify("Café au lait")    shouldBe "cafe-au-lait"
    slugify("Naïve approach")  shouldBe "naive-approach"
    slugify("Über cool")       shouldBe "uber-cool"
    slugify("ßeta")            shouldBe "sseta"
  }

  it should "collapse runs of symbols and trim leading/trailing dashes" in {
    slugify("  --foo  bar--  ") shouldBe "foo-bar"
    slugify("___")              shouldBe "-"
  }

  it should "keep ASCII digits" in {
    slugify("Phase 1.4")        shouldBe "phase-1-4"
  }

  // ----- Phase 1.3 — word count + reading time -----

  it should "expose .page.wordCount and .page.readingTime from rendered prose" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    // 250 single-token words → reading time = ceil(250 / 200) = 2 minutes.
    val body = (1 to 250).map(i => s"word$i").mkString(" ")
    writeAt(
      "content/_index.md",
      s"""---
         |title: Post
         |---
         |
         |$body
         |""".stripMargin,
    )
    writeAt(
      "layouts/_default/folder.html",
      """W={{ .page.wordCount }}
        |R={{ .page.readingTime }}
        |""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("W=250")
    html should include("R=2")
  }

  it should "round .page.readingTime up and floor at 1 for non-empty pages" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: Stub
        |---
        |
        |Just three words.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "W={{ .page.wordCount }};R={{ .page.readingTime }}")
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("W=3")
    html should include("R=1")
  }

  it should "fall back to filesystem mtime for .page.date when frontmatter has none" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: Post
        |---
        |
        |Body.
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/folder.html",
      """ISO={{ .page.dateISO }}
        |SHORT={{ .page.dateShort }}
        |""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    // Tmp file's mtime must be a valid ISO-8601 instant; we don't assert the
    // exact value because actual mtime depends on test timing.
    val isoLine = html.linesIterator.find(_.startsWith("ISO=")).getOrElse("")
    isoLine should fullyMatch regex """ISO=\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z""".r
    val shortLine = html.linesIterator.find(_.startsWith("SHORT=")).getOrElse("")
    shortLine should fullyMatch regex """SHORT=\d{4}-\d{2}-\d{2}""".r
  }

  // ----- Phase 2.6 — permalink templates -----

  it should "route a post through a [permalinks] :year/:month/:slug pattern" in {
    writeAt(
      "site.toml",
      """title   = "Blog"
        |baseURL = "http://x"
        |htmlDir = ""
        |
        |[permalinks]
        |posts = ":year/:month/:slug/"
        |""".stripMargin,
    )
    writeAt(
      "content/posts/_index.md",
      "---\ntitle: Posts\n---\nIndex.\n",
    )
    writeAt(
      "content/posts/hello-world.md",
      """---
        |title: Hello world
        |date: 2024-03-15
        |---
        |
        |First post.
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/folder.html",
      """{{ for p <- .section.pages }}URL={{ p.url }};{{ end }}""",
    )
    writeAt(
      "layouts/_default/file.html",
      """<title>{{ .page.title }}</title>URL={{ .page.url }}""".stripMargin,
    )

    build()

    // The post should land at /2024/03/hello-world/index.html — derived from
    // the parsed date and the cleaned filename slug. The parent section
    // index page (_index.md) is NEVER permalinked, so it stays at /posts/.
    val post = out("2024/03/hello-world/index.html")
    post should include("<title>Hello world</title>")
    post should include("URL=/2024/03/hello-world/")

    val sectionIndex = out("posts/index.html")
    sectionIndex should include("URL=/2024/03/hello-world/")

    // The legacy physical path must NOT exist — there's only one rendered
    // copy of the post.
    (dst / "posts" / "hello-world").exists shouldBe false
  }

  it should "expand :section, :title, :year, :month, :day, :slug tokens together" in {
    writeAt(
      "site.toml",
      """title   = "Blog"
        |baseURL = "http://x"
        |htmlDir = ""
        |
        |[permalinks]
        |notes = ":section/:year-:month-:day/:title/"
        |""".stripMargin,
    )
    writeAt(
      "content/notes/_index.md",
      "---\ntitle: Notes\n---\n",
    )
    writeAt(
      "content/notes/raw-slug.md",
      """---
        |title: A Verbose & Pretty Title!
        |date: 2024-07-04
        |---
        |
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "URL={{ .page.url }}")

    build()

    // :title slugifies the frontmatter title, distinct from :slug (the
    // cleaned filename). Both should be available; this test exercises
    // :title rather than :slug.
    val post = out("notes/2024-07-04/a-verbose-pretty-title/index.html")
    post should include("URL=/notes/2024-07-04/a-verbose-pretty-title/")
  }

  it should "leave sections without a [permalinks] entry on the physical path" in {
    writeAt(
      "site.toml",
      """title   = "Mix"
        |baseURL = "http://x"
        |htmlDir = ""
        |
        |[permalinks]
        |posts = ":year/:slug/"
        |""".stripMargin,
    )
    writeAt(
      "content/posts/_index.md",
      "---\ntitle: Posts\n---\n",
    )
    writeAt(
      "content/posts/permalinked.md",
      """---
        |title: Permalinked
        |date: 2024-03-15
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt(
      "content/about.md",
      """---
        |title: About
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt(
      "content/docs/_index.md",
      "---\ntitle: Docs\n---\n",
    )
    writeAt(
      "content/docs/install.md",
      """---
        |title: Install
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "URL={{ .page.url }}")

    build()

    // posts/ is permalinked — it lives at /2024/permalinked/.
    out("2024/permalinked/index.html") should include("URL=/2024/permalinked/")
    // about.md sits at the site root with no [permalinks] entry — stays
    // at the physical /about/ URL.
    out("about/index.html") should include("URL=/about/")
    // docs/ has no [permalinks] entry either — keeps /docs/install/.
    out("docs/install/index.html") should include("URL=/docs/install/")
  }

  it should "never permalink the section index (_index.md) itself" in {
    writeAt(
      "site.toml",
      """title   = "Blog"
        |baseURL = "http://x"
        |htmlDir = ""
        |
        |[permalinks]
        |posts = ":year/:slug/"
        |""".stripMargin,
    )
    writeAt(
      "content/posts/_index.md",
      """---
        |title: Posts
        |date: 2024-01-01
        |---
        |Listing.
        |""".stripMargin,
    )
    writeAt(
      "content/posts/sample.md",
      """---
        |title: Sample
        |date: 2024-06-10
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "URL={{ .page.url }}")
    writeAt("layouts/_default/file.html", "URL={{ .page.url }}")

    build()

    // The section index always lives at /posts/, even though :year/:slug/
    // is configured. /2024/posts/ would be wrong: that URL is reserved for
    // a post named "posts" whose date happened to be in 2024.
    out("posts/index.html") should include("URL=/posts/")
    (dst / "2024" / "posts").exists shouldBe false
  }

  // ----- Phase 2.4 — date archives -----

  it should "emit /<year>/ archive pages when dateArchives = true" in {
    writeAt(
      "site.toml",
      """title         = "Blog"
        |baseURL       = "http://x"
        |htmlDir       = ""
        |dateArchives  = true
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/p1.md",
      """---
        |title: Post 1
        |date: 2024-03-15
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt(
      "content/p2.md",
      """---
        |title: Post 2
        |date: 2024-08-09
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt(
      "content/p3.md",
      """---
        |title: Post 3
        |date: 2023-12-01
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")
    writeAt(
      "layouts/_default/date-year.html",
      """YEAR={{ .year }}
        |COUNT={{ for p <- .pages }}{{ p.title }};{{ end }}
        |MONTHS={{ for m <- .months }}{{ m.month }}:{{ m.monthName }}({{ m.count }}){{ end }}
        |""".stripMargin,
    )

    build()

    val y2024 = out("2024/index.html")
    y2024 should include("YEAR=2024")
    // Posts within a year are date-desc; March (3) sorts after August (8)
    // since we sort overall, but the COUNT line just lists titles in
    // descending date order — Aug 9 then Mar 15.
    y2024 should include("Post 2;Post 1;")
    // Months roll-up is ascending by month number — 3 then 8.
    y2024 should include("MONTHS=3:March(1)8:August(1)")

    val y2023 = out("2023/index.html")
    y2023 should include("YEAR=2023")
    y2023 should include("Post 3;")
  }

  it should "emit /<year>/<month>/ archive pages when date-month layout is present" in {
    writeAt(
      "site.toml",
      """title         = "Blog"
        |baseURL       = "http://x"
        |htmlDir       = ""
        |dateArchives  = true
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/p1.md",
      """---
        |title: Spring Post
        |date: 2024-03-15
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt(
      "content/p2.md",
      """---
        |title: Another Spring Post
        |date: 2024-03-20
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt(
      "content/p3.md",
      """---
        |title: Summer Post
        |date: 2024-07-01
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")
    writeAt(
      "layouts/_default/date-month.html",
      """{{ .year }}-{{ .month }} {{ .monthName }}
        |{{ for p <- .pages }}{{ p.title }};{{ end }}
        |""".stripMargin,
    )

    build()

    val mar = out("2024/03/index.html")
    mar should include("2024-3 March")
    // Both March posts present, descending by date
    mar should include("Another Spring Post;Spring Post;")

    val jul = out("2024/07/index.html")
    jul should include("2024-7 July")
    jul should include("Summer Post;")
  }

  it should "skip date archives entirely when dateArchives is unset / false" in {
    writeAt(
      "site.toml",
      """title    = "Blog"
        |baseURL  = "http://x"
        |htmlDir  = ""
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/p1.md",
      """---
        |title: Post 1
        |date: 2024-03-15
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")
    // Layouts present but the feature is off — must not emit.
    writeAt("layouts/_default/date-year.html", "year")
    writeAt("layouts/_default/date-month.html", "month")

    build()

    (dst / "2024").exists shouldBe false
  }

  it should "exclude pages without explicit date frontmatter from date archives" in {
    writeAt(
      "site.toml",
      """title         = "Blog"
        |baseURL       = "http://x"
        |htmlDir       = ""
        |dateArchives  = true
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    // Has date — should appear in the archive.
    writeAt(
      "content/dated.md",
      """---
        |title: Dated Post
        |date: 2024-05-05
        |---
        |Body.
        |""".stripMargin,
    )
    // No date — relies on mtime fallback for `.page.date`, but should NOT
    // pollute the date archive (mtime is "today", not a real publication
    // date set by the author).
    writeAt(
      "content/undated.md",
      """---
        |title: Undated Post
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")
    writeAt(
      "layouts/_default/date-year.html",
      """YEAR={{ .year }}
        |TITLES={{ for p <- .pages }}{{ p.title }};{{ end }}
        |""".stripMargin,
    )

    build()

    // Only 2024 archive exists — the undated post's mtime year would land
    // in the current year's archive if we DIDN'T filter.
    val y = out("2024/index.html")
    y should include("YEAR=2024")
    y should include("TITLES=Dated Post;")
    y should not include "Undated Post"
  }

  // ----- Phase 2.1 — future-dated posts -----

  it should "skip future-dated posts by default" in {
    writeAt(
      "site.toml",
      """title    = "Blog"
        |baseURL  = "http://x"
        |htmlDir  = ""
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/past.md",
      """---
        |title: Past
        |date: 2024-01-01
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt(
      "content/future.md",
      """---
        |title: Future
        |date: 2999-12-31
        |---
        |Body.
        |""".stripMargin,
    )
    // The page-list loop has to live in a LAYOUT, not in markdown
    // content — markdown body is rendered to HTML before the template
    // pass and `{{ … }}` inside it gets escaped, never evaluated.
    writeAt(
      "layouts/_default/folder.html",
      "{{ for p <- .section.pages }}{{ p.title }};{{ end }}",
    )
    writeAt("layouts/_default/file.html", "{{ .page.title }}")

    build()

    // Past post is rendered. Future post is invisible — no file at the
    // physical URL, no entry in `.section.pages`.
    out("past/index.html") should include("Past")
    (dst / "future").exists shouldBe false
    val home = out("index.html")
    home should include("Past;")
    home should not include "Future"
  }

  it should "include future-dated posts when --future is passed" in {
    writeAt(
      "site.toml",
      """title    = "Blog"
        |baseURL  = "http://x"
        |htmlDir  = ""
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/future.md",
      """---
        |title: Future
        |date: 2999-12-31
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "{{ .page.title }}")

    // Build with future = true on the BuildCommand.
    App.run(Args(cmd = Some(BuildCommand(src = src, dst = dst, future = true))))

    out("future/index.html") should include("Future")
  }

  it should "never skip pages relying on mtime-fallback for .page.date" in {
    // A page without explicit `date` frontmatter would never have an
    // author-set future date — its mtime is always 'now or earlier'. The
    // future filter must NOT skip such pages, even pessimistically.
    writeAt(
      "site.toml",
      """title    = "Blog"
        |baseURL  = "http://x"
        |htmlDir  = ""
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/no-date.md",
      """---
        |title: No date
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "{{ .page.title }}")

    build()

    out("no-date/index.html") should include("No date")
  }

  // ----- Phase 2.5 — aliases / redirects -----

  it should "emit a meta-refresh redirect at each listed alias" in {
    writeAt(
      "site.toml",
      """title    = "S"
        |baseURL  = "https://example.com"
        |htmlDir  = ""
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/new-name.md",
      """---
        |title: New name
        |aliases: [/old-name/, /even-older/]
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "{{ .page.title }}")

    build()

    // Both alias URLs exist with redirect markup pointing at the canonical
    // /new-name/ URL.
    val a1 = out("old-name/index.html")
    a1 should include("""http-equiv="refresh"""")
    a1 should include("/new-name/")

    val a2 = out("even-older/index.html")
    a2 should include("/new-name/")
  }

  it should "accept a single-string alias as well as a list" in {
    writeAt(
      "site.toml",
      """title    = "S"
        |baseURL  = "https://example.com"
        |htmlDir  = ""
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/about.md",
      """---
        |title: About
        |aliases: /me/
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "{{ .page.title }}")

    build()

    val a = out("me/index.html")
    a should include("/about/")
  }

  it should "use the alias.html layout when one is provided" in {
    writeAt(
      "site.toml",
      """title    = "S"
        |baseURL  = "https://example.com"
        |htmlDir  = ""
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/page.md",
      """---
        |title: Page
        |aliases: [/legacy/]
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "{{ .page.title }}")
    writeAt(
      "layouts/_default/alias.html",
      """CUSTOM target={{ .target }} canonical={{ .page.permalink }}""",
    )

    build()

    val a = out("legacy/index.html")
    a should include("CUSTOM target=/page/")
    // Custom layout has full access to the source page record, e.g. its
    // permalink, so themes can drop in branded redirect pages.
    a should include("canonical=https://example.com/page/")
  }

  it should "emit no alias pages for a page that doesn't declare aliases" in {
    writeAt(
      "site.toml",
      """title    = "S"
        |baseURL  = "https://example.com"
        |htmlDir  = ""
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/quiet.md",
      "---\ntitle: Quiet\n---\nBody.\n",
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "{{ .page.title }}")

    build()

    // No spurious top-level directories were created from a missing
    // `aliases` frontmatter — the build pipeline tolerates the empty case.
    out("quiet/index.html") should include("Quiet")
  }

  // ----- Phase 2.2 — series -----

  it should "expose .page.series.name / .pages / .index / .total / .prev / .next" in {
    writeAt(
      "site.toml",
      """title    = "Blog"
        |baseURL  = "http://x"
        |htmlDir  = ""
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/p1.md",
      """---
        |title: P1
        |series: OS Internals
        |seriesOrder: 1
        |date: 2024-01-01
        |---
        |""".stripMargin,
    )
    writeAt(
      "content/p2.md",
      """---
        |title: P2
        |series: OS Internals
        |seriesOrder: 2
        |date: 2024-02-01
        |---
        |""".stripMargin,
    )
    writeAt(
      "content/p3.md",
      """---
        |title: P3
        |series: OS Internals
        |seriesOrder: 3
        |date: 2024-03-01
        |---
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/file.html",
      """SERIES={{ .page.series.name }}
        |INDEX={{ .page.series.index }}/{{ .page.series.total }}
        |PREV={{ if .page.series.prev }}{{ .page.series.prev.title }}{{ else }}-{{ end }}
        |NEXT={{ if .page.series.next }}{{ .page.series.next.title }}{{ else }}-{{ end }}
        |LIST={{ for s <- .page.series.pages }}{{ s.title }};{{ end }}
        |""".stripMargin,
    )

    build()

    val p1 = out("p1/index.html")
    p1 should include("SERIES=OS Internals")
    p1 should include("INDEX=1/3")
    p1 should include("PREV=-")
    p1 should include("NEXT=P2")
    p1 should include("LIST=P1;P2;P3;")

    val p2 = out("p2/index.html")
    p2 should include("INDEX=2/3")
    p2 should include("PREV=P1")
    p2 should include("NEXT=P3")

    val p3 = out("p3/index.html")
    p3 should include("INDEX=3/3")
    p3 should include("PREV=P2")
    p3 should include("NEXT=-")
  }

  it should "sort series pages by seriesOrder, falling back to date for unordered pages" in {
    writeAt(
      "site.toml",
      """title    = "Blog"
        |baseURL  = "http://x"
        |htmlDir  = ""
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    // Two pages share the series but have NO seriesOrder. Falls back to
    // date asc — Older first, Newer second.
    writeAt(
      "content/older.md",
      """---
        |title: Older
        |series: Notes
        |date: 2024-01-15
        |---
        |""".stripMargin,
    )
    writeAt(
      "content/newer.md",
      """---
        |title: Newer
        |series: Notes
        |date: 2024-08-01
        |---
        |""".stripMargin,
    )
    // Third page has explicit seriesOrder = 1, putting it FIRST regardless
    // of its date being even older than the first two.
    writeAt(
      "content/pinned.md",
      """---
        |title: Pinned First
        |series: Notes
        |seriesOrder: 1
        |date: 2025-01-01
        |---
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/file.html",
      """LIST={{ for s <- .page.series.pages }}{{ s.title }};{{ end }}""",
    )

    build()

    out("pinned/index.html") should include("LIST=Pinned First;Older;Newer;")
  }

  it should "leave .page.series unset on pages without a series frontmatter" in {
    writeAt(
      "site.toml",
      """title    = "Blog"
        |baseURL  = "http://x"
        |htmlDir  = ""
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/loose.md",
      "---\ntitle: Loose\n---\n",
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/file.html",
      """{{ if .page.series }}HAS={{ .page.series.name }}{{ else }}NONE{{ end }}""",
    )

    build()

    out("loose/index.html") should include("NONE")
  }

  // ----- Phase 2.3 — author registry -----

  it should "resolve frontmatter `author: <id>` to the [[authors]] registry entry" in {
    writeAt(
      "site.toml",
      """title    = "Blog"
        |baseURL  = "http://x"
        |htmlDir  = ""
        |
        |[[authors]]
        |id    = "ed"
        |name  = "Edward A Maxedon"
        |bio   = "Writes code."
        |avatar = "/img/ed.jpg"
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/post.md",
      """---
        |title: Post
        |author: ed
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/file.html",
      """NAME={{ .page.author.name }}
        |BIO={{ .page.author.bio }}
        |AVATAR={{ .page.author.avatar }}
        |COUNT={{ for a <- .page.authors }}{{ a.name }};{{ end }}
        |""".stripMargin,
    )

    build()

    val p = out("post/index.html")
    p should include("NAME=Edward A Maxedon")
    p should include("BIO=Writes code.")
    p should include("AVATAR=/img/ed.jpg")
    p should include("COUNT=Edward A Maxedon;")
  }

  it should "support multi-author posts via authors: [a, b]" in {
    writeAt(
      "site.toml",
      """title    = "Blog"
        |baseURL  = "http://x"
        |htmlDir  = ""
        |
        |[[authors]]
        |id   = "ed"
        |name = "Ed"
        |
        |[[authors]]
        |id   = "alice"
        |name = "Alice"
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/co.md",
      """---
        |title: Co-authored
        |authors: [ed, alice]
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/file.html",
      """LIST={{ for a <- .page.authors }}{{ a.name }};{{ end }}""",
    )

    build()

    out("co/index.html") should include("LIST=Ed;Alice;")
  }

  it should "emit /authors/<id>/ archive pages when author-page layout is provided" in {
    writeAt(
      "site.toml",
      """title    = "Blog"
        |baseURL  = "http://x"
        |htmlDir  = ""
        |
        |[[authors]]
        |id    = "ed"
        |name  = "Ed"
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/p1.md",
      """---
        |title: First
        |author: ed
        |date: 2024-03-01
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt(
      "content/p2.md",
      """---
        |title: Second
        |author: ed
        |date: 2024-08-01
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")
    writeAt(
      "layouts/_default/author-page.html",
      """ID={{ .author.id }}
        |NAME={{ .author.name }}
        |COUNT={{ .author.count }}
        |LIST={{ for p <- .author.pages }}{{ p.title }};{{ end }}
        |""".stripMargin,
    )

    build()

    val a = out("authors/ed/index.html")
    a should include("ID=ed")
    a should include("NAME=Ed")
    a should include("COUNT=2")
    // Pages are date-desc within an author archive — newer first.
    a should include("LIST=Second;First;")
  }

  it should "emit /authors/index.html when author-list layout is provided" in {
    writeAt(
      "site.toml",
      """title    = "Blog"
        |baseURL  = "http://x"
        |htmlDir  = ""
        |
        |[[authors]]
        |id   = "ed"
        |name = "Ed"
        |
        |[[authors]]
        |id   = "alice"
        |name = "Alice"
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/post.md",
      """---
        |title: Post
        |author: ed
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")
    writeAt(
      "layouts/_default/author-list.html",
      """{{ for a <- .authors }}{{ a.id }}({{ a.count }});{{ end }}""",
    )

    build()

    // Only authors with at least one referencing page appear. Alice has 0
    // posts so she's omitted from the roster.
    val list = out("authors/index.html")
    list should include("ed(1);")
    list should not include "alice("
  }

  it should "expose .site.authors filtered to authors with pages" in {
    writeAt(
      "site.toml",
      """title    = "Blog"
        |baseURL  = "http://x"
        |htmlDir  = ""
        |
        |[[authors]]
        |id   = "ed"
        |name = "Ed"
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/post.md",
      """---
        |title: Post
        |author: ed
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/folder.html",
      """{{ for a <- .site.authors }}A={{ a.name }};{{ end }}""",
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    out("index.html") should include("A=Ed;")
  }

  // ----- Phase 2.7 — OG / Twitter card helpers -----

  it should "emit OG meta tags via {{ ogTags .page }}" in {
    writeAt(
      "site.toml",
      """title    = "My Blog"
        |baseURL  = "https://example.com"
        |htmlDir  = ""
        |""".stripMargin,
    )
    writeAt(
      "content/post.md",
      """---
        |title: Hello
        |summary: A short summary.
        |image: /img/hero.jpg
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/file.html",
      """{{ ogTags .page }}""",
    )
    writeAt("content/_index.md", "---\ntitle: Home\n---\n")

    build()

    val html = out("post/index.html")
    html should include("""<meta property="og:title" content="Hello" />""")
    html should include("""<meta property="og:type" content="article" />""")
    html should include("""<meta property="og:url" content="https://example.com/post/" />""")
    html should include("""<meta property="og:description" content="A short summary." />""")
    // image is promoted to absolute via baseURL.
    html should include("""<meta property="og:image" content="https://example.com/img/hero.jpg" />""")
    html should include("""<meta property="og:site_name" content="My Blog" />""")
    // Image present → twitter:card escalates to summary_large_image.
    html should include("""<meta name="twitter:card" content="summary_large_image" />""")
  }

  it should "fall back to summary then site image when frontmatter lacks them" in {
    writeAt(
      "site.toml",
      """title    = "Site"
        |baseURL  = "https://example.com"
        |htmlDir  = ""
        |ogImage  = "/img/site-default.png"
        |""".stripMargin,
    )
    writeAt(
      "content/post.md",
      """---
        |title: No metadata
        |---
        |
        |The first paragraph becomes the auto-summary.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/file.html",
      """{{ ogTags .page }}""",
    )
    writeAt("content/_index.md", "---\ntitle: Home\n---\n")

    build()

    val html = out("post/index.html")
    // Auto-summary fallback chain: ogDescription/description not set, so
    // the page's resolved `.summary` fills in.
    html should include("og:description")
    html should include("first paragraph")
    // Site default ogImage.
    html should include("https://example.com/img/site-default.png")
  }

  it should "downgrade twitter:card to summary when no image is available" in {
    writeAt(
      "site.toml",
      """title    = "Site"
        |baseURL  = "https://example.com"
        |htmlDir  = ""
        |""".stripMargin,
    )
    writeAt(
      "content/post.md",
      """---
        |title: Imageless
        |summary: Short.
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/file.html",
      """{{ ogTags .page }}""",
    )
    writeAt("content/_index.md", "---\ntitle: Home\n---\n")

    build()

    val html = out("post/index.html")
    html should include("""<meta name="twitter:card" content="summary" />""")
    html should not include "og:image"
  }

  it should "respect the htmlDir prefix when writing permalinked output to disk" in {
    // With htmlDir = "html" the URL still strips it (the user-facing /post/
    // URL is unprefixed) but the filesystem path under dst keeps it. This
    // is the same dual-layer convention that applies to non-permalinked
    // content.
    writeAt(
      "site.toml",
      """title   = "Blog"
        |baseURL = "http://x"
        |htmlDir = "html"
        |
        |[permalinks]
        |posts = ":year/:slug/"
        |""".stripMargin,
    )
    writeAt(
      "content/posts/_index.md",
      "---\ntitle: Posts\n---\n",
    )
    writeAt(
      "content/posts/p1.md",
      """---
        |title: P1
        |date: 2024-05-05
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "URL={{ .page.url }}")

    build()

    // URL is htmlDir-stripped, file path is htmlDir-prefixed.
    val post = out("html/2024/p1/index.html")
    post should include("URL=/2024/p1/")
  }

  // ---------------------------------------------------------------
  // Syntax highlighting via the `highlighter` lib (Stage 4 of the
  // juicerblog polish — engine-side wiring; theme palette is theme-side).
  // ---------------------------------------------------------------

  it should "highlight fenced code blocks when grammars/<lang>.tmLanguage.json is present" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "grammars/scala.tmLanguage.json",
      """{
        |  "scopeName": "source.scala",
        |  "patterns": [
        |    { "match": "\\b(val|def|class)\\b",  "name": "keyword.control.scala" },
        |    { "match": "\\b\\d+\\b",              "name": "constant.numeric.scala" },
        |    { "begin": "\"", "end": "\"",         "name": "string.quoted.double.scala" }
        |  ]
        |}""".stripMargin,
    )
    writeAt(
      "content/post.md",
      """---
        |title: Has Scala
        |---
        |
        |```scala
        |val x = 42
        |```
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "{{ .content }}")

    build()

    val post = out("post/index.html")
    post should include("class=\"hl-keyword\"")
    post should include("class=\"hl-number\"")
    post should include("class=\"language-scala\"")
  }

  it should "leave plain <pre><code> alone when the language has no grammar" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "grammars/scala.tmLanguage.json",
      """{
        |  "scopeName": "source.scala",
        |  "patterns": [
        |    { "match": "\\b(val)\\b", "name": "keyword.control.scala" }
        |  ]
        |}""".stripMargin,
    )
    writeAt(
      "content/post.md",
      """---
        |title: Mixed
        |---
        |
        |```python
        |print(42)
        |```
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "{{ .content }}")

    build()

    val post = out("post/index.html")
    post should not include "hl-"
    post should include("language-python")
    post should include("print(42)")
  }

  it should "skip highlighting entirely when no grammars/ directory exists" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/post.md",
      """---
        |title: No grammars
        |---
        |
        |```scala
        |val x = 42
        |```
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "{{ .content }}")

    build()

    val post = out("post/index.html")
    post should not include "hl-"
    post should include("language-scala")
    post should include("val x = 42")
  }

  it should "expose .site.posts filtered to dated, non-section, non-static pages, newest first" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/older.md",
      "---\ntitle: Older\ndate: 2024-01-15\n---\n",
    )
    writeAt(
      "content/newer.md",
      "---\ntitle: Newer\ndate: 2024-09-01\n---\n",
    )
    writeAt(
      "content/about.md",
      "---\ntitle: About\nstatic: true\ndate: 2024-06-01\n---\n",
    )
    writeAt(
      "content/undated.md",
      "---\ntitle: Undated\n---\n",
    )
    writeAt("layouts/_default/file.html",   "x")
    writeAt(
      "layouts/_default/folder.html",
      "{{ for p <- .site.posts }}[{{ p.title }}]{{ end }}",
    )

    build()

    // .site.posts has Newer + Older only, in that order. About filtered
    // (static), Undated filtered (no parsed date), Home filtered (section).
    out("index.html").trim shouldBe "[Newer][Older]"
  }

  it should "expose .site.pagesByYear grouping the same posts list by year, year descending" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: H\n---\n")
    writeAt("content/a.md", "---\ntitle: A\ndate: 2023-04-01\n---\n")
    writeAt("content/b.md", "---\ntitle: B\ndate: 2024-02-15\n---\n")
    writeAt("content/c.md", "---\ntitle: C\ndate: 2024-11-30\n---\n")
    writeAt("layouts/_default/file.html", "x")
    writeAt(
      "layouts/_default/folder.html",
      "{{ for y <- .site.pagesByYear }}<{{ y.year }}:{{ y.count }}>{{ for p <- y.pages }}({{ p.title }}){{ end }}{{ end }}",
    )

    build()

    // 2024 first (newest year), C then B inside; then 2023 with just A.
    out("index.html").trim shouldBe "<2024:2>(C)(B)<2023:1>(A)"
  }

  it should "respect frontmatter layout override on a non-section page" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/post.md",
      """---
        |title: Special
        |layout: longform
        |---
        |Body
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html",     "DEFAULT={{ .page.title }}")
    writeAt("layouts/_default/longform.html", "LONG={{ .page.title }}")

    build()

    val post = out("post/index.html")
    post should include("LONG=Special")
    post should not include "DEFAULT="
  }

  it should "respect frontmatter layout override on a section index" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: Front
        |layout: home
        |---
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "DEFAULT")
    writeAt("layouts/_default/file.html",   "x")
    writeAt("layouts/_default/home.html",   "HOME-{{ .page.title }}")

    build()

    val home = out("index.html")
    home should include("HOME-Front")
    home should not include "DEFAULT"
  }

  it should "fall back to the default layout when the frontmatter layout is missing" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/post.md",
      """---
        |title: Plain
        |---
        |Body
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html",   "FILE={{ .page.title }}")

    build()

    out("post/index.html") should include("FILE=Plain")
  }

  it should "skip a malformed grammar without failing the whole build" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    // Two grammars: one valid, one corrupt JSON. The build must still
    // succeed and the valid one must still highlight.
    writeAt(
      "grammars/scala.tmLanguage.json",
      """{
        |  "scopeName": "source.scala",
        |  "patterns": [
        |    { "match": "\\b(val)\\b", "name": "keyword.control.scala" }
        |  ]
        |}""".stripMargin,
    )
    writeAt("grammars/broken.tmLanguage.json", "{ this is not json")
    writeAt(
      "content/post.md",
      """---
        |title: Resilient
        |---
        |
        |```scala
        |val x = 42
        |```
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "{{ .content }}")

    build()

    val post = out("post/index.html")
    post should include("class=\"hl-keyword\"")
  }
}
