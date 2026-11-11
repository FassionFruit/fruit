package examples.callbacks;

import io.github.fassionfruit.FFM;
import io.github.fassionfruit.annotation.NativeCallback;
import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.annotation.NativeSymbol;

public final class CallbackExample {
    private CallbackExample() {
    }

    @NativeCallback
    interface Callback {
        int apply(int value);
    }

    @NativeLibrary("native_example")
    interface Library {
        @NativeSymbol("native_apply")
        int apply(Callback callback, int value);
    }

    public static void main(String[] args) {
        Library library = FFM.load(Library.class);
        System.out.println(library.apply(value -> value * 2, 21));
    }
}
