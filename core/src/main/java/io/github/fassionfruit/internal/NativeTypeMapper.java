package io.github.fassionfruit.internal;

import io.github.fassionfruit.annotation.NativeCallback;
import io.github.fassionfruit.annotation.structure.NativeStructure;
import io.github.fassionfruit.annotation.structure.NativeUnion;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Method;

/** Maps supported Java binding types to FFM layouts and descriptors. */
public final class NativeTypeMapper {

    private NativeTypeMapper() {
    }

    /** Creates a native function descriptor for a Java method. */
    public static FunctionDescriptor createDescriptor(Method method) {
        Class<?> returnType = method.getReturnType();

        MemoryLayout[] parameterLayouts = new MemoryLayout[
                method.getParameterCount()
                ];

        Class<?>[] parameterTypes = method.getParameterTypes();

        for (int i = 0; i < parameterTypes.length; i++) {
            parameterLayouts[i] = map(parameterTypes[i]);
        }

        if (returnType == void.class) {
            return FunctionDescriptor.ofVoid(parameterLayouts);
        }

        return FunctionDescriptor.of(
                map(returnType),
                parameterLayouts
        );
    }

    /** Creates a descriptor for a method stored in a JVM vtable. */
    public static FunctionDescriptor createJvmMethodDescriptor(Method method) {
        FunctionDescriptor descriptor = createDescriptor(method);

        MemoryLayout[] arguments = new MemoryLayout[
                descriptor.argumentLayouts().size() + 1
                ];

        arguments[0] = ValueLayout.ADDRESS;

        for (int i = 0; i < descriptor.argumentLayouts().size(); i++) {
            arguments[i + 1] = descriptor.argumentLayouts().get(i);
        }

        if (descriptor.returnLayout().isEmpty()) {
            return FunctionDescriptor.ofVoid(arguments);
        }

        return FunctionDescriptor.of(
                descriptor.returnLayout().orElseThrow(),
                arguments
        );
    }

    /** Returns the native value layout for a supported Java type. */
    public static MemoryLayout map(Class<?> type) {
        if (type.isAnnotationPresent(NativeCallback.class)) {
            return ValueLayout.ADDRESS;
        }

        if (type.isAnnotationPresent(NativeStructure.class)
                || type.isAnnotationPresent(NativeUnion.class)) {
            return ValueLayout.ADDRESS;
        }

        if (type == String.class || type == java.lang.foreign.MemorySegment.class) {
            return ValueLayout.ADDRESS;
        }

        if (type.isEnum()) return ValueLayout.JAVA_INT;
        if (type == byte.class || type == Byte.class) return ValueLayout.JAVA_BYTE;
        if (type == short.class || type == Short.class
                || type == char.class || type == Character.class) {
            return ValueLayout.JAVA_SHORT;
        }
        if (type == int.class || type == Integer.class
                || type == boolean.class || type == Boolean.class) {
            return ValueLayout.JAVA_INT;
        }
        if (type == long.class || type == Long.class) return ValueLayout.JAVA_LONG;
        if (type == float.class || type == Float.class) return ValueLayout.JAVA_FLOAT;
        if (type == double.class || type == Double.class) return ValueLayout.JAVA_DOUBLE;

        throw new IllegalArgumentException(
                "Unsupported native type: " + type.getName()
        );
    }
}
