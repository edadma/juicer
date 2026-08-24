package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Squiggly's `.foo` inside a `{{ for ... }}` body resolves against the
  * loop element, NOT the page root. To reach site-/page-wide data from
  * inside a loop, templates must use the `$.foo` (global-root) prefix.
  *
  * juicerblog/post-byline.html and juicerchurch/author-list.html both
  * shipped templates that violated this — they referenced
  * `.site.authorsPath` from inside a `{{ for a <- … }}` body, where `.`
  * was the author element (no `site` key). The build failed with
  * `cannot apply function 'relURL' to arguments '()'` because the
  * lookup returned `()` (Unit) and squiggly fed that as the one arg
  * to relURL.
  *
  * These tests pin the contract for future templates:
  *
  *  - bare `.site.X` inside a for-loop is broken (proves the failure
  *    mode is squiggly's actual scope semantics, not a juicer-side
  *    bug)
  *  - `$.site.X` is the correct form and reaches the global root
  *    (proves the fix the themes ship)
  *
  * Engine-side defaults (authorsPath -> "/authors/" at App.scala
  * around line 732) DO reach the global render context — that was
  * verified during the original diagnosis with a one-shot println of
  * `sitedata.contains("authorsPath")`. The gap was always at the
  * template-scope layer, not in the engine.
  */
class GlobalRootScopeSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "engine-default authorsPath" should "be reachable as $.site.authorsPath inside a for-loop body" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n[[authors]]\nid = \"alice\"\nname = \"Alice\"\n")
    writeAt(
      "content/post.md",
      """---
        |title: A post
        |author: alice
        |---
        |body
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/file.html",
      // The for-loop reassigns `.` to each `.page.authors` element.
      // Reaching site from inside the loop body MUST use `$.site.X`.
      """{{ for a <- .page.authors }}<a href="{{ $.site.authorsPath }}{{ a.id }}/">{{ a.name }}</a>{{ end }}""".stripMargin,
    )

    build()

    val post = out("post/index.html")
    post should include("""<a href="/authors/alice/">Alice</a>""")
  }

  "bare .site.X inside a for-loop body" should "fail because `.` is the loop element, not the page" in {
    // This is the pre-fix pattern that was shipping in juicerblog and
    // juicerchurch. The build raises an engine error because
    // `.site.authorsPath` evaluates to `()` (Unit) inside the loop
    // body and squiggly cannot then apply a function to `()`.
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n[[authors]]\nid = \"alice\"\nname = \"Alice\"\n")
    writeAt(
      "content/post.md",
      """---
        |title: A post
        |author: alice
        |---
        |body
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/file.html",
      """{{ for a <- .page.authors }}<a href="{{ relURL .site.authorsPath }}{{ a.id }}/">{{ a.name }}</a>{{ end }}""",
    )

    val ex = intercept[RuntimeException] { build() }
    ex.getMessage should include("relURL")
  }

  "site-set authorsPath" should "also be reachable via $.site.authorsPath inside a for-loop" in {
    // The cafe demo's pattern: `authorsPath = "/team/"` set in
    // site.toml. The global must still be reached via `$.site.X`
    // inside loops.
    writeAt(
      "site.toml",
      """title = "S"
        |baseURL = "http://x"
        |authorsPath = "/team/"
        |[[authors]]
        |id = "alice"
        |name = "Alice"
        |""".stripMargin,
    )
    writeAt(
      "content/post.md",
      """---
        |title: A post
        |author: alice
        |---
        |body
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/file.html",
      """{{ for a <- .page.authors }}<a href="{{ $.site.authorsPath }}{{ a.id }}/">{{ a.name }}</a>{{ end }}""",
    )

    build()

    val post = out("post/index.html")
    post should include("""<a href="/team/alice/">Alice</a>""")
  }
}
