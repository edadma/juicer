import xerial.sbt.Sonatype.sonatypeCentralHost

ThisBuild / licenses               := Seq("ISC" -> url("https://opensource.org/licenses/ISC"))
ThisBuild / versionScheme          := Some("semver-spec")
ThisBuild / evictionErrorLevel     := Level.Warn
ThisBuild / scalaVersion           := "3.8.3"
ThisBuild / organization           := "io.github.edadma"
ThisBuild / organizationName       := "edadma"
ThisBuild / organizationHomepage   := Some(url("https://github.com/edadma"))
ThisBuild / version                := "0.4.1"
ThisBuild / description            := "A cross-platform Scala 3 static site generator (Hugo-style) using markdown + squiggly templates"
ThisBuild / sonatypeCredentialHost := sonatypeCentralHost

ThisBuild / publishConfiguration := publishConfiguration.value.withOverwrite(true).withChecksums(Vector.empty)
ThisBuild / resolvers += Resolver.mavenLocal
ThisBuild / resolvers += Resolver.sonatypeCentralSnapshots
ThisBuild / resolvers += Resolver.sonatypeCentralRepo("releases")

ThisBuild / sonatypeProfileName := "io.github.edadma"

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/edadma/juicer"),
    "scm:git@github.com:edadma/juicer.git",
  ),
)
ThisBuild / developers := List(
  Developer(
    id = "edadma",
    name = "Edward A. Maxedon, Sr.",
    email = "edadma@gmail.com",
    url = url("https://github.com/edadma"),
  ),
)

ThisBuild / homepage := Some(url("https://github.com/edadma/juicer"))

ThisBuild / publishTo := sonatypePublishToBundle.value

lazy val commonScalacOptions = Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-language:postfixOps",
  "-language:implicitConversions",
  "-language:existentials",
  "-language:dynamics",
)

// ===== juicer-core: the generator as a library =====
//
// Everything a program embedding juicer needs — the `SiteBuild` pipeline, the source walk, the
// asset pipeline, the theme commands and `serve`. The server is here on purpose rather than in the
// CLI: previewing a site is a thing an embedder wants, and it is `file://`'s opaque origin that
// makes it necessary (a `fetch` of `search.json` is refused there, so search silently fails).
//
// What is NOT here is the command line: scopt, the option parser and the `@main`. That is the whole
// seam, and it is why this is the artifact published to Central while `juicer` itself is not — the
// CLI is consumed as a Homebrew binary, and nobody depends on a site generator from a build file.
lazy val juicerCore = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("core"))
  .settings(
    name := "juicer-core",
    scalacOptions ++= commonScalacOptions,
    libraryDependencies ++= Seq(
      "org.scalatest"          %%% "scalatest"                % "3.2.19" % "test",
      "com.lihaoyi"            %%% "pprint"                   % "0.9.0"  % "test",
      "org.scala-lang.modules" %%% "scala-parser-combinators" % "2.4.0",
      "org.virtuslab"          %%% "scala-yaml"               % "0.3.1",
      "io.github.edadma"       %%% "char_reader"              % "0.1.27",
      "io.github.edadma"       %%% "cross_platform"           % "0.1.6",
      "io.github.edadma"       %%% "path"                     % "0.0.5",
      "io.github.edadma"       %%% "toml"                     % "0.1.0",
      "io.github.edadma"       %%% "markdown"                 % "0.4.6",
      "io.github.edadma"       %%% "squiggly"                 % "0.3.0",
      "io.github.edadma"       %%% "emoji"                    % "0.1.2",
      "io.github.edadma"       %%% "highlighter"              % "0.0.9",
      "io.github.edadma"       %%% "microserve"               % "0.5.3",
    ),
    publishMavenStyle      := true,
    Test / publishArtifact := false,
    // The version the CLI banner prints comes from `ThisBuild / version`, not from a literal in
    // Main.scala. It was a literal until 0.3.0, whose binary announced itself as v0.2.0 — nothing in
    // the release flow reads the banner, so a stale one survives every check and is found by
    // whoever installs the release. Generating it leaves one place to bump.
    //
    // It is generated HERE rather than beside Main.scala because `cliBanner` — the one place that
    // formats it — lives in this module's package.scala, and BuildVersionSpec pins it.
    Compile / sourceGenerators += Def.task {
      val f = (Compile / sourceManaged).value / "io" / "github" / "edadma" / "juicer" / "BuildVersion.scala"

      IO.write(
        f,
        s"""|package io.github.edadma.juicer
            |
            |/** Generated from `ThisBuild / version` in build.sbt. Do not edit. */
            |val BuildVersion: String = "${version.value}"
            |""".stripMargin,
      )
      Seq(f)
    }.taskValue,
  )
  .jvmSettings(
    libraryDependencies += "org.scala-js" %% "scalajs-stubs" % "1.1.0" % "provided",
  )
  .nativeSettings(
    libraryDependencies += "org.scala-js"       %% "scalajs-stubs"            % "1.1.0" % "provided",
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time"          % "2.6.0",
    libraryDependencies += "io.github.cquiroz" %%% "locales-minimal-en_us-db" % "1.5.4",
  )
  .jsSettings(
    jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv(),
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
    scalaJSLinkerConfig ~= { _.withSourceMap(false) },
    Test / scalaJSUseMainModuleInitializer := false,
    Test / scalaJSUseTestModuleInitializer := true,
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time"          % "2.6.0",
    libraryDependencies += "io.github.cquiroz" %%% "locales-minimal-en_us-db" % "1.5.4",
  )

// ===== juicer: the command line, and nothing else =====
//
// One file — the scopt parser and the `@main`. It is deliberately NOT published: the CLI ships as a
// Homebrew binary, and `juicer_3` on Central would be a coordinate nobody should depend on.
lazy val juicer = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("cli"))
  .dependsOn(juicerCore)
  .settings(
    name := "juicer",
    scalacOptions ++= commonScalacOptions,
    libraryDependencies ++= Seq(
      "org.scalatest"    %%% "scalatest" % "3.2.19" % "test",
      "com.github.scopt" %%% "scopt"     % "4.1.0",
    ),
    publish / skip      := true,
    publishLocal / skip := true,
  )
  .jvmSettings(
    libraryDependencies += "org.scala-js" %% "scalajs-stubs" % "1.1.0" % "provided",
  )
  .nativeSettings(
    libraryDependencies += "org.scala-js" %% "scalajs-stubs" % "1.1.0" % "provided",
  )
  .jsSettings(
    jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv(),
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
    scalaJSLinkerConfig ~= { _.withSourceMap(false) },
    Test / scalaJSUseMainModuleInitializer := false,
    Test / scalaJSUseTestModuleInitializer := true,
    scalaJSUseMainModuleInitializer        := true,
  )

lazy val root = project
  .in(file("."))
  .aggregate(juicerCore.jvm, juicerCore.js, juicerCore.native, juicer.jvm, juicer.js, juicer.native)
  .settings(
    name                := "juicer",
    publish / skip      := true,
    publishLocal / skip := true,
  )
