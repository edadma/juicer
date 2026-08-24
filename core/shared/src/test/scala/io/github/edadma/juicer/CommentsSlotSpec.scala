package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** End-to-end tests for the `[comments]` config slot.
  *
  * juicer never ships a comments backend. The contract is: site authors
  * declare provider + provider-specific config under `[comments]` in
  * `site.toml`, and theme partials read it back via `.site.comments.*`.
  * The engine itself only surfaces the config; the embed HTML is
  * theme-and-template territory.
  */
class CommentsSlotSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  private def writeMinimalLayouts(): Unit = {
    writeAt("layouts/_default/file.html", "x")
  }

  "site.comments" should "expose [comments] config to templates" in {
    writeAt(
      "site.toml",
      """title   = "S"
        |baseURL = "http://x"
        |
        |[comments]
        |provider = "giscus"
        |repo     = "edadma/juicer"
        |repoId   = "R_kgDOXXXXXX"
        |category = "Announcements"
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/folder.html",
      """<div data-provider="{{ .site.comments.provider }}"
        |     data-repo="{{ .site.comments.repo }}"
        |     data-repo-id="{{ .site.comments.repoId }}"
        |     data-category="{{ .site.comments.category }}"></div>
        |""".stripMargin,
    )
    writeMinimalLayouts()

    build()

    val html = out("index.html")
    html should include("""data-provider="giscus"""")
    html should include("""data-repo="edadma/juicer"""")
    html should include("""data-repo-id="R_kgDOXXXXXX"""")
    html should include("""data-category="Announcements"""")
  }

  it should "be absent when no [comments] section is configured" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/folder.html",
      """{{ if .site.comments }}<has-comments/>{{ else }}<no-comments/>{{ end }}""",
    )
    writeMinimalLayouts()

    build()

    val html = out("index.html")
    html should include("<no-comments/>")
    html should not include "<has-comments/>"
  }
}
