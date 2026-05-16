package io.github.edadma.juicer

import io.github.edadma.path.Path
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for page bundles — non-markdown siblings of `_index.md` (or any
  * `.md` file) co-located in a content directory get copied to the
  * section's outdir and exposed to templates via `.page.assets` /
  * `.section.assets`. Bare image paths from `imageVariants` / `srcset` /
  * `imageDims` also resolve against the page's bundle.
  *
  * The asset URL convention is `<section-permalink>/<filename>` — a
  * bundle at `content/iceland-2024/` with `skogafoss.jpg` is reachable
  * at `/iceland-2024/skogafoss.jpg`. Filenames are kept verbatim
  * (case, dots) — assets are NOT slugified.
  */
class BundlesSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  private def writeBytesAt(rel: String, bytes: Array[Byte]): Path = {
    val p = rel.split('/').foldLeft(src)(_ / _)
    p.parent.foreach(_.createDirectories())
    p.writeBytes(bytes)
    p
  }

  "page bundles" should "copy non-markdown siblings of a section to its outdir" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/iceland/_index.md", "---\ntitle: Iceland\n---\n\n.\n")
    writeAt("content/iceland/notes.md",  "---\ntitle: Notes\n---\n\n.\n")
    writeBytesAt("content/iceland/hero.jpg", Array[Byte](1, 2, 3, 4, 5))
    writeBytesAt("content/iceland/map.png",  Array[Byte](10, 20, 30))
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html",   "x")

    build()

    // Section outdir is dst/html/iceland (htmlDir = "html" by default)
    (dst / "html" / "iceland" / "hero.jpg").exists shouldBe true
    (dst / "html" / "iceland" / "hero.jpg").readBytes shouldBe Array[Byte](1, 2, 3, 4, 5)
    (dst / "html" / "iceland" / "map.png").exists shouldBe true
  }

  it should "expose .page.assets in the order they appear on disk (filename asc)" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/trip/_index.md", "---\ntitle: Trip\n---\n\n.\n")
    writeBytesAt("content/trip/zebra.jpg", Array[Byte](1))
    writeBytesAt("content/trip/alpha.jpg", Array[Byte](2))
    writeBytesAt("content/trip/mango.png", Array[Byte](3))
    writeAt(
      "layouts/_default/folder.html",
      "[{{ for a <- .page.assets }}{{ a.name }}={{ a.url }}={{ a.ext }};{{ end }}]",
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    val s = out("html/trip/index.html")
    s should include("[alpha.jpg=/trip/alpha.jpg=jpg;mango.png=/trip/mango.png=png;zebra.jpg=/trip/zebra.jpg=jpg;]")
  }

  it should "expose the parent section's assets to leaf pages in the same bundle" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/trip/_index.md", "---\ntitle: Trip\n---\n\n.\n")
    writeAt("content/trip/day1.md",   "---\ntitle: Day1\n---\n\n.\n")
    writeBytesAt("content/trip/hero.jpg", Array[Byte](1))
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html",   "{{ for a <- .page.assets }}{{ a.url }}{{ end }}")

    build()

    // Leaf page (URL /trip/day1/) still sees the section's bundle assets
    out("html/trip/day1/index.html") should include("/trip/hero.jpg")
  }

  it should "expose .section.assets matching .page.assets for the enclosing section" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/trip/_index.md", "---\ntitle: Trip\n---\n\n.\n")
    writeAt("content/trip/day1.md",   "---\ntitle: Day1\n---\n\n.\n")
    writeBytesAt("content/trip/hero.jpg", Array[Byte](1))
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/file.html",
      "section={{ for a <- .section.assets }}{{ a.url }}{{ end }}",
    )

    build()

    out("html/trip/day1/index.html") should include("section=/trip/hero.jpg")
  }

  it should "NOT copy assets from a directory that has no markdown files" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    // No .md in here — these files are orphans and should not be copied.
    writeBytesAt("content/loose/orphan.jpg", Array[Byte](1, 2, 3))
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html",   "x")

    build()

    (dst / "html" / "loose" / "orphan.jpg").exists shouldBe false
  }

  it should "NOT treat YAML / TOML / HTML files as bundle assets" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/iceland/_index.md", "---\ntitle: Iceland\n---\n\n.\n")
    writeAt("content/iceland/notes.yaml", "key: value\n")
    writeAt("content/iceland/data.toml",  "foo = \"bar\"\n")
    writeAt("content/iceland/page.html",  "<p>hi</p>")
    writeBytesAt("content/iceland/keep.jpg", Array[Byte](1))
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html",   "x")

    build()

    (dst / "html" / "iceland" / "keep.jpg").exists  shouldBe true
    (dst / "html" / "iceland" / "notes.yaml").exists shouldBe false
    (dst / "html" / "iceland" / "data.toml").exists  shouldBe false
    (dst / "html" / "iceland" / "page.html").exists  shouldBe false
  }

  it should "not leak ancestor-bundle assets into a sub-bundle" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/parent/_index.md", "---\ntitle: Parent\n---\n\n.\n")
    writeAt("content/parent/sub/_index.md", "---\ntitle: Sub\n---\n\n.\n")
    writeBytesAt("content/parent/parent-asset.jpg", Array[Byte](1))
    writeBytesAt("content/parent/sub/sub-asset.jpg", Array[Byte](2))
    writeAt(
      "layouts/_default/folder.html",
      "[{{ for a <- .page.assets }}{{ a.name }};{{ end }}]",
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    // The sub section sees only its own bundle's assets
    out("html/parent/sub/index.html") should include("[sub-asset.jpg;]")
    out("html/parent/sub/index.html") should not include "parent-asset.jpg"

    // The parent section sees only its own bundle's assets
    out("html/parent/index.html") should include("[parent-asset.jpg;]")
    out("html/parent/index.html") should not include "sub-asset.jpg"
  }

  it should "give root content its assets at the dst root, no html prefix" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/page.md",   "---\ntitle: Page\n---\n\n.\n")
    writeBytesAt("content/hero.jpg", Array[Byte](7, 8, 9))
    writeAt(
      "layouts/_default/folder.html",
      "[{{ for a <- .page.assets }}{{ a.url }};{{ end }}]",
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    // Root section -- asset goes to dst/hero.jpg, URL is /hero.jpg
    (dst / "hero.jpg").exists shouldBe true
    out("index.html") should include("[/hero.jpg;]")
  }

  // Minimal PNG header (800×600) — same fixture pattern as ImageVariantGeneratorSpec.
  // Bytes aren't a renderable image but ImageDimensions stops at IHDR.
  private val Png800x600: Array[Byte] = Array[Int](
    0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
    0x00, 0x00, 0x00, 0x0d,
    'I'.toInt, 'H'.toInt, 'D'.toInt, 'R'.toInt,
    0x00, 0x00, 0x03, 0x20,
    0x00, 0x00, 0x02, 0x58,
    0x08, 0x02, 0x00, 0x00, 0x00,
  ).map(_.toByte)

  it should "resolve bare imageVariants paths against the page's bundle dir" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/trip/_index.md", "---\ntitle: Trip\n---\n\n.\n")
    writeBytesAt("content/trip/photo.png", Png800x600)
    writeAt(
      "layouts/_default/folder.html",
      "w={{ (imageVariants 'photo.png').originalWidth }}",
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    // Bundle-relative resolution lets `imageVariants 'photo.png'`
    // find the source PNG and read its dimensions; if bundle
    // resolution were broken the dst-root fallback would miss
    // (file isn't at dst/photo.png) and width would be 0.
    out("html/trip/index.html") should include("w=800")
  }

  it should "resolve bare imageDims paths against the page's bundle dir" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/trip/_index.md", "---\ntitle: Trip\n---\n\n.\n")
    writeBytesAt("content/trip/photo.png", Png800x600)
    writeAt(
      "layouts/_default/folder.html",
      "d={{ (imageDims 'photo.png').width }}x{{ (imageDims 'photo.png').height }}",
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    out("html/trip/index.html") should include("d=800x600")
  }

  it should "expose an empty .page.assets when the section has no bundle assets" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n\n.\n")
    writeAt("content/lone.md",   "---\ntitle: Lone\n---\n\n.\n")
    writeAt(
      "layouts/_default/folder.html",
      "[{{ for a <- .page.assets }}X{{ end }}]",
    )
    writeAt("layouts/_default/file.html", "x")

    build()

    // An empty assets list produces no body inside the for-loop.
    out("index.html") should include("[]")
  }
}
