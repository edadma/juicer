package io.github.edadma.juicer

import io.github.edadma.microserve.*
import io.github.edadma.microserve.given
import io.github.edadma.path.Path

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}

/** Cross-platform static-file server backed by `microserve` — the single
  * implementation runs on JVM (via `java.nio`), JS (Node `net`), and Native
  * (libuv). Intended for `juicer serve` — a local dev preview while authoring,
  * not a production web server.
  *
  * With `liveReload = true`:
  *   - microserve's [[FsWatcher]] registers `watchRoot` recursively and
  *     triggers a rebuild on change (debounced ~150 ms via the runtime's
  *     timer, not `Thread.sleep` so JS/Native stay non-blocking).
  *   - HTML responses get a `<script>` injected before `</body>` that opens
  *     an `EventSource` to `/__juicer/live` and reloads on the `reload` event.
  *   - The SSE channel uses microserve's streaming `Response` mode and the
  *     per-response `onClose` callback to prune subscribers when browser
  *     tabs close, instead of discovering disconnects on the next failed
  *     write.
  *
  * Bind retry: microserve's `Server.listen(...)(onListening, onError)`
  * surfaces port-in-use errors via `onError`. When `port` is taken we scan
  * upward (up to 20 attempts) and use the first free one, matching the prior
  * JVM-only behaviour.
  */
def serve(
    root:       Path,
    host:       String        = "localhost",
    port:       Int           = 8080,
    liveReload: Boolean       = false,
    watchRoot:  Path          = null,
    rebuild:    () => Boolean = () => false,
    htmlDir:    String        = "",
): Unit =
  given runtime: Runtime  = summon[Runtime]
  given ec:      ExecutionContext = runtime.executionContext

  val sse = new SseChannel
  val handler: RequestHandler = (req, res) =>
    if liveReload && req.path == "/__juicer/live" then
      sse.subscribe(res)
      // SSE handler returns immediately; the response stays open and is
      // written-to from `notifyReload` and the keep-alive ticker.
      Future.successful(())
    else
      serveStatic(root, htmlDir, injectLiveReload = liveReload, req, res)

  bindWithRetry(handler, host, port, retriesLeft = 20) { boundServer =>
    val actualPort = boundServer.actualPort
    println(s"juicer serve: http://$host:$actualPort/")
    println(s"  root: $root")
    if liveReload then
      println("  live reload: enabled")
      sse.startKeepalive(runtime.timers)
      if watchRoot != null then startWatcher(watchRoot, rebuild, sse)
    println("Press Ctrl+C to stop.")
  }

  // Block on the platform's loop until something stops it. JVM/Native block
  // here; JS returns immediately because Node owns its loop and Ctrl+C exits
  // via SIGINT — same shape as microserve.IntegrationTests / Server.run.
  runtime.run()
end serve

// ===== Static file handler ===================================================

/** Resolve the URL path against `root`, with a fallback through `htmlDir`.
  * Mirrors the prior JVM `StaticFileHandler` behaviour: `htmlDir` covers the
  * case where build output is nested under (e.g.) `<dst>/html/...` even though
  * the URL strips the `html/` segment.
  *
  * `injectLiveReload` flips on the `<script>` injection for HTML responses
  * plus a no-cache header set so the browser can't hand back yesterday's
  * artefacts during iterative authoring.
  */
private[juicer] def serveStatic(
    root:             Path,
    htmlDir:          String,
    injectLiveReload: Boolean,
    req:              Request,
    res:              Response,
): Future[Unit] =
  val raw = req.path
  val rel = if raw == "/" then "/index.html" else raw
  val sub = rel.split('/').filter(_.nonEmpty).toList

  def resolve(base: Path, segs: List[String]): Option[Path] =
    val located = segs.foldLeft(base)(_ / _)
    val target  = if located.exists && located.isDirectory then located / "index.html" else located
    if target.exists && target.isFile && target.isReadable then Some(target) else None

  val target = resolve(root, sub)
    .orElse(if htmlDir.isEmpty then None else resolve(root, htmlDir :: sub))

  target match
    case Some(t) =>
      val mime = contentType(t.filename)
      val bytes =
        if injectLiveReload && mime.startsWith("text/html") then
          injectLiveReloadHtml(t.readText()).getBytes("UTF-8")
        else t.readBytes
      res.set("Content-Type", mime)
      if injectLiveReload then
        res.set("Cache-Control", "no-cache, no-store, must-revalidate")
        res.set("Pragma", "no-cache")
        res.set("Expires", "0")
      res.end(bytes)
    case None =>
      res.status(404).set("Content-Type", "text/plain; charset=utf-8").send(s"Not found: $raw\n")
end serveStatic

/** Tiny MIME-type lookup. Covers what an SSG dev preview actually serves. */
private[juicer] def contentType(filename: String): String =
  filename.lastIndexOf('.') match
    case -1 => "application/octet-stream"
    case n =>
      filename.substring(n + 1).toLowerCase match
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

// ===== Live-reload script injection ==========================================

/** SSE client script — injected into HTML responses when live-reload is on.
  * Idempotent (the `__juicerLive` guard) so duplicate injection is harmless.
  * Exposed (private to package) for unit tests. */
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
  * script is appended at the end. */
