---
title: Configuration
summary: site.toml keys juicerpodcast reads, per-episode frontmatter, the [juicerpodcast] palette table, and the subscribe-strip.
weight: 10
---

juicerpodcast reads its configuration from three places:

- A small set of **top-level site keys**.
- A namespaced **`[juicerpodcast]` table** for palette + show artwork + the subscribe strip.
- **Per-episode frontmatter** — `audio:`, `duration:`, `image:`, etc.

## Quick example

```toml
title    = "Echo Chamber"
tagline  = "A weekly conversation with one interesting person"
author   = "Sara Lindholm"
baseURL  = "https://echochamber.example"
theme    = "juicerpodcast"

[[topnav]]
label = "Episodes"
url   = "/"

# Show artwork — used on the home hero and in the PodcastSeries JSON-LD.
[juicerpodcast]
cover = "/img/show-cover.jpg"

[[juicerpodcast.subscribe]]
label = "Apple Podcasts"
url   = "https://podcasts.apple.com/..."

[[juicerpodcast.subscribe]]
label = "RSS"
url   = "/feed.xml"
```

An episode (`content/ep01.md`):

```markdown
---
title: "Ep 01 — Pilot"
date: 2024-03-25
audio: /audio/ep01.mp3
audioType: audio/mpeg
audioLength: 9821330
duration: PT24M02S
image: /img/ep01.jpg
summary: The pilot episode.
tags: [meta]
---

Show notes go here.
```

## Top-level keys

| Key                | Type        | Default | Notes |
|--------------------|-------------|---------|-------|
| `title`            | `String`    | required | Show name. |
| `tagline`          | `String`    | unset   | Show tagline (under the hero title). |
| `description`      | `String`    | unset   | Default `<meta name="description">`. Also feeds `PodcastSeries.description`. |
| `author`           | `String`    | unset   | Host name. |
| `baseURL`          | `String`    | required | Standard juicer key. |
| `theme`            | `String`    | required | Set to `"juicerpodcast"`. |
| `favicon`          | `String`    | unset   | Path to a favicon. |
| `hideJuicerCredit` | `Boolean`   | `false` | Remove "Built with juicer" footer line. |
| `topnav`           | `[[topnav]]` | unset  | Topbar nav. |
| `links`            | `[[links]]` | unset   | Footer links. |
| `feeds`            | `Boolean`   | `true`  | Set `false` to disable the Atom feed entirely (you almost never want this for a podcast). |

## Per-episode frontmatter

| Key            | Type     | What |
|----------------|----------|------|
| `title`        | `String` | Required. |
| `date`         | `String` | Drives the feed `<updated>` and home-page ordering. |
| `audio`        | `String` | Required for the audio player + Atom enclosure + JSON-LD. Absolute URL or site-relative path. |
| `audioType`    | `String` | MIME type — `audio/mpeg` (mp3), `audio/aac` (m4a), `audio/ogg`. Feeds the enclosure `type=` attribute. |
| `audioLength`  | `Int`    | Bytes — feeds the enclosure `length=` attribute. Run `ls -l audio.mp3` to find it. Apple Podcasts validates this. |
| `duration`     | `String` | ISO 8601 — `PT42M30S`. Displayed in the meta line + JSON-LD. |
| `guest`        | `String` | Shown in the meta line as "with <guest>". |
| `image`        | `String` | Episode artwork. Falls back to the show cover if absent. |
| `imageAlt`     | `String` | Alt text for the artwork. |
| `summary`      | `String` | Card summary + `<meta name="description">` + JSON-LD description. |
| `links`        | `List`   | `{label, url}` entries shown as "Mentioned in this episode". |
| `transcript`   | `String` | Markdown string — rendered inside a `<details>` collapsible at the bottom. |
| `tags`         | `List`   | Drives `/tags/<slug>/` archives. |

## Atom feed enclosures

juicer's feed generator emits `<link rel="enclosure">` per entry that has frontmatter `audio:` (or `enclosure:`). The element follows RFC 4287 §4.2.7.2 and is picked up by podcast platforms the same way they'd read RSS 2.0 `<enclosure>`:

```xml
<link rel="enclosure"
      href="https://echochamber.example/audio/ep04.mp3"
      type="audio/mpeg"
      length="18347281"/>
```

`audioType` and `audioLength` are optional but recommended — without them the link still emits, but Apple may flag the episode. Frontmatter `enclosureType` and `enclosureLength` are accepted as aliases.

## `[juicerpodcast]` palette + subscribe overrides

```toml
[juicerpodcast]
brand        = "#2b1f4a"
brandStrong  = "#160d2d"
accent       = "#e36e5b"
cover        = "/img/show-cover.jpg"
fontSans     = "'Inter', system-ui, sans-serif"

# Subscribe-strip buttons on the home page
[[juicerpodcast.subscribe]]
label = "Apple Podcasts"
url   = "https://podcasts.apple.com/..."
```
