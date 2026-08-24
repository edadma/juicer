package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for OG / Twitter card helpers (Phase 2.7) — extracted from JuicerBuildSpec for readability. */
class OgTagsSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "juicer ogTags" should "emit OG meta tags via {{ ogTags .page }}" in {
    writeAt(
      "site.toml",
      """title    = "My Blog"
        |baseURL  = "https://example.com"
        |htmlDir  = ""
        |""".stripMargin,
    )
    writeAt(
      "content/post.md",
      """---
        |title: Hello
        |summary: A short summary.
        |image: /img/hero.jpg
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/file.html",
      """{{ ogTags .page }}""",
    )
    writeAt("content/_index.md", "---\ntitle: Home\n---\n")

    build()

    val html = out("post/index.html")
    html should include("""<meta property="og:title" content="Hello" />""")
    html should include("""<meta property="og:type" content="article" />""")
    html should include("""<meta property="og:url" content="https://example.com/post/" />""")
    html should include("""<meta property="og:description" content="A short summary." />""")
    // image is promoted to absolute via baseURL.
    html should include("""<meta property="og:image" content="https://example.com/img/hero.jpg" />""")
    html should include("""<meta property="og:site_name" content="My Blog" />""")
    // Image present → twitter:card escalates to summary_large_image.
    html should include("""<meta name="twitter:card" content="summary_large_image" />""")
  }

  it should "fall back to summary then site image when frontmatter lacks them" in {
    writeAt(
      "site.toml",
      """title    = "Site"
        |baseURL  = "https://example.com"
        |htmlDir  = ""
        |ogImage  = "/img/site-default.png"
        |""".stripMargin,
    )
    writeAt(
      "content/post.md",
      """---
        |title: No metadata
        |---
        |
        |The first paragraph becomes the auto-summary.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/file.html",
      """{{ ogTags .page }}""",
    )
    writeAt("content/_index.md", "---\ntitle: Home\n---\n")

    build()

    val html = out("post/index.html")
    // Auto-summary fallback chain: ogDescription/description not set, so
    // the page's resolved `.summary` fills in.
    html should include("og:description")
    html should include("first paragraph")
    // Site default ogImage.
    html should include("https://example.com/img/site-default.png")
  }

  it should "downgrade twitter:card to summary when no image is available" in {
    writeAt(
      "site.toml",
      """title    = "Site"
        |baseURL  = "https://example.com"
        |htmlDir  = ""
        |""".stripMargin,
    )
    writeAt(
      "content/post.md",
      """---
        |title: Imageless
        |summary: Short.
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/file.html",
      """{{ ogTags .page }}""",
    )
    writeAt("content/_index.md", "---\ntitle: Home\n---\n")

    build()

    val html = out("post/index.html")
    html should include("""<meta name="twitter:card" content="summary" />""")
    html should not include "og:image"
  }

}
