import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

public class FfmDemoConsumer {

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
