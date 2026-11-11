package io.github.fassionfruit.structure;

import io.github.fassionfruit.annotation.NativeEnumValue;
import io.github.fassionfruit.annotation.structure.NativeUnion;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Field;

/** A bounded view over a fixed-size native array field. */
public final class NativeArray<T> {

    private final Class<T> componentType;
    private final MemorySegment segment;
    private final int length;
    private final long stride;

    NativeArray(
            Class<T> componentType,
            MemorySegment segment,
            int length,
            long stride
    ) {
        this.componentType = componentType;
        this.segment = segment;
        this.length = length;
        this.stride = stride;
    }

    /** Returns the number of elements in this native array. */
    public int length() {
        return length;
    }

    /** Reads the element at {@code index}. */
    public T get(int index) {
        checkIndex(index);

        long offset = index * stride;

        if (componentType.isAnnotationPresent(
                io.github.fassionfruit.annotation.structure.NativeStructure.class
        ) || componentType.isAnnotationPresent(NativeUnion.class)) {
            return NativeStructureHandler.create(
                    componentType,
                    segment.asSlice(offset, stride)
            );
        }

        Object value;

        if (componentType == MemorySegment.class) {
            value = segment.get(ValueLayout.ADDRESS, offset);
        } else if (componentType == Byte.class) {
            value = segment.get(ValueLayout.JAVA_BYTE, offset);
        } else if (componentType == Short.class) {
            value = segment.get(ValueLayout.JAVA_SHORT, offset);
        } else if (componentType == Character.class) {
            value = (char) segment.get(ValueLayout.JAVA_SHORT, offset);
        } else if (componentType == Integer.class) {
            value = segment.get(ValueLayout.JAVA_INT, offset);
        } else if (componentType == Boolean.class) {
            value = segment.get(ValueLayout.JAVA_INT, offset) != 0;
        } else if (componentType == Long.class) {
            value = segment.get(ValueLayout.JAVA_LONG, offset);
        } else if (componentType == Float.class) {
            value = segment.get(ValueLayout.JAVA_FLOAT, offset);
        } else if (componentType == Double.class) {
            value = segment.get(ValueLayout.JAVA_DOUBLE, offset);
        } else if (componentType.isEnum()) {
            value = getEnumConstant(
                    segment.get(ValueLayout.JAVA_INT, offset)
            );
        } else {
            throw new IllegalArgumentException(
                    "Unsupported array component type: " + componentType.getName()
            );
        }

        return componentType.cast(value);
    }

    /** Writes {@code value} to the element at {@code index}. */
    public void set(int index, T value) {
        checkIndex(index);

        if (value == null && componentType != MemorySegment.class) {
            throw new IllegalArgumentException("Array value cannot be null");
        }

        long offset = index * stride;

        if (componentType.isAnnotationPresent(
                io.github.fassionfruit.annotation.structure.NativeStructure.class
        ) || componentType.isAnnotationPresent(NativeUnion.class)) {
            MemorySegment source = NativeStructureHandler.getSegment(value);

            MemorySegment.copy(
                    source,
                    0,
                    segment,
                    offset,
                    stride
            );

            return;
        }

        if (componentType == MemorySegment.class) {
            segment.set(
                    ValueLayout.ADDRESS,
                    offset,
                    value == null ? MemorySegment.NULL : (MemorySegment) value
            );
        } else if (componentType == Byte.class) {
            segment.set(ValueLayout.JAVA_BYTE, offset, (Byte) value);
        } else if (componentType == Short.class) {
            segment.set(ValueLayout.JAVA_SHORT, offset, (Short) value);
        } else if (componentType == Character.class) {
            segment.set(ValueLayout.JAVA_SHORT, offset, (short) (char) value);
        } else if (componentType == Integer.class) {
            segment.set(ValueLayout.JAVA_INT, offset, (Integer) value);
        } else if (componentType == Boolean.class) {
            segment.set(ValueLayout.JAVA_INT, offset, (Boolean) value ? 1 : 0);
        } else if (componentType == Long.class) {
            segment.set(ValueLayout.JAVA_LONG, offset, (Long) value);
        } else if (componentType == Float.class) {
            segment.set(ValueLayout.JAVA_FLOAT, offset, (Float) value);
        } else if (componentType == Double.class) {
            segment.set(ValueLayout.JAVA_DOUBLE, offset, (Double) value);
        } else if (componentType.isEnum()) {
            segment.set(ValueLayout.JAVA_INT, offset, getEnumValue((Enum<?>) value));
        } else {
            throw new IllegalArgumentException(
                    "Unsupported array component type: " + componentType.getName()
            );
        }
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException(
                    "Index: " + index + ", Length: " + length
            );
        }
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

    private T getEnumConstant(int value) {
        for (Object constant : componentType.getEnumConstants()) {
            Enum<?> enumValue = (Enum<?>) constant;

            if (getEnumValue(enumValue) == value) {
                return componentType.cast(constant);
            }
        }

        throw new IllegalArgumentException(
                "Unknown native enum value: " + value
        );
    }
}
