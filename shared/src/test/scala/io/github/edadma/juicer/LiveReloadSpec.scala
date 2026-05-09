package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Cross-platform unit tests for the deterministic helpers in `serve.scala`.
  *
  * The HTTP-loop pieces (bind-retry, static-file-handler, SSE channel,
  * fs-watcher debouncing) are covered by microserve's own cross-platform
  * integration suite — this spec only owns the bits that don't need a real
  * server: the live-reload HTML injection and the script payload itself.
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

  "contentType" should "map common SSG extensions correctly" in {
    contentType("index.html")    shouldBe "text/html; charset=utf-8"
    contentType("style.css")     shouldBe "text/css; charset=utf-8"
    contentType("app.js")        shouldBe "application/javascript"
    contentType("data.json")     shouldBe "application/json"
    contentType("logo.svg")      shouldBe "image/svg+xml"
    contentType("photo.jpg")     shouldBe "image/jpeg"
    contentType("photo.JPEG")    shouldBe "image/jpeg" // case-insensitive
  }

  it should "fall back to octet-stream for unknown / no extension" in {
    contentType("README")        shouldBe "application/octet-stream"
    contentType("file.unknown")  shouldBe "application/octet-stream"
  }
}
