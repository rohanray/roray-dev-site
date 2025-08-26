import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FfmDemoProducer {

    // Layout helpers
    private static final ValueLayout.OfByte BYTE = ValueLayout.JAVA_BYTE;
    private static final ValueLayout LONG_UA_LE_LAYOUT = ValueLayout.JAVA_LONG_UNALIGNED
            .withOrder(ByteOrder.LITTLE_ENDIAN);
    private static final ValueLayout SHORT_UA_LE_LAYOUT = ValueLayout.JAVA_SHORT_UNALIGNED
            .withOrder(ByteOrder.LITTLE_ENDIAN);

    // private static final VarHandle LONG_UA_LE_HANDLE =
    // LONG_UA_LE_LAYOUT.varHandle();
    // private static final VarHandle SHORT_UA_LE_HANDLE =
    // SHORT_UA_LE_LAYOUT.varHandle();

    static void runSource(String inputCsvPath) throws IOException {
        System.out.println("Starting Source");

        Path inPath = Path.of(inputCsvPath);

        try (FileChannel inCh = FileChannel.open(inPath, StandardOpenOption.READ)) {
            List<Long> offsets = new ArrayList<>();
            // List<Long> nameLengths = new ArrayList<>();
            long totalOutSize = 0;
            int totalRecords = 0;
            // Map file into memory
            try (Arena arena = Arena.ofShared()) {
                // SequenceLayout recordsSeqLayout = MemoryLayout.sequenceLayout(totalRecords,
                // MemoryLayout.structLayout(
                // LONG_UA_LE_LAYOUT.withName("mobile"),
                // SHORT_UA_LE_LAYOUT.withName("age"),
                // SHORT_UA_LE_LAYOUT.withName("name_length"),
                // ValueLayout.JAVA_BOOLEAN.withName("external")

                // ).withName("record")).withName("records");

                // Pass 1 - Determine size

                MemorySegment fileMapSeg = inCh.map(FileChannel.MapMode.READ_ONLY, 0, inCh.size(), arena);
                MemorySegment recMemorySegment = null;

                for (long i = 0; i < fileMapSeg.byteSize(); i++) {
                    byte b = fileMapSeg.get(BYTE, i);
                    if (b == 10) {
                        // recMemorySegment = arena.allocate(inFileSize - i + 1);
                        recMemorySegment = fileMapSeg.asSlice(i);
                        break;
                    }
                }

                if (recMemorySegment != null) {
                    int currNameLength = 0;
                    boolean firstComma = true;
                    for (long i = 0; i < recMemorySegment.byteSize(); i++) {
                        switch (recMemorySegment.get(BYTE, i)) {
                            case 10 -> {
                                offsets.add(i + 1); // 10 is new line, so increment offset
                                totalOutSize = totalOutSize + currNameLength + Long.BYTES + Short.BYTES + Short.BYTES
                                        + Byte.BYTES; // 1 for mobile, 2 for age, 2 for name_length, 1 for external
                                                      // field flag
                                currNameLength = 0;
                                firstComma = true;
                                break;
                            }
                            case 44 -> {
                                firstComma = false;
                                break;
                            }
                            default -> {
                                if (!firstComma) {
                                    currNameLength++;
                                }
                            }
                        }

                    }

                    totalRecords = offsets.size();
                    System.out.println("Total in file byte size / recMemorySegment: ");
                    System.out.println(recMemorySegment.byteSize());
                    System.out.println("Total Records: " + totalRecords);
                    System.out.println("Total Out Bin Size: " + totalOutSize);

                    // Pass 2 - Write to memory segment in binary

                    GroupLayout recordLayout = MemoryLayout.structLayout(
                            LONG_UA_LE_LAYOUT.withName("mobile"),
                            SHORT_UA_LE_LAYOUT.withName("age"),
                            SHORT_UA_LE_LAYOUT.withName("name_length"),
                            ValueLayout.JAVA_BOOLEAN.withName("external")).withName("record");

                    // Get var handlesx for fields
                    VarHandle VH_MOBILE = recordLayout.varHandle(PathElement.groupElement("mobile"));
                    VarHandle VH_AGE = recordLayout.varHandle(PathElement.groupElement("age"));
                    VarHandle VH_NAME_LENGTH = recordLayout.varHandle(PathElement.groupElement("name_length"));
                    VarHandle VH_EXTERNAL = recordLayout.varHandle(PathElement.groupElement("external"));

                    MemorySegment outBinSegment = Arena.ofShared().allocate(totalOutSize);
                    // long currBinSegOffset = 0;
                    // for (int i = 0; i < totalRecords; i++) {
                    // long offset = offsets.get(i);
                    // System.out.println("Offset at " + i + " : " + offset);
                    // System.out.println("Offsets: " + new String(new byte[] {
                    // recMemorySegment.get(BYTE, offset) },
                    // StandardCharsets.UTF_8));
                    // }

                    // START: short writer
                    long allNamesLength = 0;
                    for (int i = 0; i < totalRecords; i++) {
                        // System.out.println("Inside Writer");
                        // System.out.println("Offset at " + i + " : " + offsets.get(i));
                        for (int j = 0;; j++) {
                            if (44 == recMemorySegment.get(BYTE, offsets.get(i) + j)) {
                                // System.out.println("Writer j: " + j);
                                MemorySegment currRecMS = outBinSegment
                                        .asSlice((i * recordLayout.byteSize()) + allNamesLength, recordLayout);

                                VH_MOBILE.set(currRecMS, 0L, parseLong(recMemorySegment, offsets.get(i) + j + 4,
                                        offsets.get(i) + j + 14));

                                VH_AGE.set(currRecMS, 0L,
                                        parseShort(recMemorySegment, offsets.get(i) + j + 1, offsets.get(i) + j + 3));

                                VH_NAME_LENGTH.set(currRecMS, 0L, (short) j);

                                // VH_EXTERNAL.set(currRecMS, 0L, true);

                                outBinSegment
                                        .asSlice(((i + 1) * recordLayout.byteSize())
                                                + allNamesLength, j)
                                        .copyFrom(recMemorySegment.asSlice(offsets.get(i), j));

                                // if (i == 0) {
                                // System.out.println("Special case for record 1");
                                // char fnc = (char) recMemorySegment
                                // .asSlice(offsets.get(i), j)
                                // .get(BYTE, j - 1);
                                // System.out.println("FNC IN: " + fnc);

                                // fnc = (char) outBinSegment
                                // .asSlice(((i + 1) * recordLayout.byteSize()) + allNamesLength, j)
                                // .get(BYTE, j - 1);
                                // System.out.println("FNC OUT: " + fnc);

                                // }

                                allNamesLength = allNamesLength + j;
                                break;
                            }
                        }
                    }

                    // END: short writer

                    // START : TEST BIN MS reader
                    System.out.println("Reading from binary memory segment:");
                    // long readAllNamesLength = 0;
                    // for (int i = 0; i < totalRecords; i++) {
                    // System.out.println("--- Record " + i + " ---");
                    // MemorySegment currReadMS = outBinSegment
                    // .asSlice((i * recordLayout.byteSize()) + readAllNamesLength, recordLayout);
                    // long _mobile = (long) VH_MOBILE.get(currReadMS, 0L);
                    // System.out.println("Mobile: " + _mobile);
                    // short _age = (short) VH_AGE.get(currReadMS, 0L);
                    // System.out.println("Age: " + _age);
                    // short _name_length = (short) VH_NAME_LENGTH.get(currReadMS, 0L);
                    // System.out.println("Name Length: " + _name_length);
                    // boolean _isExt = (boolean) VH_EXTERNAL.get(currReadMS, 0L);
                    // System.out.println("Is External: " + _isExt);
                    // String name = outBinSegment
                    // .asSlice((i * recordLayout.byteSize()) + readAllNamesLength, _name_length)
                    // .getString(0, StandardCharsets.UTF_8);
                    // System.out.println("Name: " + name);

                    // if (i == 2) {
                    // System.out.println("Special case for record 1");
                    // char fnc = (char) outBinSegment
                    // .asSlice(((i + 1) * recordLayout.byteSize()) + readAllNamesLength,
                    // _name_length)
                    // .get(BYTE, _name_length - 1);
                    // System.out.println("FNC OUT 1: " + fnc);
                    // }

                    // readAllNamesLength += _name_length;

                    // }

                    // END : TEST BIN MS reader

                    try {
                        sendBinarySource(outBinSegment);
                    } catch (Throwable t) {
                        System.out.println(t.getMessage());
                    }
                }

            }
        }

    }

    static void runSink() {
        System.out.println("Starting Sink");
        int port = 22345;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("TCP Server listening on port " + port);
            while (true) {
                try (Socket clientSocket = serverSocket.accept();
                        InputStream in = clientSocket.getInputStream()) {
                    System.out.println("Client connected: " + clientSocket.getInetAddress());
                    byte[] buffer = new byte[8 * 1024]; // 8 KB chunk
                    int bytesRead;
                    long totalReceivedBytes = 0L;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        System.out.println("Received " + bytesRead + " bytes");
                        totalReceivedBytes += bytesRead;
                    }
                    System.out.println("Client disconnected.");
                    System.out.println("Total bytes rcvd:" + totalReceivedBytes);
                }
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    static void handleClient(SocketChannel client) {
        final int BUFFER_SIZE = 8 * 1024; // 8 KB

        try (client; Arena arena = Arena.ofConfined()) {
            System.out.println("Client connected: " + client.getRemoteAddress());

            // Allocate a reusable off-heap buffer for this client
            MemorySegment memBuf = arena.allocate(BUFFER_SIZE);
            ByteBuffer buf = memBuf.asByteBuffer(); // wrap for NIO read/write

//            while (client.read(buf) != -1) {
//                buf.flip();
//
//                // Example: echo back to client
//                while (buf.hasRemaining()) {
//                    client.write(buf);
//                }
//
//                // Optionally: print first 64 bytes for debugging
//                int len = Math.min(64, buf.limit());
//                byte[] firstBytes = new byte[len];
//                buf.get(firstBytes, 0, len);
//                System.out.println("Received: " + new String(firstBytes, StandardCharsets.US_ASCII));
//
//                buf.clear(); // reset buffer for next read
//            }

            System.out.println("Client disconnected: " + client.getRemoteAddress());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void startTcpServer(){
        final int PORT = 22345;

        // START: Modern TCP listener
        // using NIO & Memory Segment with ByteBuffer as view on the segments
        // Virtual threads for lightweight multi client connection

        ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());

        try (ServerSocketChannel serverSocket = ServerSocketChannel.open()) {
            serverSocket.bind(new InetSocketAddress(PORT));
            System.out.println("Virtual-thread TCP server listening on port " + PORT);

            while (true) {
                SocketChannel client = serverSocket.accept(); // blocks until client connects
//                executor.submit(() -> handleClient(client));
                Thread.startVirtualThread(() -> handleClient(client));
            }
        }
        catch (Exception e) {
            System.err.println(e.getMessage());
        }

        // END: Modern TCP listener

    }

    static void runSinkv2() throws Throwable {
        System.out.println("Starting Sink");
        startTcpServer();

        try (Arena arena = Arena.ofShared()) {

            SymbolLookup lib = SymbolLookup.libraryLookup("./libiouring_tcp.so", arena);

            // Load the shared library: Global Init for Q depth
            MemorySegment globalInitFuncAddr = lib.find("io_uring_global_init").get();
            FunctionDescriptor globalInitFd = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT
            );

            // Prepare Method Handle: Global Init for Q depth
            Linker linker = Linker.nativeLinker();
            MethodHandle globalInitHandle = linker.downcallHandle(globalInitFuncAddr, globalInitFd);
            int queueDepth = 2; // Example queue depth
            int ret = (int) globalInitHandle.invokeExact(queueDepth);
            System.out.println("Global init returned: " + ret);

            // Load the shared library: TCP Connect
            MemorySegment tcpConnectFuncAddr = lib.find("io_uring_connect").get();
            FunctionDescriptor tcpConnectFd = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, // ip string
                    ValueLayout.JAVA_INT // port
            );

            // Prepare Method Handle: TCP Connect
            MethodHandle tcpConnectHandle = linker.downcallHandle(tcpConnectFuncAddr, tcpConnectFd);
            String ip = "127.0.0.1"; // Example IP
            int port = 22345; // Example port
            byte[] ipBytes = ip.getBytes(StandardCharsets.UTF_8);
            MemorySegment ipStr = arena.allocate(ipBytes.length + 1);
            ipStr.asSlice(0, ipBytes.length).copyFrom(MemorySegment.ofArray(ipBytes));
            ipStr.set(ValueLayout.JAVA_BYTE, ipBytes.length, (byte) 0); // Null-terminate for C
            int sockFd = (int) tcpConnectHandle.invokeExact(ipStr, port);
            System.out.println("TCP Connect returned: " + sockFd);
            if (sockFd < 0)
                return;

            // Load the shared library: TCP Receive
            MemorySegment tcpReceiveFuncAddr = lib.find("io_uring_recv").get();
            FunctionDescriptor tcpReceiveFd = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG);

            // Prepare Method Handle: TCP Receive
            MethodHandle tcpReceiveHandle = linker.downcallHandle(tcpReceiveFuncAddr, tcpReceiveFd);
            long bufferSize = 8 * 1024 * 1024; // 8 MB buffer
            MemorySegment recvBuffer  = arena.allocate(bufferSize);
            recvBuffer.set(BYTE, 0, (byte) 0); // Initialize buffer

            int bytesReceived = (int) tcpReceiveHandle.invokeExact(sockFd, recvBuffer, bufferSize);
            System.out.println("TCP Receive returned: " + bytesReceived);

            byte[] arr = new byte[bytesReceived];
            for (int i = 0; i < bytesReceived; i++) {
                arr[i] = recvBuffer.get(ValueLayout.JAVA_BYTE, i);
            }
            System.out.println("Message: " + new String(arr, StandardCharsets.UTF_8));

            // Close socket
            MethodHandle tcpCloseHandle = linker.downcallHandle(
                    lib.find("io_uring_close").orElseThrow(),
                    FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT));
            tcpCloseHandle.invokeExact(sockFd);

            // Shutdown ring
            MethodHandle shutdownHandle = linker.downcallHandle(
                    lib.find("io_uring_global_shutdown").orElseThrow(),
                    FunctionDescriptor.ofVoid());
            shutdownHandle.invokeExact();

        }
    }


    public static void main(String[] args) throws Exception, Throwable {
        System.out.println("Running in mode: " + args[0]);
        try {
            switch (args[0]) {
                case "source" -> {
                    runSource(args[1]);
                }
                case "sink" -> {
                    runSinkv2();
                }
                default -> {
                    System.out.println("Usage: source / sink");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static Long parseLong(MemorySegment seg, long start, long end) {
        long value = 0L;
        for (long i = start; i < end; i++) {
            byte b = seg.get(BYTE, i);
            value = value * 10 + (b - '0');
        }
        return value;
    }

    static Short parseShort(MemorySegment seg, long start, long end) {
        short value = 0;
        for (long i = start; i < end; i++) {
            byte b = seg.get(BYTE, i);
            value = (short) (value * 10 + (b - '0'));
        }
        return value;
    }

    static void sendBinarySource(MemorySegment ms) throws Throwable {

        try (Arena arena = Arena.ofShared()) {

            // Load the shared library
            SymbolLookup lib = SymbolLookup.libraryLookup("./io_uring_tcp_sender.so", arena);
            MemorySegment funcAddr = lib.find("send_buffer_io_uring").orElseThrow();

            FunctionDescriptor fd = FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, // ip string
                    ValueLayout.JAVA_INT, // port
                    ValueLayout.ADDRESS, // buffer
                    ValueLayout.JAVA_LONG // length
            );

            // Prepare Method Handle

            Linker linker = Linker.nativeLinker();
            MethodHandle sendBuffer = linker.downcallHandle(funcAddr, fd);

            // Prepare arguments
            String ip = "127.0.0.1"; // destination IP
            int port = 22345; // destination port
            byte[] ipBytes = ip.getBytes(StandardCharsets.UTF_8);
            MemorySegment ipStr = arena.allocate(ipBytes.length + 1);
            ipStr.asSlice(0, ipBytes.length).copyFrom(MemorySegment.ofArray(ipBytes));
            ipStr.set(ValueLayout.JAVA_BYTE, ipBytes.length, (byte) 0); // Null-terminate for C

            int sent = (int) sendBuffer.invokeExact(ipStr, port, ms, ms.byteSize());

            System.out.println("Bytes sent: " + sent);

        }
    }

}