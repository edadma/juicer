package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Repeated heading ids get GitHub's numeric suffix — `buf`, `buf-1`, `buf-2`.
  *
  * Without it two headings of the same text share one `id`, which is invalid
  * HTML and leaves the second unreachable. The case that forced it is generated
  * API reference, where a type and its constructor conventionally share a name.
  */
class DuplicateHeadingIdSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  private def render(body: String, slugStyle: String = "juicer"): String = {
    writeAt(
      "site.toml",
      s"title = \"S\"\nbaseURL = \"http://x\"\nheadingShift = 0\nslugStyle = \"$slugStyle\"\n",
    )
    writeAt("content/_index.md", s"---\ntitle: T\n---\n\n$body\n")
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")
    build()
    out("index.html")
  }

  private def ids(html: String): List[String] =
    """id="([^"]+)"""".r.findAllMatchIn(html).map(_.group(1)).toList

  "a repeated heading" should "get a numeric suffix rather than a duplicate id" in {
    val got = ids(render("## buf\n\n## buf\n"))

    got should contain("buf")
    got should contain("buf-1")
  }

  it should "keep counting past the second" in {
    val got = ids(render("## buf\n\n## buf\n\n## buf\n"))

    got should contain allOf ("buf", "buf-1", "buf-2")
  }

  it should "not disturb a document whose headings are all distinct" in {
    val got = ids(render("## one\n\n## two\n"))

    got should contain("one")
    got should contain("two")
    got should not contain "one-1"
  }

  it should "leave no duplicate id anywhere in the page" in {
    val got = ids(render("## buf\n\n## buf\n\n## buf\n"))

    got.distinct.length shouldBe got.length
  }

  it should "skip a suffix that a real heading has already taken" in {
    // `buf`, `buf-1` written by hand, then a second `buf` — the second `buf`
    // must not be handed `buf-1` a second time.
    val got = ids(render("## buf\n\n## buf-1\n\n## buf\n"))

    got.distinct.length shouldBe got.length
    got should contain("buf-2")
  }

  it should "dedupe an explicit id, which shares the same namespace" in {
    val got = ids(render("## buf\n\n## Something Else {#buf}\n"))

    got.distinct.length shouldBe got.length
    got should contain("buf-1")
  }

  it should "resolve a type and its constructor separately under the github style" in {
    // The shape this exists for: sysl's `buf()` beside `Buf`. Case-folding
    // makes them one slug, and an API page links to both.
    val html = render(
      """## Functions
        |
        |### buf
        |
        |## Types
        |
        |### Buf
        |""".stripMargin,
      slugStyle = "github",
    )

    val got = ids(html)

    got should contain("buf")
    got should contain("buf-1")
    got.distinct.length shouldBe got.length
  }

  it should "carry the suffix into the page TOC, not just the HTML" in {
    // The TOC is built from the same AST, so a heading whose id was suffixed
    // must be linked by the suffixed id — otherwise the rail scrolls to the
    // wrong one, which is the failure the duplicate id caused in the first place.
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\nheadingShift = 0\n")
    writeAt("content/_index.md", "---\ntitle: T\n---\n\n## buf\n\n## buf\n")
    writeAt(
      "layouts/_default/folder.html",
      "{{ for h <- .tocList }}[{{ h.id }}]{{ end }}",
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")

    html should include("[buf]")
    html should include("[buf-1]")
  }
}
