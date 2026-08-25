---
title: bristle.buf
headingShift: 0
slugStyle: github
layout: api-module
module: bristle.buf
summary: A growable byte buffer with an explicit capacity, for building output without a copy per append.
package: bristle
coordinate: github.com/example/bristle
requires: "{ alloc }"
since: "0.1.0"
---

## Index

[`buf`](#buf) [`with_capacity`](#with_capacity) [`push`](#push)
[`extend`](#extend) [`as_slice`](#as_slice) [`clear`](#clear) [`Buf`](#buf-1)
[`Full`](#full)

## Functions

### `buf`

```sysl
buf() -> Buf
```

Answers an empty buffer that has allocated nothing yet.

The first `push` allocates. A buffer that is built and dropped without ever
being written to therefore costs no allocation at all, which is why this rather
than a capacity is the default constructor.

**Returns** `Buf` — an empty buffer with a capacity of zero.

### `with_capacity`

```sysl
with_capacity(n: usize) -> Buf
```

Answers an empty buffer with room for `n` bytes already reserved.

Reach for this when the size is known — building a line of known width, or
encoding a value whose bound the caller can compute. It turns a run of
doubling reallocations into one.

| Parameter | Type | Description |
|---|---|---|
| `n` | `usize` | how many bytes to reserve |

**Returns** `Buf` — an empty buffer whose capacity is at least `n`.

### `push`

```sysl
push(self, b: u8) -> Result[unit, Full]
```

Appends one byte, growing the buffer if it is full.

| Parameter | Type | Description |
|---|---|---|
| `self` | `&Buf` | the buffer to append to |
| `b` | `u8` | the byte to append |

**Returns** `Result[unit, Full]` — `Ok(())`, or `Err(Full)` when the allocator
refused. A refused push leaves the buffer exactly as it was.

### `extend`

```sysl
extend(self, bytes: []const u8) -> Result[unit, Full]
```

> [!WARNING]
> A failed `extend` is **not** atomic — the bytes that fit are kept. Read the
> length back if you need to know how far it got.

Appends every byte of `bytes`, growing once rather than once per byte.

| Parameter | Type | Description |
|---|---|---|
| `self` | `&Buf` | the buffer to append to |
| `bytes` | `[]const u8` | the bytes to append |

**Returns** `Result[unit, Full]` — `Ok(())`, or `Err(Full)` when the allocator
refused part-way.

### `as_slice`

```sysl
as_slice(self) -> []const u8
```

Answers the written bytes as a slice, without copying them.

The slice borrows the buffer's storage, so it is invalidated by the next
`push`, `extend` or `clear`. Hand it to a reader that finishes with it before
the buffer is touched again.

| Parameter | Type | Description |
|---|---|---|
| `self` | `&Buf` | the buffer to read |

**Returns** `[]const u8` — the bytes written so far, in order.

### `clear`

```sysl
clear(self)
```

Forgets the written bytes and keeps the storage.

The capacity is unchanged, which is the point: a buffer cleared between
iterations of a loop allocates once for the whole loop rather than once per
pass.

| Parameter | Type | Description |
|---|---|---|
| `self` | `&Buf` | the buffer to clear |

## Types

### `Buf`

```sysl
struct Buf
    ptr: *u8
    len: usize
    cap: usize
```

A growable byte buffer.

The fields are private to the module; they are listed because a reader working
out the cost of an operation needs to know there are three of them and that
none is a length prefix stored with the data.

| Field | Type | Description |
|---|---|---|
| `ptr` | `*u8` | the allocated storage, or null at capacity zero |
| `len` | `usize` | how many bytes have been written |
| `cap` | `usize` | how many bytes `ptr` has room for |

### `Full`

```sysl
struct Full
end Full
```

The allocator refused to grow the buffer.

It carries nothing: there is one way for an append to fail and no detail a
caller could act on. A program that wants to distinguish *out of memory* from
*over a configured ceiling* wants two allocators, not two errors.
