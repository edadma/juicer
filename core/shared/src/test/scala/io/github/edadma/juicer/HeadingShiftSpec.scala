package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Per-page `headingShift` frontmatter, overriding the site-wide value.
  *
  * The site default exists because a layout normally supplies the `<h1>`, so an
  * author's `#` is a subsection of the page title. A GENERATED body does not fit
  * that assumption: its `##` groups are already meant to be `<h2>`, and they have
  * to land at the same levels here as when the same file is read in a repository.
  */
class HeadingShiftSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  private def siteWith(siteShift: String, pageFrontmatter: String): String = {
    writeAt("site.toml", s"title = \"S\"\nbaseURL = \"http://x\"\n$siteShift")
    writeAt("content/_index.md", s"---\ntitle: T\n$pageFrontmatter---\n\n## Functions\n\n### push\n")
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")
    build()
    out("index.html")
  }

  "the site headingShift" should "still apply to a page that says nothing" in {
    val html = siteWith("headingShift = 2\n", "")

    html should include("<h4")
    html should include("<h5")
  }

  "a page's own headingShift" should "override the site value" in {
    val html = siteWith("headingShift = 2\n", "headingShift: 0\n")

    html should include("<h2")
    html should include("<h3")
    html should not include "<h4"
  }

  it should "override upward as well as downward" in {
    val html = siteWith("headingShift = 0\n", "headingShift: 3\n")

    html should include("<h5")
    html should include("<h6")
  }

  it should "take a quoted value, since a frontmatter number may arrive as a string" in {
    val html = siteWith("headingShift = 2\n", "headingShift: \"0\"\n")

    html should include("<h2")
    html should not include "<h4"
  }

  it should "fall back to the site value when the frontmatter is not a number" in {
    // Degrade rather than fail: a typo in one page's frontmatter must not take
    // the build down, and the site-wide value is the only sane answer.
    val html = siteWith("headingShift = 2\n", "headingShift: yes\n")

    html should include("<h4")
  }

  it should "shift only the page that asked, not its neighbours" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\nheadingShift = 2\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n## Home Section\n")
    writeAt("content/api.md", "---\ntitle: API\nheadingShift: 0\n---\n\n## Functions\n")
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "{{ .content }}")

    build()

    out("index.html") should include("<h4")
    out("api/index.html") should include("<h2")
    out("api/index.html") should not include "<h4"
  }
}
