package io.github.edadma.juicer

import io.github.edadma.path.Path
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Regression for the relative-`--source`-path bug.
  *
  * When juicer was invoked with a relative source path (`-s ../somewhere`),
  * the old `.normalize.toAbsolutePath` chain in [[App.build]] left a `..`
  * segment in the canonicalized `src1`: `Path.normalize` cannot fold a
  * leading `..` against an empty segment vector, and `toAbsolutePath`
  * doesn't re-normalize after prepending the cwd. Inside [[Process]] the
  * exclude set was computed with another `.normalize` *after* concatenation
  * (which DID fold), so the walked-dir set and the exclude set never
  * compared equal under [[Path]]'s structural equality — vendored themes
  * leaked into the site's "other templates" pass and the first one tried
  * to write `dst/themes/<name>/layouts/_default/404.html`, failing with
  * NoSuchFileException because the parent dirs didn't exist.
  *
  * Fix lives at [[App.build]] (and the matching `themeAdd`/`themeUpgrade`
  * /serve sites): canonicalize *after* absolutize — `.toAbsolutePath.normalize`.
  */
class RelativeSourcePathSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "juicer build" should "exclude vendored themes when --source is a relative path with `..`" in {
    writeAt(
      "site.toml",
      """title   = "S"
        |baseURL = "http://x"
        |theme   = "leaky"
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: home\n---\n\n# H\n")

    // Theme provides the layouts the site needs — and a 404.html, the
    // exact file that triggered NoSuchFileException in the original bug.
    writeAt("themes/leaky/layouts/_default/folder.html", "<p>theme: {{ .page.title }}</p>")
    writeAt("themes/leaky/layouts/_default/file.html", "x")
    writeAt("themes/leaky/layouts/_default/404.html", "<p>theme 404</p>")

    // Re-express the absolute `src` as a path relative to the JVM's cwd.
    // For any sane cwd (anything below `/`), `src.relativeTo(cwd)` starts
    // with at least one `..` segment — which is precisely the shape that
    // `.normalize` can't fold before `.toAbsolutePath` runs.
    val cwd    = Path(System.getProperty("user.dir"))
    val srcRel = src.relativeTo(cwd)
    assume(
      srcRel.segments.headOption.contains(".."),
      "cwd is at filesystem root; this test can't exercise the bug here",
    )

    App.run(Args(cmd = Some(BuildCommand(src = srcRel, dst = dst))))

    // The theme directory must not leak into the output tree.
    (dst / "themes").exists shouldBe false
    // Sanity: the site rendered through the theme layout — proving the
    // theme pass still ran (it's the site pass that has to skip the dir).
    out("index.html") should include("theme: home")
  }
}
