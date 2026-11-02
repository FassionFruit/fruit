import io.github.fassionfruit.FFM;
import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.annotation.NativeSymbol;
import io.github.fassionfruit.annotation.structure.JvmMethod;
import io.github.fassionfruit.annotation.structure.JvmStructure;
import org.junit.jupiter.api.Test;

@JvmStructure
public interface Player {

    @JvmMethod(order = 0)
    void showDeathAnimation();

    @JvmMethod(order = 1)
    int getHealth();

    @JvmMethod(order = 2)
    int addHealth(int amount);
}

public class PlayerImpl implements Player {

    private int health = 100;

    @Override
    public void showDeathAnimation() {
        System.out.println("Death animation");
    }

    @Override
    public int getHealth() {
        return health;
    }

    @Override
    public int addHealth(int amount) {
        health += amount;
        return health;
    }
}

@NativeLibrary("../native/player")
interface PlayerC {
    @NativeSymbol("test_player")
    int testPlayer(MemorySegment segment);
}

@Test
void main() {
    System.out.println("======[JvmTest]======");
    var library = FFM.load(PlayerC.class);

    try (Arena arena = Arena.ofConfined()) {
        Player player = new PlayerImpl();

        MemorySegment segment = FFM.export(
                Player.class,
                player,
                arena
        );

        int result = library.testPlayer(segment);

        System.out.println(result);
    }
}