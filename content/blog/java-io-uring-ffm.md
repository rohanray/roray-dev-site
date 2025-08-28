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

<br>

# FFM Use cases

By leveraging these components, developers can build high-performance applications that take advantage of native libraries and system resources while maintaining the safety and ease of use that Java provides especially in below use cases:

- **Performance-Critical Applications**: Applications that require high performance, such as game engines, scientific computing, Ultra Low Level (ULL) High Frequency Trading (HFT) systems and real-time systems, can benefit from direct access to native code and memory.
- **Big Data and Machine Learning**: Libraries like TensorFlow and PyTorch often require efficient memory management and native code execution, making FFM a suitable choice for integrating these libraries into Java applications.
- **Interoperability with Other Languages**: FFM allows Java applications to easily call functions written in other languages, such as C or C++, enabling developers to leverage existing libraries and codebases
- **Low-Level System Access**: Applications that need to interact with low-level system resources, such as hardware devices or operating system APIs, can use FFM to access these resources directly from Java e.g. accessing NIC for Kernel bypass networking, accessing GPU for parallel processing, etc.
- **High Performance Computing (HPC)**: FFM can be used in HPC applications where performance is critical, such as simulations, numerical computations, and data processing tasks that require efficient memory management and native code execution.
- **High Performance File/Data I/O**: Database files, large binary files, and other data-intensive applications can benefit by using techniques like memory-mapped files which allow direct access to file contents in memory, random access, bulk read, minimize OS calls, Zero copy & no/low GC pressure by avoiding Heap, multi-process file coordination with locks improving I/O performance and reducing latency.
- **Custom High Performance Low Latency RPC**: Remote Procedure Calls (RPC) can be optimized using FFM to reduce serialization/deserialization overhead, enabling faster communication with high throughput between distributed systems.

<br>

# Demo Example

Let's see a small example of effective usage of FFM. As a Proof of Concept, we will create a TCP server using <a href="https://developers.redhat.com/articles/2023/04/12/why-you-should-use-iouring-network-io" target="_blank" rel="noopener noreferrer">io_uring</a> (Linux kernel interface for asynchronous I/O operations) and Java FFM API. The actual TCP server using io_uring is implemented in C. We will use various APIs (standard TCP operations) viz. accept, listen, connect, send, receive etc. in Java using Foreign Function.

This is by no means a production ready code. The goal is to showcase the ease of use and integration of FFM with existing C libraries. We also won't be going into details of TCP server implementation in C nor what is io_uring as it's beyond the scope of this article.

<br>

## TCP server in C using async io-uring

Let's look at the <a href="https://github.com/rohanray/roray-dev-site/blob/main/code/java-iouring-tcp-echo/c-iouring/io_uring_tcp_io.c" target="_blank" rel="noopener noreferrer">C code</a> first especially the below APIs:

- `io_uring_global_init` - Initializes the global io_uring context and sets up the underlying ring buffers for asynchronous I/O operations
- `io_uring_listen` - Creates a TCP socket, binds it to a specified port, and starts listening for incoming connections
- `io_uring_accept` - Accepts an incoming connection request and returns a new socket file descriptor for the client
- `io_uring_connect` - Establishes an outbound TCP connection to a remote server at the specified address and port
- `io_uring_send` - Sends data through an established TCP connection using io_uring's asynchronous write operations
- `io_uring_receive` - Receives data from an established TCP connection using io_uring's asynchronous read operations

## JAVA - FFM Integration

Now let's look at the JAVA side which is the point of interest of this article. We have 2 classes:
- `FfmReceiver` - Responsible for receiving data from the TCP server
- `FfmSender` - Responsible for sending data to the TCP server

_Note: **The actual TCP server is implemented in C using io_uring. The C library exposes lifecycle methods for managing the server. These methods are then called in JAVA using FFM.**_

### FfmReceiver

