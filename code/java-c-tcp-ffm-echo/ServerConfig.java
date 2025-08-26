public record ServerConfig(
    int queueDepth,
    int port,
    int backlog,
    long bufferSize
) {
    public static ServerConfig defaults() {
        return new ServerConfig(32, 22345, 128, 8 * 1024 * 1024);
    }
    
    public ServerConfig withPort(int port) {
        return new ServerConfig(queueDepth, port, backlog, bufferSize);
    }
    
    public ServerConfig withBufferSize(long bufferSize) {
        return new ServerConfig(queueDepth, port, backlog, bufferSize);
    }
    
    public ServerConfig withQueueDepth(int queueDepth) {
        return new ServerConfig(queueDepth, port, backlog, bufferSize);
    }
}