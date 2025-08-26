import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public record NativeLinker(SymbolLookup lookup, Linker linker) {
    
    public static <T> T link(Class<T> interfaceClass, String libraryPath, Arena arena) {
        SymbolLookup lookup = SymbolLookup.libraryLookup(libraryPath, arena);
        Linker linker = Linker.nativeLinker();
        NativeLinker nativeLinker = new NativeLinker(lookup, linker);
        
        return nativeLinker.createProxy(interfaceClass);
    }
    
    @SuppressWarnings("unchecked")
    private <T> T createProxy(Class<T> interfaceClass) {
        Map<String, MethodHandle> methodHandles = createMethodHandles();
        
        InvocationHandler handler = (proxy, method, args) -> {
            MethodHandle handle = methodHandles.get(method.getName());
            if (handle == null) {
                throw new UnsupportedOperationException("Method not implemented: " + method.getName());
            }
            
            if (args == null) {
                return handle.invokeExact();
            } else {
                return handle.invokeWithArguments(args);
            }
        };
        
        return (T) Proxy.newProxyInstance(
            interfaceClass.getClassLoader(),
            new Class<?>[]{interfaceClass},
            handler
        );
    }
    
    private Map<String, MethodHandle> createMethodHandles() {
        Map<String, MethodHandle> methodHandles = new HashMap<>();
        
        methodHandles.put("globalInit", createHandle("io_uring_global_init",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)));
            
        methodHandles.put("listen", createHandle("io_uring_listen",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)));
            
        methodHandles.put("accept", createHandle("io_uring_accept",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)));
            
        methodHandles.put("recv", createHandle("io_uring_recv",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)));
            
        methodHandles.put("close", createHandle("io_uring_close",
            FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT)));
            
        methodHandles.put("globalShutdown", createHandle("io_uring_global_shutdown",
            FunctionDescriptor.ofVoid()));
        
        return methodHandles;
    }
    
    private MethodHandle createHandle(String symbolName, FunctionDescriptor descriptor) {
        MemorySegment symbol = lookup.find(symbolName).orElseThrow(() -> 
            new RuntimeException("Symbol not found: " + symbolName));
        return linker.downcallHandle(symbol, descriptor);
    }
}