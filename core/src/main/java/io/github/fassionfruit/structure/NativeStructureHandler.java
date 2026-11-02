package io.github.fassionfruit.structure;

import io.github.fassionfruit.annotation.NativeEnumValue;
import io.github.fassionfruit.annotation.structure.NativeField;
import io.github.fassionfruit.annotation.structure.NativeStructure;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
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

            Class<?> elementType = null;

            if (fieldType == NativeArray.class) {
                ParameterizedType parameterized =
                        (ParameterizedType) method.getGenericReturnType();

                elementType = (Class<?>) parameterized.getActualTypeArguments()[0];
            }

            fields.put(method, new FieldInfo(
                    annotation.value(),
                    NativeStructureLayout.offset(type, annotation.value()),
                    fieldType,
                    elementType,
                    annotation.length(),
                    method.getParameterCount() == 1
            ));
        }
    }

    public static <T> T create(Class<T> type, MemorySegment segment) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException(
                    type.getName() + " must be an interface"
            );
        }

        if (!type.isAnnotationPresent(NativeStructure.class)) {
            throw new IllegalArgumentException(
                    type.getName() + " is missing @NativeStructure"
            );
        }

        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                new NativeStructureHandler(type, segment)
        ));
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
        Class<?> fieldType = field.type();
        long offset = field.offset();

        if (fieldType.isAnnotationPresent(NativeStructure.class)) {
            if (value == null) {
                throw new IllegalArgumentException(
                        "Nested structure cannot be null: " + field.name()
                );
            }

            MemorySegment source = getSegment(value);
            MemorySegment target = segment.asSlice(
                    offset,
                    NativeStructureLayout.create(fieldType).byteSize()
            );

            MemorySegment.copy(source, 0, target, 0, target.byteSize());
            return;
        }

        if (fieldType == byte.class) {
            segment.set(ValueLayout.JAVA_BYTE, offset, (byte) value);
            return;
        }

        if (fieldType == short.class) {
            segment.set(ValueLayout.JAVA_SHORT, offset, (short) value);
            return;
        }

        if (fieldType == char.class) {
            segment.set(ValueLayout.JAVA_SHORT, offset, (short) ((char) value));
            return;
        }

        if (fieldType == int.class) {
            segment.set(ValueLayout.JAVA_INT, offset, (int) value);
            return;
        }

        if (fieldType == boolean.class) {
            segment.set(ValueLayout.JAVA_INT, offset, (boolean) value ? 1 : 0);
            return;
        }

        if (fieldType == long.class) {
            segment.set(ValueLayout.JAVA_LONG, offset, (long) value);
            return;
        }

        if (fieldType == float.class) {
            segment.set(ValueLayout.JAVA_FLOAT, offset, (float) value);
            return;
        }

        if (fieldType == double.class) {
            segment.set(ValueLayout.JAVA_DOUBLE, offset, (double) value);
            return;
        }

        if (fieldType.isEnum()) {
            segment.set(ValueLayout.JAVA_INT, offset, getEnumValue((Enum<?>) value));
            return;
        }

        throw new IllegalArgumentException(
                "Unsupported structure field type: " + fieldType.getName()
        );
    }

    private Object read(FieldInfo field) {
        Class<?> fieldType = field.type();
        long offset = field.offset();

        if (fieldType == NativeArray.class) {
            long stride = NativeStructureLayout.map(
                    field.elementType()
            ).byteSize();

            MemorySegment arraySegment = segment.asSlice(
                    offset,
                    stride * field.length()
            );

            return new NativeArray<>(
                    field.elementType(),
                    arraySegment,
                    field.length(),
                    stride
            );
        }

        if (fieldType.isAnnotationPresent(NativeStructure.class)) {
            long size = NativeStructureLayout.create(fieldType).byteSize();

            return create(
                    fieldType,
                    segment.asSlice(offset, size)
            );
        }

        if (fieldType == byte.class) return segment.get(ValueLayout.JAVA_BYTE, offset);
        if (fieldType == short.class) return segment.get(ValueLayout.JAVA_SHORT, offset);
        if (fieldType == char.class) return (char) segment.get(ValueLayout.JAVA_SHORT, offset);
        if (fieldType == int.class) return segment.get(ValueLayout.JAVA_INT, offset);
        if (fieldType == boolean.class) return segment.get(ValueLayout.JAVA_INT, offset) != 0;
        if (fieldType == long.class) return segment.get(ValueLayout.JAVA_LONG, offset);
        if (fieldType == float.class) return segment.get(ValueLayout.JAVA_FLOAT, offset);
        if (fieldType == double.class) return segment.get(ValueLayout.JAVA_DOUBLE, offset);

        if (fieldType.isEnum()) {
            int value = segment.get(ValueLayout.JAVA_INT, offset);
            return getEnumConstant(fieldType, value);
        }

        throw new IllegalArgumentException(
                "Unsupported structure field type: " + fieldType.getName()
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

        throw new IllegalArgumentException(
                "Unknown native enum value: " + value
        );
    }

    private record FieldInfo(
            String name,
            long offset,
            Class<?> type,
            Class<?> elementType,
            int length,
            boolean setter
    ) {
    }
}