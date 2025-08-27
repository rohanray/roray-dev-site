+++
title = "JAVA FFM - Foreign Function & Memory Access API (Project Panama)"
date = "2025-09-01"
description = "Sharing my initial learnings using JAVA FFM, Foreign function & memory access."
draft = true

[taxonomies]
tags = ["java", "ffm", "backend"]

[extra]
stylesheets = ["/css/java-ffm-intro.css"]
isso = true
+++

# TL;DR

Java’s Foreign Function & Memory (FFM) API enables safe, high-performance interaction with native code and memory, improving interoperability with other languages and access to low-level system resources. Working code samples are available on <a href="https://github.com/rohanray/roray-dev-site/tree/main/code/java-ffm" rel="noopener noreferrer" target="_blank">GitHub</a>.
<br>
<br>

# Overview

<p>
The Foreign Function & Memory (FFM) API in Java, finalized in JDK 22 with JEP 454, provides a robust and safe mechanism for Java code to interact with foreign functions (native code) and foreign memory (memory outside the Java heap).
</p>

<p>
The FFM API is designed to be a safer, more efficient, and more user-friendly alternative to the Java Native Interface (JNI). JNI is known for its complexity and potential for introducing errors, whereas FFM aims to provide a more "Java-first" programming model for native interoperability.
</p>

<p>
The FFM API introduces several key concepts and components that facilitate foreign function and memory access in Java:
</p>

1. **Memory Segments**: A memory segment is a contiguous block of memory that can be accessed by Java programs. The FFM API provides a way to create and manage memory segments, allowing developers to allocate and deallocate memory as needed.

2. **Arena Allocation**: The FFM API introduces the concept of arenas, which are memory pools that can be used for efficient allocation and deallocation of memory. Arenas help reduce fragmentation and improve performance when working with native memory.

3. **Value Layouts**: Value layouts define the memory layout of data structures in a platform-independent way. The FFM API allows developers to create value layouts for both Java and foreign types, ensuring compatibility between the two.

4. **Memory Access**: The FFM API provides a set of APIs for reading and writing data to memory segments, making it easy to manipulate native data from Java.

5. **Foreign Functions**: Foreign functions are native functions written in languages like C or C++ that can be called from Java. The FFM API provides a way to define and invoke foreign functions, enabling seamless integration between Java and native code.

>Note: _While the primary use case of the Java Foreign Function & Memory (FFM) API is to call C functions from Java (downcalls), it also supports the reverse: calling Java functions from C (upcalls). This is particularly useful for scenarios like C callbacks, where a C library needs to invoke a function provided by Java code._

By leveraging these components, developers can build high-performance applications that take advantage of native libraries and system resources while maintaining the safety and ease of use that Java provides especially in below use cases:

- **Performance-Critical Applications**: Applications that require high performance, such as game engines, scientific computing, Ultra Low Level (ULL) High Frequency Trading (HFT) systems and real-time systems, can benefit from direct access to native code and memory.
- **Big Data and Machine Learning**: Libraries like TensorFlow and PyTorch often require efficient memory management and native code execution, making FFM a suitable choice for integrating these libraries into Java applications.
- **Interoperability with Other Languages**: FFM allows Java applications to easily call functions written in other languages, such as C or C++, enabling developers to leverage existing libraries and codebases
- **Low-Level System Access**: Applications that need to interact with low-level system resources, such as hardware devices or operating system APIs, can use FFM to access these resources directly from Java e.g. accessing NIC for Kernel bypass networking, accessing GPU for parallel processing, etc.
- **High Performance Computing (HPC)**: FFM can be used in HPC applications where performance is critical, such as simulations, numerical computations, and data processing tasks that require efficient memory management and native code execution.
- **High Performance File/Data I/O**: Database files, large binary files, and other data-intensive applications can benefit by using techniques like memory-mapped files which allow direct access to file contents in memory, random access, bulk read, minimize OS calls, Zero copy & no/low GC pressure by avoiding Heap, multi-process file coordination with locks improving I/O performance and reducing latency.
- **Custom High Performance Low Latency RPC**: Remote Procedure Calls (RPC) can be optimized using FFM to reduce serialization/deserialization overhead, enabling faster communication with high throughput between distributed systems.

