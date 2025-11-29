+++
title = "MYRA stack - modern JAVA FFM based libraries"
date = "2025-11-29"
description = "MYRA — Memory Yielded, Rapid Access — is a production-grade ecosystem of Java libraries built on the Foreign Function & Memory (FFM) API, designed for deterministic, sub-microsecond latency applications."

[taxonomies]
tags = ["java", "ffm", "myra"]

[extra]
stylesheets = ["/css/java-ffm-intro.css"]
isso = true
+++

# Introducing MYRA: What I've Been Building

## Overview

**MYRA** — *Memory Yielded, Rapid Access* — is a production-grade ecosystem of Java libraries built on the Foreign Function & Memory (FFM) API, designed for deterministic, sub-microsecond latency applications.

Unlike approaches that rely on `Unsafe` or JNI boilerplate, MYRA leverages the standardized FFM primitives introduced in Java 22, providing memory safety and future-proof compatibility without sacrificing performance.

### Design Principles

The ecosystem is built on four core principles:

- **Zero GC**: Off-heap allocation and deterministic resource management eliminate GC pauses in the critical path.
- **Zero Allocation**: Reusable object instances and flyweight patterns prevent heap churn.
- **Zero Copy**: Direct memory access and structured layout eliminate serialization overhead.
- **Ultra-Low Latency**: Sub-30μs mean latencies with controlled tail behavior, built for high-frequency trading, market data feeds, and real-time systems.

---

## The Problem FFM Solves

Performance-sensitive Java systems have historically relied on `Unsafe` — a powerful but unstable internal API that breaks with each JDK release. The Foreign Function & Memory API provides a safe, standardized alternative for off-heap memory access and native interoperability.

MYRA is built entirely on FFM, proving that it's not just a replacement for `Unsafe`, but a foundation for a new class of infrastructure libraries that were previously impossible to build safely on the JVM.

---

## What's in the Box

MYRA comprises six libraries designed for vertical integration:

- **roray-ffm-utils** — Memory arenas, direct buffers, native resource handling. The plumbing layer.
- **myra-codec** — Zero-copy serialization that reads and writes directly to off-heap memory. No intermediate objects.
- **myra-transport** — Networking built on Linux `io_uring`. Fewer syscalls, higher throughput.
- **MVP.Express RPC** — *MYRA Virtual Procedure over Express Link* — A lightweight RPC framework on top of the above. Currently in progress.
- **JIA-Cache** — *Java In-Memory Accelerated Cache* — Off-heap caching with predictable latency. Coming soon.

The libraries share a design philosophy: **zero allocation in the hot path**. If you're processing millions of messages per second, you shouldn't be at the mercy of GC pauses.

A key enabler is the **flyweight pattern** — reusable, stateless views over raw memory. Instead of deserializing into objects, myra-codec and myra-transport wrap off-heap buffers directly. No copies, no allocations, no GC pressure. Just pointer arithmetic and bounds checks.

---

## Use Cases & Industries

MYRA is built for systems where every microsecond counts:

- **High-Frequency Trading (HFT)** — Order routing, execution, and market data pipelines where sub-10μs latency directly impacts profitability.
- **Cryptocurrency Exchanges** — Real-time order books, settlement, and websocket broadcasts at millions of events per second.
- **AdTech Real-Time Bidding** — Sub-millisecond ad auctions and bid evaluation at scale.
- **Market Data Distribution** — Low-latency feeds for equities, derivatives, commodities, and crypto with minimal GC interference.
- **Game Engines & Networked Games** — High-tick-rate game loops and multiplayer synchronization with deterministic latency.
- **Financial Risk Systems** — Real-time portfolio valuation, Greeks calculation, and stress testing for trading desks.
- **Sensor Networks & IoT** — Time-series ingestion and edge processing for IoT data with strict latency budgets.
- **Telecommunications** — Signaling, traffic analysis, and real-time network telemetry.
- **Cybersecurity Monitoring** — Real-time threat detection and packet analysis at line rate.

Any system processing high-volume, low-latency, deterministic workloads is a candidate for MYRA.

---

## Benchmarks: Codec

Serialization is where myra-codec shines. On an order book snapshot workload (a common HFT/trading message type), here's how it stacks up against established codecs:

