package io.github.edadma.juicer

import io.github.edadma.path.Path

/** Pure-Scala image header dimension reader.
  *
  * Parses just enough of PNG / JPEG / GIF / WebP headers to surface
  * pixel `width` and `height` to templates so they can emit `<img>`
  * attributes that prevent cumulative-layout-shift. Implemented in pure
  * Scala against a byte buffer so all three targets (JVM, JS, Native)
  * share the same code path — no `javax.imageio`, no FFI.
  *
  * Format references:
  *   PNG  — RFC 2083 §11.2.2 (IHDR is the first chunk after the
  *          8-byte signature; width and height are 4-byte BE ints
  *          at file offsets 16 and 20).
  *   JPEG — JFIF + JPEG SOF markers. Scan segments until an SOF
  *          marker (FFC0..FFCF excluding C4/C8/CC); the payload
  *          starts with 1 byte precision, then height (2B BE)
  *          and width (2B BE).
  *   GIF  — Logical Screen Descriptor at offset 6: width (2B LE),
  *          height (2B LE).
  *   WebP — RIFF container with VP8 / VP8L / VP8X chunk that carries
  *          the dimensions.
  */
object ImageDimensions {

  /** Decoded width × height. */
  final case class Dims(width: Int, height: Int)

  /** Read the first ~64 KB of `path` and try to decode dimensions from
    * its header. Returns `None` when the file can't be read or the
    * format is not recognized — never throws.
    */
  def fromFile(path: Path): Option[Dims] =
    try {
      if (!path.exists) None
      else {
        val all = path.readBytes
        if (all.length < 12) None else fromBytes(all)
      }
    } catch {
      case _: Throwable => None
    }

  /** Try every recognized format in turn. Order matters only for the
    * cheap magic-number sniffing — the parsers themselves are picky.
    */
  def fromBytes(buf: Array[Byte]): Option[Dims] =
    parsePng(buf)
      .orElse(parseGif(buf))
      .orElse(parseWebp(buf))
      .orElse(parseJpeg(buf))

  // ---- PNG ----------------------------------------------------------

