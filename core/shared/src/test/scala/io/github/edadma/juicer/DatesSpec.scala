package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for parsed `.page.date` + `dateISO` / `dateLong` / `dateShort` helpers
  * (Phase 1.4) — extracted from JuicerBuildSpec for readability.
  */
class DatesSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "juicer parsed page dates" should "parse a full ISO-8601 .page.date and expose format helpers" in {
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
}
