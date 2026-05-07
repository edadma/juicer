package io.github.edadma.juicer

import io.github.edadma.path.Path

/** Shell out to `git clone` to install a theme. JVM-only — Scala.js doesn't
  * have ProcessBuilder and Scala Native's process support is limited. The
  * shared `themeAdd` orchestration in App.scala calls into this; the JS /
  * Native variants stub out and tell the user to run on the JVM target.
  *
  * Streams git's stdout/stderr to ours so the user sees progress in real
  * time (clone of a typical theme is a few hundred KB, but bigger themes
  * with examples can be slow without feedback).
  */
def gitClone(url: String, target: Path, ref: Option[String]): Either[String, Unit] = {
  val cmd = scala.collection.mutable.ListBuffer[String](
    "git", "clone",
    // --depth 1 keeps the working tree small. The user can `git fetch
    // --unshallow` later if they need history.
    "--depth", "1",
  )
  ref.foreach { r => cmd += "--branch"; cmd += r }
  cmd += url
  cmd += target.toString

  try {
    val pb = new java.lang.ProcessBuilder(cmd.toArray*)
    pb.inheritIO()
    val proc = pb.start()
    val code = proc.waitFor()
    if (code == 0) Right(())
    else Left(s"git exited with status $code")
  } catch {
    case e: java.io.IOException =>
      Left(s"could not run git: ${e.getMessage} (is git installed and on PATH?)")
    case e: Throwable =>
      Left(s"git clone failed: ${e.getMessage}")
  }
}
