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
            byte[]  ipBytes = ip.getBytes(StandardCharsets.UTF_8);
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
            // sendBuf.asSlice(0, hi.length()).copyFrom(MemorySegment.ofArray(hi.getBytes(StandardCharsets.UTF_8)));

            int totalBytesSent = (int)sendBufMH.invokeExact(socketFD, sendBuf, (long)hi.length());

            System.out.println("Total bytes sent JAVA FFM: " + totalBytesSent);

        }

    }
}
