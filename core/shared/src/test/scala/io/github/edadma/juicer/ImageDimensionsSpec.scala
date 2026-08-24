package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Cross-target tests for the image-header dimension parser.
  *
  * Each case feeds the parser a minimal-but-valid header (the dimensions
  * are the part we care about; the pixel payload after the header can
  * be empty for header-only parsing). End-to-end coverage via the
  * `imageDims` template builtin lives in [[ImageDimsBuiltinSpec]].
  */
class ImageDimensionsSpec extends AnyFlatSpec with Matchers {

  "ImageDimensions.fromBytes" should "read a PNG header" in {
    // 8-byte PNG signature + IHDR chunk: length(4 BE)=13, "IHDR",
    // width(4 BE), height(4 BE), then 5 unused bytes we still write.
    val png: Array[Byte] = Array[Int](
      0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, // signature
      0x00, 0x00, 0x00, 0x0d,                          // IHDR length
      'I'.toInt, 'H'.toInt, 'D'.toInt, 'R'.toInt,
      0x00, 0x00, 0x03, 0x20,                          // width  = 800
      0x00, 0x00, 0x02, 0x58,                          // height = 600
      0x08, 0x02, 0x00, 0x00, 0x00,                    // depth/colortype/...
    ).map(_.toByte)

    ImageDimensions.fromBytes(png) shouldBe Some(ImageDimensions.Dims(800, 600))
  }

  it should "read a GIF89a header" in {
    val gif: Array[Byte] = Array[Int](
      'G'.toInt, 'I'.toInt, 'F'.toInt, '8'.toInt, '9'.toInt, 'a'.toInt,
      0x20, 0x03,                  // width  = 800 little-endian
      0x58, 0x02,                  // height = 600 little-endian
      0x00, 0x00, 0x00, 0x00,      // packed/bgcolor/aspect
    ).map(_.toByte)

    ImageDimensions.fromBytes(gif) shouldBe Some(ImageDimensions.Dims(800, 600))
  }

  it should "read a baseline JPEG SOF0 header" in {
    // SOI + minimal-length APP0 (length 2, zero payload — the length
    // field's count includes itself) + SOF0 with precision/h/w.
    val jpeg: Array[Byte] = Array[Int](
      0xff, 0xd8,                       // SOI
      0xff, 0xe0, 0x00, 0x02,           // APP0 marker + length 2 (no body)
      0xff, 0xc0,                       // SOF0
      0x00, 0x0b,                       // length 11
      0x08,                             // precision
      0x02, 0x58,                       // height = 600
      0x03, 0x20,                       // width  = 800
      0x01, 0x01, 0x11, 0x00,           // 1 component
    ).map(_.toByte)

    ImageDimensions.fromBytes(jpeg) shouldBe Some(ImageDimensions.Dims(800, 600))
  }

  it should "read a WebP VP8X header" in {
    // RIFF container, then VP8X chunk: 8-byte chunk header (FourCC +
    // LE-32 size), then payload = flags(1) + reserved(3) + width-1
    // (3 bytes LE) + height-1 (3 bytes LE) = 10 bytes total.
    val webp: Array[Byte] = Array[Int](
      'R'.toInt, 'I'.toInt, 'F'.toInt, 'F'.toInt,   // [0..3]   RIFF
      0x1e, 0x00, 0x00, 0x00,                       // [4..7]   file size (placeholder)
      'W'.toInt, 'E'.toInt, 'B'.toInt, 'P'.toInt,   // [8..11]  WEBP
      'V'.toInt, 'P'.toInt, '8'.toInt, 'X'.toInt,   // [12..15] chunk id
      0x0a, 0x00, 0x00, 0x00,                       // [16..19] chunk size = 10
      0x00,                                         // [20]     flags
      0x00, 0x00, 0x00,                             // [21..23] reserved
      0x1f, 0x03, 0x00,                             // [24..26] width-1  = 799 (LE 24-bit)
      0x57, 0x02, 0x00,                             // [27..29] height-1 = 599
    ).map(_.toByte)

    ImageDimensions.fromBytes(webp) shouldBe Some(ImageDimensions.Dims(800, 600))
  }

  it should "return None for an unrecognized blob" in {
    val junk = "not an image at all, just text".getBytes("ASCII")
    ImageDimensions.fromBytes(junk) shouldBe None
  }

  it should "return None for an empty / too-short blob" in {
    ImageDimensions.fromBytes(Array.emptyByteArray) shouldBe None
    ImageDimensions.fromBytes(Array[Byte](0x89.toByte, 'P', 'N')) shouldBe None
  }
}
