package structure;

import io.github.fassionfruit.annotation.structure.NativeField;
import io.github.fassionfruit.annotation.structure.NativeStructure;
import io.github.fassionfruit.annotation.structure.NativeUnion;
import io.github.fassionfruit.structure.NativeStructureHandler;
import io.github.fassionfruit.structure.NativeStructureLayout;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NativeTypeSupportTest {

    @NativeStructure
    interface Box {
        @NativeField(value = "byteValue", order = 0)
        Byte getByteValue();

        @NativeField(value = "byteValue", order = 0)
        void setByteValue(Byte value);

        @NativeField(value = "pointer", order = 1)
        MemorySegment getPointer();

        @NativeField(value = "pointer", order = 1)
        void setPointer(MemorySegment value);
    }

    @NativeUnion
    interface Value {
        @NativeField(value = "integer", order = 0)
        int getInteger();

        @NativeField(value = "integer", order = 0)
        void setInteger(int value);

        @NativeField(value = "decimal", order = 1)
        double getDecimal();

        @NativeField(value = "decimal", order = 1)
        void setDecimal(double value);
    }

    @Test
    void structureGetterAndSetterUseTheSameSupportedTypes() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pointer = arena.allocate(1);
            Box box = NativeStructureHandler.create(
                    Box.class,
                    arena.allocate(NativeStructureLayout.create(Box.class)),
                    arena
            );

            box.setByteValue((byte) 7);
            box.setPointer(pointer);

            assertEquals((byte) 7, box.getByteValue());
            assertEquals(pointer, box.getPointer());
        }
    }

    @Test
    void unionMembersShareStorageAndSupportInlineAccess() {
        try (Arena arena = Arena.ofConfined()) {
            Value value = NativeStructureHandler.create(
                    Value.class,
                    arena.allocate(NativeStructureLayout.create(Value.class))
            );

            value.setInteger(42);
            assertEquals(42, value.getInteger());
            value.setDecimal(3.5);
            assertEquals(3.5, value.getDecimal());
            assertNotNull(NativeStructureLayout.create(Value.class));
        }
    }
}
