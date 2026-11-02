package io.github.fassionfruit.internal;

import io.github.fassionfruit.annotation.NativeSymbol;

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NativeLoader {

    private static final Linker LINKER = Linker.nativeLinker();

    private NativeLoader() {
    }

    public static <T> T load(Class<T> type, String libraryName) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException(
                    type.getName() + " must be an interface"
            );
        }

        Path libraryPath = Path.of(libraryName).toAbsolutePath().normalize();

        if (!Files.isRegularFile(libraryPath)) {
            throw new IllegalArgumentException(
                    "Native library does not exist: " + libraryPath
            );
        }

        Arena arena = Arena.ofAuto();

        SymbolLookup lookup;

        try {
            lookup = SymbolLookup.libraryLookup(libraryPath, arena);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to load native library: " + libraryPath,
                    exception
            );
        }

        InvocationHandler handler = new NativeInvocationHandler(lookup);

        Object proxy = Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                handler
        );

        return type.cast(proxy);
    }

    private static class NativeInvocationHandler implements InvocationHandler {

        private final SymbolLookup lookup;
        private final Map<Method, MethodHandle> handles = new ConcurrentHashMap<>();

        private NativeInvocationHandler(SymbolLookup lookup) {
            this.lookup = lookup;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" ->
                            "NativeProxy(" + proxy.getClass().getInterfaces()[0].getName() + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (args == null ? null : args[0]);
                    default -> null;
                };
            }

            MethodHandle handle = handles.computeIfAbsent(
                    method,
                    this::createHandle
            );

            try (Arena arena = Arena.ofConfined()) {
                List<Object> nativeArguments = new ArrayList<>();
                Class<?>[] parameterTypes = method.getParameterTypes();

                if (args != null) {
                    for (int i = 0; i < parameterTypes.length; i++) {
                        nativeArguments.add(
                                NativeValueConverter.toNative(
                                        parameterTypes[i],
                                        args[i],
                                        arena
                                )
                        );
                    }
                }

                Object result = handle.invokeWithArguments(nativeArguments);

                return NativeValueConverter.fromNative(
                        method.getReturnType(),
                        result
                );
            }
        }

        private MethodHandle createHandle(Method method) {
            NativeSymbol annotation = method.getAnnotation(NativeSymbol.class);

            if (annotation == null) {
                throw new IllegalStateException(
                        method.getName() + " is missing @NativeSymbol"
                );
            }

            String symbolName = annotation.value();

            var symbol = lookup.find(symbolName).orElseThrow(
                    () -> new UnsatisfiedLinkError(
                            "Native symbol not found: " + symbolName
                    )
            );

            var descriptor = NativeTypeMapper.createDescriptor(method);

            return LINKER.downcallHandle(symbol, descriptor);
        }
    }
}