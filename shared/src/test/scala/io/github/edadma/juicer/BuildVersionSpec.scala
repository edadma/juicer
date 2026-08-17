package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The version the CLI prints is the version the build was cut at.
  *
  * This is here because 0.3.0 shipped a binary whose banner read `v0.2.0`:
  * the version was a literal in Main.scala, nothing in the release flow
  * reads the banner, and so the only thing that noticed was someone running
  * the installed tool. `BuildVersion` is generated from `ThisBuild / version`
  * (build.sbt) and `cliBanner` is the one place that formats it — put a
  * literal back into either and this fails at the next bump. */
class BuildVersionSpec extends AnyFlatSpec with Matchers {

  "BuildVersion" should "carry the build's semantic version" in {
    BuildVersion should fullyMatch regex """\d+\.\d+\.\d+(?:[-+].*)?"""
  }

  "cliBanner" should "name the tool and the build's version, and nothing else" in {
    cliBanner shouldBe s"Juicer Site Generator v$BuildVersion"
  }

  it should "not carry a version literal of its own" in {
    val versions = """\d+\.\d+\.\d+""".r.findAllIn(cliBanner).toList

    versions shouldBe List(BuildVersion)
  }
}
