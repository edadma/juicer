---
title: Backlinks
date: 2024-02-12
tags: [method, implementation]
summary: How the inverted link index gets built and rendered.
---

A backlink is a link pointing *to* a note, not *from* it. Tracking them turns a wiki from a directed tree into an undirected graph: you can follow the conversation in both directions.

## Construction

A backlinks index is just an inverted-index pass over the corpus. For every page, walk the rendered AST, collect every internal link destination, and append the source page to the destination's bucket.

The juicer engine does this during the markdown pass and exposes the result as `.page.backlinks` — a thin list of `{title, url, summary}` records, sorted by referrer title for deterministic output.

Tools like Roam, Obsidian, and Logseq made backlinks a first-class UI affordance; juicer just bakes the same idea into a static site generator. The implementation here is the same shape as [Zettelkasten](/zettelkasten/), just done at build time instead of at edit time.

## Edge cases

The collector filters:

- Absolute URLs (`https://…`, `mailto:`, `tel:`)
- Fragment-only anchors (`#section`)
- Self-links
- Query strings and fragments are stripped before matching, so `[X](/target/#section)` and `[X](/target/?q=1)` both count as a link to `/target/`.

See [Distributed Systems](/distributed-systems/) for an aside on graph algorithms generally.
