package io.github.fassionfruit.structure;

import io.github.fassionfruit.annotation.NativeEnumValue;
import io.github.fassionfruit.annotation.NativePointer;
import io.github.fassionfruit.annotation.structure.NativeField;
import io.github.fassionfruit.annotation.structure.NativeStructure;
import io.github.fassionfruit.annotation.structure.NativeUnion;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/** Provides proxy-backed getter and setter access to native memory. */
public final class NativeStructureHandler implements InvocationHandler {

    private static final long MAX_STRING_BYTES = 1024 * 1024;

    private final Class<?> type;
    private final MemorySegment segment;
    private final Arena stringArena;
    private final Map<Method, FieldInfo> fields = new HashMap<>();

    public NativeStructureHandler(Class<?> type, MemorySegment segment) {
        this(type, segment, Arena.ofAuto());
    }

    public NativeStructureHandler(
            Class<?> type,
            MemorySegment segment,
            Arena stringArena
    ) {
        this.type = type;
        this.segment = segment;
        this.stringArena = stringArena;

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
                    !method.isAnnotationPresent(NativePointer.class)
                            || method.getAnnotation(NativePointer.class).nullable(),
                    method.getParameterCount() == 1
            ));
        }
    }

    /** Creates a proxy over an existing native structure or union segment. */
    public static <T> T create(Class<T> type, MemorySegment segment) {
        return create(type, segment, Arena.ofAuto());
    }

    /** Creates a proxy and uses the supplied arena for string field storage. */
    public static <T> T create(
            Class<T> type,
            MemorySegment segment,
            Arena stringArena
    ) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException(
                    type.getName() + " must be an interface"
            );
        }

        if (!type.isAnnotationPresent(NativeStructure.class)
                && !type.isAnnotationPresent(NativeUnion.class)) {
            throw new IllegalArgumentException(
                    type.getName() + " is missing @NativeStructure or @NativeUnion"
            );
        }

        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                new NativeStructureHandler(type, segment, stringArena)
        ));
    }

    /** Returns the native segment backing a structure or union proxy. */
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

        if (fieldType == String.class) {
            MemorySegment pointer = value == null
                    ? MemorySegment.NULL
                    : stringArena.allocateFrom((String) value);

            segment.set(ValueLayout.ADDRESS, offset, pointer);
            return;
        }

        if (fieldType == MemorySegment.class) {
            if (value == null && !field.nullable()) {
                throw new IllegalArgumentException(
                        "Pointer field cannot be null: " + field.name()
                );
            }

            MemorySegment pointer = value == null
                    ? MemorySegment.NULL
                    : (MemorySegment) value;

            segment.set(ValueLayout.ADDRESS, offset, pointer);
            return;
        }

        if (fieldType.isAnnotationPresent(NativeStructure.class)
                || fieldType.isAnnotationPresent(NativeUnion.class)) {
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

        if (fieldType == byte.class || fieldType == Byte.class) {
            segment.set(ValueLayout.JAVA_BYTE, offset, ((Number) value).byteValue());
            return;
        }

        if (fieldType == short.class || fieldType == Short.class) {
            segment.set(ValueLayout.JAVA_SHORT, offset, ((Number) value).shortValue());
            return;
        }

        if (fieldType == char.class || fieldType == Character.class) {
            segment.set(ValueLayout.JAVA_SHORT, offset, (short) ((Character) value).charValue());
            return;
        }

        if (fieldType == int.class || fieldType == Integer.class) {
            segment.set(ValueLayout.JAVA_INT, offset, ((Number) value).intValue());
            return;
        }

        if (fieldType == boolean.class || fieldType == Boolean.class) {
            segment.set(ValueLayout.JAVA_INT, offset, (Boolean) value ? 1 : 0);
            return;
        }

        if (fieldType == long.class || fieldType == Long.class) {
            segment.set(ValueLayout.JAVA_LONG, offset, ((Number) value).longValue());
            return;
        }

        if (fieldType == float.class || fieldType == Float.class) {
            segment.set(ValueLayout.JAVA_FLOAT, offset, ((Number) value).floatValue());
            return;
        }

        if (fieldType == double.class || fieldType == Double.class) {
            segment.set(ValueLayout.JAVA_DOUBLE, offset, ((Number) value).doubleValue());
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

        if (fieldType == String.class) {
            MemorySegment pointer = segment.get(ValueLayout.ADDRESS, offset);

            if (pointer.address() == 0) {
                return null;
            }

            return pointer.reinterpret(MAX_STRING_BYTES).getString(0);
        }

        if (fieldType == MemorySegment.class) {
            return segment.get(ValueLayout.ADDRESS, offset);
        }

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

        if (fieldType.isAnnotationPresent(NativeStructure.class)
                || fieldType.isAnnotationPresent(NativeUnion.class)) {
            long size = NativeStructureLayout.create(fieldType).byteSize();

            return create(
                    fieldType,
                    segment.asSlice(offset, size)
            );
        }

        if (fieldType == byte.class || fieldType == Byte.class) {
            return segment.get(ValueLayout.JAVA_BYTE, offset);
        }
        if (fieldType == short.class || fieldType == Short.class) {
            return segment.get(ValueLayout.JAVA_SHORT, offset);
        }
        if (fieldType == char.class || fieldType == Character.class) {
            return (char) segment.get(ValueLayout.JAVA_SHORT, offset);
        }
        if (fieldType == int.class || fieldType == Integer.class) {
            return segment.get(ValueLayout.JAVA_INT, offset);
        }
        if (fieldType == boolean.class || fieldType == Boolean.class) {
            return segment.get(ValueLayout.JAVA_INT, offset) != 0;
        }
        if (fieldType == long.class || fieldType == Long.class) {
            return segment.get(ValueLayout.JAVA_LONG, offset);
        }
        if (fieldType == float.class || fieldType == Float.class) {
            return segment.get(ValueLayout.JAVA_FLOAT, offset);
        }
        if (fieldType == double.class || fieldType == Double.class) {
            return segment.get(ValueLayout.JAVA_DOUBLE, offset);
        }

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
            boolean nullable,
            boolean setter
    ) {
    }
}
