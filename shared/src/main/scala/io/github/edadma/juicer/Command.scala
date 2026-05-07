package io.github.edadma.juicer

import io.github.edadma.path.Path

trait Command

case class BuildCommand(src: Path = Path("."), dst: Path = null) extends Command

case class ServeCommand(
    src:  Path   = Path("."),
    dst:  Path   = null,
    host: String = "localhost",
    port: Int    = 8080,
) extends Command

case class ConfigCommand(src: Path = Path(".")) extends Command
