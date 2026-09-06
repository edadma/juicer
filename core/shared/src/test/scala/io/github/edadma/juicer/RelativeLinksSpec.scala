package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** A relative markdown link resolves against the directory of the page it was
  * written in, and a link that lands on another content file becomes that
  * file's rendered URL.
  *
  * That is what makes one markdown file correct in two places at once: read in
  * the repository, `[Patterns](patterns.md)` opens the sibling file; read on
  * the site, it opens `/reference/patterns/`. Every docs generator does this,
  * and juicer did not — it absolutized every relative destination against the
  * SITE ROOT and left the extension on, so a link written beside
  * `docs/reference/types.md` rendered as `href="/patterns.md"` and 404'd. The
  * build stayed green: nothing in the pipeline had an opinion about where a
  * link pointed.
  *
  * Six relative forms are covered here plus the two that were already right
  * (site-absolute and external), because when this was found only those two
  * survived a probe of all eight.
  */
class RelativeLinksSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  private val Site =
    """title  = "Docs"
      |author = "Ed"
      |""".stripMargin

  private val MinimalLayout =
    """<body>{{ .content }}</body>
      |""".stripMargin

  private def layouts(): Unit = {
    writeAt("layouts/_default/folder.html", MinimalLayout)
    writeAt("layouts/_default/file.html", MinimalLayout)
  }

  private def page(title: String, body: String): String =
    s"""---
       |title: $title
       |---
       |
       |$body
       |""".stripMargin

  // -----------------------------------------------------------------

  "a relative link" should "resolve a sibling .md against the source page's directory" in {
    writeAt("site.toml", Site)
    writeAt("content/_index.md", page("Home", "home"))
    writeAt("content/reference/_index.md", page("Reference", "reference"))
    writeAt("content/reference/types.md", page("Types", "See [Patterns](patterns.md)."))
    writeAt("content/reference/patterns.md", page("Patterns", "patterns"))
    layouts()

    build()

    val html = out("html/reference/types/index.html")
    html should include("href=\"/reference/patterns/\"")
    html should not include "patterns.md"
  }

  it should "climb out of the source directory for a parent-relative .md" in {
    writeAt("site.toml", Site)
    writeAt("content/_index.md", page("Home", "home"))
    writeAt("content/reference/_index.md", page("Reference", "reference"))
    writeAt("content/reference/types.md", page("Types", "See [HTTP](../library/http.md)."))
    writeAt("content/library/_index.md", page("Library", "library"))
    writeAt("content/library/http.md", page("HTTP", "http"))
    layouts()

    build()

    val html = out("html/reference/types/index.html")
    html should include("href=\"/library/http/\"")
    html should not include "http.md"
  }

  it should "keep a fragment on the resolved URL" in {
    writeAt("site.toml", Site)
    writeAt("content/_index.md", page("Home", "home"))
    writeAt("content/reference/_index.md", page("Reference", "reference"))
    writeAt("content/reference/types.md", page("Types", "See [Guards](patterns.md#guards)."))
    writeAt("content/reference/patterns.md", page("Patterns", "## Guards\n"))
    layouts()

    build()

    out("html/reference/types/index.html") should include("href=\"/reference/patterns/#guards\"")
  }

  // The index file of a directory is whatever `folderContent` names, so on a
  // site that names `README` the file GitHub renders as the directory's front
  // page is also the section's front page. A link to it must land on the
  // section URL — `/reference/` — and not on a page under it.
  it should "resolve a directory's index file to that section's URL" in {
    writeAt("site.toml", Site + "folderContent = \"README\"\n")
    writeAt("content/README.md", page("Home", "home"))
    writeAt("content/reference/README.md", page("Reference", "reference"))
    writeAt("content/reference/types.md", page("Types", "Back to [Reference](README.md)."))
    layouts()

    build()

    out("html/reference/types/index.html") should include("href=\"/reference/\"")
  }

  // A bundle asset is copied to its SECTION's output directory, not to the
  // page's, so an image written beside `types.md` is published at
  // `/reference/diagram.png`. Leaving the destination relative would have the
  // browser resolve it against the page URL — `/reference/types/diagram.png`,
  // where nothing is.
  it should "resolve a non-page file to the path it is published at" in {
    writeAt("site.toml", Site)
    writeAt("content/_index.md", page("Home", "home"))
    writeAt("content/reference/_index.md", page("Reference", "reference"))
    writeAt("content/reference/types.md", page("Types", "![Diagram](diagram.png)"))
    writeAt("content/reference/diagram.png", "not really a png")
    layouts()

    build()

    out("html/reference/types/index.html") should include("src=\"/reference/diagram.png\"")
  }

  it should "leave a site-absolute destination alone" in {
    writeAt("site.toml", Site)
    writeAt("content/_index.md", page("Home", "home"))
    writeAt("content/reference/_index.md", page("Reference", "reference"))
    writeAt("content/reference/types.md", page("Types", "See [Library](/library/)."))
    layouts()

    build()

    out("html/reference/types/index.html") should include("href=\"/library/\"")
  }

  it should "leave an external URL alone" in {
    writeAt("site.toml", Site)
    writeAt("content/_index.md", page("Home", "home"))
    writeAt("content/reference/_index.md", page("Reference", "reference"))
    writeAt(
      "content/reference/types.md",
      page("Types", "See [spec](https://example.org/x.md) or mail [Ed](mailto:ed@example.org)."),
    )
    layouts()

    build()

    val html = out("html/reference/types/index.html")
    html should include("href=\"https://example.org/x.md\"")
    html should include("href=\"mailto:ed@example.org\"")
  }

  // -----------------------------------------------------------------

  "a relative link on a subpath deploy" should "carry the baseURL path into the resolved URL" in {
    writeAt("site.toml", Site + "baseURL = \"https://example.com/foo/\"\n")
    writeAt("content/_index.md", page("Home", "home"))
    writeAt("content/reference/_index.md", page("Reference", "reference"))
    writeAt("content/reference/types.md", page("Types", "See [Patterns](patterns.md)."))
    writeAt("content/reference/patterns.md", page("Patterns", "patterns"))
    layouts()

    build()

    val html = out("html/reference/types/index.html")
    html should include("href=\"/foo/reference/patterns/\"")
  }

  // -----------------------------------------------------------------

  "a relative link to a directory" should "resolve to that section's URL" in {
    writeAt("site.toml", Site)
    writeAt("content/_index.md", page("Home", "home"))
    writeAt("content/reference/_index.md", page("Reference", "reference"))
    writeAt("content/reference/types.md", page("Types", "See [Library](../library/)."))
    writeAt("content/library/_index.md", page("Library", "library"))
    layouts()

    build()

    out("html/reference/types/index.html") should include("href=\"/library/\"")
  }

  // -----------------------------------------------------------------

  // The resolved destination is what the backlinks index records, so a page
  // reached only by relative links still knows who points at it.
  "backlinks" should "count a relative .md link" in {
    writeAt("site.toml", Site)
    writeAt("content/_index.md", page("Home", "home"))
    writeAt("content/reference/_index.md", page("Reference", "reference"))
    writeAt("content/reference/types.md", page("Types", "See [Patterns](patterns.md)."))
    writeAt("content/reference/patterns.md", page("Patterns", "patterns"))
    writeAt("layouts/_default/folder.html", MinimalLayout)
    writeAt(
      "layouts/_default/file.html",
      """<body>{{ for .page.backlinks }}<i>{{ .title }}</i>{{ end }}</body>
        |""".stripMargin,
    )

    build()

    out("html/reference/patterns/index.html") should include("<i>Types</i>")
  }
}
