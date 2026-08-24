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
  *   - HTML responses get a `<script>` injected before `</body>` that
  *     long-polls `GET /__juicer/wait?since=N`. The server holds each
  *     poll open until the next rebuild (or 30s) and responds with a
  *     small JSON `{reload, version}`. The client either calls
  *     `location.reload()` or immediately re-polls.
  *
  * **Why long-poll instead of SSE?** SSE keeps a single connection open
  * forever per tab. Browsers cap concurrent HTTP/1.1 connections per host
  * at 6 (Chrome/Safari). Each navigation in a multi-page site briefly
  * stacks two SSE connections (old page + new page) before page-unload
  * fires; rapid clicks accumulate enough overlap to exhaust the pool, and
  * the next request `Stalled`s on Chrome's side waiting for a slot. Each
  * long-poll cycle naturally completes (server responds → client
  * reconnects), so connection slots are freed continuously and the
  * accumulation pattern can't form.
  *
  * Bind retry: delegates to `microserve.Server.bindWithRetry`, which surfaces
  * port conflicts as `BindError.AddressInUse` and climbs to the next free port
  * (up to 20 attempts). Other categorised failures (`InvalidHost`,
  * `PermissionDenied`) get a hint message and propagate.
  */
def serve(
    root:       Path,
    host:       String        = "localhost",
    port:       Int           = 8080,
    liveReload: Boolean       = false,
    watchRoot:  Path          = null,
    rebuild:    () => Boolean = () => false,
    htmlDir:    String        = "",
    excludeDir: Path          = null,
): Unit =
  given runtime: Runtime  = summon[Runtime]
  given ec:      ExecutionContext = runtime.executionContext

  val longPoll = new LongPollChannel(runtime.timers)
  val handler: RequestHandler = (req, res) =>
    if liveReload && req.path == "/__juicer/wait" then
      val since = req.query.get("since").flatMap(s => scala.util.Try(s.toLong).toOption).getOrElse(0L)
      longPoll.handleWait(since, res)
    else
      // Pass the current build version so the injected script can poll
      // with `since = <served version>` — without that, every page would
      // load with since=0 and immediately get reload=true if any build
      // had ever happened, producing an infinite reload loop.
      serveStatic(root, htmlDir, injectLiveReload = liveReload, longPoll.currentVersion, req, res)

  Server.bindWithRetry(handler)(
    startPort    = port,
    host         = host,
    retries      = 20,
    onPortBumped = (busy, next) => println(s"[juicer] port $busy is in use; trying $next…"),
  )(
    onBound = (_, actualPort) =>
      println(s"juicer serve: http://$host:$actualPort/")
      println(s"  root: $root")
      if liveReload then
        println("  live reload: enabled")
        if watchRoot != null then startWatcher(watchRoot, excludeDir, rebuild, longPoll)
      println("Press Ctrl+C to stop."),
    onError = e =>
      // Categorised by microserve — log a hint that matches the variant
      // instead of dumping the raw exception text.
      val hint = e match
        case _: BindError.AddressInUse     => s"every port from $port to ${port + 20} was in use"
        case _: BindError.InvalidHost      => s"host '$host' is invalid or unresolvable"
        case _: BindError.PermissionDenied => s"permission denied binding to $host:$port (try a higher port)"
        case _                             => e.getMessage
      Console.err.println(s"[juicer] could not start server: $hint")
      throw e,
  )

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
    currentVersion:   Long,
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
          injectLiveReloadHtml(t.readText(), currentVersion).getBytes("UTF-8")
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

/** Long-poll client script — injected into HTML responses when live-reload
  * is on. Idempotent (the `__juicerLive` guard) so duplicate injection is
  * harmless.
  *
  * Wire protocol: each cycle is one `fetch('/__juicer/wait?since=N')` that
  * the server holds open until the next rebuild (or ~20s). Response is a
  * small JSON `{"reload": <bool>, "version": <int>}` — true triggers
  * `location.reload()`, false triggers an immediate re-poll with the new
  * version number. Network errors back off 1s before retrying, so a server
  * restart doesn't busy-loop the browser.
  *
  * The `__VERSION__` placeholder is replaced at injection time with the
  * server's current build version. Without that, every page would load
  * with `since=0` and trigger an immediate reload as soon as any rebuild
  * had ever happened — producing an infinite reload loop. The script's
  * first poll is therefore "any change since the version I was served
  * with?" — the server holds it until the *next* rebuild.
  *
  * **AbortController + pagehide is load-bearing.** Without it, the OLD
  * page's in-flight fetch stays alive until its JS context is destroyed
  * (which happens *after* the new page renders). With rapid navigation,
  * dead fetches accumulate in Chrome's per-host connection pool until the
  * 6-cap is hit and the next nav `Stalled`s. Aborting on `pagehide` frees
  * the connection slot the instant the user clicks away — catches both
  * normal navigation and bfcache transitions where `beforeunload` is
  * unreliable. AbortError is silently swallowed in the `catch` because
  * it's the *intended* outcome on navigation, not a network failure.
  *
  * Exposed (private to package) for unit tests. The literal string here
  * still contains `__VERSION__`; injection substitutes it. */
