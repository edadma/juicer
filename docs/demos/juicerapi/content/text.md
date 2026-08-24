---
title: bristle.text
headingShift: 0
layout: api-module
module: bristle.text
summary: Searching, splitting and trimming over UTF-8 strings. Every function here answers a slice of its argument rather than a copy, so the module needs no allocator.
package: bristle
coordinate: github.com/example/bristle
requires: "{}"
since: "0.1.0"
---

## Index

[`starts_with`](#starts_with) [`ends_with`](#ends_with) [`find`](#find)
[`split_once`](#split_once) [`trim`](#trim) [`Cursor`](#cursor)
[`Utf8Error`](#utf8error)

## Functions

### `starts_with`

```sysl
starts_with(s: []const u8, prefix: []const u8) -> bool
```

Answers whether `s` begins with `prefix`.

An empty prefix is present in every string, so `starts_with(s, "")` answers
`true` for every `s` — including an empty one.

| Parameter | Type | Description |
|---|---|---|
| `s` | `[]const u8` | the bytes to test |
| `prefix` | `[]const u8` | the prefix to look for |

**Returns** `bool` — `true` when `s` begins with `prefix`.

### `ends_with`

```sysl
ends_with(s: []const u8, suffix: []const u8) -> bool
```

Answers whether `s` ends with `suffix`. The mirror of `starts_with`, and it
treats an empty suffix the same way.

| Parameter | Type | Description |
|---|---|---|
| `s` | `[]const u8` | the bytes to test |
| `suffix` | `[]const u8` | the suffix to look for |

**Returns** `bool` — `true` when `s` ends with `suffix`.

### `find`

```sysl
find(haystack: []const u8, needle: []const u8) -> Option[usize]
```

Answers the byte offset of the first occurrence of `needle` in `haystack`.

The offset is in **bytes**, not characters — a needle found after a
three-byte character is at offset 3. Nothing here decodes UTF-8, which is what
lets the module make no allocation and ask for no capability.

| Parameter | Type | Description |
|---|---|---|
| `haystack` | `[]const u8` | the bytes to search |
| `needle` | `[]const u8` | the bytes to search for |

**Returns** `Option[usize]` — `Some(i)` where `i` is the offset of the first
occurrence, or `None` when `needle` does not occur. An empty needle occurs at
offset 0.

### `split_once`

```sysl
split_once(s: []const u8, sep: []const u8) -> Option[([]const u8, []const u8)]
```

Splits `s` at the first occurrence of `sep` and answers the two halves, neither
of which contains the separator.

Both halves are slices **of `s`**, so they live exactly as long as it does and
cost nothing to produce. Splitting `"key=value=more"` on `"="` answers
`("key", "value=more")` — the separator is found once, not greedily.

| Parameter | Type | Description |
|---|---|---|
| `s` | `[]const u8` | the bytes to split |
| `sep` | `[]const u8` | the separator to split at |

**Returns** `Option[([]const u8, []const u8)]` — the two halves, or `None` when
`sep` does not occur in `s`.

### `trim`

```sysl
trim(s: []const u8) -> []const u8
```

Answers `s` without leading or trailing ASCII whitespace.

Whitespace here is space, tab, carriage return, line feed, form feed and
vertical tab — the set C's `isspace` uses in the C locale. Unicode whitespace
is deliberately not recognised, for the same reason `find` does not decode: it
would make the module need a table.

> [!NOTE]
> A string that is entirely whitespace trims to an empty slice, not to `None`.

| Parameter | Type | Description |
|---|---|---|
| `s` | `[]const u8` | the bytes to trim |

**Returns** `[]const u8` — a sub-slice of `s`.

## Types

### `Cursor`

```sysl
struct Cursor
    src: []const u8
    pos: usize
```

A position within a byte slice, for walking one token at a time.

A cursor borrows its source rather than owning it, so it must not outlive the
slice it was made from. Reading past the end is not an error — the reading
members answer `None` instead.

| Field | Type | Description |
|---|---|---|
| `src` | `[]const u8` | the bytes being walked |
| `pos` | `usize` | the current offset, in bytes, from the start of `src` |

### `Utf8Error`

```sysl
enum Utf8Error
    Truncated(usize)
    Invalid(usize)
```

What went wrong while decoding, and where.

Both variants carry the byte offset at which the problem was found, so a caller
can report the position without walking the input a second time.

| Variant | Payload | Description |
|---|---|---|
| `Truncated` | `usize` | the input ended part-way through a character |
| `Invalid` | `usize` | the byte at this offset can begin no character |

## Traits

### `Scan`

```sysl
trait Scan
    type Item
    next(self) -> Option[Self.Item]
```

What a thing that yields items one at a time has to provide.

Implemented by `Cursor`, and by anything else that walks a source without
owning it. The associated `Item` is what settles what a walk yields, so one
trait covers a cursor over bytes and a cursor over lines.

**Members**

| Member | Signature | Description |
|---|---|---|
| `Item` | associated type | what each step yields |
| `next` | `(self) -> Option[Self.Item]` | the next item, or `None` at the end |
