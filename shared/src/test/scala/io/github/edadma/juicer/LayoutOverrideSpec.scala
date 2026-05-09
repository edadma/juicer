package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for frontmatter layout overrides — extracted from
  * JuicerBuildSpec for readability.
  */
class LayoutOverrideSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "juicer layout: frontmatter" should "respect frontmatter layout override on a non-section page" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/post.md",
      """---
        |title: Special
        |layout: longform
        |---
        |Body
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html",     "DEFAULT={{ .page.title }}")
    writeAt("layouts/_default/longform.html", "LONG={{ .page.title }}")

    build()

    val post = out("post/index.html")
    post should include("LONG=Special")
    post should not include "DEFAULT="
  }

  it should "respect frontmatter layout override on a section index" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      """---
        |title: Front
        |layout: home
        |---
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "DEFAULT")
    writeAt("layouts/_default/file.html",   "x")
    writeAt("layouts/_default/home.html",   "HOME-{{ .page.title }}")

    build()

    val home = out("index.html")
    home should include("HOME-Front")
    home should not include "DEFAULT"
  }

  it should "fall back to the default layout when the frontmatter layout is missing" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/post.md",
      """---
        |title: Plain
        |---
        |Body
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html",   "FILE={{ .page.title }}")

    build()

    out("post/index.html") should include("FILE=Plain")
  }
}
