package examples.basic;

import io.github.fassionfruit.FFM;
import io.github.fassionfruit.annotation.NativeEnumValue;
import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.annotation.NativeSymbol;

public final class BasicExample {
    private BasicExample() {
    }

    @NativeLibrary("native_example")
    interface Library {
        @NativeSymbol("native_add")
        int add(int left, int right);

        @NativeSymbol("native_mode")
        Mode mode(Mode value);
    }

    enum Mode {
        @NativeEnumValue(0) DEFAULT,
        @NativeEnumValue(1) FAST
    }

    public static void main(String[] args) {
        Library library = FFM.load(Library.class);

        System.out.println(library.add(10, 20));
        System.out.println(library.mode(Mode.FAST));
    }
}
