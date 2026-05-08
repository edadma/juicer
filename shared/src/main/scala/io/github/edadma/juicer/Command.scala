package io.github.edadma.juicer

import io.github.edadma.path.Path

trait Command

case class BuildCommand(
    src:    Path    = Path("."),
    dst:    Path    = null,
    drafts: Boolean = false,
    /** Include posts whose parsed `date` is in the future. Phase 2.1. */
    future: Boolean = false,
) extends Command

case class ServeCommand(
    src:        Path    = Path("."),
    dst:        Path    = null,
    host:       String  = "localhost",
    port:       Int     = 8080,
    drafts:     Boolean = false,
    /** Include posts whose parsed `date` is in the future. Phase 2.1. */
    future:     Boolean = false,
    liveReload: Boolean = false,
) extends Command

case class ConfigCommand(src: Path = Path(".")) extends Command

/** `juicer theme add <git-url>` — clone a theme repo into the site's
  * `themeDir`. Optional `--name` overrides the directory name (default:
  * the URL's last segment, stripped of `.git`). Optional `--branch` /
  * `--ref` pins a branch, tag, or commit. */
case class ThemeAddCommand(
    src:    Path           = Path("."),
    url:    String         = "",
    name:   Option[String] = None,
    ref:    Option[String] = None,
    force:  Boolean        = false,
) extends Command
