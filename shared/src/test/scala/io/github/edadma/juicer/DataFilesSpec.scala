package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** End-to-end tests for the `data/` directory and `.site.data` exposure.
  *
  * Each file under `<src>/data/` (and `<theme>/data/` for active themes)
  * becomes a key path in `.site.data`. Theme entries are loaded first, site
  * entries win at file-leaf granularity.
  */
class DataFilesSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  private val minimalLayouts: Unit = ()

  private def writeMinimalLayouts(): Unit = {
    writeAt("layouts/_default/file.html", "x")
  }

  // -----------------------------------------------------------------

  "site.data" should "expose a single top-level TOML data file" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "data/site.toml",
      """tagline = "the small ssg"
        |year    = 2026
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/folder.html",
      """<p class="tagline">{{ .site.data.site.tagline }}</p>
        |<p class="year">{{ .site.data.site.year }}</p>
        |""".stripMargin,
    )
    writeMinimalLayouts()

    build()

    val html = out("index.html")
    html should include("""<p class="tagline">the small ssg</p>""")
    html should include("""<p class="year">2026</p>""")
  }

  it should "load YAML data files alongside TOML" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "data/team.yaml",
      """members:
        |  - name: Alice
        |    role: Lead
        |  - name: Bob
        |    role: Eng
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/folder.html",
      """{{ for m <- .site.data.team.members }}<li>{{ m.name }}/{{ m.role }}</li>{{ end }}""",
    )
    writeMinimalLayouts()

    build()

    val html = out("index.html")
    html should include("<li>Alice/Lead</li>")
    html should include("<li>Bob/Eng</li>")
  }

  it should "namespace nested directories under .site.data" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "data/menu/lunch.toml",
      """soup = "pho"
        |sandwich = "banh mi"
        |""".stripMargin,
    )
    writeAt(
      "data/menu/dinner.toml",
      """main = "ramen"
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/folder.html",
      """<p>{{ .site.data.menu.lunch.soup }}</p>
        |<p>{{ .site.data.menu.lunch.sandwich }}</p>
        |<p>{{ .site.data.menu.dinner.main }}</p>
        |""".stripMargin,
    )
    writeMinimalLayouts()

    build()

    val html = out("index.html")
    html should include("<p>pho</p>")
    html should include("<p>banh mi</p>")
    html should include("<p>ramen</p>")
  }

  it should "let a site key override a theme's same-named data file" in {
    writeAt(
      "site.toml",
      """title   = "S"
        |baseURL = "http://x"
        |theme   = "demo"
        |""".stripMargin,
    )
    // Theme provides default + extra
    writeAt(
      "themes/demo/data/site.toml",
      """tagline = "default theme tagline"
        |year    = 2020
        |""".stripMargin,
    )
    writeAt(
      "themes/demo/data/about.toml",
      """email = "hi@example.com"
        |""".stripMargin,
    )
    writeAt(
      "themes/demo/layouts/_default/folder.html",
      """<p class="tagline">{{ .site.data.site.tagline }}</p>
        |<p class="year">{{ .site.data.site.year }}</p>
        |<p class="email">{{ .site.data.about.email }}</p>
        |""".stripMargin,
    )
    writeAt("themes/demo/layouts/_default/file.html", "x")
    // Site overrides only the `site.toml` data file
    writeAt(
      "data/site.toml",
      """tagline = "my tagline"
        |year    = 2026
        |""".stripMargin,
    )
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |""".stripMargin,
    )

    build()

    val html = out("index.html")
    html should include("""<p class="tagline">my tagline</p>""") // site wins
    html should include("""<p class="year">2026</p>""")          // site wins
    html should include("""<p class="email">hi@example.com</p>""") // theme passes through
  }
}
