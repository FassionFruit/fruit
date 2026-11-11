import io.github.fassionfruit.FFM;
import io.github.fassionfruit.annotation.NativeCritical;
import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.annotation.NativeSymbol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CriticalDowncallTest {

    @NativeLibrary("../native/test")
    interface CriticalLibrary {
        @NativeSymbol("add_values")
        @NativeCritical
        int add(int left, int right);
    }

    @Test
    void invokesPrimitiveCriticalDowncall() {
        assertEquals(30, FFM.load(CriticalLibrary.class).add(10, 20));
    }
}
