import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

public record ClientConnection(int fd, MemorySegment buffer) {
    
    public ReceivedData receive(IoUringOperations ioUring, long bufferSize) throws Throwable {
        int bytesReceived = ioUring.recv(fd, buffer, bufferSize);
        return new ReceivedData(bytesReceived, buffer);
    }
    
    public void close(IoUringOperations ioUring) throws Throwable {
        ioUring.close(fd);
        System.out.println("Client fd " + fd + " closed.");
    }
}