```java,linenos,FfmReceiver.java
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

public class FfmReceiver {

    private static final ValueLayout.OfByte BYTE = ValueLayout.JAVA_BYTE;

    public static void main(String[] args) throws Throwable {
        int queueDepth = 32;
        int port = 22345;
        int backlog = 128;
        long bufferSize = 8 * 1024 * 1024; // 8 MB buffer per client

        try (Arena arena = Arena.ofShared()) {

            // Load the shared library
            SymbolLookup lib = SymbolLookup.libraryLookup("./libiouring_tcp.so", arena);
            Linker linker = Linker.nativeLinker();

            // 1️⃣ Global Init
            MemorySegment globalInitAddr = lib.find("io_uring_global_init").get();
            MethodHandle mhGlobalInit = linker.downcallHandle(globalInitAddr,
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            int ret = (int) mhGlobalInit.invokeExact(queueDepth);
            System.out.println("io_uring_global_init returned: " + ret);

            // 2️⃣ Listen (server socket)
            MemorySegment listenAddr = lib.find("io_uring_listen").get();
            MethodHandle mhListen = linker.downcallHandle(listenAddr,
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            int listenFd = (int) mhListen.invokeExact(port, backlog);
            if (listenFd < 0) {
                throw new RuntimeException("io_uring_listen failed, fd=" + listenFd);
            }
            System.out.println("Server listening on port " + port + ", fd=" + listenFd);

            // 3️⃣ Accept clients in a loop
            MemorySegment acceptAddr = lib.find("io_uring_accept").get();
            MethodHandle mhAccept = linker.downcallHandle(acceptAddr,
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

            MemorySegment recvAddr = lib.find("io_uring_recv").get();
            MethodHandle mhRecv = linker.downcallHandle(recvAddr,
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

            while (true) {
                int clientFd = (int) mhAccept.invokeExact(listenFd);
                if (clientFd < 0) {
                    System.err.println("Accept failed, fd=" + clientFd);
                    continue;
                }
                System.out.println("Client connected, fd=" + clientFd);

                // Allocate buffer for this client
                MemorySegment buffer = arena.allocate(bufferSize);

                int bytesReceived = (int) mhRecv.invokeExact(clientFd, buffer, bufferSize);
                if (bytesReceived < 0) {
                    System.err.println("io_uring_recv failed, bytes=" + bytesReceived);
                    continue;
                }
                System.out.println("Received bytes: " + bytesReceived);

                // Print first 128 bytes for debugging (optional)
                int displayLen = Math.min(128, bytesReceived);
                byte[] arr = new byte[displayLen];
                for (int i = 0; i < displayLen; i++) {
                    arr[i] = buffer.get(BYTE, i);
                }
                System.out.println("First " + displayLen + " bytes:\n" +
                        new String(arr, StandardCharsets.US_ASCII));

                // Close client socket
                MemorySegment closeAddr = lib.find("io_uring_close").get();
                MethodHandle mhClose = linker.downcallHandle(closeAddr,
                        FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
                mhClose.invokeExact(clientFd);
                System.out.println("Client fd " + clientFd + " closed.");
            }

            // 4️⃣ Optional: shutdown ring (never reached in infinite loop)
            // MemorySegment shutdownAddr = lib.find("io_uring_global_shutdown").get();
            // MethodHandle mhShutdown = linker.downcallHandle(shutdownAddr, FunctionDescriptor.ofVoid());
            // mhShutdown.invokeExact();
        }
    }
}
```

This FfmReceiver class demonstrates a complete FFM workflow for calling native C functions from Java. Here's the high-level technical breakdown:

**Library Loading & Symbol Resolution**

FFM loads the compiled C shared library containing io_uring functions. SymbolLookup provides a bridge to find C function symbols by name.

**Key FFM Components in Action**

- Arena: Manages native memory lifecycle - automatically frees all allocated memory when closed (safe malloc/free).
- SymbolLookup: This is how Java finds function pointers in a native library. Think of SymbolLookup as a "directory of functions" inside the .so file.
- Linker: The bridge between Java and native code, which knows how to convert between Java calling conventions and the platform’s native ABI (x86_64 Linux, ARM64, etc.).
- FunctionDescriptor: Defines the C/native function signature (return type + parameter types)
- ValueLayout: Maps Java types to native types (JAVA_INT → C int, ADDRESS → C pointer)
- MethodHandle: Type-safe wrapper for C/native function calls
- MemorySegment: Represents native memory buffers for data exchange; essentially a safe pointer with guardrails.

**Memory Management**

Native memory is allocated outside the Java heap. Data is directly read/written to native buffers without copying (zero copy). Arena ensures safe & automatic cleanup when the try-with-resources block exits. 

Here, arena is used both to load the library and allocate buffers for receiving data.

Here, libraryLookup loads our custom shared library (.so) and lets us find symbols like io_uring_global_init, io_uring_listen, etc.

Then with linker, we can create MethodHandles that behave like normal Java methods but actually invoke C functions. We can think of a MethodHandle as a Java wrapper around a native C function. A linker can be thought of as a translator between Java's JVM world and C's native/binary code. 

