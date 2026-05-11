package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Coverage for the `excludeDirs` site-config key.
  *
  * `excludeDirs` is a top-level list of paths (relative to `src`) that the
  * site walk should skip — additional to the built-in skips of `themeDir`,
  * the active themes, `publicDir`, and the build's `dst`. Same structural
  * Path-equality match as the built-ins, so entries must name a directory
  * exactly (no glob patterns).
  */
class ExcludeDirsSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "excludeDirs" should "skip a named top-level subdirectory during the site walk" in {
    writeAt(
      "site.toml",
      """title       = "S"
        |baseURL     = "http://x"
        |excludeDirs = ["scratch"]
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: home\n---\n\n# H\n")
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")
    // A stray HTML file under `scratch/` would otherwise land in the
    // "other templates" pass and be rendered into `<dst>/scratch/note.html`.
    writeAt("scratch/note.html", "<p>do not ship</p>")

    build()

    (dst / "scratch").exists shouldBe false
    out("index.html") should include("<h3 id=\"h\">H</h3>")
  }

  it should "accept a single string as well as an array" in {
    writeAt(
      "site.toml",
      """title       = "S"
        |baseURL     = "http://x"
        |excludeDirs = "vendor"
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: home\n---\n\n# H\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")
    writeAt("vendor/lib.html", "<p>vendored</p>")

    build()

    (dst / "vendor").exists shouldBe false
  }

  it should "skip a nested path (multi-segment exclude)" in {
    writeAt(
      "site.toml",
      """title       = "S"
        |baseURL     = "http://x"
        |excludeDirs = ["assets/raw"]
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: home\n---\n\n# H\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")
    // The "assets" parent is itself walked — only the "raw" subfolder is
    // skipped. So a sibling file under `assets/` should still appear in
    // the output.
    writeAt("assets/ok.html", "<p>kept</p>")
    writeAt("assets/raw/skip.html", "<p>skipped</p>")

    build()

    (dst / "assets" / "raw").exists shouldBe false
    (dst / "assets" / "ok.html").exists shouldBe true
  }

  it should "tolerate entries that point at nonexistent directories" in {
    writeAt(
      "site.toml",
      """title       = "S"
        |baseURL     = "http://x"
        |excludeDirs = ["does-not-exist", "also-missing/nested"]
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: home\n---\n\n# H\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")

    // Build must not crash; the missing entries are simply never matched
    // by the walk's structural Path-equality check.
    noException should be thrownBy build()
    out("index.html") shouldBe "x"
  }
}
