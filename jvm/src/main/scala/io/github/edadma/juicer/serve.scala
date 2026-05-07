package io.github.edadma.juicer

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.github.edadma.path.Path

import java.io.OutputStream
import java.net.InetSocketAddress
import java.nio.file.{FileSystems, Files, Paths, StandardWatchEventKinds, WatchEvent, WatchKey}
import java.util.concurrent.{ConcurrentHashMap, Executors}
import scala.jdk.CollectionConverters.*

/** Minimal single-threaded static-file server backed by `com.sun.net.httpserver`.
  *
  * Intended for `juicer serve` — a local dev preview while authoring. Not a
  * production web server.
  *
  * With `liveReload = true`:
  *   - A `WatchService` registers every directory under `watchRoot` and
  *     triggers a rebuild on change (debounced ~150 ms).
  *   - HTML responses get a `<script>` injected before `</body>` that opens
  *     an `EventSource` to `/__juicer/live` and reloads on the `reload` event.
  *   - The server's executor is switched to a cached thread pool so SSE
  *     connections don't starve normal requests.
  */
def serve(
    root:       Path,
    host:       String        = "localhost",
    port:       Int           = 8080,
    liveReload: Boolean       = false,
    watchRoot:  Path          = null,
    rebuild:    () => Boolean = () => false,
    htmlDir:    String        = "",
): Unit = {
  val server = bindWithRetry(host, port, retriesLeft = 20)

  if (liveReload) {
    val sse = new SseChannel
    server.createContext("/__juicer/live", sse)
    server.createContext("/", new StaticFileHandler(root, injectLiveReload = true, htmlDir = htmlDir))
    server.setExecutor(Executors.newCachedThreadPool())

    if (watchRoot != null)
      startWatcher(watchRoot, rebuild, sse)
  } else {
    server.createContext("/", new StaticFileHandler(root, injectLiveReload = false, htmlDir = htmlDir))
    server.setExecutor(null) // single-threaded — fine for dev
  }

  server.start()

  // `actualPort` may differ from the requested `port` if `bindWithRetry`
  // had to scan to the next free one — print what the OS actually gave us.
  val actualPort = server.getAddress.getPort
  println(s"juicer serve: http://$host:$actualPort/")
  println(s"  root: $root")
  if (liveReload) println("  live reload: enabled")
  println("Press Ctrl+C to stop.")

  try Thread.currentThread().join()
  catch case _: InterruptedException => server.stop(0)
}

// ===== SSE channel =====
//
// Tracks a set of currently-open OutputStream sinks. `notifyReload` writes
// the SSE `reload` event to each; failed sinks are pruned. Keep-alive
// comments fire on a daemon timer so reverse proxies don't kill idle
// connections.

private final class SseChannel extends HttpHandler {
  private val sinks = ConcurrentHashMap.newKeySet[OutputStream]()

  // One ka thread for the lifetime of the JVM is fine for a dev tool;
  // daemon = true so Ctrl+C exits cleanly.
  private val keepalive = Executors.newSingleThreadScheduledExecutor(r => {
    val t = new Thread(r, "juicer-sse-keepalive"); t.setDaemon(true); t
  })
  keepalive.scheduleAtFixedRate(() => writeAll(": keepalive\n\n"), 30, 30, java.util.concurrent.TimeUnit.SECONDS)

  def handle(ex: HttpExchange): Unit = {
    val h = ex.getResponseHeaders
    h.set("Content-Type", "text/event-stream")
    h.set("Cache-Control", "no-cache")
    h.set("Connection", "keep-alive")
    // 0 = unknown / chunked. The exchange stays open until we close it.
    ex.sendResponseHeaders(200, 0L)
    val out = ex.getResponseBody
    sinks.add(out)
    try out.write("retry: 1000\n\n".getBytes("UTF-8"))
    catch case _: Throwable => sinks.remove(out)
    out.flush()
    // Block this handler thread to keep the connection open. The
    // notifyReload / writeAll paths write directly to `out`. If the client
    // disconnects, the next write throws and the sink is pruned.
    while (sinks.contains(out)) {
      try Thread.sleep(1000)
      catch case _: InterruptedException => sinks.remove(out)
    }
    try out.close() catch case _: Throwable => ()
  }

