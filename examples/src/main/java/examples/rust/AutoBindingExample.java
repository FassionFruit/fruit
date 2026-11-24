package examples.rust;

import io.github.fassionfruit.FFM;

import java.lang.foreign.Arena;

public final class AutoBindingExample {
    private AutoBindingExample() {
    }

    public static void main(String[] args) {
        System.out.println(RustExample.add(10, 20));
        System.out.println(RustExample.multiply(6, 7));
        System.out.println(RustExample.subtract(30, 12));
        System.out.println(RustExample.get_version());

        try (Arena arena = Arena.ofConfined()) {
            RustExample.Point point = FFM.allocateStructure(RustExample.Point.class, arena);
            point.setX(10);
            point.setY(20);
            System.out.println(RustExample.rust_point_sum(point));
        }
    }
}
