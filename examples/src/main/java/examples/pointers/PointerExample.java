package examples.pointers;

import io.github.fassionfruit.FFM;
import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.annotation.NativePointer;
import io.github.fassionfruit.annotation.NativeSymbol;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public final class PointerExample {
    private PointerExample() {
    }

    @NativeLibrary("native_example")
    interface Library {
        @NativeSymbol("native_increment")
        int increment(@NativePointer MemorySegment value);
    }

    public static void main(String[] args) {
        Library library = FFM.load(Library.class);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = arena.allocate(ValueLayout.JAVA_INT);
            value.set(ValueLayout.JAVA_INT, 0, 41);

            System.out.println(library.increment(value));
            System.out.println(value.get(ValueLayout.JAVA_INT, 0));
        }
    }
}
