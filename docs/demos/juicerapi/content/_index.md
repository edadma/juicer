---
title: Bristle
headingShift: 0
layout: api-index
summary: A small library for text and byte buffers. Two modules, no allocator on the text side.
version: 0.4.0
---

Bristle is a demonstration package. Its documentation is generated from the doc
comments on its declarations — one page per module, which is the unit a reader
imports.

## Modules

| Module | Summary |
|---|---|
| [`bristle.text`](text/) | Searching, splitting and trimming over UTF-8 strings. |
| [`bristle.buf`](buf/) | A growable byte buffer with an explicit capacity. |

## Reading these pages

Each module page opens with an index of its symbols, then lists them grouped by
kind. A declaration's signature is the fenced block directly under its name;
where the author wrote `@param` and `@return` tags, they become the parameter
table and the returns line.

A module documented in a literate source shows its signatures and little else.
That is not a defect — the narrative in a `.lsysl` file is an essay about the
program and belongs to the weaver, not to an API reference.
