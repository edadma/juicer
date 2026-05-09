package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for date archives (Phase 2.4) — extracted from JuicerBuildSpec for readability. */
class DateArchivesSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "juicer date archives" should "emit /<year>/ archive pages when dateArchives = true" in {
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
}
