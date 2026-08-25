package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Per-page `slugStyle` frontmatter, overriding the site-wide value.
  *
  * `slugStyle = "github"` exists for Markdown that is read twice — once as a
  * page here, once in a repository — but it is a site key, and turning it on
  * rewrites the anchor of every existing heading that carries punctuation. On a
  * site of any age those are links people have shared, so the switch cannot be
  * thrown for the sake of one generated section.
  *
  * Anchors are a per-page property: nothing outside a page's own headings is
  * named by them. So the override is coherent, and it is what lets a generated
  * API section sit beside hand-written prose without moving the prose.
  */
class PageSlugStyleSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  private def siteWith(siteStyle: String, pageFrontmatter: String, body: String = "## starts_with"): String = {
    writeAt("site.toml", s"title = \"S\"\nbaseURL = \"http://x\"\nheadingShift = 0\n$siteStyle")
    writeAt("content/_index.md", s"---\ntitle: T\n$pageFrontmatter---\n\n$body\n")
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")
    build()
    out("index.html")
  }

  "the site slugStyle" should "still apply to a page that says nothing" in {
    val html = siteWith("slugStyle = \"github\"\n", "")

    html should include("""id="starts_with"""")
  }

  "a page's own slugStyle" should "override the site default" in {
    val html = siteWith("", "slugStyle: github\n")

    html should include("""id="starts_with"""")
    html should not include """id="starts-with""""
  }

  it should "override in the other direction too, so a site on github can hold a juicer page" in {
    val html = siteWith("slugStyle = \"github\"\n", "slugStyle: juicer\n")

    html should include("""id="starts-with"""")
    html should not include """id="starts_with""""
  }

  it should "take a quoted value, since frontmatter strings are written both ways" in {
    val html = siteWith("", "slugStyle: \"github\"\n")

    html should include("""id="starts_with"""")
  }

  it should "fall back to the site value when the frontmatter is not a string" in {
    // Degrade rather than fail, exactly as `headingShift` does: a typo in one
    // page's frontmatter must not take the build down.
    val html = siteWith("slugStyle = \"github\"\n", "slugStyle: 3\n")

    html should include("""id="starts_with"""")
  }

  it should "leave an unrecognised style on the markdown library's own, not on no ids at all" in {
    val html = siteWith("", "slugStyle: klingon\n")

    html should include("""id="starts-with"""")
  }

  it should "carry the whole github algorithm, not merely the underscore" in {
    val html = siteWith("", "slugStyle: github\n", "## Buf[T]\n\n## sysl.text")

    html should include("""id="buft"""")
    html should include("""id="sysltext"""")
  }

  it should "make a same-page link written for GitHub resolve on that page" in {
    val html = siteWith(
      "",
      "slugStyle: github\n",
      """## Index
        |
        |[`split_once`](#split_once)
        |
        |## split_once
        |
        |Splits at the first separator.""".stripMargin,
    )

    html should include("""href="#split_once"""")
    html should include("""id="split_once"""")
  }

  "a page that overrides" should "not move its neighbours' anchors" in {
    // The whole point of the key being per page: the 110 hand-written headings
    // measured on sysl.sh have to keep the anchors they were published with.
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\nheadingShift = 0\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n## the type_s own values\n")
    writeAt("content/api.md", "---\ntitle: API\nslugStyle: github\n---\n\n## starts_with\n")
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "{{ .content }}")

    build()

    out("index.html") should include("""id="the-type-s-own-values"""")
    out("api/index.html") should include("""id="starts_with"""")
  }

  it should "use its own style in the table of contents as well as the body" in {
    // `.tocList` is built from the same parsed document, so a body that got the
    // override and a TOC that did not would link into nothing.
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\nheadingShift = 0\n")
    writeAt("content/_index.md", "---\ntitle: T\nslugStyle: github\n---\n\n## starts_with\n")
    writeAt("layouts/_default/folder.html", "{{ for h <- .tocList }}[{{ h.id }}]{{ end }}")
    writeAt("layouts/_default/file.html", "x")

    build()

    out("index.html") should include("[starts_with]")
  }
}