private[juicer] val LiveReloadScript: String =
  """<script>
    |(function() {
    |  if (window.__juicerLive) return;
    |  window.__juicerLive = true;
    |  var since = __VERSION__;
    |  var controller = null;
    |  function poll() {
    |    controller = new AbortController();
    |    fetch('/__juicer/wait?since=' + since, { cache: 'no-store', signal: controller.signal })
    |      .then(function(r) { return r.json(); })
    |      .then(function(data) {
    |        if (data.reload) location.reload();
    |        else { since = data.version; poll(); }
    |      })
    |      .catch(function(e) {
    |        if (e.name === 'AbortError') return;
    |        setTimeout(poll, 1000);
    |      });
    |  }
    |  window.addEventListener('pagehide', function() {
    |    if (controller) try { controller.abort(); } catch (e) {}
    |  });
    |  poll();
    |})();
    |</script>""".stripMargin

/** Insert the live-reload script just before the closing `</body>` tag, with
  * `__VERSION__` substituted to the current build version so the first poll
  * starts from "since this page's version". If the document has no
  * `</body>` (a stripped-down or hand-written HTML), the script is appended
  * at the end. */
private[juicer] def injectLiveReloadHtml(html: String, currentVersion: Long): String =
  val script = LiveReloadScript.replace("__VERSION__", currentVersion.toString)
  if html.contains("</body>") then html.replace("</body>", script + "\n</body>")
  else html + script

// ===== Long-poll channel =====================================================

/** Tracks pending `/__juicer/wait` requests and wakes them when the build
  * completes. Each entry holds the request open until either:
  *   - a `notifyReload` call (build finished) responds `{reload: true}`, or
  *   - the wait timeout (30s) responds `{reload: false}`, or
  *   - the client closes the connection (peer FIN), in which case we just
  *     clean up state — no response needed since the client is gone.
  *
  * Single-threaded discipline: every method runs on microserve's runtime
  * event loop, so no synchronisation is required. The mutable `pending` set
  * is fine without a lock for the same reason.
  */
private[juicer] final class LongPollChannel(timers: Timers)(using ExecutionContext):
  /** One pending poll. `cancelTimeout` is mutable so we can install the
    * timeout handle AFTER the entry is in `pending` — otherwise a timer
    * that fires synchronously would race the add. (Single-threaded loop
    * makes the race impossible in practice, but the explicit ordering is
    * load-bearing if we ever move to a multi-threaded runtime.) */
  private final class Pending(
      val res:     Response,
      val promise: Promise[Unit],
  ):
    var cancelTimeout: () => Unit = () => ()

  private val pending = mutable.Set.empty[Pending]
  private var version = 0L
  // Hold each poll for up to ~20s before responding "no change". MUST be
  // shorter than microserve's 30s connection-idle timeout — otherwise the
  // two timers race at exactly 30s, and when the idle timer wins the
  // connection closes before we can write the response (curl sees status
  // 000 / EOF). 20s leaves a comfortable 10s margin.
  private val WaitTimeoutMs = 20000L

  /** The current build version. Read by the static-file handler so the
    * injected script's first poll starts from "since this page's version"
    * rather than from 0 — without that, every page load triggers an
    * immediate reload as soon as any rebuild has ever happened. */
  def currentVersion: Long = version

  /** Handle a `/__juicer/wait?since=N` request. Returns the future the
    * microserve handler should return — completes when the response is
    * fully written, or when the client disconnects without one. */
  def handleWait(since: Long, res: Response): Future[Unit] =
    if since < version then
      // Build already happened past the client's last-seen version. Reply
      // immediately so the client reloads on next paint.
      respondReload(res)
    else
      val entry = new Pending(res, Promise[Unit]())
      pending += entry
      // Now arm the timeout. (See `Pending` docstring for ordering note.)
      entry.cancelTimeout = timers.setTimeout(WaitTimeoutMs) { () =>
        if pending.remove(entry) then
          respondNoChange(entry.res).onComplete(r => entry.promise.tryComplete(r))
      }
      // Client disconnect — cancel the timeout and complete the handler
      // future. Do NOT try to write a response; the transport is gone.
      res.onClose { () =>
        if pending.remove(entry) then
          entry.cancelTimeout()
          val _ = entry.promise.trySuccess(())
      }
      entry.promise.future

  /** Build finished — bump the version and wake every pending poll. */
  def notifyReload(): Unit =
    version += 1
    val toRespond = pending.toList
    pending.clear()
    toRespond.foreach { e =>
      e.cancelTimeout()
      respondReload(e.res).onComplete(r => e.promise.tryComplete(r))
    }

  private def respondReload(res: Response): Future[Unit] =
    res.set("Content-Type", "application/json").set("Cache-Control", "no-store")
    res.send(s"""{"reload":true,"version":$version}""")

  private def respondNoChange(res: Response): Future[Unit] =
    res.set("Content-Type", "application/json").set("Cache-Control", "no-store")
    res.send(s"""{"reload":false,"version":$version}""")
