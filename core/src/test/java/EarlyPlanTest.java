import io.github.fassionfruit.FFM;
import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.annotation.NativeSymbol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

@NativeLibrary("../native/test")
interface MissingNativeSymbolLibrary {
    @NativeSymbol("symbol_that_does_not_exist")
    int missing();
}

@Test
void main() {
    assertThrows(
            UnsatisfiedLinkError.class,
            () -> FFM.load(MissingNativeSymbolLibrary.class)
    );
}
