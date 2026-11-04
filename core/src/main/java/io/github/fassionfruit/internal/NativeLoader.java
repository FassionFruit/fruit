package io.github.fassionfruit.internal;

import io.github.fassionfruit.annotation.NativeCallback;
import io.github.fassionfruit.annotation.NativeErrno;
import io.github.fassionfruit.annotation.NativeSymbol;
import io.github.fassionfruit.annotation.structure.NativeField;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Map;
import java.util.HashMap;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;

public final class NativeLoader {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final Object[] EMPTY_ARGUMENTS = new Object[0];
    private static final VarHandle ERRNO_HANDLE =
            Linker.Option.captureStateLayout().varHandle(groupElement("errno"));
    private static final ThreadLocal<Integer> LAST_ERRNO =
            ThreadLocal.withInitial(() -> 0);

    private NativeLoader() {
    }

    public static int lastErrno() {
        return LAST_ERRNO.get();
    }

    public static <T> T load(Class<T> type, String libraryName) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException(
                    type.getName() + " must be an interface"
            );
        }

        Path libraryPath = NativeLibraryResolver.resolve(libraryName);
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

        InvocationHandler handler = new NativeInvocationHandler(type, lookup);

        Object proxy = Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                handler
        );

        return type.cast(proxy);
    }

    private static class NativeInvocationHandler implements InvocationHandler {

        private final SymbolLookup lookup;
        private final Map<Method, InvocationPlan> plans = new HashMap<>();

        private NativeInvocationHandler(Class<?> type, SymbolLookup lookup) {
            this.lookup = lookup;

            for (Method method : type.getMethods()) {
                if (method.isAnnotationPresent(NativeField.class)) {
                    continue;
                }

                plans.put(method, createPlan(method));
            }
        }

        @Override
        public Object invoke(
                Object proxy,
                Method method,
                Object[] args
        ) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" ->
                            "NativeProxy(" +
                                    proxy.getClass().getInterfaces()[0].getName() +
                                    ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (args == null ? null : args[0]);
                    default -> null;
                };
            }

            InvocationPlan plan = plans.get(method);
            if (plan == null) {
                throw new IllegalStateException(
                        method.getName() + " is missing @NativeSymbol"
                );
            }

            Class<?>[] parameterTypes = plan.parameterTypes;
            if (plan.primitiveOnly && !plan.captureErrno) {
                return plan.invoker.invokeExact(
                        args == null ? EMPTY_ARGUMENTS : args
                );
            }

            Object[] nativeArguments = parameterTypes.length == 0
                    ? EMPTY_ARGUMENTS
                    : new Object[parameterTypes.length];

            if (plan.requiresArena) {
                try (Arena arena = Arena.ofConfined()) {
                    convertArguments(
                            parameterTypes,
                            args,
                            nativeArguments,
                            arena
                    );

                    return invokeNative(plan, nativeArguments, arena);
                }
            }

            convertArguments(
                    parameterTypes,
                    args,
                    nativeArguments,
                    null
            );

            return invokeNative(plan, nativeArguments, null);
        }

        private void convertArguments(
                Class<?>[] parameterTypes,
                Object[] args,
                Object[] nativeArguments,
                Arena arena
        ) {
            for (int i = 0; i < parameterTypes.length; i++) {
                nativeArguments[i] = NativeValueConverter.toNative(
                        parameterTypes[i],
                        args[i],
                        arena
                );
            }
        }

        private Object invokeNative(
                InvocationPlan plan,
                Object[] arguments,
                Arena arena
        ) throws Throwable {
            MemorySegment capturedState = plan.captureErrno
                    ? arena.allocate(Linker.Option.captureStateLayout())
                    : null;
            Object result = plan.captureErrno
                    ? plan.invoker.invokeExact(capturedState, arguments)
                    : plan.invoker.invokeExact(arguments);

            if (plan.captureErrno) {
                LAST_ERRNO.set((int) ERRNO_HANDLE.get(capturedState, 0L));
            }

            return NativeValueConverter.fromNative(
                    plan.returnType,
                    result
            );
        }

        private InvocationPlan createPlan(Method method) {
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

            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean captureErrno = method.isAnnotationPresent(NativeErrno.class);
            Linker.Option[] options = captureErrno
                    ? new Linker.Option[]{Linker.Option.captureCallState("errno")}
                    : new Linker.Option[0];

            MethodHandle invoker = LINKER.downcallHandle(
                    symbol,
                    NativeTypeMapper.createDescriptor(method),
                    options
            );

            invoker = invoker.asSpreader(Object[].class, parameterTypes.length);
            invoker = captureErrno
                    ? invoker.asType(MethodType.methodType(
                            Object.class,
                            MemorySegment.class,
                            Object[].class
                    ))
                    : invoker.asType(MethodType.methodType(
                            Object.class,
                            Object[].class
                    ));

            boolean requiresArena = captureErrno;

            for (Class<?> parameterType : parameterTypes) {
                if (parameterType == String.class
                        || parameterType.isAnnotationPresent(NativeCallback.class)) {
                    requiresArena = true;
                    break;
                }
            }

            boolean primitiveOnly = isConversionFreePrimitive(method.getReturnType())
                    && allParametersAreConversionFreePrimitives(parameterTypes);

            return new InvocationPlan(
                    invoker,
                    parameterTypes,
                    method.getReturnType(),
                    requiresArena,
                    primitiveOnly,
                    captureErrno
            );
        }

        private boolean allParametersAreConversionFreePrimitives(
                Class<?>[] parameterTypes
        ) {
            for (Class<?> parameterType : parameterTypes) {
                if (!isConversionFreePrimitive(parameterType)) {
                    return false;
                }
            }

            return true;
        }

        private boolean isConversionFreePrimitive(Class<?> type) {
            return type.isPrimitive()
                    && type != boolean.class
                    && type != char.class;
        }
    }

    private static class InvocationPlan {

        private final MethodHandle invoker;
        private final Class<?>[] parameterTypes;
        private final Class<?> returnType;
        private final boolean requiresArena;
        private final boolean primitiveOnly;
        private final boolean captureErrno;

        private InvocationPlan(
                MethodHandle invoker,
                Class<?>[] parameterTypes,
                Class<?> returnType,
                boolean requiresArena,
                boolean primitiveOnly,
                boolean captureErrno
        ) {
            this.invoker = invoker;
            this.parameterTypes = parameterTypes;
            this.returnType = returnType;
            this.requiresArena = requiresArena;
            this.primitiveOnly = primitiveOnly;
            this.captureErrno = captureErrno;
        }
    }
}
