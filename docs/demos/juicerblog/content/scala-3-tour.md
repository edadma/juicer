---
title: A whirlwind tour of Scala 3
date: 2024-11-18
author: ed
tags: [scala, language]
summary: Five things Scala 3 gets right, illustrated with the smallest examples that make the case.
---

Scala 3 cleans up a decade of accreted complexity. Five things land squarely:

## Significant indentation

```scala
class Box[T](var value: T):
  def map[U](f: T => U): Box[U] =
    Box(f(value))
```

Curly braces are still legal, but most code reads better without them. The compiler accepts both styles — pick what fits.

## Enums are first-class

```scala
enum Json:
  case Obj(fields: Map[String, Json])
  case Arr(elements: List[Json])
  case Str(value: String)
  case Num(value: BigDecimal)
  case Bool(value: Boolean)
  case Null
```

A real algebraic-data-type syntax. Pattern matching gets exhaustiveness checking automatically.

## Given / using replaces implicit

```scala
trait Show[T]:
  def show(t: T): String

given Show[Int] with
  def show(n: Int): String = n.toString

def render[T](t: T)(using s: Show[T]): String = s.show(t)
```

The intent reads off the page now: `given` introduces an instance, `using` consumes one.

## Extension methods

```scala
extension (s: String)
  def shout: String = s.toUpperCase + "!"
```

Cleaner than the implicit-class song-and-dance Scala 2 made you do.

## Match types and inline

The advanced features (match types, transparent inline, capture checking) round out a language that finally fits in your head.
