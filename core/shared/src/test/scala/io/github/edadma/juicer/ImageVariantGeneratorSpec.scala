package io.github.edadma.juicer

import io.github.edadma.path.Path
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.compiletime.uninitialized
import scala.util.Random

/** Tests the cross-platform parts of [[ImageVariantGenerator]] — the
  * passthrough behaviour when the encoder is unavailable, the cache
  * memoization, and the on-disk variant emission when a stub backend
  * pretends to encode by just touching the destination file.
  *
  * No `magick` invocation here — the JVM integration path is exercised
  * in JVM-only specs that gate on `magick -version` availability.
  */
class ImageVariantGeneratorSpec extends AnyFlatSpec with Matchers {

  private var src: Path = uninitialized
  private var dst: Path = uninitialized

  /** Minimal 800×600 PNG header — same fixture pattern as
    * `ImageDimsBuiltinSpec`. Bytes are not a renderable image but the
    * dimension reader stops at IHDR. */
  private val Png800x600: Array[Byte] = Array[Int](
    0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
    0x00, 0x00, 0x00, 0x0d,
    'I'.toInt, 'H'.toInt, 'D'.toInt, 'R'.toInt,
    0x00, 0x00, 0x03, 0x20,
    0x00, 0x00, 0x02, 0x58,
    0x08, 0x02, 0x00, 0x00, 0x00,
  ).map(_.toByte)

  private def freshWorkspace(): Unit = {
    val nonce = System.currentTimeMillis().toString + "-" + Random.nextInt(1_000_000)
    src = Path("/tmp") / s"juicer-imgvar-src-$nonce"
    dst = Path("/tmp") / s"juicer-imgvar-dst-$nonce"
    src.createDirectories()
    dst.createDirectories()
  }

  private def write(rel: String, bytes: Array[Byte], under: Path = null): Path = {
    val base = if (under == null) src else under
    val p    = rel.split('/').foldLeft(base)(_ / _)
    p.parent.foreach(_.createDirectories())
    p.writeBytes(bytes)
    p
  }

  /** A backend that "encodes" by writing a deterministic 4-byte
    * payload to dst. Lets the generator's on-disk cache check + URL
    * construction be exercised without a real image encoder. */
  private object StubBackend extends ImageEncoderBackend {
    var calls: List[(String, String, Int, String, Int)] = Nil
    val available: Boolean = true
    def encode(src: Path, dst: Path, width: Int, format: String, quality: Int): Either[String, Unit] = {
      calls = calls :+ (src.toString, dst.toString, width, format, quality)
      dst.writeBytes(Array[Byte](1, 2, 3, 4))
      Right(())
    }
  }

  "variantsFor" should "return passthrough only when feature is disabled" in {
    freshWorkspace()
    write("img/photo.png", Png800x600)
    val gen = new ImageVariantGenerator(
      config  = ImageVariants.Config.Disabled,
      backend = StubBackend,
      srcRoot = src,
      dstRoot = dst,
    )
    val vs = gen.variantsFor("/img/photo.png")
    vs.original       shouldBe "/img/photo.png"
    vs.originalWidth  shouldBe 800
    vs.originalHeight shouldBe 600
    vs.variants       shouldBe Nil
  }

  it should "return passthrough only when the backend is Unavailable" in {
    freshWorkspace()
    write("img/photo.png", Png800x600)
    val gen = new ImageVariantGenerator(
      config  = ImageVariants.Config.DefaultEnabled,
      backend = ImageEncoderBackend.Unavailable,
      srcRoot = src,
      dstRoot = dst,
    )
    val vs = gen.variantsFor("/img/photo.png")
    vs.original       shouldBe "/img/photo.png"
    vs.originalWidth  shouldBe 800
    vs.originalHeight shouldBe 600
    vs.variants       shouldBe Nil
  }

  it should "return passthrough only when the source is missing" in {
    freshWorkspace()
    val gen = new ImageVariantGenerator(
      config  = ImageVariants.Config.DefaultEnabled,
      backend = StubBackend,
      srcRoot = src,
      dstRoot = dst,
    )
    val vs = gen.variantsFor("/img/missing.png")
    vs.original  shouldBe "/img/missing.png"
    vs.variants  shouldBe Nil
  }

  it should "generate variants in (format, width) order with the stub backend" in {
    freshWorkspace()
    write("img/photo.png", Png800x600)
    val cfg = ImageVariants.Config(
      enabled  = true,
      widths   = List(320, 640),
      formats  = List("webp", "original"),
      quality  = 80,
      cacheDir = ".image-cache",
    )
    val gen = new ImageVariantGenerator(cfg, StubBackend, src, dst)
    val vs  = gen.variantsFor("/img/photo.png")
    // 320 and 640 are below the source's 800px; 800 itself is added
    // automatically so the largest variant is a 1:1 copy.
    val widths  = vs.variants.map(_.width).distinct
    widths shouldBe List(320, 640, 800)
    // webp comes before original in the variants list because the
    // generator orders by config.formats index.
    vs.variants.head.format shouldBe "webp"
    vs.variants.last.format shouldBe "original"
    // The 800px original is a 1:1 byte copy — the backend was NOT
    // invoked for that slot.
    StubBackend.calls.exists { case (_, _, w, f, _) => w == 800 && f == "original" } shouldBe false
  }

  it should "memoise per build — second call returns the same VariantSet" in {
    freshWorkspace()
    write("img/photo.png", Png800x600)
    val gen = new ImageVariantGenerator(
      config  = ImageVariants.Config.DefaultEnabled.copy(widths = List(320), formats = List("webp")),
      backend = StubBackend,
      srcRoot = src,
      dstRoot = dst,
    )
    StubBackend.calls = Nil
    val a = gen.variantsFor("/img/photo.png")
    val b = gen.variantsFor("/img/photo.png")
    (a eq b) shouldBe true
    // Backend called once per variant slot, not twice.
    StubBackend.calls.length should be > 0
    val firstRound = StubBackend.calls.length
    gen.variantsFor("/img/photo.png")
    StubBackend.calls.length shouldBe firstRound
  }

  "srcsetFor" should "build a comma-separated url + w descriptor list" in {
    freshWorkspace()
    write("img/photo.png", Png800x600)
    val cfg = ImageVariants.Config(
      enabled  = true,
      widths   = List(320, 640),
      formats  = List("webp"),
      quality  = 80,
      cacheDir = ".image-cache",
    )
    val gen     = new ImageVariantGenerator(cfg, StubBackend, src, dst)
    val srcset  = gen.srcsetFor("/img/photo.png", "webp")
    srcset should include("/.image-cache/photo-320w.")
    srcset should include(" 320w")
    srcset should include(" 640w")
    srcset.split(", ").length shouldBe 3 // 320, 640, 800
  }

  it should "return empty when the requested format isn't configured" in {
    freshWorkspace()
    write("img/photo.png", Png800x600)
    val cfg = ImageVariants.Config(
      enabled  = true,
      widths   = List(320),
      formats  = List("webp"),
      quality  = 80,
      cacheDir = ".image-cache",
    )
    val gen = new ImageVariantGenerator(cfg, StubBackend, src, dst)
    gen.srcsetFor("/img/photo.png", "avif") shouldBe ""
  }
}
