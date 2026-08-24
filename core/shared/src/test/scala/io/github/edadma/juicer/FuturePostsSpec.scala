package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for future-dated posts (Phase 2.1) — extracted from JuicerBuildSpec for readability. */
class FuturePostsSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "juicer future-dated posts" should "skip future-dated posts by default" in {
    writeAt(
      "site.toml",
      """title    = "Blog"
        |baseURL  = "http://x"
        |htmlDir  = ""
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/past.md",
      """---
        |title: Past
        |date: 2024-01-01
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt(
      "content/future.md",
      """---
        |title: Future
        |date: 2999-12-31
        |---
        |Body.
        |""".stripMargin,
    )
    // The page-list loop has to live in a LAYOUT, not in markdown
    // content — markdown body is rendered to HTML before the template
    // pass and `{{ … }}` inside it gets escaped, never evaluated.
    writeAt(
      "layouts/_default/folder.html",
      "{{ for p <- .section.pages }}{{ p.title }};{{ end }}",
    )
    writeAt("layouts/_default/file.html", "{{ .page.title }}")

    build()

    // Past post is rendered. Future post is invisible — no file at the
    // physical URL, no entry in `.section.pages`.
    out("past/index.html") should include("Past")
    (dst / "future").exists shouldBe false
    val home = out("index.html")
    home should include("Past;")
    home should not include "Future"
  }

  it should "include future-dated posts when --future is passed" in {
    writeAt(
      "site.toml",
      """title    = "Blog"
        |baseURL  = "http://x"
        |htmlDir  = ""
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/future.md",
      """---
        |title: Future
        |date: 2999-12-31
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "{{ .page.title }}")

    // Build with future = true on the BuildCommand.
    App.run(Args(cmd = Some(BuildCommand(src = src, dst = dst, future = true))))

    out("future/index.html") should include("Future")
  }

  it should "never skip pages relying on mtime-fallback for .page.date" in {
    // A page without explicit `date` frontmatter would never have an
    // author-set future date — its mtime is always 'now or earlier'. The
    // future filter must NOT skip such pages, even pessimistically.
    writeAt(
      "site.toml",
      """title    = "Blog"
        |baseURL  = "http://x"
        |htmlDir  = ""
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/no-date.md",
      """---
        |title: No date
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "{{ .page.title }}")

    build()

    out("no-date/index.html") should include("No date")
  }
}
