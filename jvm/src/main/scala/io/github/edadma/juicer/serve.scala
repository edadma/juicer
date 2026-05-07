package io.github.edadma.juicer

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.github.edadma.path.Path

import java.net.InetSocketAddress

/** Minimal single-threaded static-file server backed by `com.sun.net.httpserver`.
  *
  * Intended for `juicer serve` — a local dev preview while authoring. Not a
  * production web server. Cross-platform support belongs in a separate
  * `microserve`-style library; the JS / Native variants of this file are
  * intentional stubs that print a "not implemented" message.
  */
def serve(root: Path, host: String = "localhost", port: Int = 8080): Unit = {
  val server = HttpServer.create(new InetSocketAddress(host, port), 0)
  server.createContext("/", StaticFileHandler(root))
  server.setExecutor(null) // single-threaded — fine for dev
  server.start()

  println(s"juicer serve: http://$host:$port/")
  println(s"  root: $root")
  println("Press Ctrl+C to stop.")

  // Block forever; the http server runs on its own thread.
  try Thread.currentThread().join()
  catch case _: InterruptedException => server.stop(0)
}

/** Resolve incoming paths to files under `root`. Directory requests fall
  * through to `index.html`. Returns `404 Not Found` if the resolved file
  * doesn't exist or isn't readable.
  */
private final class StaticFileHandler(root: Path) extends HttpHandler {
  def handle(ex: HttpExchange): Unit = {
    val raw     = ex.getRequestURI.getPath
    val rel     = if (raw == "/") "/index.html" else raw
    val sub     = rel.split('/').filter(_.nonEmpty).toList
    val located = sub.foldLeft(root)(_ / _)
    val target  = if (located.exists && located.isDirectory) located / "index.html" else located

    if (target.exists && target.isFile && target.isReadable) {
      val bytes = target.readBytes
      ex.getResponseHeaders.set("Content-Type", contentType(target.filename))
      ex.sendResponseHeaders(200, bytes.length.toLong)
      val out = ex.getResponseBody
      try out.write(bytes)
      finally out.close()
    } else {
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
          case _                     => "application/octet-stream"
        }
    }
}
