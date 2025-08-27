import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public class BinFfmDemoConsumer {

    public static void main(String[] args) throws Throwable {
        ServerConfig config = ServerConfig.defaults();

        try (Arena arena = Arena.ofShared()) {
            
            // Link the interface to native functions 
            IoUringOperations ioUring = NativeLinker.link(IoUringOperations.class, "./libiouring_tcp.so", arena);

            // 1️⃣ Global Init
            int ret = ioUring.globalInit(config.queueDepth());
            System.out.println("io_uring_global_init returned: " + ret);

            // 2️⃣ Listen (server socket)
            int listenFd = ioUring.listen(config.port(), config.backlog());
            if (listenFd < 0) {
                throw new RuntimeException("io_uring_listen failed, fd=" + listenFd);
            }
            System.out.println("Server listening on port " + config.port() + ", fd=" + listenFd);

            // 3️⃣ Accept clients in a loop
            while (true) {
                int clientFd = ioUring.accept(listenFd);
                if (clientFd < 0) {
                    System.err.println("Accept failed, fd=" + clientFd);
                    continue;
                }
                System.out.println("Client connected, fd=" + clientFd);

                // Create client connection with allocated buffer
                MemorySegment buffer = arena.allocate(config.bufferSize());
                ClientConnection client = new ClientConnection(clientFd, buffer);

                // Receive data
                ReceivedData data = client.receive(ioUring, config.bufferSize());
                if (!data.isValid()) {
                    System.err.println("io_uring_recv failed, bytes=" + data.bytesReceived());
                    continue;
                }
                
                System.out.println("Received bytes: " + data.bytesReceived());
                System.out.println("First 128 bytes:\n" + data.preview());

                // Close client connection
                client.close(ioUring);
            }

            // 4️⃣ Optional: shutdown ring (never reached in infinite loop)
            // ioUring.globalShutdown();
        }
    }
}