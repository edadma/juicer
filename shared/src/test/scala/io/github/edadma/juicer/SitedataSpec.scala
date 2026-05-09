package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for `.site.posts` and `.site.pagesByYear` — extracted from
  * JuicerBuildSpec for readability.
  */
class SitedataSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "juicer sitedata" should "expose .site.posts filtered to dated, non-section, non-static pages, newest first" in {
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
}
