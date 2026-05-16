---
title: juicerpodcast
summary: Audio-first blog / podcast theme. Native HTML5 audio player per episode, Atom feed with <link rel="enclosure"> entries, PodcastEpisode JSON-LD, subscribe-strip.
weight: 100
---

`juicerpodcast` turns juicer into a podcast hosting front-end. Each episode is a markdown file with an `audio:` path in frontmatter — the page embeds a native `<audio controls>` player, the Atom feed emits a `<link rel="enclosure">` so Apple / Overcast / Pocket Casts can ingest it, and a schema.org `PodcastEpisode` JSON-LD block ships in the head.

It lives at `docs/themes/juicerpodcast/` in the juicer repo:

```bash
juicer theme add https://github.com/edadma/juicer.git -n juicerpodcast
```

…and set `theme = "juicerpodcast"` in `site.toml`. The [Demo site](./demo/) — Echo Chamber, a fictional weekly interview show with four episodes — is the canonical example.

## What's in the box

An episode is a single markdown file with an `audio:` path in frontmatter. The home page is a vertical list of episode cards (artwork on the left, title + summary + inline audio player on the right). Subscribe buttons sit above the list; an RSS icon sits in the topbar.

- **Audio-first home page** — every episode card has an inline `<audio>` player so listeners can sample without leaving the index.
- **Native HTML5 audio** — no JS framework, no third-party player. Works in every browser, on every device.
- **Atom feed enclosures** — juicer's feed generator was extended to emit `<link rel="enclosure">` per entry that declares `audio:` in frontmatter. Apple Podcasts, Overcast, and Pocket Casts ingest this directly.
- **`PodcastSeries` + `PodcastEpisode` JSON-LD** — schema.org structured data for richer search / podcast directory results.
- **Subscribe strip** — `[[juicerpodcast.subscribe]]` in `site.toml` populates the home-page subscribe buttons (Apple Podcasts, Overcast, Spotify, RSS, etc.).
- **Show notes, mentions, transcript** — frontmatter `links:` renders as a "Mentioned in this episode" list; `transcript:` renders inside a `<details>` collapsible at the bottom of the page.
- **Episode prev/next nav** at the bottom of each episode.
- **Light + dark mode** — deep indigo / coral aesthetic.

## Frontmatter

```yaml
---
title: Episode 12 — Talking to Claude
date: 2024-04-01
audio: /audio/ep12.mp3
audioType: audio/mpeg
audioLength: 18347281        # bytes — `ls -l audio/ep12.mp3` to find it
duration: PT42M30S           # ISO 8601 — shown next to the date
guest: Alice Author
image: /img/ep12-art.jpg
summary: A conversation about writing with an AI partner.
tags: [interview, writing]
links:
  - { label: "Alice's site", url: "https://example.com" }
transcript: |
  > Sara: Welcome to the show.
  > Alice: Thanks, glad to be here.
---

Show notes go in the markdown body.
```

`audio:`, `audioType:`, and `audioLength:` are the three keys the Atom feed enclosure reads. The same keys also drive the JSON-LD `associatedMedia` block. Without `audioLength`, the enclosure still emits but Apple's podcast pipeline may flag it — set it (the file size in bytes) for production use.

## When to pick juicerpodcast vs juicerblog

**juicerblog** is for written posts. You can attach an audio file via the markdown body, but the Atom feed won't have proper enclosures and no platform will ingest the audio.

**juicerpodcast** is for audio-led content. The audio file is the entity; the show notes are the metadata. Pick this if you publish podcasts; pick juicerblog if you publish written posts that sometimes link to audio.
