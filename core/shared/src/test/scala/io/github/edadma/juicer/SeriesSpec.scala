package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for series (Phase 2.2) — extracted from JuicerBuildSpec for readability. */
class SeriesSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "juicer series" should "expose .page.series.name / .pages / .index / .total / .prev / .next" in {
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
}
