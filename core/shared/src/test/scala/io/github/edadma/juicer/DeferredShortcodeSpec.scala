package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Deferred shortcodes — invoked with the `[~ name ~]` delimiter pair
  * — run after juicer's page + section + site pipeline finishes, so
  * their templates can reach `.page.pages`, `.page.subsections`,
  * `.section.*`, and the full `.site.*`.
  *
  * The classic `[= name =]` pre-markdown delimiter is unchanged and
  * keeps its current limited context (args + content only). These
  * tests pin the contract for the new deferred path.
  */
class DeferredShortcodeSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "[~ name ~]" should "expand with access to .page.title from the enclosing file" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: Welcome
        |---
        |
        |Body before.
        |
        |[~ pagebadge /~]
        |
        |Body after.
        |""".stripMargin,
    )
    writeAt("shortcodes/pagebadge.html", "<span class=\"b\">{{ .page.title }}</span>")
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("<span class=\"b\">Welcome</span>")
  }

  it should "list section children via .page.pages on a section _index" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\nHi.\n")
    writeAt(
      "content/blog/_index.md",
      """---
        |title: Blog
        |---
        |
        |Welcome to the blog.
        |
        |[~ sectionlist /~]
        |""".stripMargin,
    )
    writeAt(
      "content/blog/first.md",
      """---
        |title: First post
        |---
        |
        |Hello.
        |""".stripMargin,
    )
    writeAt(
      "content/blog/second.md",
      """---
        |title: Second post
        |---
        |
        |World.
        |""".stripMargin,
    )
    writeAt(
      "shortcodes/sectionlist.html",
      """<ul>{{ for p <- .page.pages }}<li><a href="{{ p.url }}">{{ p.title }}</a></li>{{ end }}</ul>""",
    )
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "{{ .content }}")

    build()

    // Default htmlDir = "html" wraps output paths; URLs strip it.
    val html = out("html/blog/index.html")
    html should include("<li><a href=\"/blog/first/\">First post</a></li>")
    html should include("<li><a href=\"/blog/second/\">Second post</a></li>")
  }

  it should "expose .site.title from a deferred shortcode" in {
    writeAt(
      "site.toml",
      """title = "My Site"
        |baseURL = "http://x"
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |
        |[~ sitename /~]
        |""".stripMargin,
    )
    writeAt("shortcodes/sitename.html", "<b>{{ .site.title }}</b>")
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")

    build()

    out("index.html") should include("<b>My Site</b>")
  }

  it should "be a no-op when no [~ marker appears in the rendered content" in {
    // Pure regression guard: the deferred-pass code path has a cheap
    // `contains` fast-exit; this test pins that path so a refactor
    // can't accidentally make the deferred pass charge per-file
    // overhead on sites that don't use the feature.
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |
        |Just plain markdown, no shortcodes.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")

    build()

    out("index.html") should include("Just plain markdown")
  }

  "the classic [= name =] preprocessor" should "still work unchanged" in {
    // Regression guard: deferred-pass plumbing should not affect the
    // legacy immediate-pass behaviour at all.
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: T
        |---
        |
        |[= note =]Hi[= /note =]
        |""".stripMargin,
    )
    writeAt("shortcodes/note.html", "<aside>{{ .content }}</aside>")
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")

    build()

    out("index.html") should include("<aside>Hi</aside>")
  }
}
