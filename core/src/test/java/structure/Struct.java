import io.github.fassionfruit.FFM;
import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.annotation.NativeSymbol;
import io.github.fassionfruit.annotation.structure.NativeField;
import io.github.fassionfruit.annotation.structure.NativeStructure;
import io.github.fassionfruit.structure.NativeArray;
import io.github.fassionfruit.structure.NativeStructureHandler;
import io.github.fassionfruit.structure.NativeStructureLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NativeStructure
@NativeLibrary("../native/struct")
public interface Vector3d {

    @NativeField(value = "x", order = 0)
    float getX();

    @NativeField(value = "x", order = 0)
    void setX(float value);

    @NativeField(value = "y", order = 1)
    float getY();

    @NativeField(value = "y", order = 1)
    void setY(float value);

    @NativeField(value = "z", order = 2)
    float getZ();

    @NativeField(value = "z", order = 2)
    void setZ(float value);

    @NativeSymbol("calculate_length")
    float calculateLength(Vector3d vector);

    @NativeSymbol("create_vector")
    Vector3d createVector(float x, float y, float z);
}

@NativeStructure
interface Vector2 {

    @NativeField(value = "x", order = 0)
    float getX();

    @NativeField(value = "x", order = 0)
    void setX(float value);

    @NativeField(value = "y", order = 1)
    float getY();

    @NativeField(value = "y", order = 1)
    void setY(float value);
}

@NativeLibrary("../native/struct")
@NativeStructure
interface Polygon {

    @NativeField(value = "points", order = 0, length = 3)
    NativeArray<Vector2> getPoints();

    @NativeField(value = "count", order = 1)
    int getCount();

    @NativeField(value = "count", order = 1)
    void setCount(int value);

    @NativeSymbol("calculate_polygon_sum")
    float calculateSum(Polygon polygon);
}

@Test
void main() {
    System.out.println("========[Structure]========");
    try (Arena arena = Arena.ofConfined()) {
        var vector = FFM.allocateStructure(Vector3d.class, arena);
        var library = FFM.load(Vector3d.class);

        vector.setX(1.0f);
        vector.setY(2.0f);
        vector.setZ(3.0f);

        float result = library.calculateLength(vector);

        System.out.println(result);

        vector = library.createVector(1.0f, 2.0f, 3.0f);

        System.out.println(vector.getX());
        System.out.println(vector.getY());
        System.out.println(vector.getZ());
    } catch (Exception e) {
        e.printStackTrace();
    }

    try (Arena arena = Arena.ofConfined()) {
        Polygon polygon = FFM.allocateStructure(Polygon.class, arena);

        NativeArray<Vector2> points = polygon.getPoints();

        points.get(0).setX(1.0f);
        points.get(0).setY(2.0f);

        points.get(1).setX(3.0f);
        points.get(1).setY(4.0f);

        points.get(2).setX(5.0f);
        points.get(2).setY(6.0f);

        polygon.setCount(3);

        var library = FFM.load(Polygon.class);

        System.out.println("polygon sum: " + library.calculateSum(polygon));
    }

    try (Arena arena = Arena.ofConfined()) {
        GroupLayout layout = NativeStructureLayout.create(Data.class);

        MemorySegment dataSegment = arena.allocate(layout);
        MemorySegment values = arena.allocate(
                ValueLayout.JAVA_FLOAT,
                3
        );

        values.setAtIndex(ValueLayout.JAVA_FLOAT, 0, 10.0f);
        values.setAtIndex(ValueLayout.JAVA_FLOAT, 1, 20.0f);
        values.setAtIndex(ValueLayout.JAVA_FLOAT, 2, 30.0f);

        Data data = NativeStructureHandler.create(Data.class, dataSegment);

        data.setId(1);
        data.setValues(values);
        data.setCount(3);

        assertEquals(values.address(), data.getValues().address());
        assertEquals(3, data.getCount());
    }
}

@NativeStructure
public interface Data {
    @NativeField(value = "id", order = 0)
    int getId();

    @NativeField(value = "id", order = 0)
    void setId(int id);

    @NativeField(value = "values", order = 1)
    MemorySegment getValues();

    @NativeField(value = "values", order = 1)
    void setValues(MemorySegment values);

    @NativeField(value = "count", order = 2)
    int getCount();

    @NativeField(value = "count", order = 2)
    void setCount(int count);
}