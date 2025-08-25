import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class SimpleTcpServer {
    public static void main(String[] args) throws Exception {
        int port = 12345;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("TCP Server listening on port " + port);
            while (true) {
                try (Socket clientSocket = serverSocket.accept();
                        InputStream in = clientSocket.getInputStream()) {
                    System.out.println("Client connected: " + clientSocket.getInetAddress());
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        System.out.println("Received " + bytesRead + " bytes");
                    }
                    System.out.println("Client disconnected.");
                }
            }
        }
    }
}
