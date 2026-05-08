package io.github.edadma.juicer

/** Pagination helpers for section list pages and taxonomy archives.
  *
  * Both paginated surfaces share the same shape — a flat list of
  * already-rendered page records, sliced into uniform runs and addressed by a
  * `<base>/page/N/` URL convention. The output `Slice` carries everything a
  * list-layout template needs: the current slice, the slice's pages, neighbour
  * URLs for prev/next, and total/first/last anchors for a numbered pager.
  *
  * Empty inputs collapse to a single empty slice so the list page still
  * renders (a category with zero posts is a valid edge case during early
  * authoring). Inputs at or below `size` collapse to a single full slice with
  * `total = 1` and empty prev/next URLs — templates can either iterate
  * `.paginator.pages` unconditionally or branch on `.paginator.total`.
  */
object Paginate {

  /** One slice of a paginated list, plus the metadata templates need to render
    * a pager UI around it.
    *
    * Pages are passed through as `Map[String, Any]` (squiggly's any-data
    * shape) so the helper stays content-shape-agnostic — sections, tags,
    * categories, and any future taxonomy can all funnel through the same
    * slicing path without growing a richer type. */
  case class Slice(
      current: Int,
      total:   Int,
      pages:   List[Map[String, Any]],
      url:     String,                 // URL of THIS slice
      first:   String,
      last:    String,
      prevURL: String,                 // "" on slice 1
      nextURL: String,                 // "" on the last slice
  )

  /** Slice `all` into runs of `size` pages. `baseURL` is the URL of the
    * section / archive index (must end with `/`); slice 1 lives at `baseURL`
    * itself, slice N at `<baseURL>page/N/`.
    *
    * Throws `IllegalArgumentException` for non-positive `size` — that's a
    * configuration bug callers should surface eagerly.
    */
  def paginate(all: List[Map[String, Any]], size: Int, baseURL: String): List[Slice] = {
    require(size > 0, s"paginate size must be positive, got $size")
    val groups = if (all.isEmpty) List(Nil) else all.grouped(size).toList
    val total  = groups.length
    val first  = baseURL
    val last   = if (total <= 1) baseURL else s"${baseURL}page/$total/"
    groups.zipWithIndex.map { case (group, idx) =>
      val current = idx + 1
      val url     = if (current == 1) baseURL else s"${baseURL}page/$current/"
      val prevURL =
        if (current <= 1) ""
        else if (current == 2) baseURL
        else s"${baseURL}page/${current - 1}/"
      val nextURL = if (current >= total) "" else s"${baseURL}page/${current + 1}/"
      Slice(current, total, group, url, first, last, prevURL, nextURL)
    }
  }

  /** Surface a `Slice` as a `Map[String, Any]` for squiggly's renderer.
    * Numeric fields ride as `BigDecimal` so squiggly's comparison handler
    * (`{{ if .total > 1 }}`) sees a shape it knows — Java `Integer` falls
    * through with a `MatchError`. */
  def sliceToMap(s: Slice): Map[String, Any] =
    Map(
      "current" -> BigDecimal(s.current),
      "total"   -> BigDecimal(s.total),
      "pages"   -> s.pages,
      "url"     -> s.url,
      "first"   -> s.first,
      "last"    -> s.last,
      "prevURL" -> s.prevURL,
      "nextURL" -> s.nextURL,
    )
}
