package examples.critical;

import io.github.fassionfruit.FFM;
import io.github.fassionfruit.annotation.NativeCritical;
import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.annotation.NativeSymbol;

public final class CriticalExample {
    private CriticalExample() {
    }

    @NativeLibrary("native_example")
    interface Library {
        @NativeSymbol("native_add")
        @NativeCritical
        int add(int left, int right);
    }

    public static void main(String[] args) {
        System.out.println(FFM.load(Library.class).add(10, 20));
    }
}
