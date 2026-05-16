package io.github.edadma.juicer

import io.github.edadma.path.Path

/** Per-target shell-out to the asset-pipeline tools (`sass` for SCSS →
  * CSS, `esbuild` for JS bundling/minification). The interface mirrors
  * [[ImageEncoderBackend]] — narrow, one operation per output — so the
  * per-target implementation surface stays small.
  *
  * The cross-platform story is the same:
  *
  *   - **JVM** ships real implementations using
  *     `java.lang.ProcessBuilder` to invoke the CLIs. See
  *     `jvm/src/main/scala/.../AssetBuilderBackendImpl.scala`.
  *
  *   - **Scala Native** and **Scala.js** ship passthrough stubs that
  *     report each tool as unavailable. [[AssetPipeline]] falls back to
  *     copying the source file verbatim in that case — the build still
  *     succeeds, the asset URL still resolves, no template change is
  *     required.
  *
  * Why shell-out rather than a JVM library:
  *
  *   - Authors get the freshest Sass / esbuild without juicer tracking
  *     upstream library releases — same trade-off as `magick`.
  *
  *   - The dependency graph stays clean: no Node `node_modules`, no
  *     dart-sass on the classpath, no Scala-Native split.
  *
  *   - The two CLIs are widely available on every package manager
  *     (`brew install sass esbuild`, `apt install`, `npm install -g`).
  */
trait AssetBuilderBackend {

  /** Whether `sass` is invokable on this target. [[AssetPipeline]]
    * branches on this to decide between real compilation and the
    * "copy through verbatim" degradation path. */
  def sassAvailable: Boolean

  /** Whether `esbuild` is invokable on this target. Same branching
    * story as [[sassAvailable]]. */
  def esbuildAvailable: Boolean

  /** Compile a Sass / SCSS source file to CSS. `src` may be `.scss` or
    * `.sass`; the CLI infers the syntax from the extension. `minify`
    * passes `--style=compressed` instead of `--style=expanded`.
    *
    * Returns `Right(())` on success, `Left(<reason>)` on failure or
    * unavailability. Implementations MUST NOT throw — surface failures
    * through `Left` so the pipeline can fall back cleanly. */
  def compileSass(
      src:    Path,
      dst:    Path,
      minify: Boolean,
  ): Either[String, Unit]

  /** Bundle (and optionally minify) JavaScript. `src` is the bundle's
    * entry point; `esbuild` resolves imports relative to it. `minify`
    * passes `--minify` to esbuild.
    *
    * Returns `Right(())` on success, `Left(<reason>)` otherwise. Same
    * no-throw contract as [[compileSass]]. */
  def bundleJs(
      src:    Path,
      dst:    Path,
      minify: Boolean,
  ): Either[String, Unit]
}

object AssetBuilderBackend {

  /** A backend that declines every operation. Used by the Native / JS
    * stubs and as a test seam — passing this to [[AssetPipeline]] is
    * equivalent to running on a machine without `sass` / `esbuild`
    * installed, and exercises the verbatim-copy degradation path. */
  val Unavailable: AssetBuilderBackend = new AssetBuilderBackend {
    val sassAvailable: Boolean    = false
    val esbuildAvailable: Boolean = false

    def compileSass(src: Path, dst: Path, minify: Boolean): Either[String, Unit] =
      Left("sass is unavailable on this target")
    def bundleJs(src: Path, dst: Path, minify: Boolean): Either[String, Unit] =
      Left("esbuild is unavailable on this target")
  }
}