Next, we define the function signature with FunctionDescriptor.of(JAVA_INT, JAVA_INT) which includes parameter(input) types & all return types. In this case, the function says it takes one int argument & returns one int as the result. invokeExact(queueDepth) → actually executes the C function.

**Safety & Type Checking**

invokeExact() enforces exact type matching between Java and C function signatures. Compile-time validation prevents type mismatches that would cause runtime errors. No manual memory management or pointer arithmetic required. This approach eliminates JNI's complexity while providing direct, high-performance access to native libraries with Java's safety guarantees intact.

*Click <a href="#memo-walking-through-ffmreceiver-a-first-taste-of-java-ffm-ai-generated">here</a> to see a more detailed AI generated walk through of FfmReceiver implementation.*

### FfmSender

```java,linenos,FfmSender.java
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

public class FfmSender {
    public static void main(String[] args) throws Throwable {
        int queueDepth = 32;
        int port = 22345;
        long bufferSize = 4 * 1024; // 4 KB buffer per client

        System.out.println("FfmSender running...");
        try (Arena arena = Arena.ofShared()) {
            String hi = "ping";

            // Load the shared library
            SymbolLookup lib = SymbolLookup.libraryLookup("./libiouring_tcp.so", arena);
            Linker linker = Linker.nativeLinker();

            // Global IO URing Init
            MemorySegment globalInitAddrMS = lib.find("io_uring_global_init").get();
            FunctionDescriptor globalInitFD = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT);
            MethodHandle globalInitMH = linker.downcallHandle(globalInitAddrMS, globalInitFD);
            int ret = (int) globalInitMH.invokeExact(queueDepth);
            System.out.println("io_uring_global_init returned: " + ret);

            // TCP Open Socket FD
            MemorySegment tcpSocketAddrMS = lib.find("io_uring_connect").get();
            FunctionDescriptor tcpConnectFD = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, // return value of socket File Descriptor
                    ValueLayout.ADDRESS, // server address
                    ValueLayout.JAVA_INT); // server port
            MethodHandle tcpConnectMH = linker.downcallHandle(tcpSocketAddrMS, tcpConnectFD);

            String ip = "127.0.0.1"; // destination IP
            byte[] ipBytes = ip.getBytes(StandardCharsets.UTF_8);
            MemorySegment ipStr = arena.allocate(ipBytes.length + 1);
            ipStr.asSlice(0, ipBytes.length).copyFrom(MemorySegment.ofArray(ipBytes));
            ipStr.set(ValueLayout.JAVA_BYTE, ipBytes.length, (byte) 0); // Null-terminate for C

            int socketFD = (int) tcpConnectMH.invokeExact(ipStr, port);

            // IO URing send data through memory segment off-heap buffer
            MemorySegment sendBufAddrMS = lib.find("io_uring_send_all").get();
            FunctionDescriptor sendBufFD = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, // return value: total sent bytes
                    ValueLayout.JAVA_INT, // socket fd
                    ValueLayout.ADDRESS, // buffer address
                    ValueLayout.JAVA_LONG); // buffer length
            MethodHandle sendBufMH = linker.downcallHandle(sendBufAddrMS, sendBufFD);

            // Prepare buffer
            MemorySegment sendBuf = arena.allocateFrom(hi);

            int totalBytesSent = (int) sendBufMH.invokeExact(socketFD, sendBuf, (long) hi.length());

            System.out.println("Total bytes sent JAVA FFM: " + totalBytesSent);

        }

    }
}
```


<br>

# Appendix
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

<hr>

### 📝 Walking Through FfmReceiver — A First Taste of Java FFM (_AI generated_)

In this demo, we’re calling native io_uring functions in C from Java, to build a **high-performance TCP receiver**.

**1. `Arena` — Memory Lifetime Manager**

```java
try (Arena arena = Arena.ofShared()) {
    ...
}
```

- **Arena** is a memory allocator with automatic cleanup.
- Think of it as a **“scope for native memory”**: allocate native buffers inside it, and when the `try` block exits, the arena frees them automatically.

- Variants:

| Arena Type  | Bounded Lifetime | Manual Close (arena.close()) | Thread Access | Common Use Case |
|-------------|------------------|-------------------------------|---------------|------------------|
| Global      | No               | No                            | Yes (accessible by any thread) | For memory that needs to persist for the entire lifetime of the application. |
| Automatic   | Yes              | No (managed by Garbage Collector) | Yes (accessible by any thread) | Simplest memory management for bounded lifetimes. The memory is deallocated when the arena becomes unreachable by the garbage collector. |
| Confined    | Yes              | Yes (mandatory)              | No (only by the creating thread) | Perfect for single-threaded applications. Provides deterministic deallocation of memory when the arena is closed, often used with try-with-resources. |
| Shared      | Yes              | Yes (mandatory)              | Yes (accessible by multiple threads) | For concurrent applications where memory segments must be shared between threads. Closing the arena is safe and atomic, even when accessed by multiple threads. |

