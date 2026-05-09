package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for word count + reading time (Phase 1.3) — extracted from
  * JuicerBuildSpec for readability.
  */
class WordCountReadingTimeSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "juicer word count + reading time" should "expose .page.wordCount and .page.readingTime from rendered prose" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    // 250 single-token words → reading time = ceil(250 / 200) = 2 minutes.
    val body = (1 to 250).map(i => s"word$i").mkString(" ")
    writeAt(
      "content/_index.md",
      s"""---
         |title: Post
         |---
         |
         |$body
         |""".stripMargin,
    )
    writeAt(
      "layouts/_default/folder.html",
      """W={{ .page.wordCount }}
        |R={{ .page.readingTime }}
        |""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("W=250")
    html should include("R=2")
  }

  it should "round .page.readingTime up and floor at 1 for non-empty pages" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: Stub
        |---
        |
        |Just three words.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "W={{ .page.wordCount }};R={{ .page.readingTime }}")
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("W=3")
    html should include("R=1")
  }

  it should "fall back to filesystem mtime for .page.date when frontmatter has none" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: Post
        |---
        |
        |Body.
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/folder.html",
      """ISO={{ .page.dateISO }}
        |SHORT={{ .page.dateShort }}
        |""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    // Tmp file's mtime must be a valid ISO-8601 instant; we don't assert the
    // exact value because actual mtime depends on test timing.
    val isoLine = html.linesIterator.find(_.startsWith("ISO=")).getOrElse("")
    isoLine should fullyMatch regex """ISO=\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z""".r
    val shortLine = html.linesIterator.find(_.startsWith("SHORT=")).getOrElse("")
    shortLine should fullyMatch regex """SHORT=\d{4}-\d{2}-\d{2}""".r
  }
}
