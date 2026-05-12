---
title: Why static sites still win
date: 2024-12-04
authors: [ben, ed]
tags: [meta, web]
aliases: [/posts/why-static-2024/, /static-still-wins/]
summary: Generated HTML is fast, cheap, and ages well. A short defence of the boring choice.
---

A static site generator turns markdown + templates into HTML at build time. No runtime database, no app server, no per-request rendering — the bytes are just *there*, ready to be served by any file host that knows what a directory looks like.

## The boring case

It's hard to overstate how good this story is in 2024. CDNs are everywhere; the marginal cost of serving a request from cache is essentially zero. There's no scaling story to plan for, no failover to design, no security patches to chase down at 3 AM because some CMS plugin opened a hole.

You write markdown, you push to git, the site updates. That's the whole deployment story.

## The ageing case

A WordPress install from 2014 is a forensic exercise in 2024. A static HTML directory from 2014 still serves its content. The format is the format; browsers haven't lost the ability to render `<h1>`.

When the next paradigm shift hits — and there will be one — your static site keeps working. That's not nothing.
