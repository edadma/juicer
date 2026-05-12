---
title: Markdown is enough
date: 2024-10-30
author: ed
tags: [meta, writing]
summary: Plain-text markup turned out to be the right level of abstraction for the writing web.
---

Twenty years of WYSIWYG editors lost to a syntax invented for read-this-as-text emails. The lesson, in retrospect, is obvious: **text is the universal interchange format**, and markdown is the smallest possible layer of structure on top of it.

## Why markdown won

> A specification readable by ten-year-olds. A toolchain that runs in any language. Diffs that highlight prose, not bracket-soup.

You can read a markdown file as plain text and never miss a thing. You can also render it to HTML, PDF, an EPUB, a man page. The format encodes intent (`# Heading`, `**bold**`) without committing to a presentation.

## What it isn't

- Not a layout language. Use HTML or CSS for that.
- Not a programming language. No conditionals, no loops, no data flow.
- Not WYSIWYG. The preview is downstream of the source.

These limits are what makes markdown durable. Add a third comma and you get reST; add a fourth and you get TeX. Each step toward power is a step away from "anyone can author this on a phone."
