package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for the author registry (Phase 2.3) — extracted from JuicerBuildSpec for readability. */
class AuthorsSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "juicer authors" should "resolve frontmatter `author: <id>` to the [[authors]] registry entry" in {
    writeAt(
      "site.toml",
      """title    = "Blog"
        |baseURL  = "http://x"
        |htmlDir  = ""
        |
        |[[authors]]
        |id    = "ed"
        |name  = "Edward A Maxedon"
        |bio   = "Writes code."
        |avatar = "/img/ed.jpg"
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/post.md",
      """---
        |title: Post
        |author: ed
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/file.html",
      """NAME={{ .page.author.name }}
        |BIO={{ .page.author.bio }}
        |AVATAR={{ .page.author.avatar }}
        |COUNT={{ for a <- .page.authors }}{{ a.name }};{{ end }}
        |""".stripMargin,
    )

    build()

    val p = out("post/index.html")
    p should include("NAME=Edward A Maxedon")
    p should include("BIO=Writes code.")
    p should include("AVATAR=/img/ed.jpg")
    p should include("COUNT=Edward A Maxedon;")
  }

  it should "support multi-author posts via authors: [a, b]" in {
    writeAt(
      "site.toml",
      """title    = "Blog"
        |baseURL  = "http://x"
        |htmlDir  = ""
        |
        |[[authors]]
        |id   = "ed"
        |name = "Ed"
        |
        |[[authors]]
        |id   = "alice"
        |name = "Alice"
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/co.md",
      """---
        |title: Co-authored
        |authors: [ed, alice]
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/file.html",
      """LIST={{ for a <- .page.authors }}{{ a.name }};{{ end }}""",
    )

    build()

    out("co/index.html") should include("LIST=Ed;Alice;")
  }

  it should "emit /authors/<id>/ archive pages when author-page layout is provided" in {
    writeAt(
      "site.toml",
      """title    = "Blog"
        |baseURL  = "http://x"
        |htmlDir  = ""
        |
        |[[authors]]
        |id    = "ed"
        |name  = "Ed"
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/p1.md",
      """---
        |title: First
        |author: ed
        |date: 2024-03-01
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt(
      "content/p2.md",
      """---
        |title: Second
        |author: ed
        |date: 2024-08-01
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")
    writeAt(
      "layouts/_default/author-page.html",
      """ID={{ .author.id }}
        |NAME={{ .author.name }}
        |COUNT={{ .author.count }}
        |LIST={{ for p <- .author.pages }}{{ p.title }};{{ end }}
        |""".stripMargin,
    )

    build()

    val a = out("authors/ed/index.html")
    a should include("ID=ed")
    a should include("NAME=Ed")
    a should include("COUNT=2")
    // Pages are date-desc within an author archive — newer first.
    a should include("LIST=Second;First;")
  }

  it should "emit /authors/index.html when author-list layout is provided" in {
    writeAt(
      "site.toml",
      """title    = "Blog"
        |baseURL  = "http://x"
        |htmlDir  = ""
        |
        |[[authors]]
        |id   = "ed"
        |name = "Ed"
        |
        |[[authors]]
        |id   = "alice"
        |name = "Alice"
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/post.md",
      """---
        |title: Post
        |author: ed
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")
    writeAt(
      "layouts/_default/author-list.html",
      """{{ for a <- .authors }}{{ a.id }}({{ a.count }});{{ end }}""",
    )

    build()

    // Only authors with at least one referencing page appear. Alice has 0
    // posts so she's omitted from the roster.
    val list = out("authors/index.html")
    list should include("ed(1);")
    list should not include "alice("
  }

  it should "expose .site.authors filtered to authors with pages" in {
    writeAt(
      "site.toml",
      """title    = "Blog"
        |baseURL  = "http://x"
        |htmlDir  = ""
        |
        |[[authors]]
        |id   = "ed"
        |name = "Ed"
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/post.md",
      """---
        |title: Post
        |author: ed
        |---
        |Body.
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/folder.html",
      """{{ for a <- .site.authors }}A={{ a.name }};{{ end }}""",
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    out("index.html") should include("A=Ed;")
  }
}