  /** Tell every connected client to reload. */
  def notifyReload(): Unit = writeAll("event: reload\ndata: 1\n\n")

  private def writeAll(payload: String): Unit = {
    val bytes = payload.getBytes("UTF-8")
    val it    = sinks.iterator()
    while (it.hasNext) {
      val out = it.next()
      try { out.write(bytes); out.flush() }
      catch case _: Throwable => sinks.remove(out)
    }
  }
}

// ===== File watcher + rebuild loop =====

/** Try to bind a fresh `HttpServer` on `host:startPort`. If the port is in
  * use, scan upward up to `retriesLeft` times and use the first free one.
  * Prints a one-liner whenever it bumps so the user knows what happened.
  * Throws the original `BindException` after exhausting `retriesLeft` —
  * that's better than silently picking some far-away port the user didn't
  * ask for. Exposed for tests. */
@scala.annotation.tailrec
private[juicer] def bindWithRetry(host: String, startPort: Int, retriesLeft: Int): HttpServer = {
  val attempt: Either[java.net.BindException, HttpServer] =
    try Right(HttpServer.create(new InetSocketAddress(host, startPort), 0))
    catch case e: java.net.BindException => Left(e)
  attempt match {
    case Right(s)            => s
    case Left(e) if retriesLeft <= 0 => throw e
    case Left(_) =>
      println(s"[juicer] port $startPort is in use; trying ${startPort + 1}…")
      bindWithRetry(host, startPort + 1, retriesLeft - 1)
  }
}

/** SSE client script — injected into HTML responses when live-reload is on.
  * Idempotent (the `__juicerLive` guard) so duplicate injection is harmless. */
private[juicer] val LiveReloadScript: String =
  """<script>
    |(function() {
    |  if (window.__juicerLive) return;
    |  window.__juicerLive = true;
    |  var es = new EventSource('/__juicer/live');
    |  es.addEventListener('reload', function() { location.reload(); });
    |  es.onerror = function() { /* keep retrying — EventSource auto-reconnects */ };
    |})();
    |</script>""".stripMargin

/** Insert the live-reload script just before the closing `</body>` tag. If
  * the document has no `</body>` (a stripped-down or hand-written HTML), the
  * script is appended at the end. Exposed for tests. */
private[juicer] def injectLiveReloadHtml(html: String): String =
  if (html.contains("</body>")) html.replace("</body>", LiveReloadScript + "\n</body>")
  else html + LiveReloadScript

private def startWatcher(src: Path, rebuild: () => Boolean, sse: SseChannel): Unit = {
  val watchService = FileSystems.getDefault.newWatchService()

  /** Register `dir` and every subdirectory underneath. Re-registers on
    * directory creation in the event loop, so newly-added dirs join the
    * watch set without restarting `serve`. */
  def registerAll(dir: java.nio.file.Path): Unit = {
    Files.walk(dir).iterator().asScala.filter(Files.isDirectory(_)).foreach { d =>
      d.register(
        watchService,
        StandardWatchEventKinds.ENTRY_CREATE,
        StandardWatchEventKinds.ENTRY_DELETE,
        StandardWatchEventKinds.ENTRY_MODIFY,
      )
    }
  }

  val srcPath = Paths.get(src.toString).toAbsolutePath
  registerAll(srcPath)

  val thread = new Thread(
    () => {
      var running = true
      while (running && !Thread.currentThread().isInterrupted) {
        val keyOpt: Option[WatchKey] =
          try Some(watchService.take())
          catch case _: InterruptedException => running = false; None

        keyOpt.foreach { key =>
          var anyEvent = false
          for (ev <- key.pollEvents().asScala) {
            val k = ev.kind()
            if (k != StandardWatchEventKinds.OVERFLOW) {
              anyEvent = true
              // Newly-created directories join the watch set so deeper edits
              // also fire.
              if (k == StandardWatchEventKinds.ENTRY_CREATE) {
                val ctx = ev.context().asInstanceOf[java.nio.file.Path]
                val p = key.watchable().asInstanceOf[java.nio.file.Path].resolve(ctx)
                if (Files.isDirectory(p)) registerAll(p)
              }
            }
          }

          // Drain further events that arrive within the debounce window —
          // typical editors save → trigger N events; one rebuild covers all.
          if (anyEvent) {
            Thread.sleep(150)
            var k = watchService.poll()
            while (k != null) {
              k.pollEvents() // discard
              k.reset()
              k = watchService.poll()
            }
            println("[juicer] source changed; rebuilding…")
            if (rebuild()) sse.notifyReload()
          }
          key.reset()
        }
      }
    },
    "juicer-watcher",
  )
  thread.setDaemon(true)
  thread.start()
}

