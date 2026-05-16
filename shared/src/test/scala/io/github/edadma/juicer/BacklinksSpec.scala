package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** `.page.backlinks` — every page that links to *this* page, exposed
  * as a list of thin `{title, url, summary}` records. The inverted
  * index is built during the markdown pass from each page's AST link
  * destinations, so any internal `[text](/path/)` reference flows
  * through; absolute URLs (`https://…`, `mailto:`, etc.) and
  * fragment-only anchors are filtered out at collection time.
  */
class BacklinksSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "page.backlinks" should "list every page that links to the current page" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\nhtmlDir = \"\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |
        |See [the target](/target/) for more.
        |""".stripMargin,
    )
    writeAt(
      "content/target.md",
      """---
        |title: Target
        |---
        |
        |This is the target.
        |""".stripMargin,
    )
    writeAt(
      "content/other.md",
      """---
        |title: Other linker
        |---
        |
        |I also point at [it](/target/).
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/file.html",
      """{{ for b <- .page.backlinks }}[{{ b.title }}|{{ b.url }}]{{ end }}""",
    )
    writeAt("layouts/_default/folder.html", "x")

    build()

    val html = out("target/index.html")
    html should include("[Home|/]")
    html should include("[Other linker|/other/]")
  }

  it should "be empty when no pages link in" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\nhtmlDir = \"\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\nNo links anywhere.\n")
    writeAt("content/orphan.md", "---\ntitle: Orphan\n---\n\nNothing points here.\n")
    writeAt(
      "layouts/_default/file.html",
      """{{ if .page.backlinks }}has-some{{ else }}none{{ end }}""",
    )
    writeAt("layouts/_default/folder.html", "x")

    build()

    out("orphan/index.html") should include("none")
  }

  it should "skip self-links" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\nhtmlDir = \"\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\nNo links.\n")
    writeAt(
      "content/loop.md",
      """---
        |title: Loop
        |---
        |
        |This page links to [itself](/loop/) for some reason.
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/file.html",
      """count={{ .page.backlinks.length }}""",
    )
    writeAt("layouts/_default/folder.html", "x")

    build()

    out("loop/index.html") should include("count=0")
  }

  it should "ignore absolute URLs and fragment-only anchors" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\nhtmlDir = \"\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |
        |[external](https://example.com/) and [section](#section) — neither should count.
        |""".stripMargin,
    )
    writeAt("content/target.md", "---\ntitle: Target\n---\n\nbody\n")
    writeAt(
      "layouts/_default/file.html",
      """count={{ .page.backlinks.length }}""",
    )
    writeAt("layouts/_default/folder.html", "x")

    build()

    out("target/index.html") should include("count=0")
  }

  it should "strip fragment and query from link destinations before matching" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\nhtmlDir = \"\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |
        |Deep link: [target](/target/#section)
        |Query link: [target](/target/?utm=x)
        |""".stripMargin,
    )
    writeAt("content/target.md", "---\ntitle: Target\n---\n\nbody\n")
    writeAt(
      "layouts/_default/file.html",
      """count={{ .page.backlinks.length }}""",
    )
    writeAt("layouts/_default/folder.html", "x")

    build()

    // Both /target/#section and /target/?utm=x normalize to /target/
    // and merge into a single backlink from /.
    out("target/index.html") should include("count=1")
  }

  it should "collect links inside lists and tables" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\nhtmlDir = \"\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |
        |- See [target](/target/)
        |
        || col |
        ||-----|
        || [target](/target/) |
        |""".stripMargin,
    )
    writeAt("content/target.md", "---\ntitle: Target\n---\n\nbody\n")
    writeAt(
      "layouts/_default/file.html",
      """{{ for b <- .page.backlinks }}{{ b.url }} {{ end }}""",
    )
    writeAt("layouts/_default/folder.html", "x")

    build()

    out("target/index.html") should include("/")
  }

  it should "expose backlinks in section _index records too" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\nhtmlDir = \"\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\nSee [blog](/blog/).\n")
    writeAt(
      "content/blog/_index.md",
      """---
        |title: Blog
        |---
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/folder.html",
      """{{ if .page.backlinks }}has{{ else }}none{{ end }}""",
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    out("blog/index.html") should include("has")
  }
}
