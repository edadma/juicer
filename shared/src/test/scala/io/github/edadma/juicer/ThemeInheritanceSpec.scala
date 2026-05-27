package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for `theme.toml` `inherits` resolution: a theme can declare
  * dependencies on other themes, which are spliced into the lookup chain
  * just after the theme that names them. Precedence stays what users know:
  * site files > first theme > … > last theme; inheritance only lengthens
  * the chain.
  */
class ThemeInheritanceSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  /** Minimal site that renders a single partial into the body so a test can
    * assert which theme's copy of the partial won resolution. The layout
    * lives in the site so it never competes with the themes under test. */
  private def stageSite(theme: String, partials: String*): Unit = {
    writeAt("site.toml", s"""title = "S"\nbaseURL = "http://x"\ntheme = $theme\n""")
    writeAt("content/post.md", "---\ntitle: P\n---\nBody\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", partials.map(p => s"{{ partial '$p' . }}").mkString("|"))
  }

  "theme inheritance" should "resolve a partial that lives only in an inherited theme" in {
    stageSite("\"themeA\"", "fromB")
    writeAt("themes/themeA/theme.toml", "inherits = [\"themeB\"]\n")
    writeAt("themes/themeB/partials/fromB.html", "PARTIAL-B")

    build()

    out("post/index.html") should include("PARTIAL-B")
  }

  it should "let the inheriting theme override a partial from its parent" in {
    stageSite("\"themeA\"", "shared")
    writeAt("themes/themeA/theme.toml", "inherits = [\"themeB\"]\n")
    writeAt("themes/themeA/partials/shared.html", "FROM-A")
    writeAt("themes/themeB/partials/shared.html", "FROM-B")

    build()

    val html = out("post/index.html")
    html should include("FROM-A")
    html should not include "FROM-B"
  }

  it should "let site files override a partial from any theme in the chain" in {
    stageSite("\"themeA\"", "shared")
    writeAt("themes/themeA/theme.toml", "inherits = [\"themeB\"]\n")
    writeAt("themes/themeA/partials/shared.html", "FROM-A")
    writeAt("themes/themeB/partials/shared.html", "FROM-B")
    writeAt("partials/shared.html", "FROM-SITE")

    build()

    val html = out("post/index.html")
    html should include("FROM-SITE")
    html should not include "FROM-A"
    html should not include "FROM-B"
  }

  it should "resolve transitively through a chain A -> B -> C" in {
    stageSite("\"themeA\"", "fromC")
    writeAt("themes/themeA/theme.toml", "inherits = [\"themeB\"]\n")
    writeAt("themes/themeB/theme.toml", "inherits = [\"themeC\"]\n")
    writeAt("themes/themeC/partials/fromC.html", "PARTIAL-C")

    build()

    out("post/index.html") should include("PARTIAL-C")
  }

  it should "combine a site-level overlay with a theme's own inheritance" in {
    // theme = ["overlay", "themeA"], themeA inherits themeB.
    // Resolved chain: site > overlay > themeA > themeB.
    stageSite("[\"overlay\", \"themeA\"]", "fromB", "shared")
    writeAt("themes/themeA/theme.toml", "inherits = [\"themeB\"]\n")
    writeAt("themes/themeB/partials/fromB.html", "PARTIAL-B")
    writeAt("themes/overlay/partials/shared.html", "FROM-OVERLAY")
    writeAt("themes/themeA/partials/shared.html", "FROM-A")

    build()

    val html = out("post/index.html")
    html should include("PARTIAL-B")     // resolved from the transitively-inherited themeB
    html should include("FROM-OVERLAY")  // overlay beats themeA's own copy
    html should not include "FROM-A"
  }

  it should "fail with a traced error when the inheritance graph has a cycle" in {
    stageSite("\"themeA\"")
    writeAt("themes/themeA/theme.toml", "inherits = [\"themeB\"]\n")
    writeAt("themes/themeB/theme.toml", "inherits = [\"themeA\"]\n")
    // Both need a partial dir so the dirs exist and are walked.
    writeAt("themes/themeA/partials/a.html", "a")
    writeAt("themes/themeB/partials/b.html", "b")

    val ex = intercept[RuntimeException](build())
    ex.getMessage should include("theme cycle")
    ex.getMessage should include("themeA → themeB → themeA")
  }

  it should "resolve a diamond once, keeping the first occurrence" in {
    // theme = ["themeA", "themeB"], both inherit themeC.
    // Chain: site > themeA > themeC > themeB. themeC resolved once;
    // a partial defined in A, B and C is won by A (earliest).
    stageSite("[\"themeA\", \"themeB\"]", "fromC", "shared")
    writeAt("themes/themeA/theme.toml", "inherits = [\"themeC\"]\n")
    writeAt("themes/themeB/theme.toml", "inherits = [\"themeC\"]\n")
    writeAt("themes/themeC/partials/fromC.html", "PARTIAL-C")
    writeAt("themes/themeA/partials/shared.html", "FROM-A")
    writeAt("themes/themeB/partials/shared.html", "FROM-B")
    writeAt("themes/themeC/partials/shared.html", "FROM-C")

    build()

    val html = out("post/index.html")
    html should include("PARTIAL-C")
    html should include("FROM-A")
    html should not include "FROM-B"
    html should not include "FROM-C"
  }

  it should "fail with a clear error when an inherited theme does not exist" in {
    stageSite("\"themeA\"")
    writeAt("themes/themeA/theme.toml", "inherits = [\"does-not-exist\"]\n")
    writeAt("themes/themeA/partials/a.html", "a")

    val ex = intercept[RuntimeException](build())
    ex.getMessage should include("does-not-exist")
  }

  it should "build unchanged when a theme ships no theme.toml" in {
    stageSite("\"themeA\"", "fromA")
    writeAt("themes/themeA/partials/fromA.html", "PARTIAL-A")

    build()

    out("post/index.html") should include("PARTIAL-A")
  }
}
