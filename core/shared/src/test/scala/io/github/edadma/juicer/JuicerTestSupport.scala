package io.github.edadma.juicer

import io.github.edadma.path.Path
import org.scalatest.BeforeAndAfterEach
import org.scalatest.Suite

import scala.compiletime.uninitialized
import scala.util.Random

/** Shared workspace + helpers for end-to-end juicer build tests.
  *
  * Each test gets a fresh `src` + `dst` temp directory under `/tmp/`,
  * then composes a small site with `writeAt`, builds it via
  * `build()`, and reads files back from the output tree with `out()`.
  *
  * Per-test isolation is via a millisecond + random-int nonce so two
  * specs running in parallel don't trample each other's workspaces.
  *
  * Mix into any spec that needs the build pipeline:
  *
  * {{{
  * class MyFeatureSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {
  *   "feature X" should "..." in { writeAt(...); build(); out(...) shouldBe ... }
  * }
  * }}}
  */
trait JuicerTestSupport extends BeforeAndAfterEach { this: Suite =>

  /** Live workspace for the test currently in flight. Recreated per test. */
  var src: Path = uninitialized
  var dst: Path = uninitialized

  override def beforeEach(): Unit = {
    val nonce = System.currentTimeMillis().toString + "-" + Random.nextInt(1_000_000)
    src = Path("/tmp") / s"juicer-test-src-$nonce"
    dst = Path("/tmp") / s"juicer-test-dst-$nonce"
    src.createDirectories()
  }

  override def afterEach(): Unit = {
    deleteTree(src)
    deleteTree(dst)
  }

  private def deleteTree(p: Path): Unit = {
    if (!p.exists) return
    if (p.isDirectory) {
      p.listDirectory().foreach(e => deleteTree(p / e.name))
      p.delete()
    } else p.delete()
  }

  /** Convenience: write `content` to `src / relPath`, creating parents. */
  protected def writeAt(relPath: String, content: String): Unit = {
    val p = relPath.split('/').foldLeft(src)(_ / _)
    p.parent.foreach(_.createDirectories())
    p.writeText(content)
  }

  /** Run `juicer build` against the staged `src` / `dst`. */
  protected def build(args: Args = Args()): Unit =
    App.run(args.copy(cmd = Some(BuildCommand(src = src, dst = dst))))

  /** Read a file from the output tree. */
  protected def out(relPath: String): String =
    relPath.split('/').foldLeft(dst)(_ / _).readText()
}
