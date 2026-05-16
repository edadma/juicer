package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Unit tests for the pure-Scala parts of the image-variants pipeline:
  * config parsing, content hash, filename scheme, and the
  * format/extension/MIME helpers. The encoder shell-out itself is
  * tested separately under JVM-only integration tests (where `magick`
  * may or may not be installed) — these tests are cross-platform safe.
  */
class ImageVariantsSpec extends AnyFlatSpec with Matchers {

  "Config.parseFromToml" should "return Disabled when [images] is absent" in {
    val c = ImageVariants.Config.parseFromToml(Map.empty)
    c.enabled shouldBe false
    c shouldBe ImageVariants.Config.Disabled
  }

  it should "return Disabled when [images] enabled = false" in {
    val toml: Map[String, Any] = Map(
      "images" -> Map[String, Any]("enabled" -> false, "widths" -> List(320, 640)),
    )
    ImageVariants.Config.parseFromToml(toml).enabled shouldBe false
  }

  it should "use DefaultEnabled values when only enabled = true is set" in {
    val toml: Map[String, Any] = Map("images" -> Map[String, Any]("enabled" -> true))
    val c = ImageVariants.Config.parseFromToml(toml)
    c.enabled  shouldBe true
    c.widths   shouldBe ImageVariants.Config.DefaultEnabled.widths
    c.formats  shouldBe ImageVariants.Config.DefaultEnabled.formats
    c.quality  shouldBe ImageVariants.Config.DefaultEnabled.quality
    c.cacheDir shouldBe ImageVariants.Config.DefaultEnabled.cacheDir
  }

  it should "honour widths, formats, quality, and cacheDir overrides" in {
    val toml: Map[String, Any] = Map(
      "images" -> Map[String, Any](
        "enabled"  -> true,
        "widths"   -> List(400, 800),
        "formats"  -> List("webp", "original"),
        "quality"  -> 75,
        "cacheDir" -> ".my-cache",
      ),
    )
    val c = ImageVariants.Config.parseFromToml(toml)
    c.widths   shouldBe List(400, 800)
    c.formats  shouldBe List("webp", "original")
    c.quality  shouldBe 75
    c.cacheDir shouldBe ".my-cache"
  }

  it should "clamp quality to 1..100 and drop bogus widths" in {
    val toml: Map[String, Any] = Map(
      "images" -> Map[String, Any](
        "enabled" -> true,
        "widths"  -> List(0, -5, 320, 640, 320),
        "quality" -> 999,
      ),
    )
    val c = ImageVariants.Config.parseFromToml(toml)
    c.widths  shouldBe List(320, 640)
    c.quality shouldBe 100
  }

  it should "drop unknown format names silently" in {
    val toml: Map[String, Any] = Map(
      "images" -> Map[String, Any](
        "enabled" -> true,
        "formats" -> List("webp", "heic", "original", "tga"),
      ),
    )
    ImageVariants.Config.parseFromToml(toml).formats shouldBe List("webp", "original")
  }

  "contentHash" should "be deterministic and 16 hex chars" in {
    val bytes = "hello, world".getBytes("UTF-8")
    val h1    = ImageVariants.contentHash(bytes)
    val h2    = ImageVariants.contentHash(bytes)
    h1 shouldBe h2
    h1 should fullyMatch regex "[0-9a-f]{16}"
  }

  it should "differ for different inputs" in {
    val a = ImageVariants.contentHash("a".getBytes("UTF-8"))
    val b = ImageVariants.contentHash("b".getBytes("UTF-8"))
    a should not be b
  }

  it should "produce the FNV-1a 64 reference value for the empty input" in {
    // The published FNV-1a 64 offset basis is 14695981039346656037 =
    // 0xcbf29ce484222325. Hashing zero bytes should round-trip the
    // offset basis verbatim — locks in the algorithm choice so a
    // future micro-rewrite doesn't silently change the hash function
    // and invalidate every cached variant.
    ImageVariants.contentHash(Array.empty[Byte]) shouldBe "cbf29ce484222325"
  }

  "variantFilename" should "follow the <stem>-<width>w.<hash>.<ext> scheme" in {
    ImageVariants.variantFilename("photo", 640, "webp", "deadbeef01234567") shouldBe
      "photo-640w.deadbeef01234567.webp"
  }

  "extForFormat" should "use the source ext for `original`" in {
    ImageVariants.extForFormat("original", "jpg")  shouldBe "jpg"
    ImageVariants.extForFormat("original", "JPEG") shouldBe "JPEG"
  }

  it should "map known formats to canonical extensions" in {
    ImageVariants.extForFormat("webp", "png") shouldBe "webp"
    ImageVariants.extForFormat("avif", "jpg") shouldBe "avif"
    ImageVariants.extForFormat("jpeg", "png") shouldBe "jpg"
    ImageVariants.extForFormat("png",  "jpg") shouldBe "png"
  }

  "mimeForFormat" should "type known sources for `original`" in {
    ImageVariants.mimeForFormat("original", "jpg")  shouldBe "image/jpeg"
    ImageVariants.mimeForFormat("original", "jpeg") shouldBe "image/jpeg"
    ImageVariants.mimeForFormat("original", "png")  shouldBe "image/png"
    ImageVariants.mimeForFormat("original", "webp") shouldBe "image/webp"
    ImageVariants.mimeForFormat("original", "avif") shouldBe "image/avif"
  }

  it should "return empty for unrecognised source extensions" in {
    ImageVariants.mimeForFormat("original", "tiff") shouldBe ""
    ImageVariants.mimeForFormat("original", "")     shouldBe ""
  }

  it should "look up MIME for named formats" in {
    ImageVariants.mimeForFormat("webp", "png") shouldBe "image/webp"
    ImageVariants.mimeForFormat("avif", "jpg") shouldBe "image/avif"
  }
}
