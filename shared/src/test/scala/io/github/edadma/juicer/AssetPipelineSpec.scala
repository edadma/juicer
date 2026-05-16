package io.github.edadma.juicer

import io.github.edadma.path.Path
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for the `[assets]` pipeline — Sass / esbuild shell-out with
  * optional content-hash fingerprinting and an `asset` template
  * builtin that resolves logical names to final URLs.
  *
  * The orchestrator-level tests use a fake [[AssetBuilderBackend]] so
  * the suite doesn't take a hard dependency on `sass` / `esbuild`
  * being on PATH. The end-to-end build tests exercise the
  * "unavailable backend → verbatim copy" degradation path the same
  * way a real machine without the tools would behave — that's the
  * codepath every CI runner without a Sass install hits, so it's the
  * important one to pin.
  */
class AssetPipelineSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  /** Fake backend that "compiles" by uppercasing the source bytes and
    * "bundles" by prepending a sentinel — enough to verify the
    * pipeline plumbing without depending on the real tools. */
  private class FakeBackend(val sassOk: Boolean = true, val esbuildOk: Boolean = true) extends AssetBuilderBackend {
    val sassAvailable: Boolean    = sassOk
    val esbuildAvailable: Boolean = esbuildOk

    def compileSass(src: Path, dst: Path, minify: Boolean): Either[String, Unit] =
      if (!sassOk) Left("fake sass unavailable")
      else {
        val body = src.readText().toUpperCase
        val out  = if (minify) body.replaceAll("\\s+", "") else body
        dst.writeText(out)
        Right(())
      }

    def bundleJs(src: Path, dst: Path, minify: Boolean): Either[String, Unit] =
      if (!esbuildOk) Left("fake esbuild unavailable")
      else {
        val body = "/*bundled*/\n" + src.readText()
        val out  = if (minify) body.replaceAll("\\s+", " ") else body
        dst.writeText(out)
        Right(())
      }
  }

  "AssetPipeline.Config.parseFromToml" should "return Disabled when [assets] is missing" in {
    AssetPipeline.Config.parseFromToml(Map.empty) shouldBe AssetPipeline.Config.Disabled
  }

  it should "return Disabled when [assets] enabled is false" in {
    val toml = Map[String, Any]("assets" -> Map[String, Any]("enabled" -> false))
    AssetPipeline.Config.parseFromToml(toml) shouldBe AssetPipeline.Config.Disabled
  }

  it should "parse a single inline sass entry" in {
    val toml = Map[String, Any](
      "assets" -> Map[String, Any](
        "enabled"     -> true,
        "fingerprint" -> false,
        "sass"        -> Map[String, Any]("input" -> "src/site.scss", "output" -> "/css/site.css"),
      ),
    )
    val cfg = AssetPipeline.Config.parseFromToml(toml)
    cfg.enabled shouldBe true
    cfg.entries should contain(
      AssetPipeline.SassEntry("site.css", "src/site.scss", "/css/site.css", minify = false),
    )
  }

  it should "parse a list of esbuild entries with minify and custom logical name" in {
    val toml = Map[String, Any](
      "assets" -> Map[String, Any](
        "enabled" -> true,
        "esbuild" -> Seq(
          Map[String, Any](
            "logical" -> "app",
            "input"   -> "src/index.js",
            "output"  -> "/js/app.js",
            "minify"  -> true,
          ),
        ),
      ),
    )
    val cfg = AssetPipeline.Config.parseFromToml(toml)
    cfg.entries should contain(
      AssetPipeline.EsbuildEntry("app", "src/index.js", "/js/app.js", minify = true),
    )
  }

  it should "ignore entries missing input or output" in {
    val toml = Map[String, Any](
      "assets" -> Map[String, Any](
        "enabled" -> true,
        "sass"    -> Seq(
          Map[String, Any]("output" -> "/foo.css"), // no input
          Map[String, Any]("input"  -> "src/x.scss"), // no output
        ),
      ),
    )
    AssetPipeline.Config.parseFromToml(toml).entries shouldBe empty
  }

  "AssetPipeline.run" should "be a no-op when config is disabled" in {
    val manifest = AssetPipeline.run(
      AssetPipeline.Config.Disabled,
      new FakeBackend,
      src, dst,
    )
    manifest shouldBe empty
  }

  it should "compile sass through the backend and put the URL in the manifest" in {
    (src / "src").createDirectories()
    (src / "src" / "site.scss").writeText("body { color: red; }\n")
    dst.createDirectories()

    val cfg = AssetPipeline.Config(
      enabled     = true,
      fingerprint = false,
      entries     = List(AssetPipeline.SassEntry("site.css", "src/site.scss", "/css/site.css", minify = false)),
    )
    val manifest = AssetPipeline.run(cfg, new FakeBackend, src, dst)

    manifest should contain("site.css" -> "/css/site.css")
    (dst / "css" / "site.css").exists shouldBe true
    (dst / "css" / "site.css").readText() should include("BODY")
  }

  it should "fingerprint the output filename and produce a stable hash for stable bytes" in {
    (src / "src").createDirectories()
    (src / "src" / "site.scss").writeText("body { color: red; }\n")
    dst.createDirectories()

    val cfg = AssetPipeline.Config(
      enabled     = true,
      fingerprint = true,
      entries     = List(AssetPipeline.SassEntry("site.css", "src/site.scss", "/css/site.css", minify = false)),
    )
    val m1 = AssetPipeline.run(cfg, new FakeBackend, src, dst)
    val m2 = AssetPipeline.run(cfg, new FakeBackend, src, dst)

    val url = m1("site.css")
    url should startWith("/css/site.")
    url should endWith(".css")
    url should not be "/css/site.css"
    m2("site.css") shouldBe url
  }

  it should "register a sass entry's URL in the manifest without writing the file when the backend is unavailable" in {
    (src / "src").createDirectories()
    (src / "src" / "site.scss").writeText("@use 'foo'; body { color: red; }\n")
    dst.createDirectories()

    val cfg = AssetPipeline.Config(
      enabled     = true,
      fingerprint = false,
      entries     = List(AssetPipeline.SassEntry("site.css", "src/site.scss", "/css/site.css", minify = false)),
    )
    val manifest = AssetPipeline.run(cfg, new FakeBackend(sassOk = false), src, dst)

    // Manifest still gets the URL — themes typically ship a
    // pre-compiled fallback CSS that the static-copy pass writes to
    // this same path, so the URL resolves. We deliberately do NOT
    // write the SCSS source bytes to /css/site.css because they're
    // not valid CSS in general (`@use` would break).
    manifest should contain("site.css" -> "/css/site.css")
    (dst / "css" / "site.css").exists shouldBe false
  }

  it should "fall back to a verbatim copy for esbuild entries when the backend is unavailable" in {
    (src / "src").createDirectories()
    (src / "src" / "main.js").writeText("console.log('hi');\n")
    dst.createDirectories()

    val cfg = AssetPipeline.Config(
      enabled     = true,
      fingerprint = false,
      entries     = List(AssetPipeline.EsbuildEntry("main.js", "src/main.js", "/js/main.js", minify = false)),
    )
    val manifest = AssetPipeline.run(cfg, new FakeBackend(esbuildOk = false), src, dst)

    // JS-source-without-bundling is usually still valid JS, so a
    // verbatim copy is a reasonable degradation: the URL resolves
    // and the script runs (just no import-resolution from the
    // bundler).
    manifest should contain("main.js" -> "/js/main.js")
    (dst / "js" / "main.js").readText() should include("console.log('hi')")
  }

  it should "skip an entry whose source is missing" in {
    val cfg = AssetPipeline.Config(
      enabled     = true,
      fingerprint = false,
      entries     = List(AssetPipeline.SassEntry("missing.css", "src/missing.scss", "/css/missing.css", minify = false)),
    )
    val manifest = AssetPipeline.run(cfg, new FakeBackend, src, dst)
    manifest shouldBe empty
  }

  it should "support a CopyEntry that just copies bytes" in {
    (src / "src").createDirectories()
    (src / "src" / "robots.txt").writeText("User-agent: *\n")
    dst.createDirectories()

    val cfg = AssetPipeline.Config(
      enabled     = true,
      fingerprint = false,
      entries     = List(AssetPipeline.CopyEntry("robots.txt", "src/robots.txt", "/robots.txt")),
    )
    val manifest = AssetPipeline.run(cfg, new FakeBackend, src, dst)
    manifest should contain("robots.txt" -> "/robots.txt")
    (dst / "robots.txt").readText() should include("User-agent")
  }

  "the `asset` template builtin (end-to-end)" should "return the input string unchanged when no [assets] block is configured" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt(
      "layouts/_default/folder.html",
      "<link href=\"{{ asset 'site.css' }}\">",
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    out("index.html") should include("<link href=\"site.css\">")
  }

  it should "register the configured sass output URL in the manifest regardless of whether sass is available" in {
    writeAt(
      "site.toml",
      """title = "S"
        |baseURL = "http://x"
        |
        |[assets]
        |enabled = true
        |
        |[[assets.sass]]
        |input  = "src/site.scss"
        |output = "/css/site.css"
        |""".stripMargin,
    )
    writeAt("src/site.scss", "body { color: red; }\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt(
      "layouts/_default/folder.html",
      "<link href=\"{{ asset 'site.css' }}\">",
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    // The URL assertion is what's invariant across "sass installed"
    // and "sass missing" environments. With sass installed, the
    // pipeline compiles to /css/site.css. Without sass, the
    // manifest still registers /css/site.css (themes ship static
    // fallbacks at that path); we deliberately do NOT write the
    // SCSS source bytes there, so a file-existence assertion would
    // pass only in one of the two environments.
    out("index.html") should include("<link href=\"/css/site.css\">")
  }

  it should "fingerprint the URL when [assets] fingerprint = true" in {
    writeAt(
      "site.toml",
      """title = "S"
        |baseURL = "http://x"
        |
        |[assets]
        |enabled = true
        |fingerprint = true
        |
        |[[assets.copy]]
        |input  = "src/robots.txt"
        |output = "/robots.txt"
        |""".stripMargin,
    )
    writeAt("src/robots.txt", "User-agent: *\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt(
      "layouts/_default/folder.html",
      "[{{ asset 'robots.txt' }}]",
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val s = out("index.html")
    // Fingerprinted URL has a 16-char hex hash inserted before the
    // extension: `/robots.<hash>.txt`. The test asserts the shape
    // rather than a specific hash so refactors to the hashing
    // strategy can update the assertion in one place if they ever
    // happen.
    s should include regex """\[/robots\.[0-9a-f]{16}\.txt\]"""
  }
}
