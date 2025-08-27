import java.lang.foreign.MemorySegment;

public interface IoUringOperations {
    int globalInit(int queueDepth);

    int listen(int port, int backlog);

    int accept(int listenFd);

    int recv(int clientFd, MemorySegment buffer, long bufferSize);

    void close(int fd);

    void globalShutdown();
}
