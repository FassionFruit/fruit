package examples.unions;

import io.github.fassionfruit.FFM;
import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.annotation.NativeSymbol;
import io.github.fassionfruit.annotation.structure.NativeField;
import io.github.fassionfruit.annotation.structure.NativeUnion;

import java.lang.foreign.Arena;

public final class UnionExample {
    private UnionExample() {
    }

    @NativeUnion
    interface Value {
        @NativeField(value = "integer", order = 0)
        int getInteger();

        @NativeField(value = "integer", order = 0)
        void setInteger(int value);

        @NativeField(value = "decimal", order = 1)
        float getDecimal();

        @NativeField(value = "decimal", order = 1)
        void setDecimal(float value);
    }

    @NativeLibrary("native_example")
    interface Library {
        @NativeSymbol("native_value_integer")
        int readInteger(Value value);
    }

    public static void main(String[] args) {
        Library library = FFM.load(Library.class);

        try (Arena arena = Arena.ofConfined()) {
            Value value = FFM.allocateStructure(Value.class, arena);
            value.setInteger(123);

            System.out.println(library.readInteger(value));
        }
    }
}
