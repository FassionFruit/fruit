package examples.rust;

import io.github.fassionfruit.FFM;
import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.annotation.NativeSymbol;
import io.github.fassionfruit.annotation.structure.NativeField;
import io.github.fassionfruit.annotation.structure.NativeStructure;

import java.lang.foreign.Arena;

public final class RustExample {
    private RustExample() {
    }

    @NativeLibrary("native_example_rust")
    interface Library {
        @NativeSymbol("rust_add")
        int add(int left, int right);

        @NativeSymbol("rust_point_sum")
        int pointSum(Point point);
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

    public static void main(String[] args) {
        Library library = FFM.load(Library.class);
        System.out.println(library.add(10, 20));

        try (Arena arena = Arena.ofConfined()) {
            Point point = FFM.allocateStructure(Point.class, arena);
            point.setX(10);
            point.setY(20);

            System.out.println(library.pointSum(point));
        }
    }
}