end LongPollChannel

// ===== File watcher + rebuild loop ===========================================

/** Wire microserve's `FsWatcher` to a debounced rebuild + reload broadcast.
  * Editor saves typically fire 2–5 events for a single user-visible change;
  * we debounce by 150 ms (one rebuild per burst) using the runtime's timer.
  *
  * On macOS the JVM `WatchService` is polling-backed and events come in
  * 1–10 s after the change; that's a known platform quirk, not a bug here.
  *
  * `excludeDir` is the build's output directory, typically a child of the
  * watched `src` tree (e.g. `<src>/public/`). Without filtering, the build's
  * own writes trigger a rebuild that triggers another rebuild — the page
  * "jumps every couple of seconds" until the user kills the server. We
  * suppress events whose absolute path is inside `excludeDir`. Pass `null`
  * to disable. */
private[juicer] def startWatcher(
    src:        Path,
    excludeDir: Path,
    rebuild:    () => Boolean,
    longPoll:   LongPollChannel,
)(using runtime: Runtime): Unit =
  val watcher  = runtime.newFsWatcher()
  var pending: () => Unit = null
  val timers   = runtime.timers
  val excluded = if excludeDir eq null then null else excludeDir.normalize.toAbsolutePath.toString

  val _ = watcher.watch(src.toString, recursive = true) { ev =>
    if isWatchEventRelevant(ev.path, excluded) then
      // Coalesce a burst of events into a single rebuild. The cancel function
      // returned by setTimeout makes "I already scheduled one — push it out"
      // trivial: cancel and re-schedule.
      if pending != null then pending()
      pending = timers.setTimeout(150) { () =>
        pending = null
        println("[juicer] source changed; rebuilding…")
        if rebuild() then longPoll.notifyReload()
      }
  }
end startWatcher

/** Pure helper, exposed for unit testing. Returns true when an event at
  * `eventPath` should trigger a rebuild — i.e. it is *not* under the build
  * output directory `excludedAbs`. A sibling directory whose name happens
  * to share a prefix (e.g. `public2/` next to `public/`) must NOT be
  * accidentally excluded — so after the prefix match we verify the next
  * character is an actual path separator.
  *
  * Recognises both `/` and `\` as separators. The previous version used
  * `java.io.File.separator` for "the right thing on this platform", but
  * that class isn't available in Scala.js, breaking the cross-platform
  * build. Recognising both is correct everywhere we ship: macOS / Linux
  * / Native always use `/`; Windows JVM uses `\`; Node on Windows
  * normalises to `/`. There's no platform where allowing both would be
  * wrong.
  *
  * `excludedAbs == null` disables filtering and every event is relevant. */
private[juicer] def isWatchEventRelevant(eventPath: String, excludedAbs: String): Boolean =
  if excludedAbs == null then true
  else
    // Strip a trailing separator off `excludedAbs` so its length lines up
    // with the segment boundary in `eventPath` for the prefix check.
    val ex =
      if excludedAbs.endsWith("/") || excludedAbs.endsWith("\\") then
        excludedAbs.dropRight(1)
      else excludedAbs
    if eventPath == ex then false
    else if !eventPath.startsWith(ex) then true
    else
      val next = eventPath.charAt(ex.length)
      next != '/' && next != '\\'

