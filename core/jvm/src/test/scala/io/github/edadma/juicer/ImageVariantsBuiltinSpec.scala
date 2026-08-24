package io.github.edadma.juicer

import io.github.edadma.path.Path
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.IOException

/** End-to-end test for the `imageVariants` and `srcset` template
  * builtins, running a real `magick` shell-out against a real source
  * PNG. Gated on `magick -version` succeeding — when ImageMagick is
  * not installed, all assertions become `cancel` so the build still
  * passes on CI runners that don't have it.
  *
  * Lives under `jvm/` rather than `shared/` because Scala Native and
  * Scala.js still rely on the stub backend; the encoder-driven
  * assertions only make sense on JVM.
  */
class ImageVariantsBuiltinSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  private lazy val magickAvailable: Boolean =
    try {
      val pb = new java.lang.ProcessBuilder("magick", "-version")
      pb.redirectErrorStream(true)
      pb.start().waitFor() == 0
    } catch {
      case _: IOException => false
      case _: Throwable   => false
    }

  /** Hand-crafted IHDR-only PNG fragment. Bytes are NOT a renderable
    * PNG (missing IDAT/IEND), and the IHDR CRC is intentionally not
    * computed — `ImageDimensions.fromFile` reads IHDR width/height
    * without verifying CRC, so this fixture lets the
    * passthrough/disabled tests run without needing `magick` to be
    * installed. */
  private val Png16x12Ihdr: Array[Byte] = Array[Int](
    0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
    0x00, 0x00, 0x00, 0x0d,
    'I'.toInt, 'H'.toInt, 'D'.toInt, 'R'.toInt,
    0x00, 0x00, 0x00, 0x10,
    0x00, 0x00, 0x00, 0x0c,
    0x08, 0x02, 0x00, 0x00, 0x00,
  ).map(_.toByte)

  /** Generate a real 16×12 solid-colour PNG with `magick` itself.
    * Used only by tests that exercise the encoder path — they gate on
    * `magickAvailable` so the absence of ImageMagick cancels the test
    * rather than failing it. */
  private def makePng16x12(target: Path): Unit = {
    target.parent.foreach(_.createDirectories())
    val pb = new java.lang.ProcessBuilder(
      "magick", "-size", "16x12", "canvas:magenta", target.toString,
    )
    pb.redirectErrorStream(true)
    pb.start().waitFor()
  }

  private def writeBytes(relPath: String, bytes: Array[Byte]): Unit = {
    val p = relPath.split('/').foldLeft(src)(_ / _)
    p.parent.foreach(_.createDirectories())
    p.writeBytes(bytes)
  }

  private def writePngFixture(relPath: String): Unit = {
    val p = relPath.split('/').foldLeft(src)(_ / _)
    makePng16x12(p)
  }

  "imageVariants builtin" should "emit a passthrough-only set when [images] is absent" in {
    writeAt(
      "site.toml",
      """title = "S"
        |baseURL = "http://x"
        |""".stripMargin,
    )
    writeBytes("static/img/p.png", Png16x12Ihdr)
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/folder.html",
      """{{ v := imageVariants '/img/p.png' }}orig={{ v.original }} w={{ v.originalWidth }} variants={{ v.variants.length }}""",
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("orig=/img/p.png")
    html should include("w=16")
    html should include("variants=0")
  }

  it should "generate webp + original variants when [images] enabled = true" in {
    if (!magickAvailable) cancel("magick (ImageMagick) is not installed on PATH")

    writeAt(
      "site.toml",
      """title = "S"
        |baseURL = "http://x"
        |
        |[images]
        |enabled = true
        |widths  = [8, 12]
        |formats = ["webp", "original"]
        |quality = 80
        |""".stripMargin,
    )
    writePngFixture("static/img/p.png")
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/folder.html",
      """{{ v := imageVariants '/img/p.png' }}{{ for var <- v.variants }}[{{ var.width }}|{{ var.format }}|{{ var.mime }}] {{ end }}""",
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    // 8 and 12 are below the source's 16px; 16 is added automatically.
    // webp variants come first (config.formats order), then original.
    html should include("[8|webp|image/webp]")
    html should include("[12|webp|image/webp]")
    html should include("[16|webp|image/webp]")
    html should include("[8|original|image/png]")
    html should include("[12|original|image/png]")
    html should include("[16|original|image/png]")

    // Variant files actually landed in the cache dir on disk.
    val cacheDir = dst / ".image-cache"
    cacheDir.exists shouldBe true
    val produced = cacheDir.listDirectory().map(_.name).toList
    // 3 widths × 2 formats — minus the duplicate 16×original (which is
    // a 1:1 byte copy of the source, written without invoking magick)
    produced.length shouldBe 6
    produced.exists(_.endsWith(".webp")) shouldBe true
    produced.exists(_.endsWith(".png"))  shouldBe true
  }

  "srcset builtin" should "build a comma-separated url + width descriptor list" in {
    if (!magickAvailable) cancel("magick (ImageMagick) is not installed on PATH")

    writeAt(
      "site.toml",
      """title = "S"
        |baseURL = "http://x"
        |
        |[images]
        |enabled = true
        |widths  = [8, 12]
        |formats = ["webp"]
        |""".stripMargin,
    )
    writePngFixture("static/img/p.png")
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/folder.html",
      """SS:{{ srcset '/img/p.png' 'webp' }}""",
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("/.image-cache/p-8w.")
    html should include(" 8w")
    html should include(" 12w")
    html should include(" 16w")
    // exactly three "<url> Nw" tuples, comma-separated:
    val ss = html.linesIterator.find(_.startsWith("SS:")).map(_.stripPrefix("SS:")).getOrElse("")
    ss.split(", ").length shouldBe 3
  }
}