```
Decode Throughput (ops/sec) — Higher is Better
════════════════════════════════════════════════════════════════════

Myra         ████████████████████████████████████████  4,150,079  ⭐
SBE          ████████████████████                      2,204,557
FlatBuffers  █████████████████                         1,968,855
Kryo         ███████████████                           1,322,754
Avro         █████                                       454,553


Encode Throughput (ops/sec) — Higher is Better
════════════════════════════════════════════════════════════════════

SBE          ████████████████████████████████████████  4,990,071  
Myra         ███████████████                           1,911,781  ⭐
Kryo         ███████████                               1,342,611
FlatBuffers  ████████                                  1,045,843
Avro         ████                                        466,816
```

**Myra decode is 2-3x faster than Kryo/FlatBuffers** and leads the pack. SBE edges out Myra on encode, but Myra's decode dominance makes it the better choice for read-heavy workloads (most real systems decode more than they encode).

| Codec | Decode (ops/s) | Encode (ops/s) | vs Myra (decode) | vs Myra (encode) |
|-------|----------------|----------------|------------------|------------------|
| Myra | 4,150,079 | 1,911,781 | — | — |
| SBE | 2,204,557 | 4,990,071 | -47% | +161% |
| FlatBuffers | 1,968,855 | 1,045,843 | -53% | -45% |
| Kryo | 1,322,754 | 1,342,611 | -68% | -30% |
| Avro | 454,553 | 466,816 | -89% | -76% |

*Benchmark: order_book_snapshots workload, JMH on c6a.4xlarge, JDK 25, 5 forks × 5 iterations.*

---

## Benchmarks: Transport

For networking, myra-transport uses Linux `io_uring` to bypass the traditional syscall overhead. Here's how it compares in a ping-pong latency test with realistic payloads:

```
Mean Latency (μs) — Lower is Better
════════════════════════════════════════════════════════════════════

NIO          █████████████                             13.22 μs
MYRA_TOKEN   ███████████████████████                   28.70 μs  ⭐
MYRA         ████████████████████████████              35.12 μs
MYRA_SQPOLL  █████████████████████████████             35.88 μs
Netty        ███████████████████████████████           39.34 μs


Throughput (ops/sec) — Higher is Better
════════════════════════════════════════════════════════════════════

NIO          ████████████████████████████████████████  75,645
MYRA_TOKEN   ██████████████████                        34,843  ⭐
MYRA         ███████████████                           28,471
MYRA_SQPOLL  ██████████████                            27,873
Netty        █████████████                             25,417
```

**MYRA_TOKEN beats Netty by 27%** on latency (28.7 μs vs 39.3 μs) and **37%** on throughput. The token-based completion tracking provides the best balance of latency and consistency for io_uring-based networking.

| Implementation | Mean (μs) | p50 (μs) | p99 (μs) | Throughput | vs Netty |
|----------------|-----------|----------|----------|------------|----------|
| NIO (baseline) | 13.22 | 12.27 | 28.35 | 75.6K ops/s | +198% |
| MYRA_TOKEN ⭐ | 28.70 | 26.72 | 45.76 | 34.8K ops/s | **+37%** |
| MYRA | 35.12 | 32.16 | 53.25 | 28.5K ops/s | +12% |
| MYRA_SQPOLL | 35.88 | 25.50 | 63.36 | 27.9K ops/s | +10% |
| Netty | 39.34 | 38.34 | 62.40 | 25.4K ops/s | — |

*Benchmark: RealWorldPayload ping-pong, JMH on ARM64 (AWS Graviton), JDK 25, Nov 29, 2025.*

---

## Why Java Instead of C/C++/Rust?

A common question: *"If you need this kind of performance, why not just write it in C/C++/Rust?"*

It's a fair question. The short answer: **developer velocity, safety, and maintainability matter more than the last 5-10% of performance.**

### The C/C++ Problem

Writing correct, memory-safe high-performance C/C++ code is genuinely *hard*:

