package io.github.fassionfruit;

import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.internal.NativeLoader;

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
}