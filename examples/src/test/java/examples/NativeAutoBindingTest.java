package examples;

import examples.c.CExample;
import examples.rust.RustExample;
import io.github.fassionfruit.FFM;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NativeAutoBindingTest {
    @Test
    void generatesRustStaticBindingsAndStructures() {
        assertEquals(30, RustExample.add(10, 20));
        assertEquals(42, RustExample.multiply(6, 7));
        assertEquals(18, RustExample.subtract(30, 12));
        assertEquals("0.5.0", RustExample.get_version());

        try (Arena arena = Arena.ofConfined()) {
            RustExample.Point point = FFM.allocateStructure(RustExample.Point.class, arena);
            point.setX(10);
            point.setY(20);
            assertEquals(30, RustExample.rust_point_sum(point));

            RustExample.PointArray values = FFM.allocateStructure(RustExample.PointArray.class, arena);
            values.getValues().set(0, 10);
            values.getValues().set(1, 20);
            values.getValues().set(2, 30);
            assertEquals(60, RustExample.rust_array_sum(values));
        }
    }

    @Test
    void generatesCStaticBindingsStructuresAndUnions() {
        assertEquals(30, CExample.native_add(10, 20));
        assertEquals("Hello from C", CExample.native_message());
        assertEquals(5, CExample.native_string_length("fruit"));

        try (Arena arena = Arena.ofConfined()) {
            CExample.Point point = FFM.allocateStructure(CExample.Point.class, arena);
            point.setX(12);
            point.setY(30);
            assertEquals(42, CExample.native_point_sum(point));

            CExample.IntArray values = FFM.allocateStructure(CExample.IntArray.class, arena);
            values.getValues().set(0, 10);
            values.getValues().set(1, 20);
            values.getValues().set(2, 30);
            assertEquals(60, CExample.native_array_sum(values));

            CExample.Value value = FFM.allocateStructure(CExample.Value.class, arena);
            value.setInteger(42);
            assertEquals(42, CExample.native_value_integer(value));
        }
    }
}
