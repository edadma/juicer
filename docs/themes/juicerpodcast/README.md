# juicerpodcast

Audio-first blog / podcast theme for [juicer](https://github.com/edadma/juicer). Each episode is a markdown file with an `audio:` path — the page embeds a native `<audio>` player, the Atom feed emits a `<link rel="enclosure">` so podcast platforms can ingest it, and a schema.org `PodcastEpisode` JSON-LD block ships in the head.

## Shape

- **Episodes are flat or sectioned by season.** `content/ep-01.md` or `content/season-1/ep-01.md`.
- **Hero artwork + audio player + show notes** is the episode page shape.
- **Atom feed enclosures** for podcast platforms (Apple, Overcast, Pocket Casts). Frontmatter `audio:`, `audioType:`, `audioLength:` drive the enclosure attributes.
- **Subscribe strip** — `[[juicerpodcast.subscribe]]` in `site.toml` populates the home-page subscribe buttons.

## Frontmatter

```yaml
---
title: Episode 12 — Talking to Claude
date: 2024-04-01
audio: /audio/ep12.mp3
audioType: audio/mpeg
audioLength: 18347281        # bytes — read with `ls -l audio/ep12.mp3`
duration: PT42M30S           # ISO 8601 — shown on the episode card
guest: Alice Author
image: /img/ep12-art.jpg
summary: A conversation with Alice Author about writing with an AI partner.
tags: [interview, writing]
links:
  - { label: "Alice's site", url: "https://example.com" }
  - { label: "The book we discussed", url: "https://example.com/book" }
---

Show notes go in the markdown body. Drop a `## Transcript` heading
near the bottom and put the transcript text below it.
```

## site.toml palette overrides

```toml
[juicerpodcast]
brand        = "#2b1f4a"   # deep indigo
brandStrong  = "#160d2d"
accent       = "#e36e5b"   # coral

# Subscribe-strip buttons
[[juicerpodcast.subscribe]]
label = "Apple Podcasts"
url   = "https://podcasts.apple.com/..."

[[juicerpodcast.subscribe]]
label = "RSS"
url   = "/feed.xml"
```
