package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Syntax highlighting via the `highlighter` lib (Stage 4 of the
  * juicerblog polish — engine-side wiring; theme palette is theme-side).
  *
  * Extracted from JuicerBuildSpec for readability.
  */
class HighlighterSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "juicer syntax highlighting" should "highlight fenced code blocks when grammars/<lang>.tmLanguage.json is present" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "grammars/scala.tmLanguage.json",
      """{
        |  "scopeName": "source.scala",
        |  "patterns": [
        |    { "match": "\\b(val|def|class)\\b",  "name": "keyword.control.scala" },
        |    { "match": "\\b\\d+\\b",              "name": "constant.numeric.scala" },
        |    { "begin": "\"", "end": "\"",         "name": "string.quoted.double.scala" }
        |  ]
        |}""".stripMargin,
    )
    writeAt(
      "content/post.md",
      """---
        |title: Has Scala
        |---
        |
        |```scala
        |val x = 42
        |```
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "{{ .content }}")

    build()

    val post = out("post/index.html")
    post should include("class=\"hl-keyword\"")
    post should include("class=\"hl-number\"")
    post should include("class=\"language-scala\"")
  }

  it should "leave plain <pre><code> alone when the language has no grammar" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "grammars/scala.tmLanguage.json",
      """{
        |  "scopeName": "source.scala",
        |  "patterns": [
        |    { "match": "\\b(val)\\b", "name": "keyword.control.scala" }
        |  ]
        |}""".stripMargin,
    )
    writeAt(
      "content/post.md",
      """---
        |title: Mixed
        |---
        |
        |```python
        |print(42)
        |```
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "{{ .content }}")

    build()

    val post = out("post/index.html")
    post should not include "hl-"
    post should include("language-python")
    post should include("print(42)")
  }

  it should "skip highlighting entirely when no grammars/ directory exists" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/post.md",
      """---
        |title: No grammars
        |---
        |
        |```scala
        |val x = 42
        |```
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "{{ .content }}")

    build()

    val post = out("post/index.html")
    post should not include "hl-"
    post should include("language-scala")
    post should include("val x = 42")
  }

  it should "skip a malformed grammar without failing the whole build" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    // Two grammars: one valid, one corrupt JSON. The build must still
    // succeed and the valid one must still highlight.
    writeAt(
      "grammars/scala.tmLanguage.json",
      """{
        |  "scopeName": "source.scala",
        |  "patterns": [
        |    { "match": "\\b(val)\\b", "name": "keyword.control.scala" }
        |  ]
        |}""".stripMargin,
    )
    writeAt("grammars/broken.tmLanguage.json", "{ this is not json")
    writeAt(
      "content/post.md",
      """---
        |title: Resilient
        |---
        |
        |```scala
        |val x = 42
        |```
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "{{ .content }}")

    build()

    val post = out("post/index.html")
    post should include("class=\"hl-keyword\"")
  }

  it should "colour a string's quotes as the string, not as punctuation" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "grammars/scala.tmLanguage.json",
      """{
        |  "scopeName": "source.scala",
        |  "patterns": [
        |    { "match": "\\b(val)\\b", "name": "keyword.control.scala" },
        |    { "begin": "\"", "end": "\"", "name": "string.quoted.double.scala",
        |      "beginCaptures": { "0": { "name": "punctuation.definition.string.begin.scala" } },
        |      "endCaptures":   { "0": { "name": "punctuation.definition.string.end.scala" } } }
        |  ]
        |}""".stripMargin,
    )
    writeAt(
      "content/post.md",
      """---
        |title: Quotes
        |---
        |
        |```scala
        |val s = "hi"
        |```
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "{{ .content }}")

    build()

    val post = out("post/index.html")
    post should include("hl-string")
    post should not include "hl-punctuation"
  }

  it should "read \\w as Unicode, so an accented identifier is one token" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "grammars/scala.tmLanguage.json",
      """{
        |  "scopeName": "source.scala",
        |  "patterns": [
        |    { "match": "\\w+", "name": "keyword.control.scala" }
        |  ]
        |}""".stripMargin,
    )
    writeAt(
      "content/post.md",
      """---
        |title: Unicode
        |---
        |
        |```scala
        |naïve
        |```
        |""".stripMargin,
    )
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/file.html", "{{ .content }}")

    build()

    out("post/index.html") should include("<span class=\"hl-keyword\">naïve</span>")
  }
}