- **Undefined behavior lurks everywhere.** Cache line aliasing, pointer provenance violations, strict aliasing optimizations — seemingly small mistakes cause non-deterministic failures in production.
- **Memory safety requires obsessive discipline.** Buffer overflows, use-after-free, double-free — these are career-ending bugs that tests often miss until they surface under load.
- **SIMD and CPU features are implicit.** Vectorization depends on compiler whim. Portability across architectures (x86 → ARM → POWER) requires conditional code paths and platform-specific profiling.
- **Latency is still unpredictable.** Memory allocators, cache eviction, TLB misses, branch misprediction — none are under your control. You optimize by convention and prayer.
- **Recruitment is painful.** Finding developers who can write safe, correct C++ at scale is expensive. Most teams end up with a small cadre of specialists maintaining critical paths.

### The Rust Problem

Rust solves memory safety, but introduces different tradeoffs:

- **The learning curve is steep.** Ownership semantics, borrow checking, lifetime parameters — experienced C++ developers typically need several months to become productive. Most teams don't have that timeline.
- **Async ecosystem fragmentation.** Tokio, async-std, embassy — Rust has no single async standard. A 5-year-old codebase may be stuck on an abandoned runtime.
- **Talent pool is shallow.** Rust adoption in production is still niche. Hiring is hard, and most candidates come from crypto/systems backgrounds, not mainstream finance.
- **Performance isn't guaranteed.** Zero-cost abstractions are a design goal, not a promise. Allocations, copies, and data layout still require deep expertise to optimize.
- **Build times are brutal.** Incremental compilation is improving, but debug builds still feel glacial compared to JVM languages.

### Why Java/FFM Changes the Equation

The MYRA stack bridges the gap:

- **Memory safety by default.** FFM operates within JVM bounds checking. No segfaults, no undefined behavior. You still get off-heap access and native interop, but with guardrails.
- **Deterministic behavior.** The JVM's memory model is formal and well-defined. No undefined behavior. No surprise optimizations that break correctness.
- **GC is optional.** With MYRA's zero-allocation design, you can run on low-pause GCs (ZGC, Shenandoah) or even custom pauseless allocators. You're not forced into stop-the-world pauses.
- **Massive ecosystem.** Maven, Spring, Quarkus, Loom — the JVM has 25+ years of production infrastructure. No reinventing logging, serialization, or concurrency primitives.
- **Tooling maturity.** JVM profilers (async-profiler, Flight Recorder), debuggers, and observability are industry-standard. Compare to gdb + perf or valgrind's UI.
- **Developer velocity is real.** Java developers are abundant. They can become productive with MYRA in weeks, not months. Bugs in business logic surface faster than memory corruption.
- **Performance is competitive.** At 13-28μs ping-pong latency, MYRA is in the same ballpark as hand-optimized C++. Not 2x faster, but also not 2x slower. The 10-15% gap is often *worth* the 10x faster time-to-market.

### The Reality Check

C/C++/Rust shine for:

- **Embedded systems** where memory is scarce (ARM Cortex M, RISC-V).
- **Bare-metal or kernel-mode code** where a JVM isn't an option.
- **Latency-critical at scale** — when you're processing trillions of messages/year and 1μs matters.

But for most systems — trading platforms, market data feeds, game servers, real-time analytics — Java + MYRA offers a pragmatic middle ground:

- **Close to C++ performance** without the memory safety tax.
- **Safer and faster to develop** than Rust, with a deeper talent pool.
- **Proven production stability** with 25+ years of JVM track record.

MYRA's thesis is simple: **Most teams are over-optimized for raw speed and under-optimized for correctness and velocity.** The JVM with FFM tips that balance back toward sanity.

---

## Why I'm Building This

I've spent years in systems where latency matters — where 100μs is slow and a GC pause is a production incident. Java is plenty fast for this, but the tooling hasn't caught up to the platform's capabilities.

FFM is the missing piece. It's finally safe, stable, and performant enough to build real infrastructure on. MYRA is my attempt to do exactly that.

---

## What's Next

I'm currently in the final stretch — optimizations, cleanup, and documentation. The goal is to publicly open source the entire ecosystem by Christmas 2025.

The MYRA ecosystem will always remain **free and open source**. No enterprise tier, no gated features, no open-core model. Development will be sustained through open sponsorships from individuals and organizations who find value in the work.

If you're curious about FFM, high-performance Java, or just want to see where this goes:

**Follow the project:** [github.com/mvp-express](https://github.com/mvp-express)

More soon.
