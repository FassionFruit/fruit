import com.sun.jdi.Value;
import io.github.fassionfruit.FFM;
import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.annotation.NativeSymbol;
import io.github.fassionfruit.annotation.structure.NativeField;
import io.github.fassionfruit.annotation.structure.NativeUnion;
import io.github.fassionfruit.structure.NativeArray;
import io.github.fassionfruit.structure.NativeStructureLayout;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NativeUnion
public interface NativeValue {
    @NativeField(value = "integer", order = 0)
    int getInteger();

    @NativeField(value = "integer", order = 0)
    void setInteger(int value);

    @NativeField(value = "decimal", order = 1)
    double getDecimal();

    @NativeField(value = "decimal", order = 1)
    void setDecimal(double value);

    @NativeField(value = "bytes", order = 2, length = 16)
    NativeArray<Byte> getBytes();
}

@NativeLibrary("../native/union_test")
interface UnionTest {
    @NativeSymbol("native_value_size")
    long nativeValueSize();

    @NativeSymbol("native_value_alignment")
    long nativeValueAlignment();

    @NativeSymbol("native_value_set_integer")
    void nativeValueSetInteger(NativeValue value, int number);

    @NativeSymbol("native_value_get_integer")
    int nativeValueGetInteger(NativeValue value);

    @NativeSymbol("native_value_set_decimal")
    void nativeValueSetDecimal(NativeValue value, double number);

    @NativeSymbol("native_value_get_decimal")
    double nativeValueGetDecimal(NativeValue value);
}

void main() {
    var nativeLibrary = FFM.load(UnionTest.class);

    assertEquals(
            nativeLibrary.nativeValueSize(),
            NativeStructureLayout.create(NativeValue.class).byteSize()
    );

    assertEquals(
            nativeLibrary.nativeValueAlignment(),
            NativeStructureLayout.create(NativeValue.class).byteAlignment()
    );

    try (Arena arena = Arena.ofConfined()) {
        NativeValue value = FFM.allocateStructure(NativeValue.class, arena);

        nativeLibrary.nativeValueSetInteger(value, 123);
        assertEquals(123, nativeLibrary.nativeValueGetInteger(value));

        nativeLibrary.nativeValueSetDecimal(value, 3.14);
        assertEquals(3.14, nativeLibrary.nativeValueGetDecimal(value), 0.0001);
    }
}