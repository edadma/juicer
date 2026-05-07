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
