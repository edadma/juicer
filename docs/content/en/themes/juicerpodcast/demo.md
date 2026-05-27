---
title: Demo site
summary: Echo Chamber — a fictional weekly interview show with four episodes. Showcases the audio player, Atom enclosures, subscribe strip, and PodcastEpisode JSON-LD.
weight: 20
---

This page is the placeholder for the live juicerpodcast demo. When the docs site is built and deployed, this URL serves the actual demo rendered with juicerpodcast.

## What the demo exercises

"Echo Chamber" — a fictional weekly long-form interview show with four episodes:

- **Audio player + cover artwork** on every episode page.
- **Audio player on every home-page card** so listeners can sample without leaving the index.
- **Atom feed enclosures** at `/feed.xml`. Open it in a podcast platform's "add by URL" flow to confirm.
- **PodcastEpisode JSON-LD** on every episode page.
- **Subscribe strip** with four platforms.

## Preview locally

```bash
sbt 'juicerJVM/run serve -s docs/demos/juicerpodcast -L'
```

## Use it as a template

Copy `docs/demos/juicerpodcast/` into your own project, drop your audio files into `static/audio/`, write each episode as a markdown file with the `audio:` frontmatter, and the rest takes care of itself.
