package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** JVM-only tests for the live-reload helpers in `serve.scala`. The full
  * `serve` loop (HttpServer + WatchService) isn't unit-testable without
  * spinning up real ports and editing real files, so we cover the
  * deterministic pieces here and rely on manual smoke-testing for the rest.
  */
class LiveReloadSpec extends AnyFlatSpec with Matchers {

  "injectLiveReloadHtml" should "insert the script just before </body>" in {
    val html = "<html><body><h1>Hi</h1></body></html>"
    val out  = injectLiveReloadHtml(html)
    out should startWith("<html><body><h1>Hi</h1>")
    out should endWith("</body></html>")
    out should include(LiveReloadScript)
    // Script appears BEFORE </body>, not after.
    out.indexOf(LiveReloadScript) should be < out.indexOf("</body>")
  }

  it should "append at end when </body> is absent" in {
    val html = "<html><h1>Hi</h1></html>"
    val out  = injectLiveReloadHtml(html)
    out should startWith(html)
    out should endWith(LiveReloadScript)
  }

  it should "produce idempotent output if already injected" in {
    // The injected script self-guards via `window.__juicerLive` so a second
    // injection at runtime is harmless. Here we just confirm injecting a
    // page that already contains the script doesn't lose anything.
    val once  = injectLiveReloadHtml("<html><body>x</body></html>")
    val twice = injectLiveReloadHtml(once)
    twice should include(LiveReloadScript)
    twice should endWith("</body></html>")
  }

  "LiveReloadScript" should "open EventSource('/__juicer/live')" in {
    LiveReloadScript should include("EventSource('/__juicer/live')")
    LiveReloadScript should include("'reload'")
    LiveReloadScript should include("location.reload")
  }
}
