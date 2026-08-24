package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for section list pages + page navigation (Tier 2 #9), plus
  * regressions surfaced during the section-list / nav rollout —
  * extracted from JuicerBuildSpec for readability.
  *
  * NB: with the default `htmlDir = "html"`, nested sections live on disk at
  * `dst/html/<section>/...` while their URLs strip the `html` segment. Tests
  * that read the rendered file go through `html/...`; tests that match URLs
  * in HTML use `/<section>/...`.
  */
class SectionsSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "juicer sections" should "expose .section.pages on a section's _index page" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\nHi.\n")
    writeAt(
      "content/docs/_index.md",
      """---
        |title: Docs
        |---
        |
        |Section.
        |""".stripMargin,
    )
    writeAt("content/docs/install.md", "---\ntitle: Install\n---\n\nA.\n")
    writeAt("content/docs/usage.md", "---\ntitle: Usage\n---\n\nB.\n")
    writeAt(
      "layouts/_default/folder.html",
      """{{ for p <- .section.pages }}<a href="{{ p.url }}">{{ p.title }}</a>
        |{{ end }}""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "{{ .content }}")

    build()

    val html = out("html/docs/index.html")
    html should include("""<a href="/docs/install/">Install</a>""")
    html should include("""<a href="/docs/usage/">Usage</a>""")
  }

  it should "sort .section.pages by frontmatter weight then name" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: H\n---\n\nx\n")
    writeAt("content/docs/_index.md", "---\ntitle: D\n---\n\nx\n")
    writeAt("content/docs/aaa.md", "---\ntitle: A\nweight: 30\n---\n\nx\n")
    writeAt("content/docs/bbb.md", "---\ntitle: B\nweight: 10\n---\n\nx\n")
    writeAt("content/docs/ccc.md", "---\ntitle: C\nweight: 20\n---\n\nx\n")
    writeAt(
      "layouts/_default/folder.html",
      """{{ for p <- .section.pages }}{{ p.title }} {{ end }}""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    out("html/docs/index.html") should include("B C A")
  }

  it should "expose .section.subsections listing immediate child sections" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/docs/_index.md", "---\ntitle: Docs\n---\n\n.\n")
    writeAt("content/docs/api/_index.md", "---\ntitle: API\nweight: 10\n---\n\n.\n")
    writeAt("content/docs/cli/_index.md", "---\ntitle: CLI\nweight: 20\n---\n\n.\n")
    writeAt(
      "layouts/_default/folder.html",
      """{{ for s <- .section.subsections }}[{{ s.title }}: {{ s.url }}]{{ end }}""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("html/docs/index.html")
    html should include("[API: /docs/api/]")
    html should include("[CLI: /docs/cli/]")
  }

  it should "expose .page.parent pointing at the enclosing section's _index" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/docs/_index.md", "---\ntitle: Docs\n---\n\n.\n")
    writeAt("content/docs/install.md", "---\ntitle: Install\n---\n\n.\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/file.html",
      """parent={{ .page.parent.title }}""".stripMargin,
    )

    build()

    out("html/docs/install/index.html") should include("parent=Docs")
  }

  it should "expose .page.ancestors as a chain from root down to parent" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/docs/_index.md", "---\ntitle: Docs\n---\n\n.\n")
    writeAt("content/docs/api/_index.md", "---\ntitle: API\n---\n\n.\n")
    writeAt("content/docs/api/spec.md", "---\ntitle: Spec\n---\n\n.\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/file.html",
      """{{ for a <- .page.ancestors }}> {{ a.title }} {{ end }}""".stripMargin,
    )

    build()

    out("html/docs/api/spec/index.html") should include("> Home > Docs > API")
  }

  it should "walk .page.prev / .page.next across section boundaries (DFS reading order)" in {
    // Reading order (DFS over the section tree) is:
    //   Home → A → A/p1 → A/p2 → B → B/q1 → B/q2
    // So the last page in A points "next" to B's _index, and the first
    // page in B points "prev" back to A's last page.
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/a/_index.md", "---\ntitle: A\nweight: 10\n---\n\n.\n")
    writeAt("content/a/p1.md", "---\ntitle: P1\nweight: 10\n---\n\n.\n")
    writeAt("content/a/p2.md", "---\ntitle: P2\nweight: 20\n---\n\n.\n")
    writeAt("content/b/_index.md", "---\ntitle: B\nweight: 20\n---\n\n.\n")
    writeAt("content/b/q1.md", "---\ntitle: Q1\nweight: 10\n---\n\n.\n")
    writeAt("content/b/q2.md", "---\ntitle: Q2\nweight: 20\n---\n\n.\n")
    writeAt(
      "layouts/_default/folder.html",
      """{{ if .page.prev }}prev={{ .page.prev.title }} {{ end }}{{ if .page.next }}next={{ .page.next.title }}{{ end }}""".stripMargin,
    )
    writeAt(
      "layouts/_default/file.html",
      """{{ if .page.prev }}prev={{ .page.prev.title }} {{ end }}{{ if .page.next }}next={{ .page.next.title }}{{ end }}""".stripMargin,
    )

    build()

    // Home — first page in reading order.
    out("index.html") should (include("next=A") and not include "prev=")

    // First page of A: prev=A (the section _index above it).
    out("html/a/p1/index.html") should (include("prev=A") and include("next=P2"))

    // Last page of A: next=B (cross-section into the next _index).
    out("html/a/p2/index.html") should (include("prev=P1") and include("next=B"))

    // Section B's _index: prev=last page of A, next=first page of B.
    out("html/b/index.html") should (include("prev=P2") and include("next=Q1"))

    // Last page everywhere — no `next`.
    out("html/b/q2/index.html") should (include("prev=Q1") and not include "next=")
  }

  it should "expose isSection on every page record" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: H\n---\n\n.\n")
    writeAt("content/docs/_index.md", "---\ntitle: D\n---\n\n.\n")
    writeAt("content/docs/a.md", "---\ntitle: A\n---\n\n.\n")
    writeAt(
      "layouts/_default/folder.html",
      """isSection={{ .page.isSection }}""".stripMargin,
    )
    writeAt(
      "layouts/_default/file.html",
      """isSection={{ .page.isSection }}""".stripMargin,
    )

    build()

    out("html/docs/index.html") should include("isSection=true")
    out("html/docs/a/index.html") should include("isSection=false")
  }

  // ===== Regressions: bugs surfaced during section-list / nav rollout =====

  // Bug: `mktocFromContent` used to call `h.toc.headings.head` unconditionally
  // and crashed with NoSuchElementException whenever a content file had no
  // headings. Now falls back to the frontmatter title, then to the file name.
  it should "not crash when content files have no headings" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\nJust a paragraph, no heading.\n")
    writeAt("content/about.md", "---\ntitle: About\n---\n\nNo heading here either.\n")
    writeAt("layouts/_default/folder.html", "ok")
    writeAt("layouts/_default/file.html", "ok")

    noException should be thrownBy build()

    out("index.html") shouldBe "ok"
    out("about/index.html") shouldBe "ok"
  }

  it should "treat \\[= as a literal start-delimiter (preprocessor escape)" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: T
        |---
        |
        |Use `\[= name =]` to call a shortcode.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    // The literal `[= name =]` survives through the preprocessor.
    html should include("[= name =]")
    // No leading backslash leaks through into the rendered HTML.
    html should not include "\\[="
  }

  it should "respect headingShift = 0 (no level shift)" in {
    writeAt(
      "site.toml",
      """title = "S"
        |baseURL = "http://x"
        |headingShift = 0
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      """---
        |title: T
        |---
        |
        |# Top
        |
        |## Sub
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("<h1 id=\"top\">Top</h1>")
    html should include("<h2 id=\"sub\">Sub</h2>")
    // No shifted h3
    html should not include "<h3"
  }

  it should "respect headingShift = 1" in {
    writeAt(
      "site.toml",
      """title = "S"
        |baseURL = "http://x"
        |headingShift = 1
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: T\n---\n\n# A\n\n## B\n")
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("<h2 id=\"a\">A</h2>")
    html should include("<h3 id=\"b\">B</h3>")
  }

  it should "expose .site.root pointing at the root section's _index" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/docs/_index.md", "---\ntitle: Docs\nweight: 10\n---\n\n.\n")
    writeAt("content/blog/_index.md", "---\ntitle: Blog\nweight: 20\n---\n\n.\n")
    writeAt(
      "layouts/_default/folder.html",
      """root={{ .site.root.title }} | {{ for s <- .site.root.subsections }}{{ s.title }}={{ s.url }} {{ end }}""".stripMargin,
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("root=Home")
    html should include("Docs=/docs/")
    html should include("Blog=/blog/")
  }
}