private[juicer] def injectLiveReloadHtml(html: String): String =
  if html.contains("</body>") then html.replace("</body>", LiveReloadScript + "\n</body>")
  else html + LiveReloadScript

// ===== SSE channel ===========================================================

/** Tracks the set of currently-open streaming `Response`s, broadcasts reload
  * events to each, and prunes subscribers as their connections close.
  *
  * The previous JVM implementation used a `ConcurrentHashMap[OutputStream]`
  * keyed off raw streams and discovered dead clients on the next failed
  * write. Microserve's per-response `onClose` callback lets us prune
  * synchronously the moment a tab closes, with no synchronization needed
  * because every callback fires on the runtime's event-loop thread.
  */
private[juicer] final class SseChannel:
  private val sinks = mutable.Set.empty[Response]

  /** Wire `res` as a fresh SSE client. The handler that called us has
    * already returned `Future.successful(())` — this Response stays alive,
    * being written-to from `notifyReload` and the keep-alive ticker, until
    * its underlying connection drops. */
  def subscribe(res: Response): Unit =
    res.writeHead(200, Map(
      "Content-Type"  -> "text/event-stream",
      "Cache-Control" -> "no-cache",
      "Connection"    -> "keep-alive",
    ))
    // Initial empty comment encourages clients to commit to the connection
    // and sets a reconnect-backoff hint per the SSE spec.
    val _ = res.write("retry: 1000\n\n")
    sinks += res
    res.onClose { () =>
      sinks -= res
    }

  /** Tell every connected client to reload. Failed writes (already-dead
    * connections that haven't fired `onClose` yet) are caught and the sink
    * is removed eagerly. */
  def notifyReload(): Unit =
    writeAll("event: reload\ndata: 1\n\n")

  /** Schedule a 30-second keep-alive comment so reverse proxies / browsers
    * don't kill idle connections. Uses the runtime's timer (cross-platform)
    * instead of a JVM `ScheduledExecutorService`. The reschedule chains
    * itself on each tick so cancellation just stops scheduling. */
  def startKeepalive(timers: Timers): Unit =
    def schedule(): Unit =
      val _ = timers.setTimeout(30000)(() => {
        writeAll(": keepalive\n\n")
        schedule()
      })
    schedule()

  private def writeAll(payload: String): Unit =
    // Snapshot before iterating since onClose may mutate `sinks` from within
    // the same loop turn (microserve fires onClose synchronously when a
    // write fails on a dead transport).
    sinks.toList.foreach { res =>
      try
        val _ = res.write(payload)
      catch case _: Throwable => sinks -= res
    }
end SseChannel

// ===== File watcher + rebuild loop ===========================================

/** Wire microserve's `FsWatcher` to a debounced rebuild + reload broadcast.
  * Editor saves typically fire 2–5 events for a single user-visible change;
  * we debounce by 150 ms (one rebuild per burst) using the runtime's timer.
  *
  * On macOS the JVM `WatchService` is polling-backed and events come in
  * 1–10 s after the change; that's a known platform quirk, not a bug here. */
private[juicer] def startWatcher(
    src:     Path,
    rebuild: () => Boolean,
    sse:     SseChannel,
)(using runtime: Runtime): Unit =
  val watcher = runtime.newFsWatcher()
  var pending: () => Unit = null
  val timers = runtime.timers

  val _ = watcher.watch(src.toString, recursive = true) { _ =>
    // Coalesce a burst of events into a single rebuild. The cancel function
    // returned by setTimeout makes "I already scheduled one — push it out"
    // trivial: cancel and re-schedule.
    if pending != null then pending()
    pending = timers.setTimeout(150) { () =>
      pending = null
      println("[juicer] source changed; rebuilding…")
      if rebuild() then sse.notifyReload()
    }
  }
end startWatcher

// ===== Bind with retry =======================================================

/** Try to bind a fresh microserve `Server` on `host:startPort`. If the port
  * is in use, scan upward up to `retriesLeft` times and use the first free
  * one. Calls `onBound(server)` with the successfully-bound server; logs
  * each port bump so the user knows what happened. Throws after exhausting
  * `retriesLeft` — better than silently picking some far-away port the user
  * didn't ask for. */
private[juicer] def bindWithRetry(
    handler:     RequestHandler,
    host:        String,
    startPort:   Int,
    retriesLeft: Int,
)(onBound: Server => Unit)(using runtime: Runtime, ec: ExecutionContext): Unit =
  def attempt(port: Int, remaining: Int): Unit =
    val server = createServer(handler)
    server.listen(port, host)(
      onListening = () => onBound(server),
      onError = e =>
        if remaining <= 0 then
          // Surface the failure on the loop's reportFailure path. Throwing
          // from a Future continuation would be swallowed; printing matches
          // the dev-tool ergonomics of the prior JVM serve.
          Console.err.println(s"[juicer] could not bind: ${e.getMessage}")
          throw e
        else
          println(s"[juicer] port $port is in use; trying ${port + 1}…")
          attempt(port + 1, remaining - 1),
    )
  attempt(startPort, retriesLeft)
end bindWithRetry
