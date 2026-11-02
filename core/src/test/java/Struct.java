import io.github.fassionfruit.FFM;
import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.annotation.NativeSymbol;
import io.github.fassionfruit.annotation.structure.NativeField;
import io.github.fassionfruit.annotation.structure.NativeStructure;
import org.junit.jupiter.api.Test;

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

@Test
void main() {
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
}