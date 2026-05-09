package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for tag + category archives (Phase 1.1) — extracted from JuicerBuildSpec for readability. */
class TaxonomiesSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "juicer taxonomies" should "emit a /tags/<slug>/ archive page per tag" in {
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
}