// ===== Static-file handler =====

private[juicer] final class StaticFileHandler(
    root:             Path,
    injectLiveReload: Boolean,
    htmlDir:          String,
) extends HttpHandler {

  def handle(ex: HttpExchange): Unit = {
    val raw = ex.getRequestURI.getPath
    val rel = if (raw == "/") "/index.html" else raw
    val sub = rel.split('/').filter(_.nonEmpty).toList

    /** Resolve `segs` against `base`, falling through to `<base>/index.html`
      * when the resolved path is a directory. Returns `None` when the path
      * doesn't exist or isn't readable. */
    def resolve(base: Path, segs: List[String]): Option[Path] = {
      val located = segs.foldLeft(base)(_ / _)
      val target  = if (located.exists && located.isDirectory) located / "index.html" else located
      if (target.exists && target.isFile && target.isReadable) Some(target) else None
    }

    // Try the URL path verbatim against `root`. If that misses, try with the
    // configured `htmlDir` injected as the leading filesystem segment — that's
    // where the build pipeline puts nested content under the default
    // `htmlDir = "html"` layout, even though URLs strip it.
    val target = resolve(root, sub)
      .orElse(if (htmlDir.isEmpty) None else resolve(root, htmlDir :: sub))

    target match {
      case Some(t) =>
        val mime = contentType(t.filename)
        val bytes =
          if (injectLiveReload && mime.startsWith("text/html"))
            injectLiveReloadHtml(t.readText()).getBytes("UTF-8")
          else t.readBytes
        ex.getResponseHeaders.set("Content-Type", mime)
        ex.sendResponseHeaders(200, bytes.length.toLong)
        val out = ex.getResponseBody
        try out.write(bytes)
        finally out.close()
      case None =>
        val msg = s"Not found: $raw\n".getBytes("UTF-8")
        ex.getResponseHeaders.set("Content-Type", "text/plain; charset=utf-8")
        ex.sendResponseHeaders(404, msg.length.toLong)
        val out = ex.getResponseBody
        try out.write(msg)
        finally out.close()
    }
  }

  /** A tiny MIME-type lookup. Covers what an SSG dev preview actually serves. */
  private def contentType(filename: String): String =
    filename.lastIndexOf('.') match {
      case -1 => "application/octet-stream"
      case n =>
        filename.substring(n + 1).toLowerCase match {
          case "html" | "htm"        => "text/html; charset=utf-8"
          case "css"                 => "text/css; charset=utf-8"
          case "js" | "mjs"          => "application/javascript"
          case "json"                => "application/json"
          case "svg"                 => "image/svg+xml"
          case "png"                 => "image/png"
          case "jpg" | "jpeg"        => "image/jpeg"
          case "gif"                 => "image/gif"
          case "webp"                => "image/webp"
          case "ico"                 => "image/x-icon"
          case "txt" | "md"          => "text/plain; charset=utf-8"
          case "woff"                => "font/woff"
          case "woff2"               => "font/woff2"
          case "ttf"                 => "font/ttf"
          case "otf"                 => "font/otf"
          case "xml"                 => "application/xml; charset=utf-8"
          case _                     => "application/octet-stream"
        }
    }
}