Let's see a small example of effective usage of FFM. As a Proof of Concept, we will create a TCP server using <a href="https://developers.redhat.com/articles/2023/04/12/why-you-should-use-iouring-network-io" target="_blank" rel="noopener noreferrer">io_uring</a> (Linux kernel interface for asynchronous I/O operations) and Java FFM API. The actual TCP server using io_uring is implemented in C. We will use various APIs (standard TCP operations) viz. accept, listen, connect, send, receive etc. in Java using Foreign Function.

This is by no means a production ready code but can be extended for similar usage. The goal is to showcase the ease of use and integration of FFM with existing C libraries. We also won't be going into details of TCP server implementation in C nor what is io_uring as it's beyond the scope of this article.

Let's look at the <a href="http://github.com/your-repo/your-project" target="_blank" rel="noopener noreferrer">C code</a> first.

<br>

## Appendix
<br>

### JAVA Versions support

|JDK Version|API Status|Enabling Preview Features|Key Enhancements (FFM API versions)|Package|
|---|---|---|---|---|
|**JDK 14**|Foreign-Memory Access API (Incubator)|Not applicable (Incubator)|Memory segments, Arenas (via Foreign-Memory Access API)|[jdk.incubator.foreign](https://docs.oracle.com/en/java/javase/14/docs/api/jdk.incubator.foreign/jdk/incubator/foreign/package-use.html)|
|**JDK 16**|Incubator|Not applicable (Incubator)|Calling native functions (via Foreign Linker API)|[jdk.incubator.foreign](https://docs.oracle.com/en/java/javase/16/docs/api/jdk.incubator.foreign/jdk/incubator/foreign/package-use.html)|
|**JDK 17**|Incubator|Not applicable (Incubator)|Unified FFM API, enhancements to MemorySegment and MemoryAddress abstractions, improved memory layout hierarchy|[jdk.incubator.foreign](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.incubator.foreign/jdk/incubator/foreign/package-use.html)|
|**JDK 18**|Incubator|Not applicable (Incubator)|Refinements based on feedback|[jdk.incubator.foreign](https://docs.oracle.com/en/java/javase/18/docs/api/jdk.incubator.foreign/jdk/incubator/foreign/package-use.html)|
|**JDK 19**|Preview|--enable-preview flag required|Refinements based on feedback|[java.lang.foreign](https://docs.oracle.com/en/java/javase/19/docs/api/java.base/java/lang/foreign/package-summary.html)|
|**JDK 20**|Second Preview|--enable-preview flag required|Refinements based on feedback, including linker option for heap segments, Enable-Native-Access attribute, programmatic function descriptors|[java.lang.foreign](https://docs.oracle.com/en/java/javase/20/docs/api/java.base/java/lang/foreign/package-summary.html)|
|**JDK 21**|Third Preview|--enable-preview flag required|Further refinements based on feedback|[java.lang.foreign](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/foreign/package-summary.html)|
|**JDK 22+**|Finalized|Not required (Finalized)|API stabilization, minor refinements|[java.lang.foreign](https://docs.oracle.com/en/java/javase/22/docs/api/java.base/java/lang/foreign/package-summary.html)|


**Explanation**
<ul class="explanation">
<li>Incubator Phase: The initial steps of the FFM API were as incubating features, meaning they were experimental and subject to change. These releases introduced the core concepts like memory access and foreign function invocation. JEP 434 mentions that the FFM API was in its incubator phase in JDK 17 and JDK 18.</li>
<br>
<li>Preview Phase: In the preview phase, the API's design and implementation were complete, but still subject to potential changes based on user feedback. OpenJDK JEP 424 describes the Foreign Function & Memory API as a preview API in JDK 19.</li>
<br>
<li>Finalized: In JDK 22, the FFM API was declared stable and ready for production use, removing the need for preview flags and providing a more robust and reliable native interoperability solution.</li>
<br>
<li>Key Enhancements: This section highlights the significant changes and refinements introduced in each version of the FFM API as it evolved.</li>
</ul>
