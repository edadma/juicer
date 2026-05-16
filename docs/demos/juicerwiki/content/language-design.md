---
title: Language Design
date: 2024-04-02
tags: [computing, theory, design]
summary: Notes on what makes a programming language pleasant to use over time.
---

The hardest part of programming language design is what you leave out. Every feature is forever; every feature interacts with every other feature; every feature is a thing the implementer has to maintain and the reader has to learn.

## The orthogonality test

Two features are orthogonal if they can be combined without surprise. If feature X breaks when used inside feature Y, the language has a wart that grows over time.

Most language warts come from features designed in isolation: pattern matching that doesn't compose with lambdas, generics that don't compose with subtyping, async/await that doesn't compose with iterators. The fix is to design every feature against every other feature *up front*, before either ships.

## Static vs dynamic

A pet division. Static typing pays a cost in expressiveness at the boundary (where types meet user-supplied data) and earns interest in the middle (where types meet other types). Dynamic typing pays its bill in inverse. Pick based on where the program spends most of its time. See [Distributed Systems](/distributed-systems/) for an unrelated note about another classic trade-off.
