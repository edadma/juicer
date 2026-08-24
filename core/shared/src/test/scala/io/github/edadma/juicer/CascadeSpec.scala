package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for frontmatter cascade — a section's `_index.md` can declare
  * a `cascade:` map whose keys are inherited by every descendant page
  * unless the page sets its own value. Mirrors Hugo's `cascade` shape.
  *
  * Each test stitches a value into a layout via `{{ .page.<key> }}` so
  * the assertion sees the *rendered* effective frontmatter — that's
  * exactly the surface authors care about, and it exercises the same
  * code path as the live build.
  *
  * Cascade keys here are deliberately ones juicer does NOT post-process
  * (`tone`, `region`, `flavour`, `notice`) so the assertion sees the
  * raw cascaded value, not a juicer-resolved derivative. Using
  * `author` here for instance would trigger the authors-registry
  * resolver and the value would come back as a map, not a string.
  *
  * Tests read out of `html/<path>/index.html` because juicer's default
  * `htmlDir` is `"html"` — content pages get nested under that prefix
  * even though feeds / sitemap / robots.txt sit at the dst root.
  */
class CascadeSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "frontmatter cascade" should "apply root _index cascade to every descendant page" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |cascade:
        |  notice: WIP
        |---
        |""".stripMargin,
    )
    writeAt("content/post-a.md", "---\ntitle: A\n---\n\n# A\n")
    writeAt("content/sub/_index.md", "---\ntitle: Sub\n---\n\n.\n")
    writeAt("content/sub/post-b.md", "---\ntitle: B\n---\n\n# B\n")
    writeAt("layouts/_default/folder.html", "{{ .page.title }}|{{ .page.notice }}")
    writeAt("layouts/_default/file.html",   "{{ .page.title }}|{{ .page.notice }}")

    build()

    out("post-a/index.html")          should include("A|WIP")
    out("html/sub/post-b/index.html") should include("B|WIP")
    // Nested section index also inherits root cascade
    out("html/sub/index.html")        should include("Sub|WIP")
  }

  it should "apply nested-section cascade only to descendants of that section" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt(
      "content/blog/_index.md",
      """---
        |title: Blog
        |cascade:
        |  tone: editorial
        |---
        |""".stripMargin,
    )
    writeAt("content/blog/post.md", "---\ntitle: BlogPost\n---\n\n.\n")
    writeAt("content/notes/note.md", "---\ntitle: Note\n---\n\n.\n")
    writeAt("layouts/_default/folder.html", "{{ .page.title }}|{{ .page.tone }}")
    writeAt("layouts/_default/file.html",   "{{ .page.title }}|{{ .page.tone }}")

    build()

    // Blog post inherits the cascade
    out("html/blog/post/index.html") should include("BlogPost|editorial")
    // notes/note.md is outside /blog/, gets no cascade
    out("html/notes/note/index.html") should include("Note|")
    out("html/notes/note/index.html") should not include "editorial"
  }

  it should "let a page's own frontmatter override a cascaded value" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |cascade:
        |  notice: WIP
        |---
        |""".stripMargin,
    )
    writeAt(
      "content/guest-post.md",
      """---
        |title: Guest
        |notice: FINAL
        |---
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html",   "{{ .page.title }}|{{ .page.notice }}")

    build()

    out("guest-post/index.html") should include("Guest|FINAL")
  }

  it should "let a nearer ancestor's cascade override a farther ancestor's" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |cascade:
        |  region: global
        |---
        |""".stripMargin,
    )
    writeAt(
      "content/europe/_index.md",
      """---
        |title: Europe
        |cascade:
        |  region: europe
        |---
        |""".stripMargin,
    )
    writeAt("content/europe/paris.md", "---\ntitle: Paris\n---\n\n.\n")
    writeAt("content/other.md", "---\ntitle: Other\n---\n\n.\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html",   "{{ .page.title }}|{{ .page.region }}")

    build()

    // Nearer ancestor wins
    out("html/europe/paris/index.html") should include("Paris|europe")
    // Outside the override section, the root cascade still applies
    out("other/index.html")             should include("Other|global")
  }

  it should "NOT apply a section's own cascade to its own _index.md" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt(
      "content/blog/_index.md",
      """---
        |title: Blog
        |cascade:
        |  flavour: secret-sauce
        |---
        |""".stripMargin,
    )
    writeAt("content/blog/post.md", "---\ntitle: Post\n---\n\n.\n")
    writeAt(
      "layouts/_default/folder.html",
      "FOLDER:{{ .page.title }}|{{ .page.flavour }}",
    )
    writeAt(
      "layouts/_default/file.html",
      "FILE:{{ .page.title }}|{{ .page.flavour }}",
    )

    build()

    // The cascade reaches the descendant page
    out("html/blog/post/index.html") should include("FILE:Post|secret-sauce")
    // …but NOT the declaring section's own index
    val blogIndex = out("html/blog/index.html")
    blogIndex should include("FOLDER:Blog|")
    blogIndex should not include "secret-sauce"
  }

  it should "leave the rendered frontmatter unchanged when no cascade is declared anywhere" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/p.md",      "---\ntitle: P\n---\n\n.\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html",   "{{ .page.title }}|{{ .page.notice }}")

    build()

    // .page.notice renders as empty (no cascade ever wrote it)
    out("p/index.html") should include("P|")
  }
}
