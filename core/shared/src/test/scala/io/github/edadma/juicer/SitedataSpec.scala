package io.github.edadma.juicer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for `.site.posts` and `.site.pagesByYear` — extracted from
  * JuicerBuildSpec for readability.
  */
class SitedataSpec extends AnyFlatSpec with Matchers with JuicerTestSupport {

  "juicer sitedata" should "expose .site.posts filtered to dated, non-section, non-static pages, newest first" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt(
      "content/_index.md",
      "---\ntitle: Home\n---\n",
    )
    writeAt(
      "content/older.md",
      "---\ntitle: Older\ndate: 2024-01-15\n---\n",
    )
    writeAt(
      "content/newer.md",
      "---\ntitle: Newer\ndate: 2024-09-01\n---\n",
    )
    writeAt(
      "content/about.md",
      "---\ntitle: About\nstatic: true\ndate: 2024-06-01\n---\n",
    )
    writeAt(
      "content/undated.md",
      "---\ntitle: Undated\n---\n",
    )
    writeAt("layouts/_default/file.html",   "x")
    writeAt(
      "layouts/_default/folder.html",
      "{{ for p <- .site.posts }}[{{ p.title }}]{{ end }}",
    )

    build()

    // .site.posts has Newer + Older only, in that order. About filtered
    // (static), Undated filtered (no parsed date), Home filtered (section).
    out("index.html").trim shouldBe "[Newer][Older]"
  }

  it should "expose .site.authorRegistry as the raw [[authors]] list in declaration order" in {
    writeAt(
      "site.toml",
      """title = "S"
        |baseURL = "http://x"
        |
        |[[authors]]
        |id = "alice"
        |name = "Alice Author"
        |role = "Founder"
        |
        |[[authors]]
        |id = "bob"
        |name = "Bob Builder"
        |role = "Volunteer"
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: H\n---\n")
    writeAt("layouts/_default/file.html", "x")
    writeAt(
      "layouts/_default/folder.html",
      "{{ for a <- .site.authorRegistry }}[{{ a.id }}|{{ a.name }}|{{ a.role }}]{{ end }}",
    )

    build()

    // Both authors render in declaration order, regardless of whether they
    // have any referencing pages — that's the registry shape needed for
    // staff-directory layouts.
    out("index.html").trim shouldBe "[alice|Alice Author|Founder][bob|Bob Builder|Volunteer]"
  }

  it should "expose .site.now with iso/date/long/year keys captured at build time" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n")
    writeAt("layouts/_default/file.html", "x")
    writeAt(
      "layouts/_default/folder.html",
      "iso={{ .site.now.iso }}|date={{ .site.now.date }}|long={{ .site.now.long }}|year={{ .site.now.year }}",
    )

    build()

    val rendered = out("index.html").trim
    // ISO: `YYYY-MM-DDTHH:MM:SS<offset>`. Just match the date prefix to avoid
    // race-y minute boundaries.
    val today = java.time.OffsetDateTime
      .now(java.time.ZoneOffset.UTC)
      .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val year  = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).getYear

    rendered should include(s"iso=${today}T")
    rendered should include(s"date=$today")
    rendered should include(s"year=$year")
    // long is "Month D, YYYY" — just confirm the year is there at the end of
    // that piece.
    rendered should include regex s"""long=[A-Z][a-z]+ \\d{1,2}, $year"""
  }

  it should "expose .site.events as the dated event-section pages, sorted ascending — including future dates" in {
    // Mix of past + future dates. Events are exempt from the future-post
    // filter (they're announced *before* they happen) so all four dated
    // event pages should surface, sorted ascending. The non-event page
    // (`news/elsewhere.md`) is a regular post and DOES get future-skipped
    // — but we use a past date for it here so it'd survive on its own;
    // the assertion is that it's filtered by the *section* check, not by
    // future-skip.
    val past1   = "2024-03-15"
    val past2   = "2024-09-01"
    val future1 = java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusMonths(3L).toString
    val future2 = java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusYears(1L).toString
    writeAt(
      "site.toml",
      "title = \"S\"\nbaseURL = \"http://x\"\n",
    )
    writeAt("content/_index.md", "---\ntitle: H\n---\n")
    writeAt("content/events/_index.md", "---\ntitle: Events\n---\n")
    writeAt("content/events/early.md",  s"---\ntitle: Early\ndate: $past1\n---\n")
    writeAt("content/events/middle.md", s"---\ntitle: Middle\ndate: $past2\n---\n")
    writeAt("content/events/soon.md",   s"---\ntitle: Soon\ndate: $future1\n---\n")
    writeAt("content/events/farout.md", s"---\ntitle: Farout\ndate: $future2\n---\n")
    writeAt("content/events/undated.md", "---\ntitle: Undated\n---\n")
    writeAt("content/news/elsewhere.md", s"---\ntitle: Elsewhere\ndate: $past1\n---\n")
    writeAt("layouts/_default/file.html", "x")
    writeAt(
      "layouts/_default/folder.html",
      "{{ for e <- .site.events }}[{{ e.title }}]{{ end }}",
    )

    build()

    out("index.html").trim shouldBe "[Early][Middle][Soon][Farout]"
  }

  it should "still emit detail pages for future-dated events" in {
    // Regression guard for the future-filter exemption: a future-dated
    // *event* must render its own detail page. Future-dated *posts*
    // continue to be future-skipped (FuturePostsSpec covers that).
    val future = java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusMonths(6L).toString
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: H\n---\n")
    writeAt("content/events/_index.md", "---\ntitle: E\n---\n")
    writeAt("content/events/upcoming.md", s"---\ntitle: Upcoming\ndate: $future\n---\nbody\n")
    writeAt("content/blog/draft.md",      s"---\ntitle: Draft\ndate: $future\n---\nbody\n")
    writeAt("layouts/_default/file.html", "{{ .page.title }}")
    writeAt("layouts/_default/folder.html", "x")

    build()

    // The default `htmlDir = "html"` puts rendered pages under `html/`.
    out("html/events/upcoming/index.html").trim shouldBe "Upcoming"
    // Posts continue to be future-skipped — the draft's detail page must not exist.
    val draftPath = (((dst / "html") / "blog") / "draft") / "index.html"
    draftPath.exists shouldBe false
  }

  it should "honour eventsSection override" in {
    writeAt(
      "site.toml",
      "title = \"S\"\nbaseURL = \"http://x\"\neventsSection = \"calendar\"\n",
    )
    writeAt("content/_index.md", "---\ntitle: H\n---\n")
    writeAt("content/calendar/_index.md", "---\ntitle: C\n---\n")
    writeAt(
      "content/calendar/x.md",
      "---\ntitle: X\ndate: 2024-04-01\n---\n",
    )
    writeAt(
      "content/events/y.md",
      "---\ntitle: Y\ndate: 2024-05-01\n---\n",
    )
    writeAt("layouts/_default/file.html", "x")
    writeAt(
      "layouts/_default/folder.html",
      "{{ for e <- .site.events }}[{{ e.title }}]{{ end }}",
    )

    build()

    out("index.html").trim shouldBe "[X]"
  }

  it should "expose .site.calendar as 12 months from current, each with six 7-day weeks" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: H\n---\n")
    writeAt("layouts/_default/file.html", "x")
    writeAt(
      "layouts/_default/folder.html",
      """months={{ count := 0 }}{{ for m <- .site.calendar }}{{ count := count + 1 }}{{ end }}{{ count }}|""" +
        """firstWeeks={{ wcount := 0 }}{{ for w <- .site.calendar[0].weeks }}{{ wcount := wcount + 1 }}{{ end }}{{ wcount }}|""" +
        """firstWeekCells={{ ccount := 0 }}{{ for c <- .site.calendar[0].weeks[0] }}{{ ccount := ccount + 1 }}{{ end }}{{ ccount }}""",
    )

    build()

    out("index.html").trim shouldBe "months=12|firstWeeks=6|firstWeekCells=7"
  }

  it should "expand a weekly recurring event onto every matching weekday in .site.calendar" in {
    // Past start date so the event isn't future-filtered. We don't know
    // which calendar month "now" lands in, so just count Wednesdays across
    // the whole 12-month surface — any way you slice it, 12 months contain
    // 52 or 53 Wednesdays, but the recurring event's start clips the count
    // to whatever lies between the start month and the end of the surface.
    // Easier assertion: at least 50 occurrences (well below either ceiling
    // but well above zero).
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: H\n---\n")
    writeAt("content/events/_index.md", "---\ntitle: E\n---\n")
    writeAt(
      "content/events/weekly.md",
      // 2020-01-01 (a Wednesday). Far enough in the past that the
      // recurring event has been "ongoing" for all 12 months we render.
      "---\ntitle: Weekly\ndate: 2020-01-01\nrecurring: weekly\nrecurringDay: Wednesday\n---\n",
    )
    writeAt("layouts/_default/file.html", "x")
    writeAt(
      "layouts/_default/folder.html",
      """{{ wedCount := 0 }}{{ for m <- .site.calendar }}{{ for w <- m.weeks }}{{ for c <- w }}{{ for e <- c.events }}{{ if e.title = "Weekly" }}{{ wedCount := wedCount + 1 }}{{ end }}{{ end }}{{ end }}{{ end }}{{ end }}wedCount={{ wedCount }}""",
    )

    build()

    val s = out("index.html").trim
    val n = s.stripPrefix("wedCount=").toInt
    n should (be >= 50 and be <= 53)
  }

  it should "aggregate `photos:` frontmatter into .site.photos sorted newest-first" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: H\n---\n")
    writeAt(
      "content/older.md",
      "---\ntitle: Older\ndate: 2024-03-15\nphotos:\n" +
        "  - \"/img/a1.svg\"\n" +
        "  - { src: \"/img/a2.svg\", caption: \"At the picnic\" }\n" +
        "---\n",
    )
    writeAt(
      "content/newer.md",
      "---\ntitle: Newer\ndate: 2024-09-01\nphotos:\n" +
        "  - \"/img/b1.svg\"\n" +
        "---\n",
    )
    writeAt("content/no-photos.md", "---\ntitle: NoPhotos\ndate: 2024-06-01\n---\n")
    writeAt("layouts/_default/file.html", "x")
    writeAt(
      "layouts/_default/folder.html",
      "{{ for p <- .site.photos }}[{{ p.src }}|{{ p.caption }}|{{ p.page.title }}]{{ end }}",
    )

    build()

    // Newer's photos first (b1), then Older's (a1, a2 in declaration order).
    out("index.html").trim shouldBe "[/img/b1.svg||Newer][/img/a1.svg||Older][/img/a2.svg|At the picnic|Older]"
  }

  it should "expose .page.slug as the URL stem (last path segment, no slashes)" in {
    // For section pages it's the section name; for leaf pages it's the
    // file stem; for the root index it's the empty string. Useful for
    // in-page anchors when a layout walks .section.pages and wants a
    // stable per-section HTML id.
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: Home\n---\n")
    writeAt("content/menu/_index.md", "---\ntitle: Menu\n---\n")
    writeAt("content/menu/espresso.md", "---\ntitle: Espresso\nstatic: true\n---\n")
    writeAt("layouts/_default/file.html", "[{{ .page.slug }}]")
    writeAt("layouts/_default/folder.html", "[{{ .page.slug }}]")

    build()

    // The root `_index` lands at the bare top of the output tree;
    // everything else is wrapped under the default `htmlDir = "html"`.
    out("index.html").trim shouldBe "[]"
    out("html/menu/index.html").trim shouldBe "[menu]"
    out("html/menu/espresso/index.html").trim shouldBe "[espresso]"
  }

  it should "render /authors/index.html when a registry exists, even with no referencing pages" in {
    // Regression for the original behaviour where /authors/ was only emitted
    // if at least one page had `author:` frontmatter — that's right for the
    // per-author archive, but wrong for the team/staff list. juicercafe
    // surfaces a "team" page that's about who works there, not who has
    // published posts.
    writeAt(
      "site.toml",
      """title = "S"
        |baseURL = "http://x"
        |
        |[[authors]]
        |id = "rosa"
        |name = "Rosa"
        |role = "Owner"
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: H\n---\n")
    writeAt("layouts/_default/file.html", "x")
    writeAt("layouts/_default/folder.html", "x")
    writeAt(
      "layouts/_default/author-list.html",
      "REGISTRY:{{ for a <- .site.authorRegistry }}[{{ a.id }}]{{ end }}",
    )

    build()

    // /authors/index.html exists and walks the full registry (one author),
    // even though no page has `author: rosa` in its frontmatter.
    out("authors/index.html").trim shouldBe "REGISTRY:[rosa]"
  }

  it should "skip /authors/<id>/index.html for authors with no referencing pages" in {
    // The per-author archive page is a list of THEIR posts; emitting it for
    // an author with zero posts would be misleading. Author-list (above)
    // and author-page have different gates on purpose.
    writeAt(
      "site.toml",
      """title = "S"
        |baseURL = "http://x"
        |
        |[[authors]]
        |id = "rosa"
        |name = "Rosa"
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: H\n---\n")
    writeAt("layouts/_default/file.html", "x")
    writeAt("layouts/_default/folder.html", "x")
    writeAt("layouts/_default/author-list.html", "L")
    writeAt("layouts/_default/author-page.html", "P")

    build()

    out("authors/index.html").trim shouldBe "L"
    (dst / "authors" / "rosa" / "index.html").exists shouldBe false
  }

  it should "default .site.authorsPath to /authors/ when site.toml doesn't set it" in {
    writeAt(
      "site.toml",
      """title = "S"
        |baseURL = "http://x"
        |
        |[[authors]]
        |id = "rosa"
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: H\n---\n")
    writeAt("layouts/_default/file.html", "x")
    writeAt("layouts/_default/folder.html", "[{{ .site.authorsPath }}]")

    build()

    // Default surfaces on .site.authorsPath, and the per-author archive is
    // emitted at the legacy /authors/ tree.
    out("index.html").trim shouldBe "[/authors/]"
    (dst / "authors" / "index.html").exists shouldBe false  // no author-list layout
  }

  it should "honour site.toml authorsPath = \"/team/\" — emit team listing + per-author at /team/" in {
    // The engine wires the URL prefix through three places: the .site.authorsPath
    // string surfaced for templates, the `url` field on each .site.authors term,
    // and the on-disk output directory for author-list / author-page layouts.
    // All three should pivot from /authors/ to /team/ in lockstep when the
    // setting is overridden — this is the regression a missed call site would
    // surface as.
    writeAt(
      "site.toml",
      """title = "S"
        |baseURL = "http://x"
        |authorsPath = "/team/"
        |
        |[[authors]]
        |id = "rosa"
        |name = "Rosa"
        |""".stripMargin,
    )
    writeAt("content/_index.md", "---\ntitle: H\n---\n")
    writeAt("content/post.md", "---\ntitle: Post\ndate: 2025-01-01\nauthor: rosa\n---\nbody\n")
    writeAt("layouts/_default/file.html", "x")
    writeAt(
      "layouts/_default/folder.html",
      "[{{ .site.authorsPath }}]{{ for a <- .site.authors }}<{{ a.id }}|{{ a.url }}>{{ end }}",
    )
    writeAt("layouts/_default/author-list.html", "L:{{ .site.authorsPath }}")
    writeAt("layouts/_default/author-page.html", "P:{{ .author.id }}")

    build()

    // .site.authorsPath surfaces the override, and each author term's url uses it.
    out("index.html").trim shouldBe "[/team/]<rosa|/team/rosa/>"
    // Files live at /team/, NOT /authors/.
    out("team/index.html").trim shouldBe "L:/team/"
    out("team/rosa/index.html").trim shouldBe "P:rosa"
    (dst / "authors").exists shouldBe false
  }

  it should "accept forgiving authorsPath shapes (team, /team, team/, /team/) and normalize" in {
    // Be lenient with what site authors write — silently normalize to the
    // canonical /seg/ form rather than failing or emitting weird paths.
    for (raw <- Seq("team", "/team", "team/", "/team/")) {
      writeAt(
        "site.toml",
        s"""title = "S"
           |baseURL = "http://x"
           |authorsPath = "$raw"
           |
           |[[authors]]
           |id = "rosa"
           |""".stripMargin,
      )
      writeAt("content/_index.md", "---\ntitle: H\n---\n")
      writeAt("layouts/_default/file.html", "x")
      writeAt("layouts/_default/folder.html", "[{{ .site.authorsPath }}]")

      build()

      out("index.html").trim shouldBe "[/team/]"
    }
  }

  it should "expose .site.pagesByYear grouping the same posts list by year, year descending" in {
    writeAt("site.toml", "title = \"S\"\nbaseURL = \"http://x\"\n")
    writeAt("content/_index.md", "---\ntitle: H\n---\n")
    writeAt("content/a.md", "---\ntitle: A\ndate: 2023-04-01\n---\n")
    writeAt("content/b.md", "---\ntitle: B\ndate: 2024-02-15\n---\n")
    writeAt("content/c.md", "---\ntitle: C\ndate: 2024-11-30\n---\n")
    writeAt("layouts/_default/file.html", "x")
    writeAt(
      "layouts/_default/folder.html",
      "{{ for y <- .site.pagesByYear }}<{{ y.year }}:{{ y.count }}>{{ for p <- y.pages }}({{ p.title }}){{ end }}{{ end }}",
    )

    build()

    // 2024 first (newest year), C then B inside; then 2023 with just A.
    out("index.html").trim shouldBe "<2024:2>(C)(B)<2023:1>(A)"
  }
}