Here we use `ofShared()` because multiple native calls may work with the allocated memory.

**2. `SymbolLookup` — Finding Functions in a Shared Library**

```java
SymbolLookup lib = SymbolLookup.libraryLookup("./libiouring_tcpa.so", arena);
```
- `SymbolLookup` is how Java finds functions or variables inside a native shared library (`.so`, `.dll`, `.dylib`).
- You pass the library name and a scope (`arena`).
- Later, we’ll do `lib.find("io_uring_listen")` to grab the raw address of that function.

👉 Without `SymbolLookup`, Java wouldn’t know *where in memory* the C function lives.

**3. `Linker` — Bridging Java ↔ Native**

```java
Linker linker = Linker.nativeLinker();
```

- **Linker** builds a bridge between Java and C functions.
- It knows how to:
    - Translate Java values to C values (e.g., `int` ↔ `int32_t`)
    - Call into native functions and return values back

`nativeLinker()` automatically picks the system ABI (x86_64, ARM64, etc.).

**4. `MemorySegment` — Safe, Structured Native Memory**

```java
MemorySegment buffer = arena.allocate(bufferSize);
```

- **MemorySegment** is a safe view of native memory.
- Unlike `ByteBuffer` (which is limited and unsafe), a `MemorySegment` tracks bounds, lifetime, and thread access.
- Example:

```java
byte b = buffer.get(ValueLayout.JAVA_BYTE, offset);
buffer.set(ValueLayout.JAVA_BYTE, offset, (byte) 42);
```
- Here, we allocate an **8 MB receive buffer** per client.
- Later, we copy received bytes into a Java `byte[]` for debugging.

**5. `MethodHandle` — Calling Native Functions**

Every native function we call has 3 steps:

1. **Find its address**:

```java
MemorySegment addr = lib.find("io_uring_global_init").get();
```

2. **Describe its signature** using `FunctionDescriptor`:

```java
FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
```

→ “returns int, takes an int argument”

3. **Bind with a** `MethodHandle`:

```java
MethodHandle mhGlobalInit = linker.downcallHandle(globalInitAddr, descriptor);
```

4. **Call it** like a Java method:

```java
int ret = (int) mhGlobalInit.invokeExact(queueDepth);
```

👉 A `MethodHandle` is like a strongly-typed function pointer.

It hides the messy details of JNI — no boilerplate, no unsafe casts.

**6. `ValueLayout` — Mapping Java ↔ C Types**

- `ValueLayout` tells the FFM API how Java types map to C types.
- Examples:
    - `JAVA_INT` → C `int32_t`
    - `JAVA_LONG` → C `int64_t`
    - `JAVA_BYTE` → C `int8_t`
    - `ADDRESS` → C pointers

So when we say:

```java
FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
```

We mean:

```c
int io_uring_recv(int clientFd, void* buffer, long length);
```

**7. Putting It All Together**

In the demo flow:

1. Initialize io_uring

```java
mhGlobalInit.invokeExact(queueDepth);
```

2. Create a TCP server socket

```java
int listenFd = (int) mhListen.invokeExact(port, backlog);
```

3. Accept connections

```java
int clientFd = (int) mhAccept.invokeExact(listenFd);
```

4. Receive data into a native buffer

```java
int bytesReceived = (int) mhRecv.invokeExact(clientFd, buffer, bufferSize);
```

5. Inspect data in Java (first 128 bytes)

```java
byte[] arr = new byte[displayLen];
arr[i] = buffer.get(BYTE, i);
```

6. Close the socket

```java
mhClose.invokeExact(clientFd);
```

**8. Why This Matters**

Traditionally, Java needed **JNI** for native calls — painful, verbose, unsafe.

With **FFM**, we now have:

- **Memory safety** (bounds checks, lifetime control)
- **Performance** (zero-copy native memory access)
- **Clarity** (call native functions like Java methods)
- **Portability** (same code runs across OS/CPU with correct ABI handling)

This demo shows: **pure Java controlling a high-performance Linux feature (io_uring)** — something unimaginable/complex with old JNI boilerplate.