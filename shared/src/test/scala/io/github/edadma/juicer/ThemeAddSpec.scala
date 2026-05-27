package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for `juicer theme add` — extracted from JuicerBuildSpec for readability. */
class ThemeAddSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "juicer theme add" should "derive a theme name from a typical HTTPS git URL" in {
    ThemeCommands.deriveThemeName("https://github.com/edadma/juicer-theme-foo.git") shouldBe "juicer-theme-foo"
  }

  it should "derive a name from an SSH-style git URL" in {
    ThemeCommands.deriveThemeName("git@github.com:edadma/juicer-theme-foo.git") shouldBe "juicer-theme-foo"
  }

  it should "leave a name without .git suffix alone" in {
    ThemeCommands.deriveThemeName("https://example.com/themes/minty") shouldBe "minty"
  }
}
