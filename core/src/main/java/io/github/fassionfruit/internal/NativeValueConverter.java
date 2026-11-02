package io.github.fassionfruit.internal;

import io.github.fassionfruit.annotation.NativeEnumValue;

import java.lang.reflect.Field;

public final class NativeValueConverter {

    private NativeValueConverter() {
    }

    public static Object toNative(Class<?> type, Object value) {
        if (value == null) {
            return null;
        }

        if (type == boolean.class || type == Boolean.class) {
            return (boolean) value ? 1 : 0;
        }

        if (type == char.class || type == Character.class) {
            return (short) ((Character) value).charValue();
        }

        if (type.isEnum()) {
            return getEnumValue((Enum<?>) value);
        }

        return value;
    }

    public static Object fromNative(Class<?> type, Object value) {
        if (type == void.class) {
            return null;
        }

        if (type == boolean.class || type == Boolean.class) {
            return ((int) value) != 0;
        }

        if (type == char.class || type == Character.class) {
            return (char) ((short) value);
        }

        if (type.isEnum()) {
            return getEnumConstant(type, (int) value);
        }

        return value;
    }

    private static int getEnumValue(Enum<?> value) {
        try {
            Field field = value.getDeclaringClass().getField(value.name());
            NativeEnumValue annotation = field.getAnnotation(
                    NativeEnumValue.class
            );

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

        throw new IllegalArgumentException(
                "Unknown native enum value: " + value
        );
    }
}