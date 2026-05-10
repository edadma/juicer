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
  * the URL's last segment, stripped of `.git`). Optional `--ref` pins a
  * branch, tag, or commit. Optional `--subdir` selects a subdirectory of
  * the repo as the theme (useful when a single repo bundles multiple
  * themes or ships the theme alongside other code). */
case class ThemeAddCommand(
    src:    Path           = Path("."),
    url:    String         = "",
    name:   Option[String] = None,
    ref:    Option[String] = None,
    subdir: Option[String] = None,
    force:  Boolean        = false,
) extends Command

/** `juicer theme upgrade [<name>]` — re-fetch one or all installed themes
  * from the URL recorded in their `.juicer-theme.toml` metadata. Without a
  * name, every installed theme that has metadata is upgraded. Themes that
  * lack metadata (vendored before 0.2.x or hand-installed) are reported
  * with the suggested `theme add --force ...` command to seed them. */
case class ThemeUpgradeCommand(
    src:  Path           = Path("."),
    name: Option[String] = None,
    ref:  Option[String] = None,
) extends Command
