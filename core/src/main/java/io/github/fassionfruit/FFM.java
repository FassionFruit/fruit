package io.github.fassionfruit;

import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.annotation.structure.JvmStructure;
import io.github.fassionfruit.annotation.structure.NativeStructure;
import io.github.fassionfruit.internal.NativeLoader;
import io.github.fassionfruit.jvm.JvmStructureHandler;
import io.github.fassionfruit.structure.NativeStructureHandler;
import io.github.fassionfruit.structure.NativeStructureLayout;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public final class FFM {
    private FFM() {
    }

    public static <T> T load(Class<T> type) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException(
                    type.getName() + " must be an interface"
            );
        }

        NativeLibrary annotation = type.getAnnotation(NativeLibrary.class);

        if (annotation == null) {
            throw new IllegalArgumentException(
                    type.getName() + " is missing @NativeLibrary"
            );
        }

        return NativeLoader.load(type, annotation.value());
    }

    public static <T> T allocateStructure(Class<T> type, Arena arena) {
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

        MemorySegment segment = arena.allocate(
                NativeStructureLayout.create(type)
        );

        return NativeStructureHandler.create(type, segment);
    }

    public static <T> MemorySegment export(
            Class<T> type,
            T implementation,
            Arena arena
    ) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException(
                    type.getName() + " must be an interface"
            );
        }

        if (!type.isAnnotationPresent(JvmStructure.class)) {
            throw new IllegalArgumentException(
                    type.getName() + " is missing @JvmStructure"
            );
        }

        if (!type.isInstance(implementation)) {
            throw new IllegalArgumentException(
                    "Implementation must implement " + type.getName()
            );
        }

        return JvmStructureHandler.export(type, implementation, arena);
    }
}