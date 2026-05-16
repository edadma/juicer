package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Regression tests for the baseURL.path link prefix.
  *
  * On a deploy where `baseURL` has a non-empty path component (e.g.
  * `https://example.com/foo/`), root-relative markdown links of the
  * form `[text](/page/)` must be rewritten to `<a href="/foo/page/">`
  * in the rendered HTML — otherwise the browser resolves them against
  * the apex and 404s.
  *
  * The bug this guards against: `transformLinks` walked Paragraph,
  * Heading, and BlockQuote but silently dropped lists, tables,
  * definition lists, callouts, footnotes, and collapsibles — so a
  * link in a list item (the most common authoring location) never had
  * the prefix applied.
  */
class SubpathLinksSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  private val Site =
    """title   = "Sub"
      |author  = "Ed"
      |baseURL = "https://example.com/foo/"
      |""".stripMargin

  private val MinimalLayout =
    """<body>{{ .content }}</body>
      |""".stripMargin

  // -----------------------------------------------------------------

  "subpath links" should "prefix paragraph links with baseURL.path" in {
    writeAt("site.toml", Site)
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |
        |See [Reference](/reference/opcodes/) for details.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", MinimalLayout)
    writeAt("layouts/_default/file.html", MinimalLayout)

    build()

    val html = out("index.html")
    html should include("href=\"/foo/reference/opcodes/\"")
    html should not include "href=\"/reference/opcodes/\""
  }

  it should "prefix list-item links with baseURL.path" in {
    writeAt("site.toml", Site)
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |
        |- [One](/one/) — first
        |- [Two](/two/) — second
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", MinimalLayout)
    writeAt("layouts/_default/file.html", MinimalLayout)

    build()

    val html = out("index.html")
    html should include("href=\"/foo/one/\"")
    html should include("href=\"/foo/two/\"")
    html should not include "href=\"/one/\""
    html should not include "href=\"/two/\""
  }

  it should "prefix nested list-item links" in {
    writeAt("site.toml", Site)
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |
        |- top
        |  - [Deep](/deep/) inside a nested list
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", MinimalLayout)
    writeAt("layouts/_default/file.html", MinimalLayout)

    build()

    out("index.html") should include("href=\"/foo/deep/\"")
  }

  it should "prefix blockquote links" in {
    writeAt("site.toml", Site)
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |
        |> See [there](/there/).
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", MinimalLayout)
    writeAt("layouts/_default/file.html", MinimalLayout)

    build()

    out("index.html") should include("href=\"/foo/there/\"")
  }

  it should "leave absolute http(s) URLs alone" in {
    writeAt("site.toml", Site)
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |
        |- [Off-site](https://example.org/page/)
        |- [Local](/page/)
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", MinimalLayout)
    writeAt("layouts/_default/file.html", MinimalLayout)

    build()

    val html = out("index.html")
    html should include("href=\"https://example.org/page/\"")
    html should include("href=\"/foo/page/\"")
  }
}
