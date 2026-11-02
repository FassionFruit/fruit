import io.github.fassionfruit.FFM;
import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.annotation.NativeSymbol;

@NativeLibrary("samples/test.dll")
public interface TestLibrary {

    @NativeSymbol("calculate_value")
    int calculate(int input);

    @NativeSymbol("add_values")
    int add(int a, int b);

    @NativeSymbol("set_value")
    void set(int value);
}

void main() {
    var lib = FFM.load(TestLibrary.class);

    System.out.println(lib.add(3, 2));
}