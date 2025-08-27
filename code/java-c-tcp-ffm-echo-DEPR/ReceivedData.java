import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

public record ReceivedData(int bytesReceived, MemorySegment buffer) {
    
    private static final ValueLayout.OfByte BYTE = ValueLayout.JAVA_BYTE;
    
    public boolean isValid() {
        return bytesReceived >= 0;
    }
    
    public String preview(int maxBytes) {
        if (!isValid()) return "[ERROR]";
        
        int displayLen = Math.min(maxBytes, bytesReceived);
        byte[] arr = new byte[displayLen];
        for (int i = 0; i < displayLen; i++) {
            arr[i] = buffer.get(BYTE, i);
        }
        return new String(arr, StandardCharsets.US_ASCII);
    }
    
    public String preview() {
        return preview(128);
    }
}