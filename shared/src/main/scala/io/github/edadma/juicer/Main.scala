package io.github.edadma.juicer

import io.github.edadma.cross_platform.processArgs
import io.github.edadma.path.Path
import scopt.OParser

@main def run(args: String*): Unit =

  // Update the active BuildCommand in-place, preserving any fields already
  // set by previous flags. The naive `c.copy(cmd = Some(BuildCommand(src = …)))`
  // approach drops the dst that an earlier `-d` already wrote.
  def updateBuild(c: Args, f: BuildCommand => BuildCommand): Args =
    val cur = c.cmd.collect { case b: BuildCommand => b }.getOrElse(BuildCommand())
    c.copy(cmd = Some(f(cur)))

  def updateServe(c: Args, f: ServeCommand => ServeCommand): Args =
    val cur = c.cmd.collect { case s: ServeCommand => s }.getOrElse(ServeCommand())
    c.copy(cmd = Some(f(cur)))

  def updateConfig(c: Args, f: ConfigCommand => ConfigCommand): Args =
    val cur = c.cmd.collect { case s: ConfigCommand => s }.getOrElse(ConfigCommand())
    c.copy(cmd = Some(f(cur)))

  val builder = OParser.builder[Args]
  val parser =
    import builder._

    val BOLD         = Console.BOLD
    var firstSection = true

    def section(name: String) = {
      val res =
        s"${if (!firstSection) "\n" else ""}$BOLD━━━━━ $name ${"━" * (20 - name.length)}${Console.RESET}"
      firstSection = false
      res
    }

    OParser.sequence(
      programName("juicer"),
      head("Juicer Site Generator", "v0.1.0"),
      note(section("General Options")),
      opt[String]('b', "baseurl")
        .valueName("<URL>")
        .action((b, c) => c.copy(baseurl = Some(b)))
        .text("base site URL"),
      opt[String]('c', "config")
        .valueName("<name>")
        .action((b, c) => c.copy(config = b))
        .text("base site configuration (default is 'standard')"),
      help('h', "help").text("prints this usage text"),
      opt[Unit]('v', "verbose")
        .action((_, c) => c.copy(verbose = true))
        .text("verbose output"),
      version("version").text("prints the version"),
      note(section("Commands")),
      cmd("build")
        .action((_, c) => c.copy(cmd = Some(BuildCommand())))
        .text("  Build the site")
        .children(
          opt[String]('d', "dest")
            .valueName("<path>")
            .action((o, c) => updateBuild(c, _.copy(dst = Path(o))))
            .text("destination directory path"),
          opt[String]('s', "source")
            .valueName("<path>")
            .action((i, c) => updateBuild(c, _.copy(src = Path(i))))
            .text("site sources directory path"),
          opt[Unit]('D', "drafts")
            .action((_, c) => updateBuild(c, _.copy(drafts = true)))
            .text("include draft pages (frontmatter `draft: true`)"),
        ),
      cmd("config")
        .action((_, c) => c.copy(cmd = Some(ConfigCommand())))
        .text("  Show build configuration")
        .children(
          opt[String]('s', "source")
            .valueName("<path>")
            .action((s, c) => updateConfig(c, _.copy(src = Path(s))))
            .text("site sources directory path"),
        ),
      cmd("serve")
        .action((_, c) => c.copy(cmd = Some(ServeCommand())))
        .text("  Build and serve the site")
        .children(
          opt[String]('d', "dest")
            .valueName("<path>")
            .action((d, c) => updateServe(c, _.copy(dst = Path(d))))
            .text("destination directory path"),
          opt[String]('s', "source")
            .valueName("<path>")
            .action((s, c) => updateServe(c, _.copy(src = Path(s))))
            .text("site sources directory path"),
          opt[String]("host")
            .valueName("<host>")
            .action((h, c) => updateServe(c, _.copy(host = h)))
            .text("host to bind to (default 'localhost')"),
          opt[Int]('p', "port")
            .valueName("<port>")
            .action((p, c) => updateServe(c, _.copy(port = p)))
            .text("port to listen on (default 8080)"),
          opt[Unit]('D', "drafts")
            .action((_, c) => updateServe(c, _.copy(drafts = true)))
            .text("include draft pages (frontmatter `draft: true`)"),
        ),
    )

  OParser.parse(parser, processArgs(args), Args()) match {
    case Some(args: Args) if args.cmd.nonEmpty => App.run(args)
    case Some(_)                               => println(OParser.usage(parser))
    case _                                     =>
  }
