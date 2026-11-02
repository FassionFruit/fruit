package io.github.fassionfruit.structure;

import io.github.fassionfruit.annotation.NativeEnumValue;
import io.github.fassionfruit.annotation.structure.NativeField;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public final class NativeStructureHandler implements InvocationHandler {

    private final Class<?> type;
    private final MemorySegment segment;
    private final Map<Method, FieldInfo> fields = new HashMap<>();

    public NativeStructureHandler(Class<?> type, MemorySegment segment) {
        this.type = type;
        this.segment = segment;

        for (Method method : type.getMethods()) {
            NativeField annotation = method.getAnnotation(NativeField.class);

            if (annotation == null) {
                continue;
            }

            Class<?> fieldType = method.getParameterCount() == 0
                    ? method.getReturnType()
                    : method.getParameterTypes()[0];

            fields.put(method, new FieldInfo(
                    annotation.value(),
                    NativeStructureLayout.offset(type, annotation.value()),
                    fieldType,
                    method.getParameterCount() == 1
            ));
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "toString" -> type.getSimpleName() + "[" + segment + "]";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        FieldInfo field = fields.get(method);

        if (field == null) {
            throw new UnsupportedOperationException(
                    "Unsupported structure method: " + method.getName()
            );
        }

        if (field.setter()) {
            write(field, args[0]);
            return null;
        }

        return read(field);
    }

    private void write(FieldInfo field, Object value) {
        Class<?> type = field.type();
        long offset = field.offset();

        if (type == byte.class) {
            segment.set(ValueLayout.JAVA_BYTE, offset, (byte) value);
            return;
        }

        if (type == short.class) {
            segment.set(ValueLayout.JAVA_SHORT, offset, (short) value);
            return;
        }

        if (type == char.class) {
            segment.set(ValueLayout.JAVA_SHORT, offset, (short) ((char) value));
            return;
        }

        if (type == int.class) {
            segment.set(ValueLayout.JAVA_INT, offset, (int) value);
            return;
        }

        if (type == boolean.class) {
            segment.set(ValueLayout.JAVA_INT, offset, (boolean) value ? 1 : 0);
            return;
        }

        if (type == long.class) {
            segment.set(ValueLayout.JAVA_LONG, offset, (long) value);
            return;
        }

        if (type == float.class) {
            segment.set(ValueLayout.JAVA_FLOAT, offset, (float) value);
            return;
        }

        if (type == double.class) {
            segment.set(ValueLayout.JAVA_DOUBLE, offset, (double) value);
            return;
        }

        if (type.isEnum()) {
            segment.set(ValueLayout.JAVA_INT, offset, getEnumValue((Enum<?>) value));
            return;
        }

        throw new IllegalArgumentException(
                "Unsupported structure field type: " + type.getName()
        );
    }

    private Object read(FieldInfo field) {
        Class<?> type = field.type();
        long offset = field.offset();

        if (type == byte.class) return segment.get(ValueLayout.JAVA_BYTE, offset);
        if (type == short.class) return segment.get(ValueLayout.JAVA_SHORT, offset);
        if (type == char.class) return (char) segment.get(ValueLayout.JAVA_SHORT, offset);
        if (type == int.class) return segment.get(ValueLayout.JAVA_INT, offset);
        if (type == boolean.class) return segment.get(ValueLayout.JAVA_INT, offset) != 0;
        if (type == long.class) return segment.get(ValueLayout.JAVA_LONG, offset);
        if (type == float.class) return segment.get(ValueLayout.JAVA_FLOAT, offset);
        if (type == double.class) return segment.get(ValueLayout.JAVA_DOUBLE, offset);

        if (type.isEnum()) {
            int value = segment.get(ValueLayout.JAVA_INT, offset);
            return getEnumConstant(type, value);
        }

        throw new IllegalArgumentException(
                "Unsupported structure field type: " + type.getName()
        );
    }

    private int getEnumValue(Enum<?> value) {
        try {
            Field field = value.getDeclaringClass().getField(value.name());
            NativeEnumValue annotation = field.getAnnotation(NativeEnumValue.class);

            if (annotation == null) {
                throw new IllegalArgumentException(
                        "Missing @NativeEnumValue on " + value.name()
                );
            }

            return annotation.value();
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Object getEnumConstant(Class<?> type, int value) {
        for (Object constant : type.getEnumConstants()) {
            Enum<?> enumValue = (Enum<?>) constant;

            if (getEnumValue(enumValue) == value) {
                return constant;
            }
        }

        throw new IllegalArgumentException("Unknown native enum value: " + value);
    }

    public static MemorySegment getSegment(Object proxy) {
        if (proxy == null || !Proxy.isProxyClass(proxy.getClass())) {
            throw new IllegalArgumentException(
                    "Object is not a native structure proxy"
            );
        }

        if (!(Proxy.getInvocationHandler(proxy) instanceof NativeStructureHandler handler)) {
            throw new IllegalArgumentException(
                    "Object is not a native structure proxy"
            );
        }

        return handler.segment;
    }

    private record FieldInfo(
            String name,
            long offset,
            Class<?> type,
            boolean setter
    ) {
    }
}