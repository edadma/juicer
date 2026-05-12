package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** robots.txt + sitemap.xml SEO behaviour.
  *
  * Engine emits both as part of every build by default. Pins:
  *
  *   - default robots.txt: `User-agent: *` / `Disallow:` (allow-all)
  *     plus a `Sitemap:` line pointing at the absolute sitemap URL.
  *   - `disallow = [...]` in site.toml lands as one `Disallow:` line
  *     per entry.
  *   - `noindex = true` in site.toml emits `Disallow: /` (whole-site
  *     keep-out — for staging / preview domains).
  *   - `robots = false` in site.toml opts out entirely (don't overwrite a
  *     hand-rolled robots.txt that ships in `static/`).
  *   - per-page `noindex: true` in frontmatter excludes the page from
  *     sitemap.xml. The page is still built and reachable by direct URL —
  *     the theme's <meta name="robots" content="noindex"> is the
  *     in-page signal.
  */
class RobotsSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "juicer robots.txt" should "emit allow-all + Sitemap line by default" in {
    writeAt(
      "site.toml",
      """title   = "S"
        |baseURL = "https://example.com"
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: Home\n---\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")

    build()

    val robots = out("robots.txt")
    robots should include("User-agent: *")
    robots should include("Disallow:")
    robots should not include "Disallow: /\n"
    robots should include("Sitemap: https://example.com/sitemap.xml")
  }

  it should "emit one Disallow: line per entry in site.toml `disallow`" in {
    writeAt(
      "site.toml",
      """title    = "S"
        |baseURL  = "https://example.com"
        |disallow = ["/admin/", "/drafts/"]
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: Home\n---\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")

    build()

    val robots = out("robots.txt")
    robots should include("Disallow: /admin/")
    robots should include("Disallow: /drafts/")
    robots should include("Sitemap: https://example.com/sitemap.xml")
  }

  it should "block everything when site.toml sets noindex = true" in {
    writeAt(
      "site.toml",
      """title   = "S"
        |baseURL = "https://staging.example.com"
        |noindex = true
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: Home\n---\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")

    build()

    val robots = out("robots.txt")
    robots should include("User-agent: *")
    robots should include("Disallow: /")
  }

  it should "skip robots.txt when site.toml sets robots = false" in {
    writeAt(
      "site.toml",
      """title   = "S"
        |baseURL = "https://example.com"
        |robots  = false
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: Home\n---\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")

    build()

    (dst / "robots.txt").exists shouldBe false
  }

  "juicer sitemap.xml" should "skip pages with frontmatter `noindex: true`" in {
    writeAt(
      "site.toml",
      """title   = "S"
        |baseURL = "https://example.com"
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: Home\n---\n")
    writeAt("content/public.md", "---\ntitle: Public\n---\n")
    writeAt("content/hidden.md", "---\ntitle: Hidden\nnoindex: true\n---\n")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "x")

    build()

    val sitemap = out("sitemap.xml")
    sitemap should include("<loc>https://example.com/</loc>")
    sitemap should include("<loc>https://example.com/public/</loc>")
    sitemap should not include "/hidden/"
  }
}
