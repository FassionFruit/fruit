package io.github.fassionfruit.internal;

import io.github.fassionfruit.annotation.NativeEnumValue;
import io.github.fassionfruit.annotation.structure.NativeStructure;
import io.github.fassionfruit.structure.NativeStructureHandler;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Field;

public final class NativeValueConverter {

    private static final long MAX_STRING_BYTES = 1024 * 1024;

    private NativeValueConverter() {
    }

    public static Object toNative(Class<?> type, Object value, Arena arena) {
        if (value == null) {
            if (type == String.class
                    || type == MemorySegment.class
                    || type.isAnnotationPresent(NativeStructure.class)) {
                return MemorySegment.NULL;
            }

            return null;
        }

        if (type.isAnnotationPresent(NativeStructure.class)) {
            if (!type.isInstance(value)) {
                throw new IllegalArgumentException(
                        "Expected native structure: " + type.getName()
                );
            }

            return NativeStructureHandler.getSegment(value);
        }

        if (type == String.class) {
            return arena.allocateFrom((String) value);
        }

        if (type == MemorySegment.class) {
            return value;
        }

        if (type == char.class || type == Character.class) {
            return (short) ((Character) value).charValue();
        }

        if (type == boolean.class || type == Boolean.class) {
            return (boolean) value ? 1 : 0;
        }

        if (type.isEnum()) {
            return getEnumValue((Enum<?>) value);
        }

        return value;
    }

    public static Object fromNative(Class<?> type, Object value) {
        if (type == void.class) return null;

        if (type.isAnnotationPresent(NativeStructure.class)) {
            throw new IllegalArgumentException(
                    "Native structure return values are not supported yet: "
                            + type.getName()
            );
        }

        if (type == String.class) {
            MemorySegment segment = (MemorySegment) value;

            if (segment.address() == 0) return null;

            return segment.reinterpret(MAX_STRING_BYTES).getString(0);
        }

        if (type == MemorySegment.class) return value;

        if (type == char.class || type == Character.class) {
            return (char) ((short) value);
        }

        if (type == boolean.class || type == Boolean.class) {
            return ((int) value) != 0;
        }

        if (type.isEnum()) {
            return getEnumConstant(type, (int) value);
        }

        return value;
    }

    private static int getEnumValue(Enum<?> value) {
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

    private static <T> T getEnumConstant(Class<T> type, int value) {
        for (Object constant : type.getEnumConstants()) {
            Enum<?> enumValue = (Enum<?>) constant;

            if (getEnumValue(enumValue) == value) {
                return type.cast(constant);
            }
        }

        throw new IllegalArgumentException("Unknown native enum value: " + value);
    }
}