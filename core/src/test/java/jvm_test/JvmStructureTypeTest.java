import io.github.fassionfruit.FFM;
import io.github.fassionfruit.annotation.NativeEnumValue;
import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.annotation.NativeSymbol;
import io.github.fassionfruit.annotation.structure.JvmMethod;
import io.github.fassionfruit.annotation.structure.JvmStructure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@JvmStructure
public interface PlayerTypes {

    @JvmMethod(order = 0)
    long getScore();

    @JvmMethod(order = 1)
    float getSpeed();

    @JvmMethod(order = 2)
    double calculate(int count, double multiplier);

    @JvmMethod(order = 3)
    char getGrade();

    @JvmMethod(order = 4)
    boolean isAlive();

    @JvmMethod(order = 5)
    Mode getMode();
}

public class PlayerTypesImpl implements PlayerTypes {

    @Override
    public long getScore() {
        return 9876543210L;
    }

    @Override
    public float getSpeed() {
        return 4.5f;
    }

    @Override
    public double calculate(int count, double multiplier) {
        return count * multiplier;
    }

    @Override
    public char getGrade() {
        return 'A';
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    @Override
    public Mode getMode() {
        return Mode.COMBAT;
    }
}

enum Mode {
    @NativeEnumValue(0)
    NORMAL,

    @NativeEnumValue(1)
    COMBAT,

    @NativeEnumValue(2)
    DEAD
}

@NativeLibrary("../native/jvm_structure_test")
interface PlayerC {
    @NativeSymbol("test_player_types")
    int testPlayerTypes(MemorySegment player);
}

@Test
void main() {
    var library = FFM.load(PlayerC.class);

    try (Arena arena = Arena.ofConfined()) {
        PlayerTypes player = new PlayerTypesImpl();

        MemorySegment segment = FFM.export(
                PlayerTypes.class,
                player,
                arena
        );

        assertEquals(0, library.testPlayerTypes(segment));
    }
}