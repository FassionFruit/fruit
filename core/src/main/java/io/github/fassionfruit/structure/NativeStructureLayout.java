package io.github.fassionfruit.structure;

import io.github.fassionfruit.annotation.structure.NativeField;
import io.github.fassionfruit.annotation.structure.NativeStructure;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NativeStructureLayout {

    private NativeStructureLayout() {
    }

    public static GroupLayout create(Class<?> type) {
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

        Map<String, FieldInfo> fields = new LinkedHashMap<>();

        for (Method method : type.getMethods()) {
            NativeField annotation = method.getAnnotation(NativeField.class);

            if (annotation == null) {
                continue;
            }

            validateMethod(method);

            String name = annotation.value();

            if (name.isBlank()) {
                throw new IllegalArgumentException(
                        "Structure field name cannot be blank"
                );
            }

            Class<?> fieldType = getFieldType(method);
            Class<?> elementType = getElementType(method);
            int length = annotation.length();

            if (fieldType == NativeArray.class && length <= 0) {
                throw new IllegalArgumentException(
                        "NativeArray length must be greater than zero: " + name
                );
            }

            if (fieldType != NativeArray.class && length != 0) {
                throw new IllegalArgumentException(
                        "Length can only be specified for NativeArray: " + name
                );
            }

            FieldInfo info = new FieldInfo(
                    name,
                    annotation.order(),
                    fieldType,
                    elementType,
                    length
            );

            FieldInfo existing = fields.putIfAbsent(name, info);

            if (existing != null && !existing.equals(info)) {
                throw new IllegalArgumentException(
                        "Inconsistent field declaration: " + name
                );
            }
        }

        if (fields.isEmpty()) {
            throw new IllegalArgumentException(
                    "Structure must declare at least one @NativeField"
            );
        }

        List<FieldInfo> orderedFields = new ArrayList<>(fields.values());
        orderedFields.sort(Comparator.comparingInt(FieldInfo::order));

        for (int i = 1; i < orderedFields.size(); i++) {
            if (orderedFields.get(i - 1).order() == orderedFields.get(i).order()) {
                throw new IllegalArgumentException(
                        "Duplicate structure field order: "
                                + orderedFields.get(i).order()
                );
            }
        }

        List<MemoryLayout> layouts = new ArrayList<>();

        long offset = 0;
        long maxAlignment = 1;

        for (FieldInfo field : orderedFields) {
            MemoryLayout layout;

            if (field.type() == NativeArray.class) {
                layout = MemoryLayout.sequenceLayout(
                        field.length(),
                        map(field.elementType())
                );
            } else {
                layout = map(field.type());
            }

            long alignment = layout.byteAlignment();
            long padding = (alignment - offset % alignment) % alignment;

            if (padding > 0) {
                layouts.add(MemoryLayout.paddingLayout(padding));
                offset += padding;
            }

            layouts.add(layout.withName(field.name()));
            offset += layout.byteSize();

            maxAlignment = Math.max(maxAlignment, alignment);
        }

        long finalPadding = (maxAlignment - offset % maxAlignment) % maxAlignment;

        if (finalPadding > 0) {
            layouts.add(MemoryLayout.paddingLayout(finalPadding));
        }

        return MemoryLayout.structLayout(
                layouts.toArray(MemoryLayout[]::new)
        ).withByteAlignment(maxAlignment);
    }

    public static long offset(Class<?> type, String fieldName) {
        return create(type).byteOffset(
                PathElement.groupElement(fieldName)
        );
    }

    public static MemoryLayout fieldLayout(Class<?> type, String fieldName) {
        return create(type).select(
                PathElement.groupElement(fieldName)
        );
    }

    public static MemoryLayout map(Class<?> type) {
        if (type == String.class || type == MemorySegment.class) {
            return ValueLayout.ADDRESS;
        }

        if (type.isAnnotationPresent(NativeStructure.class)) {
            return create(type);
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
                "Unsupported structure field type: " + type.getName()
        );
    }

    private static void validateMethod(Method method) {
        if (method.getParameterCount() > 1) {
            throw new IllegalArgumentException(
                    "Structure field method must have zero or one parameter: "
                            + method.getName()
            );
        }

        if (method.getParameterCount() == 0 && method.getReturnType() == void.class) {
            throw new IllegalArgumentException(
                    "Structure getter cannot return void: " + method.getName()
            );
        }

        if (method.getParameterCount() == 1 && method.getReturnType() != void.class) {
            throw new IllegalArgumentException(
                    "Structure setter must return void: " + method.getName()
            );
        }

        if (method.getReturnType() == NativeArray.class
                && method.getParameterCount() != 0) {
            throw new IllegalArgumentException(
                    "NativeArray must be declared as a getter: " + method.getName()
            );
        }
    }

    private static Class<?> getFieldType(Method method) {
        return method.getParameterCount() == 0
                ? method.getReturnType()
                : method.getParameterTypes()[0];
    }

    private static Class<?> getElementType(Method method) {
        if (method.getReturnType() != NativeArray.class) {
            return null;
        }

        if (!(method.getGenericReturnType() instanceof ParameterizedType parameterized)) {
            throw new IllegalArgumentException(
                    "NativeArray must declare its component type: " + method.getName()
            );
        }

        if (!(parameterized.getActualTypeArguments()[0] instanceof Class<?> elementType)) {
            throw new IllegalArgumentException(
                    "Unsupported NativeArray component type: " + method.getName()
            );
        }

        return elementType;
    }

    private record FieldInfo(
            String name,
            int order,
            Class<?> type,
            Class<?> elementType,
            int length
    ) {
    }
}
