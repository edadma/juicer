package io.github.edadma.juicer

import io.github.edadma.path.Path
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** End-to-end test for the `imageDims` template builtin: takes a path,
  * resolves it against the built output (or source root), reads the
  * image header, and returns a `{width, height}` map to the template.
  */
class ImageDimsBuiltinSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  /** Minimal 800×600 PNG: 8-byte signature + IHDR. We don't write any
    * IDAT/IEND chunks because the dimension reader stops at IHDR. The
    * resulting bytes are not a renderable image, but the header is
    * valid for our purposes.
    */
  private val Png800x600: Array[Byte] = Array[Int](
    0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
    0x00, 0x00, 0x00, 0x0d,
    'I'.toInt, 'H'.toInt, 'D'.toInt, 'R'.toInt,
    0x00, 0x00, 0x03, 0x20,
    0x00, 0x00, 0x02, 0x58,
    0x08, 0x02, 0x00, 0x00, 0x00,
  ).map(_.toByte)

  private def writeBytes(relPath: String, bytes: Array[Byte]): Unit = {
    val p = relPath.split('/').foldLeft(src)(_ / _)
    p.parent.foreach(_.createDirectories())
    p.writeBytes(bytes)
  }

  "imageDims" should "read dimensions of a static-shipped image" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeBytes("static/img/photo.png", Png800x600)
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/folder.html",
      """{{ d := imageDims '/img/photo.png' }}<img width="{{ d.width }}" height="{{ d.height }}" />""",
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val html = out("index.html")
    html should include("""<img width="800" height="600" />""")
  }

  it should "return an empty map when the image is missing" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: Home
        |---
        |""".stripMargin,
    )
    writeAt(
      "layouts/_default/folder.html",
      """{{ d := imageDims '/img/nope.png' }}{{ if d.width }}has-w{{ else }}no-w{{ end }}""",
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    out("index.html") should include("no-w")
  }
}