  private val PngSig: Array[Byte] =
    Array(0x89.toByte, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n')

  private def parsePng(buf: Array[Byte]): Option[Dims] = {
    if (buf.length < 24) return None
    var i = 0
    while (i < PngSig.length) {
      if (buf(i) != PngSig(i)) return None
      i += 1
    }
    // IHDR width @ 16, height @ 20 (both 4-byte big-endian).
    Some(Dims(beInt32(buf, 16), beInt32(buf, 20)))
  }

  // ---- GIF ----------------------------------------------------------

  private def parseGif(buf: Array[Byte]): Option[Dims] = {
    if (buf.length < 10) return None
    if (buf(0) != 'G' || buf(1) != 'I' || buf(2) != 'F') return None
    if (buf(3) != '8' || (buf(4) != '7' && buf(4) != '9') || buf(5) != 'a') return None
    Some(Dims(leUInt16(buf, 6), leUInt16(buf, 8)))
  }

  // ---- WebP ---------------------------------------------------------
  //
  // RIFF<4B>....WEBP<chunkId 4B><chunkSize 4B LE>...
  // chunkId is one of "VP8 ", "VP8L", "VP8X".

  private def parseWebp(buf: Array[Byte]): Option[Dims] = {
    if (buf.length < 30) return None
    if (buf(0) != 'R' || buf(1) != 'I' || buf(2) != 'F' || buf(3) != 'F') return None
    if (buf(8) != 'W' || buf(9) != 'E' || buf(10) != 'B' || buf(11) != 'P') return None

    val chunk = new String(buf, 12, 4, "ASCII")
    chunk match {
      case "VP8 " =>
        // Lossy VP8 frame. The chunk payload starts at offset 20.
        // First 3 bytes are the frame tag; next 3 must be the start
        // code (0x9d 0x01 0x2a); then width (14 low bits) and height
        // (14 low bits) as little-endian 16-bit values.
        if (buf.length < 30) None
        else if (buf(23) != 0x9d.toByte || buf(24) != 0x01.toByte || buf(25) != 0x2a.toByte) None
        else {
          val w = leUInt16(buf, 26) & 0x3fff
          val h = leUInt16(buf, 28) & 0x3fff
          Some(Dims(w, h))
        }
      case "VP8L" =>
        // Lossless. Chunk payload at offset 20; first byte must be
        // 0x2f; next 4 bytes pack width-1 (14 bits), height-1 (14
        // bits), alpha (1 bit), version (3 bits).
        if (buf.length < 25) None
        else if (buf(20) != 0x2f.toByte) None
        else {
          val b0 = buf(21) & 0xff
          val b1 = buf(22) & 0xff
          val b2 = buf(23) & 0xff
          val b3 = buf(24) & 0xff
          val wMinus = b0 | ((b1 & 0x3f) << 8)
          val hMinus = (b1 >>> 6) | (b2 << 2) | ((b3 & 0x0f) << 10)
          Some(Dims(wMinus + 1, hMinus + 1))
        }
      case "VP8X" =>
        // Extended. Width-1 and Height-1 as 24-bit little-endian
        // values at offsets 24 and 27 respectively.
        if (buf.length < 30) None
        else {
          val w = leUInt24(buf, 24) + 1
          val h = leUInt24(buf, 27) + 1
          Some(Dims(w, h))
        }
      case _ => None
    }
  }

  // ---- JPEG ---------------------------------------------------------
  //
  // Walk segments until we hit a Start-Of-Frame marker; the SOF
  // payload starts with one byte of sample precision, then height
  // (2-byte BE), then width (2-byte BE). Skip every non-SOF segment
  // by reading its 2-byte BE length and advancing.

  private def parseJpeg(buf: Array[Byte]): Option[Dims] = {
    if (buf.length < 4) return None
    if (buf(0) != 0xff.toByte || buf(1) != 0xd8.toByte) return None
    var i = 2
    while (i < buf.length - 9) {
      if ((buf(i) & 0xff) != 0xff) return None
      // Skip fill bytes (consecutive 0xff before the actual marker).
      while (i < buf.length && (buf(i) & 0xff) == 0xff) i += 1
      if (i >= buf.length) return None
      val marker = buf(i) & 0xff
      i += 1
      marker match {
        case 0xd9 | 0xda                      => return None // EOI or SOS reached before SOF
        case 0x00 | 0x01 | 0xd0 | 0xd1 | 0xd2 // RST and TEM markers
            | 0xd3 | 0xd4 | 0xd5 | 0xd6 | 0xd7 | 0xd8 =>
          // Standalone markers — no length, just keep scanning for the
          // next 0xff.
          ()
        case m if isJpegSof(m) =>
          if (i + 7 >= buf.length) return None
          // skip 2-byte length, 1-byte precision; then height/width
          val h = beUInt16(buf, i + 3)
          val w = beUInt16(buf, i + 5)
          return Some(Dims(w, h))
        case _ =>
          if (i + 1 >= buf.length) return None
          val segLen = beUInt16(buf, i)
          if (segLen < 2) return None
          i += segLen
      }
    }
    None
  }

  private def isJpegSof(marker: Int): Boolean =
    (marker >= 0xc0 && marker <= 0xcf) &&
      marker != 0xc4 && marker != 0xc8 && marker != 0xcc

  // ---- byte helpers --------------------------------------------------

  private def beInt32(buf: Array[Byte], off: Int): Int =
    ((buf(off) & 0xff) << 24) |
      ((buf(off + 1) & 0xff) << 16) |
      ((buf(off + 2) & 0xff) << 8) |
      (buf(off + 3) & 0xff)

  private def beUInt16(buf: Array[Byte], off: Int): Int =
    ((buf(off) & 0xff) << 8) | (buf(off + 1) & 0xff)

  private def leUInt16(buf: Array[Byte], off: Int): Int =
    (buf(off) & 0xff) | ((buf(off + 1) & 0xff) << 8)

  private def leUInt24(buf: Array[Byte], off: Int): Int =
    (buf(off) & 0xff) |
      ((buf(off + 1) & 0xff) << 8) |
      ((buf(off + 2) & 0xff) << 16)
}
