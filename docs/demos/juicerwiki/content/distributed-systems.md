---
title: Distributed Systems
date: 2024-03-08
tags: [computing, theory]
summary: A grab-bag of notes on building reliable systems out of unreliable parts.
---

The fundamental problem of distributed computing: you have many machines, each of which fails independently, and you need them to agree on something.

## Eight fallacies

Peter Deutsch's classic list of things you must never assume about a distributed system:

1. The network is reliable.
2. Latency is zero.
3. Bandwidth is infinite.
4. The network is secure.
5. Topology doesn't change.
6. There is one administrator.
7. Transport cost is zero.
8. The network is homogeneous.

Every distributed-systems failure mode is a corollary of violating one of these. See [Consistency Models](/consistency-models/) for the most important consequence: you can't have everything.

## Consensus

The canonical problem is **consensus**: all nodes agree on a value despite some subset failing. Paxos and Raft are the standard solutions. The cost is a network round trip per decision.

The Zettelkasten note ([Zettelkasten](/zettelkasten/)) is unrelated content-wise, but the *shape* is the same — a graph of nodes that must converge by exchanging messages.
