package io.github.fassionfruit;

import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.annotation.structure.JvmStructure;
import io.github.fassionfruit.annotation.structure.NativeStructure;
import io.github.fassionfruit.annotation.structure.NativeUnion;
import io.github.fassionfruit.internal.NativeLoader;
import io.github.fassionfruit.jvm.JvmStructureHandler;
import io.github.fassionfruit.structure.NativeStructureHandler;
import io.github.fassionfruit.structure.NativeStructureLayout;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public final class FFM {
    private FFM() {
    }

    /** Loads the native library declared by {@link NativeLibrary}. */
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

    /** Returns the errno captured by the most recent native call on this thread. */
    public static int lastErrno() {
        return NativeLoader.lastErrno();
    }

    /** Allocates a native structure or union in the supplied arena. */
    public static <T> T allocateStructure(Class<T> type, Arena arena) {
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

        MemorySegment segment = arena.allocate(
                NativeStructureLayout.create(type)
        );

        return NativeStructureHandler.create(type, segment, arena);
    }

    /** Exports an implementation as a native-callable JVM structure. */
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
