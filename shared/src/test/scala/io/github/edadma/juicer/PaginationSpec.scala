package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for pagination (Phase 1.2) — extracted from JuicerBuildSpec for readability. */
class PaginationSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "juicer pagination" should "slice a section's pages into page/N/index.html runs at the configured paginate size" in {
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
}
