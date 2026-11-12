package io.github.fassionfruit.structure;

import io.github.fassionfruit.annotation.structure.NativeField;
import io.github.fassionfruit.annotation.NativeCallback;
import io.github.fassionfruit.annotation.NativePointer;
import io.github.fassionfruit.annotation.structure.NativeStructure;
import io.github.fassionfruit.annotation.structure.NativeUnion;
import io.github.fassionfruit.internal.NativeTypeMapper;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds and queries layouts for annotated native structures and unions. */
public final class NativeStructureLayout {

    private NativeStructureLayout() {
    }

    /** Creates the ABI layout for a native structure or union interface. */
    public static GroupLayout create(Class<?> type) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException(
                    type.getName() + " must be an interface"
            );
        }

        boolean structure = type.isAnnotationPresent(NativeStructure.class);
        boolean union = type.isAnnotationPresent(NativeUnion.class);
        boolean packed = structure && type.getAnnotation(NativeStructure.class).packed();

        if (!structure && !union) {
            throw new IllegalArgumentException(
                    type.getName() + " is missing @NativeStructure or @NativeUnion"
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

            if (method.isAnnotationPresent(NativePointer.class)
                    && fieldType != MemorySegment.class) {
                throw new IllegalArgumentException(
                        "@NativePointer requires MemorySegment: " + name
                );
            }

            if (fieldType == NativeArray.class && length <= 0) {
                throw new IllegalArgumentException(
                        "NativeArray length must be greater than zero: " + name
                );
            }

            if (fieldType == NativeArray.class
                    && (elementType == String.class
                    || elementType.isAnnotationPresent(NativeCallback.class))) {
                throw new IllegalArgumentException(
                        "Unsupported NativeArray component type: " + name
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

        if (union) {
            MemoryLayout[] unionLayouts = orderedFields.stream()
                    .map(field -> field.type() == NativeArray.class
                            ? MemoryLayout.sequenceLayout(
                                    field.length(),
                                    map(field.elementType())
                            ).withName(field.name())
                            : map(field.type()).withName(field.name()))
                    .toArray(MemoryLayout[]::new);

            return MemoryLayout.unionLayout(unionLayouts);
        }

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

            long alignment = packed ? 1 : layout.byteAlignment();

            layout = layout.withByteAlignment(alignment);

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

    /** Returns the byte offset of a named structure or union member. */
    public static long offset(Class<?> type, String fieldName) {
        return create(type).byteOffset(
                PathElement.groupElement(fieldName)
        );
    }

    /** Returns the layout of a named structure or union member. */
    public static MemoryLayout fieldLayout(Class<?> type, String fieldName) {
        return create(type).select(
                PathElement.groupElement(fieldName)
        );
    }

    /** Maps a Java field type to its inline native memory layout. */
    public static MemoryLayout map(Class<?> type) {
        if (type.isAnnotationPresent(NativeCallback.class)) {
            throw new IllegalArgumentException(
                    "Native callbacks are only supported as function values: "
                            + type.getName()
            );
        }

        if (type.isAnnotationPresent(NativeStructure.class)
                || type.isAnnotationPresent(NativeUnion.class)) {
            return create(type);
        }

        return NativeTypeMapper.map(type);
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

    private static long alignUp(long offset, long alignment) {
        return ((offset + alignment - 1) / alignment) * alignment;
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
