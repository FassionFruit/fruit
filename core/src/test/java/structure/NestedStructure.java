import io.github.fassionfruit.FFM;
import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.annotation.NativeSymbol;
import io.github.fassionfruit.annotation.structure.NativeField;
import io.github.fassionfruit.annotation.structure.NativeStructure;
import io.github.fassionfruit.structure.NativeStructureLayout;
import org.junit.jupiter.api.Test;
import java.lang.foreign.Arena;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NativeStructure
public interface Vector2 {

    @NativeField(value = "x", order = 0)
    float getX();

    @NativeField(value = "x", order = 0)
    void setX(float value);

    @NativeField(value = "y", order = 1)
    float getY();

    @NativeField(value = "y", order = 1)
    void setY(float value);
}

@NativeStructure
public interface Entity {

    @NativeField(value = "position", order = 0)
    Vector2 getPosition();

    @NativeField(value = "position", order = 0)
    void setPosition(Vector2 position);

    @NativeField(value = "velocity", order = 1)
    Vector2 getVelocity();

    @NativeField(value = "velocity", order = 1)
    void setVelocity(Vector2 velocity);
}

@NativeLibrary("../native/entity")
public interface EntityNativeLibrary {

    @NativeSymbol("initialize_entity")
    void initializeEntity(Entity entity);

    @NativeSymbol("calculate_entity")
    float calculateEntity(Entity entity);

    @NativeSymbol("move_entity")
    void moveEntity(Entity entity, float x, float y);

    @NativeSymbol("get_entity_size")
    long getEntitySize();

    @NativeSymbol("get_position_offset")
    long getPositionOffset();

    @NativeSymbol("get_velocity_offset")
    long getVelocityOffset();
}

public class NestedStructureAbiTest {
    void shouldShareNativeMemoryWithC() {
        var library = FFM.load(EntityNativeLibrary.class);

        try (Arena arena = Arena.ofConfined()) {
            Entity entity = FFM.allocateStructure(Entity.class, arena);

            System.out.println("========[Java -> C]========");

            entity.getPosition().setX(3.0f);
            entity.getPosition().setY(4.0f);

            entity.getVelocity().setX(5.0f);
            entity.getVelocity().setY(6.0f);

            float result = library.calculateEntity(entity);

            System.out.println("C calculated: " + result);
            assertEquals(18.0f, result);

            System.out.println("========[C -> Java]========");

            library.initializeEntity(entity);

            assertEquals(10.0f, entity.getPosition().getX());
            assertEquals(20.0f, entity.getPosition().getY());
            assertEquals(1.0f, entity.getVelocity().getX());
            assertEquals(2.0f, entity.getVelocity().getY());

            System.out.println("Position: "
                    + entity.getPosition().getX() + ", "
                    + entity.getPosition().getY());

            System.out.println("Velocity: "
                    + entity.getVelocity().getX() + ", "
                    + entity.getVelocity().getY());

            System.out.println("========[C modifies Java memory]========");

            library.moveEntity(entity, 5.0f, 10.0f);

            assertEquals(15.0f, entity.getPosition().getX());
            assertEquals(30.0f, entity.getPosition().getY());

            System.out.println("Position after move: "
                    + entity.getPosition().getX() + ", "
                    + entity.getPosition().getY());

            System.out.println("========[ABI Layout]========");

            long javaSize = NativeStructureLayout.create(Entity.class).byteSize();
            long javaPositionOffset = NativeStructureLayout.offset(
                    Entity.class, "position"
            );
            long javaVelocityOffset = NativeStructureLayout.offset(
                    Entity.class, "velocity"
            );

            assertEquals(library.getEntitySize(), javaSize);
            assertEquals(library.getPositionOffset(), javaPositionOffset);
            assertEquals(library.getVelocityOffset(), javaVelocityOffset);

            System.out.println("C Entity size: " + library.getEntitySize());
            System.out.println("Java Entity size: " + javaSize);

            System.out.println("C position offset: " + library.getPositionOffset());
            System.out.println("Java position offset: " + javaPositionOffset);

            System.out.println("C velocity offset: " + library.getVelocityOffset());
            System.out.println("Java velocity offset: " + javaVelocityOffset);
        }
    }
}

@Test
void main() {
    var str = new NestedStructureAbiTest();
    str.shouldShareNativeMemoryWithC();
}