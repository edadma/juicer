package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Sanity check: a partial that calls another partial which calls
  * `ogTags .page` should work. This is the shape juicer's themes use
  * after the SEO refactor (file.html → baseof → partial 'head' → partial 'seo' → ogTags).
  */
class NestedPartialSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "partial-in-partial" should "preserve .page access through two levels" in {
    writeAt(
      "site.toml",
      """title    = "S"
        |baseURL  = "https://example.com"
        |htmlDir  = ""
        |""".stripMargin,
    )
    writeAt(
      "content/post.md",
      """---
        |title: Hello
        |summary: A summary.
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: Home\n---\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/file.html",
      """FILE:.page.title={{ .page.title }}
        |{{ partial 'level1' . }}""".stripMargin,
    )
    writeAt(
      "partials/level1.html",
      """L1:.page.title={{ .page.title }}
        |{{ partial 'level2' . }}""".stripMargin,
    )
    writeAt(
      "partials/level2.html",
      """L2:.page.title={{ .page.title }}
        |{{ ogTags .page }}""".stripMargin,
    )

    build()

    val html = out("post/index.html")
    html should include("FILE:.page.title=Hello")
    html should include("L1:.page.title=Hello")
    html should include("L2:.page.title=Hello")
    html should include("""<meta property="og:title" content="Hello" />""")
  }
}
