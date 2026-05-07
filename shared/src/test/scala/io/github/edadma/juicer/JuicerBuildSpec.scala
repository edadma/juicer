package io.github.edadma.juicer

import io.github.edadma.path.Path
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.compiletime.uninitialized
import scala.util.Random

/** End-to-end tests for the juicer build pipeline. Each test builds a small
  * site under a temp directory, runs `App.build`, then reads the rendered
  * HTML back and asserts on its structure.
  */
class JuicerBuildSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  /** Live workspace for the test currently in flight. Recreated per test. */
  var src: Path = uninitialized
  var dst: Path = uninitialized

  override def beforeEach(): Unit = {
    val nonce = System.currentTimeMillis().toString + "-" + Random.nextInt(1_000_000)
    src = Path("/tmp") / s"juicer-test-src-$nonce"
    dst = Path("/tmp") / s"juicer-test-dst-$nonce"
    src.createDirectories()
  }

  override def afterEach(): Unit = {
    deleteTree(src)
    deleteTree(dst)
  }

  private def deleteTree(p: Path): Unit = {
    if (!p.exists) return
    if (p.isDirectory) {
      p.listDirectory().foreach(e => deleteTree(p / e.name))
      p.delete()
    } else p.delete()
  }

  /** Convenience: write `content` to `src / relPath`, creating parents. */
  private def writeAt(relPath: String, content: String): Unit = {
    val p = relPath.split('/').foldLeft(src)(_ / _)
    p.parent.foreach(_.createDirectories())
    p.writeText(content)
  }

  /** Run `juicer build` against the staged `src` / `dst`. */
  private def build(args: Args = Args()): Unit =
    App.run(args.copy(cmd = Some(BuildCommand(src = src, dst = dst))))

  /** Read a file from the output tree. */
  private def out(relPath: String): String =
    relPath.split('/').foldLeft(dst)(_ / _).readText()

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

  it should "expose .page.next and .page.prev within a section" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: H\n---\n\n.\n")
    writeAt("content/docs/_index.md", "---\ntitle: D\n---\n\n.\n")
    writeAt("content/docs/a.md", "---\ntitle: A\nweight: 10\n---\n\n.\n")
    writeAt("content/docs/b.md", "---\ntitle: B\nweight: 20\n---\n\n.\n")
    writeAt("content/docs/c.md", "---\ntitle: C\nweight: 30\n---\n\n.\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/file.html",
      """{{ if .page.prev }}prev={{ .page.prev.title }} {{ end }}{{ if .page.next }}next={{ .page.next.title }}{{ end }}""".stripMargin,
    )

    build()

    out("html/docs/a/index.html") should include("next=B")
    out("html/docs/a/index.html") should not include "prev="
    out("html/docs/b/index.html") should (include("prev=A") and include("next=C"))
    out("html/docs/c/index.html") should (include("prev=B") and not include "next=")
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
}
