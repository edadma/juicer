package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for permalink templates (Phase 2.6) — extracted from
  * JuicerBuildSpec for readability.
  */
class PermalinksSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "juicer permalinks" should "route a post through a [permalinks] :year/:month/:slug pattern" in {
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
}
