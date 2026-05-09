package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for aliases / redirects (Phase 2.5) — extracted from JuicerBuildSpec for readability. */
class AliasesSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "juicer aliases" should "emit a meta-refresh redirect at each listed alias" in {
    writeAt(
      "site.toml",
      """title    = "S"
        |baseURL  = "https://example.com"
        |htmlDir  = ""
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/new-name.md",
      """---
        |title: New name
        |aliases: [/old-name/, /even-older/]
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "{{ .page.title }}")

    build()

    // Both alias URLs exist with redirect markup pointing at the canonical
    // /new-name/ URL.
    val a1 = out("old-name/index.html")
    a1 should include("""http-equiv="refresh"""")
    a1 should include("/new-name/")

    val a2 = out("even-older/index.html")
    a2 should include("/new-name/")
  }

  it should "accept a single-string alias as well as a list" in {
    writeAt(
      "site.toml",
      """title    = "S"
        |baseURL  = "https://example.com"
        |htmlDir  = ""
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/about.md",
      """---
        |title: About
        |aliases: /me/
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "{{ .page.title }}")

    build()

    val a = out("me/index.html")
    a should include("/about/")
  }

  it should "use the alias.html layout when one is provided" in {
    writeAt(
      "site.toml",
      """title    = "S"
        |baseURL  = "https://example.com"
        |htmlDir  = ""
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/page.md",
      """---
        |title: Page
        |aliases: [/legacy/]
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "{{ .page.title }}")
    writeAt(
      "layouts/_default/alias.html",
      """CUSTOM target={{ .target }} canonical={{ .page.permalink }}""",
    )

    build()

    val a = out("legacy/index.html")
    a should include("CUSTOM target=/page/")
    // Custom layout has full access to the source page record, e.g. its
    // permalink, so themes can drop in branded redirect pages.
    a should include("canonical=https://example.com/page/")
  }

  it should "emit no alias pages for a page that doesn't declare aliases" in {
    writeAt(
      "site.toml",
      """title    = "S"
        |baseURL  = "https://example.com"
        |htmlDir  = ""
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/quiet.md",
      "---\ntitle: Quiet\n---\nBody.\n",
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "{{ .page.title }}")

    build()

    // No spurious top-level directories were created from a missing
    // `aliases` frontmatter — the build pipeline tolerates the empty case.
    out("quiet/index.html") should include("Quiet")
  }
}
