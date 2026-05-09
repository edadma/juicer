package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for the slug helper (used by tags / categories / future series +
  * permalinks) — extracted from JuicerBuildSpec for readability.
  */
class SlugSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "juicer slugify" should "lowercase ASCII alphanumerics and dash-separate non-alnum runs" in {
    slugify("Hello, World!") shouldBe "hello-world"
  }

  it should "fold common Latin diacritics to ASCII" in {
    slugify("Café au lait")    shouldBe "cafe-au-lait"
    slugify("Naïve approach")  shouldBe "naive-approach"
    slugify("Über cool")       shouldBe "uber-cool"
    slugify("ßeta")            shouldBe "sseta"
  }

  it should "collapse runs of symbols and trim leading/trailing dashes" in {
    slugify("  --foo  bar--  ") shouldBe "foo-bar"
    slugify("___")              shouldBe "-"
  }

  it should "keep ASCII digits" in {
    slugify("Phase 1.4")        shouldBe "phase-1-4"
  }
}
