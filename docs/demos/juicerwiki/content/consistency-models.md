---
title: Consistency Models
date: 2024-03-12
tags: [computing, theory]
summary: The trade-off between availability, consistency, and partition-tolerance.
---

A **consistency model** is a contract between a distributed system and its clients about what reads are allowed to see after writes happen.

## The big three

- **Linearizable** — every operation appears to take effect at a single instant between its invocation and its response. Hardest to provide; easiest to reason about.
- **Sequential** — operations from all clients appear in some single total order, but the order doesn't have to match real time.
- **Eventual** — reads can see stale data; given enough time without writes, all replicas converge.

## CAP theorem

Eric Brewer's [CAP theorem](https://www.glassbeam.com/sites/all/themes/glassbeam/images/blog/10.1.1.67.6951.pdf) says: in the face of a network partition, a system must choose between consistency and availability. There is no third option.

This is why most production systems are tunable: they offer linearizable reads with a latency cost, or fast eventually-consistent reads, and the client picks per request.

See [Distributed Systems](/distributed-systems/) for the broader context.
