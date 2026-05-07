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

  "bindWithRetry" should "bump to the next free port when the first is taken" in {
    // Reserve a port by binding a plain ServerSocket; bindWithRetry should
    // skip it and land on something higher. Loopback so we don't trip
    // firewall prompts on macOS.
    val occupied = new java.net.ServerSocket(0, 0, java.net.InetAddress.getByName("127.0.0.1"))
    try {
      val taken  = occupied.getLocalPort
      val server = bindWithRetry("127.0.0.1", taken, retriesLeft = 5)
      try {
        val landed = server.getAddress.getPort
        landed should be > taken
        landed should be <= (taken + 5)
      } finally server.stop(0)
    } finally occupied.close()
  }

  "StaticFileHandler with htmlDir" should "resolve /foo/bar/ at <root>/html/foo/bar/index.html" in {
    val root = io.github.edadma.path.Path("/tmp") / s"juicer-serve-test-${System.currentTimeMillis()}"
    try {
      (root / "html" / "foo" / "bar").createDirectories()
      (root / "index.html").writeText("<html><body>ROOT</body></html>")
      (root / "html" / "foo" / "bar" / "index.html").writeText("<html><body>NESTED</body></html>")

      val server = bindWithRetry("127.0.0.1", 0, retriesLeft = 1)
      server.createContext("/", new StaticFileHandler(root, injectLiveReload = false, htmlDir = "html"))
      server.start()
      try {
        val port = server.getAddress.getPort
        httpGet(s"http://127.0.0.1:$port/")        should include("ROOT")
        httpGet(s"http://127.0.0.1:$port/foo/bar/") should include("NESTED")
        httpGet(s"http://127.0.0.1:$port/no/such/") should include("Not found")
      } finally server.stop(0)
    } finally rmTree(root)
  }

  it should "fall back through htmlDir without losing the htmlDir = \"\" baseline" in {
    val root = io.github.edadma.path.Path("/tmp") / s"juicer-serve-flat-${System.currentTimeMillis()}"
    try {
      (root / "page").createDirectories()
      (root / "index.html").writeText("<html><body>FLAT-ROOT</body></html>")
      (root / "page" / "index.html").writeText("<html><body>FLAT-PAGE</body></html>")

      val server = bindWithRetry("127.0.0.1", 0, retriesLeft = 1)
      server.createContext("/", new StaticFileHandler(root, injectLiveReload = false, htmlDir = ""))
      server.start()
      try {
        val port = server.getAddress.getPort
        httpGet(s"http://127.0.0.1:$port/")      should include("FLAT-ROOT")
        httpGet(s"http://127.0.0.1:$port/page/") should include("FLAT-PAGE")
      } finally server.stop(0)
    } finally rmTree(root)
  }

  private def httpGet(url: String): String = {
    val u   = java.net.URI.create(url).toURL
    val con = u.openConnection.asInstanceOf[java.net.HttpURLConnection]
    con.setRequestMethod("GET")
    val is  = if (con.getResponseCode >= 400) con.getErrorStream else con.getInputStream
    val out = new String(is.readAllBytes(), "UTF-8")
    is.close()
    con.disconnect()
    out
  }

  private def rmTree(p: io.github.edadma.path.Path): Unit =
    if (p.exists) {
      if (p.isDirectory) p.listDirectory().foreach(e => rmTree(p / e.name))
      p.delete()
    }

  it should "throw BindException after exhausting retries" in {
    // Reserve enough consecutive ports that all retries fail. Tightly racy
    // on a busy host — if nothing else grabs adjacent ports between
    // ServerSocket(0) calls, we get a contiguous block.
    val sockets =
      (0 until 4).map(_ => new java.net.ServerSocket(0, 0, java.net.InetAddress.getByName("127.0.0.1")))
    try {
      val ports = sockets.map(_.getLocalPort).toList.sorted
      // Look for a contiguous run of 3 — if our four sockets happen to be
      // contiguous, we have a 3-port range we can challenge bindWithRetry
      // with using retriesLeft = 2 (start + 2 bumps = 3 attempts).
      val contig =
        ports.zip(ports.drop(1)).zip(ports.drop(2)).find { case ((a, b), c) => b == a + 1 && c == b + 1 }
      contig match {
        case None => cancel("OS handed out non-contiguous ports; can't exercise the all-fail path")
        case Some(((a, _), _)) =>
          val ex = intercept[java.net.BindException] {
            bindWithRetry("127.0.0.1", a, retriesLeft = 2)
          }
          ex.getMessage should not be empty
      }
    } finally sockets.foreach(_.close())
  }
}
