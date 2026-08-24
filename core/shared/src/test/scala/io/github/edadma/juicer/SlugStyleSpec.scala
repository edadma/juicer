package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** `slugStyle = "github"` — the auto heading id algorithm GitHub uses, for
  * sites whose Markdown is also read in a repository.
  *
  * The case that forced it is generated API reference: `### starts_with` gets
  * the id `starts-with` under the markdown library's default and `starts_with`
  * on GitHub, so a link written for one rendering is dead in the other. Every
  * assertion below pins a difference between the two algorithms rather than
  * merely that ids exist.
  */
class SlugStyleSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  private def siteWith(slugStyle: Option[String], body: String): Unit = {
    val style = slugStyle.map(s => s"slugStyle = \"$s\"\n").getOrElse("")

    writeAt("site.toml", s"title = \"S\"\nbaseURL = \"http://x\"\nheadingShift = 0\n$style")
    writeAt("content/_index.md", s"---\ntitle: T\n---\n\n$body\n")
    writeAt("layouts/_default/folder.html", "{{ .content }}")
    writeAt("layouts/_default/file.html", "x")
    build()
  }

  "the default slug style" should "collapse an underscore to a hyphen, as it always has" in {
    siteWith(None, "## starts_with")

    out("index.html") should include("""id="starts-with"""")
  }

  "slugStyle github" should "keep an underscore, which is the whole reason it exists" in {
    siteWith(Some("github"), "## starts_with")

    val html = out("index.html")

    html should include("""id="starts_with"""")
    html should not include """id="starts-with""""
  }

  it should "drop punctuation without leaving a hyphen behind" in {
    // The default answers `buf-t`; GitHub answers `buft`. A generated page full
    // of `Buf[T]`-shaped headings is where this is felt.
    siteWith(Some("github"), "## Buf[T]")

    out("index.html") should include("""id="buft"""")
  }

  it should "drop a dot rather than turn it into a separator" in {
    siteWith(Some("github"), "## sysl.text")

    val html = out("index.html")

    html should include("""id="sysltext"""")
    html should not include """id="sysl-text""""
  }

  it should "lowercase, and turn a space into a hyphen" in {
    siteWith(Some("github"), "## Reading These Pages")

    out("index.html") should include("""id="reading-these-pages"""")
  }

  it should "keep a hyphen the author wrote" in {
    siteWith(Some("github"), "## with_capacity-ish")

    out("index.html") should include("""id="with_capacity-ish"""")
  }

  it should "make a same-page link written for GitHub resolve here too" in {
    // The end-to-end point: an index of links at the top of a generated page,
    // written once, has to land on the headings in both renderings.
    siteWith(
      Some("github"),
      """## Index
        |
        |[`split_once`](#split_once)
        |
        |## split_once
        |
        |Splits at the first separator.""".stripMargin,
    )

    val html = out("index.html")

    html should include("""href="#split_once"""")
    html should include("""id="split_once"""")
  }

  it should "leave an unrecognised style on the default rather than dropping ids" in {
    siteWith(Some("klingon"), "## starts_with")

    out("index.html") should include("""id="starts-with"""")
  }

  "githubSlugify" should "match GitHub's non-collapsing behaviour on a double space" in {
    // Deliberately NOT tidied — GitHub really does answer `a--b`, and agreeing
    // with it is the point of the function.
    githubSlugify("a  b") shouldBe "a--b"
  }

  it should "answer an empty string for a heading that is entirely punctuation" in {
    githubSlugify("!!!") shouldBe ""
  }
}
