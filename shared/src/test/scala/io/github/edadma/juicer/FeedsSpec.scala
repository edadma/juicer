package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for Atom feeds (Tier 2 #8) — extracted from JuicerBuildSpec for readability. */
class FeedsSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "juicer atom feeds" should "emit a site-wide feed.xml at the root" in {
    writeAt("site.toml", "title = \"My Blog\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/post-a.md", "---\ntitle: A\ndate: 2025-12-31\n---\n\n# A\n")
    writeAt("content/post-b.md", "---\ntitle: B\ndate: 2026-01-15\n---\n\n# B\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "{{ .content }}")

    build()

    val xml = out("feed.xml")
    xml should startWith("<?xml")
    xml should include("<feed xmlns=\"http://www.w3.org/2005/Atom\">")
    xml should include("<title>My Blog</title>")
    xml should include("<title>A</title>")
    xml should include("<title>B</title>")
    // Newer entry first (B is later)
    xml.indexOf("<title>B</title>") should be < xml.indexOf("<title>A</title>")
  }

  it should "emit per-section feed.xml inside each section directory" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/blog/_index.md", "---\ntitle: Blog\n---\n\n.\n")
    writeAt("content/blog/a.md", "---\ntitle: A\ndate: 2026-01-01\n---\n\n# A\n")
    writeAt("content/blog/b.md", "---\ntitle: B\ndate: 2026-02-01\n---\n\n# B\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")

    build()

    val xml = out("html/blog/feed.xml")
    xml should include("<feed xmlns=\"http://www.w3.org/2005/Atom\">")
    xml should include("<title>S · Blog</title>")
    xml should include("<title>A</title>")
    xml should include("<title>B</title>")
  }

  it should "skip per-section feed for sections with no non-_index pages" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: H\n---\n\n.\n")
    writeAt("content/empty/_index.md", "---\ntitle: Empty\n---\n\n.\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")

    build()

    // Site-wide feed exists but is empty of entries
    out("feed.xml") should include("<feed xmlns=")
    // Empty section doesn't get its own feed
    val emptySectionFeed = (dst / "html" / "empty" / "feed.xml")
    emptySectionFeed.exists shouldBe false
  }

  it should "respect feeds = false to disable feed emission" in {
    writeAt(
      "site.toml",
      """title = "S"
        |baseURL = "http://x"
        |feeds = false
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: H\n---\n\n.\n")
    writeAt("content/post.md", "---\ntitle: P\ndate: 2026-01-01\n---\n\n# P\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")

    build()

    (dst / "feed.xml").exists shouldBe false
  }
}
