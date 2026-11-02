import io.github.fassionfruit.FFM;
import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.annotation.NativeSymbol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NativeLibrary("../samples/test.dll")
public interface TestLibrary {

    @NativeSymbol("calculate_value")
    int calculate(int input);

    @NativeSymbol("add_values")
    int add(int a, int b);

    @NativeSymbol("set_value")
    void set(int value);
}

@Test
void main() {
    var lib = FFM.load(TestLibrary.class);

    assertEquals(5, lib.add(3, 2));
}