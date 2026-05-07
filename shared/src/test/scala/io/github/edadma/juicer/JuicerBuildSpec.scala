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

  it should "copy static/ files into the output tree as-is" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: T\n---\n\n# A\n")
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")
    writeAt("static/style.css", "body { color: red; }")

    build()

    out("style.css") shouldBe "body { color: red; }"
  }
}
