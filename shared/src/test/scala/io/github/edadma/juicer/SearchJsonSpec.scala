package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for the search.json emitter — extracted from JuicerBuildSpec for readability. */
class SearchJsonSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "juicer search.json" should "emit search.json with one entry per page" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n# Home\n\nWelcome here.\n")
    writeAt("content/about.md", "---\ntitle: About\n---\n\n# About\n\nOur story.\n")
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "{{ .content }}")

    build()

    val json = out("search.json")
    json should startWith("[")
    json should endWith("]")
    json should include(""""title":"Home"""")
    json should include(""""title":"About"""")
    // URL fields are present and stripped of htmlDir
    json should include(""""url":"/"""")
    json should include(""""url":"/about/"""")
  }

  it should "strip HTML tags from search.json content" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: T
        |---
        |
        |# Heading
        |
        |Some **bold** and *emphasised* text.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")

    build()

    val json = out("search.json")
    json should include("Some bold and emphasised text.")
    // No HTML tags leaked through
    json should not include "<strong>"
    json should not include "<em>"
    json should not include "<h"
  }

  it should "escape special JSON characters in search.json" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: 'Quote: "hi" \backslash'
        |---
        |
        |Line one.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")

    build()

    val json = out("search.json")
    // Embedded `"` becomes `\"`; backslash becomes `\\`.
    json should include("""\"hi\"""")
    json should include("""\\backslash""")
  }

  // Regression: a stale <src>/<publicDir> from a prior default build used to
  // get walked + parsed as squiggly templates whenever the user redirected
  // output with `-d /elsewhere`. The dst-only exclude didn't catch the
  // default publicDir; now both are excluded.
  it should "skip <src>/<publicDir> from the source walk even when -d redirects elsewhere" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: H\n---\n\n# Hi\n")
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")
    // Stale rendered HTML in the default publicDir — would trip the
    // squiggly template parser if it weren't excluded.
    writeAt("public/leftover/index.html", "<html><body>... random rendered output ...</body></html>")
    writeAt("public/leftover.css", "body { color: red; }")

    noException should be thrownBy build()
  }

  // Bug: nested section indexes (`content/docs/_index.md`) used to fail with
  // NoSuchFileException because the section directory (`html/docs/`) was
  // never created on disk. Now `outdir.createDirectories()` is called before
  // writing the section index file.
  it should "create the section directory for nested _index.md files" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: H\n---\n\n# H\n")
    writeAt("content/a/_index.md", "---\ntitle: A\n---\n\n# A\n")
    writeAt("content/a/b/_index.md", "---\ntitle: B\n---\n\n# B\n")
    writeAt("content/a/b/c/_index.md", "---\ntitle: C\n---\n\n# C\n")
    writeAt("layouts/_default/folder.html", "{{ .page.title }}")
    writeAt("layouts/_default/file.html", "x")

    noException should be thrownBy build()

    out("index.html") shouldBe "H"
    out("html/a/index.html") shouldBe "A"
    out("html/a/b/index.html") shouldBe "B"
    out("html/a/b/c/index.html") shouldBe "C"
  }
}
