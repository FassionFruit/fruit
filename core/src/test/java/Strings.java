import io.github.fassionfruit.FFM;
import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.annotation.NativeSymbol;
import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NativeLibrary("../native/strings")
public interface TestLibrary {

    @NativeSymbol("get_message")
    String getMessage();

    @NativeSymbol("echo_string")
    String echoString(String value);

    @NativeSymbol("get_string_length")
    int getStringLength(String value);

    @NativeSymbol("get_pointer")
    MemorySegment getPointer();
}

@Test
void main() {
    System.out.println("========[Strings]========");
    var library = FFM.load(TestLibrary.class);

    assertEquals("Hello from native!", library.getMessage());
    assertEquals("FassionFruit", library.echoString("FassionFruit"));
    assertEquals(5, library.getStringLength("Hello"));

    MemorySegment pointer = library.getPointer();
    assertEquals(
            1234,
            pointer.reinterpret(Integer.BYTES)
                    .get(ValueLayout.JAVA_INT, 0)
    );
}
