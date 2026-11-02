import io.github.fassionfruit.FFM;
import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.annotation.NativeSymbol;
import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;

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

    System.out.println(library.getMessage());
    System.out.println(library.echoString("FassionFruit"));
    System.out.println(library.getStringLength("Hello"));

    MemorySegment pointer = library.getPointer();
    System.out.println(pointer);
}