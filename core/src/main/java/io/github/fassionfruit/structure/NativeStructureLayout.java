package io.github.fassionfruit.structure;

import io.github.fassionfruit.annotation.structure.NativeField;
import io.github.fassionfruit.annotation.structure.NativeStructure;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.reflect.Method;
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

            if (annotation == null) continue;

            validateMethod(method);

            Class<?> fieldType = getFieldType(method);
            FieldInfo info = new FieldInfo(
                    annotation.value(),
                    annotation.order(),
                    fieldType
            );

            FieldInfo existing = fields.putIfAbsent(info.name(), info);

            if (existing != null) {
                if (existing.order() != info.order()
                        || existing.type() != info.type()) {
                    throw new IllegalArgumentException(
                            "Inconsistent field declaration: " + info.name()
                    );
                }
            }
        }

        if (fields.isEmpty()) {
            throw new IllegalArgumentException(
                    "Structure must declare at least one @NativeField"
            );
        }

        List<FieldInfo> orderedFields = new ArrayList<>(fields.values());
        orderedFields.sort(Comparator.comparingInt(FieldInfo::order));

        List<MemoryLayout> layouts = new ArrayList<>();
        long offset = 0;
        long maxAlignment = 1;

        for (FieldInfo field : orderedFields) {
            MemoryLayout layout = map(field.type())
                    .withName(field.name());

            long alignment = layout.byteAlignment();
            long padding = (alignment - offset % alignment) % alignment;

            if (padding > 0) {
                layouts.add(MemoryLayout.paddingLayout(padding * 8));
                offset += padding;
            }

            layouts.add(layout);
            offset += layout.byteSize();
            maxAlignment = Math.max(maxAlignment, alignment);
        }

        long finalPadding = (maxAlignment - offset % maxAlignment) % maxAlignment;

        if (finalPadding > 0) {
            layouts.add(MemoryLayout.paddingLayout(finalPadding * 8));
        }

        return MemoryLayout.structLayout(
                layouts.toArray(MemoryLayout[]::new)
        );
    }

    public static long offset(Class<?> type, String fieldName) {
        GroupLayout layout = create(type);

        return layout.byteOffset(PathElement.groupElement(fieldName));
    }

    public static MemoryLayout fieldLayout(Class<?> type, String fieldName) {
        GroupLayout layout = create(type);

        return layout.select(PathElement.groupElement(fieldName));
    }

    private static MemoryLayout map(Class<?> type) {
        if (type.isEnum()) return ValueLayout.JAVA_INT;
        if (type == byte.class) return ValueLayout.JAVA_BYTE;
        if (type == short.class || type == char.class) return ValueLayout.JAVA_SHORT;
        if (type == int.class || type == boolean.class) return ValueLayout.JAVA_INT;
        if (type == long.class) return ValueLayout.JAVA_LONG;
        if (type == float.class) return ValueLayout.JAVA_FLOAT;
        if (type == double.class) return ValueLayout.JAVA_DOUBLE;

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
    }

    private static Class<?> getFieldType(Method method) {
        return method.getParameterCount() == 0
                ? method.getReturnType()
                : method.getParameterTypes()[0];
    }

    private record FieldInfo(
            String name,
            int order,
            Class<?> type
    ) {
    }
}