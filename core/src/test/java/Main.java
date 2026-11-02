import io.github.fassionfruit.FFM;
import io.github.fassionfruit.annotation.NativeEnumValue;
import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.annotation.NativeSymbol;
import org.junit.jupiter.api.Test;

@NativeLibrary("../native/test.dll")
public interface TestLibrary {
    @NativeSymbol("calculate_value")
    int calculate(int input);

    @NativeSymbol("add_values")
    int add(int a, int b);

    @NativeSymbol("is_positive")
    boolean isPositive(int value);

    @NativeSymbol("get_character")
    char getCharacter();

    @NativeSymbol("echo_character")
    char echoCharacter(char value);

    @NativeSymbol("set_render_mode")
    RenderMode setRenderMode(RenderMode mode);
}

public enum RenderMode {
    @NativeEnumValue(0) DEFAULT,
    @NativeEnumValue(1) FAST,
    @NativeEnumValue(2) QUALITY
}

@Test
void main() {
    TestLibrary library = FFM.load(TestLibrary.class);

    assert library.calculate(10) == 20;
    assert library.add(10, 20) == 30;

    assert library.isPositive(10);
    assert !library.isPositive(-10);

    assert library.getCharacter() == 'A';
    assert library.echoCharacter('B') == 'B';

    RenderMode result = library.setRenderMode(RenderMode.QUALITY);
    assert result == RenderMode.QUALITY;
}