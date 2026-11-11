package io.github.fassionfruit.jvm;

import io.github.fassionfruit.internal.NativeTypeMapper;
import io.github.fassionfruit.internal.NativeValueConverter;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/** Exports Java implementations as native-callable vtable structures. */
public final class JvmStructureHandler {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final MethodHandle INVOKE_JVM_METHOD =
            createInvokeJvmMethodHandle();

    private JvmStructureHandler() {
    }

    /** Allocates and populates a vtable structure in the supplied arena. */
    public static <T> MemorySegment export(
            Class<T> type,
            T implementation,
            Arena arena
    ) {
        if (implementation == null) {
            throw new IllegalArgumentException(
                    "Implementation must not be null"
            );
        }

        MemoryLayout layout = JvmStructureLayout.create(type);
        List<Method> methods = JvmStructureLayout.getMethods(type);

        MemorySegment segment = arena.allocate(layout);

        segment.set(
                ValueLayout.ADDRESS,
                layout.byteOffset(MemoryLayout.PathElement.groupElement("context")),
                MemorySegment.NULL
        );

        for (Method method : methods) {
            FunctionDescriptor descriptor =
                    NativeTypeMapper.createJvmMethodDescriptor(method);

            MethodHandle target = createTarget(
                    implementation,
                    method,
                    descriptor,
                    arena
            );

            MemorySegment stub = LINKER.upcallStub(
                    target,
                    descriptor,
                    arena
            );

            long offset = layout.byteOffset(
                    MemoryLayout.PathElement.groupElement(method.getName())
            );

            segment.set(ValueLayout.ADDRESS, offset, stub);
        }

        return segment;
    }

    private static MethodHandle createTarget(
            Object implementation,
            Method method,
            FunctionDescriptor descriptor,
            Arena arena
    ) {
        MethodHandle target = INVOKE_JVM_METHOD;

        target = MethodHandles.insertArguments(
                target,
                0,
                implementation,
                method,
                arena
        );

        target = MethodHandles.dropArguments(
                target,
                0,
                MemorySegment.class
        );

        target = target.asCollector(
                Object[].class,
                method.getParameterCount()
        );

        return target.asType(toMethodType(descriptor));
    }

    private static MethodHandle createInvokeJvmMethodHandle() {
        try {
            return MethodHandles.lookup().findStatic(
                    JvmStructureHandler.class,
                    "invokeJvmMethod",
                    MethodType.methodType(
                            Object.class,
                            Object.class,
                            Method.class,
                            Arena.class,
                            Object[].class
                    )
            );
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Object invokeJvmMethod(
            Object implementation,
            Method method,
            Arena arena,
            Object[] nativeArguments
    ) {
        try {
            Class<?>[] parameterTypes = method.getParameterTypes();
            Object[] arguments = new Object[nativeArguments.length];

            for (int i = 0; i < nativeArguments.length; i++) {
                arguments[i] = NativeValueConverter.fromNative(
                        parameterTypes[i],
                        nativeArguments[i]
                );
            }

            Object result = method.invoke(implementation, arguments);

            return NativeValueConverter.toNative(
                    method.getReturnType(),
                    result,
                    arena
            );
        } catch (Throwable ignored) {
            return defaultValue(method.getReturnType());
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == char.class) return '\0';
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0f;
        if (type == double.class) return 0.0;

        return null;
    }

    private static MethodType toMethodType(FunctionDescriptor descriptor) {
        Class<?>[] parameterTypes = descriptor.argumentLayouts()
                .stream()
                .map(JvmStructureHandler::getCarrier)
                .toArray(Class<?>[]::new);

        Class<?> returnType = descriptor.returnLayout()
                .<Class<?>>map(JvmStructureHandler::getCarrier)
                .orElse(void.class);

        return MethodType.methodType(returnType, parameterTypes);
    }

    private static Class<?> getCarrier(MemoryLayout layout) {
        if (layout instanceof ValueLayout valueLayout) {
            return valueLayout.carrier();
        }

        throw new IllegalArgumentException(
                "Unsupported JVM structure layout: " + layout
        );
    }
}
