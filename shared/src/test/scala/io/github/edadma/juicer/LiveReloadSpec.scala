package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Cross-platform unit tests for the deterministic helpers in `serve.scala`.
  *
  * The HTTP-loop pieces (bind-retry, static-file-handler, long-poll
  * dispatch, fs-watcher debouncing) are covered by microserve's own
  * cross-platform integration suite — this spec only owns the bits that
  * don't need a real server: the live-reload HTML injection and the
  * script payload itself.
  */
class LiveReloadSpec extends AnyFlatSpec with Matchers {

  "injectLiveReloadHtml" should "insert the script just before </body>" in {
    val html = "<html><body><h1>Hi</h1></body></html>"
    val out  = injectLiveReloadHtml(html, 0L)
    out should startWith("<html><body><h1>Hi</h1>")
    out should endWith("</body></html>")
    // Substituted script (with __VERSION__ resolved) appears in the output.
    out should include("var since = 0;")
    // Script body appears BEFORE </body>, not after.
    out.indexOf("__juicerLive") should be < out.indexOf("</body>")
  }

  it should "append at end when </body> is absent" in {
    val html = "<html><h1>Hi</h1></html>"
    val out  = injectLiveReloadHtml(html, 0L)
    out should startWith(html)
    out should endWith("</script>")
  }

  it should "substitute __VERSION__ with the current build version" in {
    // The first poll's `since` MUST match the version the page was served
    // with, otherwise every page load triggers an immediate reload as
    // soon as any rebuild has happened (infinite reload loop).
    val out = injectLiveReloadHtml("<html><body>x</body></html>", 7L)
    out should include("var since = 7;")
    out should not include "__VERSION__"
  }

  it should "produce idempotent-shaped output if already injected" in {
    // The injected script self-guards via `window.__juicerLive` so a second
    // injection at runtime is harmless. Here we just confirm injecting a
    // page that already contains the script doesn't lose anything.
    val once  = injectLiveReloadHtml("<html><body>x</body></html>", 0L)
    val twice = injectLiveReloadHtml(once, 0L)
    twice should include("__juicerLive")
    twice should endWith("</body></html>")
  }

  "LiveReloadScript" should "long-poll /__juicer/wait and reload on signal" in {
    // Wire-protocol expectations the server depends on:
    //   GET /__juicer/wait?since=N  →  {"reload": <bool>, "version": <int>}
    LiveReloadScript should include("/__juicer/wait?since=")
    LiveReloadScript should include("data.reload")
    LiveReloadScript should include("location.reload")
    LiveReloadScript should include("data.version")
  }

  it should "include a backoff retry path on network error" in {
    // A server restart (or transient blip) should not busy-loop the browser.
    // The 1s setTimeout in the catch handler makes the retry friendly.
    LiveReloadScript should include("catch")
    LiveReloadScript should include("setTimeout")
  }

  it should "set cache: 'no-store' on the wait fetch" in {
    // Without this, the browser HTTP cache would happily serve a stale
    // {reload: false} forever — we'd never see the next build event.
    LiveReloadScript should include("cache: 'no-store'")
  }

  it should "abort the in-flight fetch on pagehide so connection slots free immediately" in {
    // Without AbortController + pagehide, the old page's in-flight fetch
    // stays alive until its JS context is destroyed (after the new page
    // renders). Rapid navigation accumulates dead fetches in Chrome's
    // 6-per-host pool until the next nav Stalls. This test guards the
    // mechanism: any rewrite that drops AbortController or the pagehide
    // listener will fail before reaching the user.
    LiveReloadScript should include("AbortController")
    LiveReloadScript should include("controller.abort")
    LiveReloadScript should include("pagehide")
    LiveReloadScript should include("AbortError")  // silently ignored, not retried
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

  // The watcher's exclude-dir filter exists to break the rebuild loop where
  // a build's own writes to <src>/public/ trigger the next rebuild. Test the
  // pure helper across the cases that matter.
  "isWatchEventRelevant" should "fire for events outside the excluded directory" in {
    val ex = "/tmp/site/public"
    isWatchEventRelevant("/tmp/site/content/_index.md", ex) shouldBe true
    isWatchEventRelevant("/tmp/site/site.toml", ex)         shouldBe true
    isWatchEventRelevant("/tmp/elsewhere/foo", ex)          shouldBe true
  }

  it should "suppress events inside the excluded directory" in {
    val ex = "/tmp/site/public"
    isWatchEventRelevant("/tmp/site/public/index.html", ex)        shouldBe false
    isWatchEventRelevant("/tmp/site/public/img/logo.svg", ex)      shouldBe false
    isWatchEventRelevant("/tmp/site/public/deep/nested/file.json", ex) shouldBe false
  }

  it should "suppress an event whose path is exactly the excluded directory" in {
    isWatchEventRelevant("/tmp/site/public", "/tmp/site/public") shouldBe false
  }

  it should "not be fooled by a sibling whose name shares a prefix" in {
    // /tmp/site/public2/x is NOT under /tmp/site/public — without the
    // separator-aware check, a naive startsWith would match it.
    isWatchEventRelevant("/tmp/site/public2/x.html", "/tmp/site/public") shouldBe true
    isWatchEventRelevant("/tmp/site/public-staging.txt", "/tmp/site/public") shouldBe true
  }

  it should "fire for every event when excludedAbs is null (filter disabled)" in {
    isWatchEventRelevant("/anything", null) shouldBe true
    isWatchEventRelevant("",          null) shouldBe true
  }
}
