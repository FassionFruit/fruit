package examples.strings;

import io.github.fassionfruit.FFM;
import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.annotation.NativeSymbol;

public final class StringExample {
    private StringExample() {
    }

    @NativeLibrary("native_example")
    interface Library {
        @NativeSymbol("native_message")
        String message();

        @NativeSymbol("native_string_length")
        int length(String value);
    }

    public static void main(String[] args) {
        Library library = FFM.load(Library.class);

        System.out.println(library.message());
        System.out.println(library.length("FassionFruit"));
    }
}
