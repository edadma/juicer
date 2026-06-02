import xerial.sbt.Sonatype.sonatypeCentralHost

ThisBuild / licenses               := Seq("ISC" -> url("https://opensource.org/licenses/ISC"))
ThisBuild / versionScheme          := Some("semver-spec")
ThisBuild / evictionErrorLevel     := Level.Warn
ThisBuild / scalaVersion           := "3.8.3"
ThisBuild / organization           := "io.github.edadma"
ThisBuild / organizationName       := "edadma"
ThisBuild / organizationHomepage   := Some(url("https://github.com/edadma"))
ThisBuild / version                := "0.2.0"
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

lazy val juicer = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("."))
  .settings(
    name := "juicer",
    scalacOptions ++=
      Seq(
        "-deprecation",
        "-feature",
        "-unchecked",
        "-language:postfixOps",
        "-language:implicitConversions",
        "-language:existentials",
        "-language:dynamics",
      ),
    libraryDependencies ++= Seq(
      "org.scalatest"          %%% "scalatest"                % "3.2.19" % "test",
      "com.github.scopt"       %%% "scopt"                    % "4.1.0",
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
    scalaJSUseMainModuleInitializer        := true,
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time"          % "2.6.0",
    libraryDependencies += "io.github.cquiroz" %%% "locales-minimal-en_us-db" % "1.5.4",
  )

lazy val root = project
  .in(file("."))
  .aggregate(juicer.jvm, juicer.js, juicer.native)
  .settings(
    name                := "juicer",
    publish / skip      := true,
    publishLocal / skip := true,
  )
