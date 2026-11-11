package examples.structures;

import io.github.fassionfruit.FFM;
import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.annotation.NativeSymbol;
import io.github.fassionfruit.annotation.structure.NativeField;
import io.github.fassionfruit.annotation.structure.NativeStructure;

import java.lang.foreign.Arena;

public final class StructureExample {
    private StructureExample() {
    }

    @NativeStructure
    interface Point {
        @NativeField(value = "x", order = 0)
        int getX();

        @NativeField(value = "x", order = 0)
        void setX(int value);

        @NativeField(value = "y", order = 1)
        int getY();

        @NativeField(value = "y", order = 1)
        void setY(int value);
    }

    @NativeLibrary("native_example")
    interface Library {
        @NativeSymbol("native_point_sum")
        int sum(Point point);
    }

    public static void main(String[] args) {
        Library library = FFM.load(Library.class);

        try (Arena arena = Arena.ofConfined()) {
            Point point = FFM.allocateStructure(Point.class, arena);
            point.setX(10);
            point.setY(20);

            System.out.println(library.sum(point));
        }
    }
}
